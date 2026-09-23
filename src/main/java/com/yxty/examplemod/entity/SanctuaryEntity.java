package com.yxty.examplemod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Consumer;

/** 只伤害亡灵的圣光法阵。 */
public class SanctuaryEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(SanctuaryEntity.class, EntityDataSerializers.FLOAT);
    private LivingEntity owner;
    private int ownerId;
    private float radius = 4.5F;
    private float halfHeight = 3.0F;
    private int durationTicks = 180;
    private int damageInterval = 10;
    private float damage = 4.0F;
    private boolean purified;
    private transient Consumer<Hit> hitCallback;

    public SanctuaryEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public void setup(LivingEntity owner, Vec3 center, float radius, float halfHeight,
                      int durationTicks, int damageInterval, float damage, boolean purified,
                      Consumer<Hit> hitCallback) {
        this.owner = owner;
        this.ownerId = owner.getId();
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.durationTicks = durationTicks;
        this.damageInterval = damageInterval;
        this.damage = damage;
        this.purified = purified;
        this.entityData.set(DATA_RADIUS, radius);
        this.hitCallback = hitCallback;
        setPos(center);
    }

    @Override
    public void tick() {
        super.tick();
        owner = resolveOwner();
        if (level().isClientSide) {
            return;
        }
        if (owner == null || !owner.isAlive() || tickCount > durationTicks) {
            discard();
            return;
        }

        spawnHolyLight();
        if (purified || tickCount % damageInterval == 0) {
            damageUndead();
        }
    }

    private void damageUndead() {
        AABB area = new AABB(
                getX() - radius, getY() - halfHeight, getZ() - radius,
                getX() + radius, getY() + halfHeight, getZ() + radius);
        DamageSource source = owner.damageSources().indirectMagic(owner, owner);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate.isAlive() && candidate.getMobType() == MobType.UNDEAD)) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            float before = target.getHealth() + target.getAbsorptionAmount();
            target.invulnerableTime = 0;
            float requestedDamage = purified ? Float.MAX_VALUE : damage;
            boolean accepted = target.hurt(source, requestedDamage);
            float after = target.getHealth() + target.getAbsorptionAmount();
            float actualDamage = accepted ? Math.max(0.0F, before - after) : 0.0F;
            if (actualDamage > 0.0F && hitCallback != null) {
                hitCallback.accept(new Hit(target, requestedDamage, actualDamage,
                        !target.isAlive(), source));
            }
        }
    }

    private void spawnHolyLight() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                getX(), getY(), getZ(),
                Math.max(8, (int) (radius * 2.0F)),
                radius * 0.7D, halfHeight * 0.65D, radius * 0.7D, 0.015D);
        if (tickCount % 2 == 0) {
            double groundY = getY() - halfHeight * 0.55D + 0.10D;
            serverLevel.sendParticles(ParticleTypes.WAX_ON,
                    getX(), groundY, getZ(),
                    Math.max(12, (int) (radius * 3.0F)),
                    radius * 0.72D, 0.08D, radius * 0.72D, 0.03D);
            if (tickCount % 4 == 0) {
                serverLevel.sendParticles(ParticleTypes.GLOW,
                        getX(), groundY + 0.04D, getZ(),
                        Math.max(5, (int) (radius * 1.4F)),
                        radius * 0.72D, 0.05D, radius * 0.72D, 0.005D);
            }
        }
    }

    /** Finds the top surface under a point so the client light field can follow terrain. */
    public double findSurfaceY(double x, double z) {
        BlockPos pos = BlockPos.containing(x, getY() + halfHeight, z);
        int minimumY = (int) Math.floor(getY() - halfHeight - 2.0D);

        while (pos.getY() >= minimumY) {
            BlockPos below = pos.below();
            BlockState belowState = level().getBlockState(below);
            if (belowState.isFaceSturdy(level(), below, Direction.UP)) {
                double collisionHeight = 0.0D;
                BlockState stateAtPos = level().getBlockState(pos);
                if (!stateAtPos.isAir()) {
                    VoxelShape shape = stateAtPos.getCollisionShape(level(), pos);
                    if (!shape.isEmpty()) {
                        collisionHeight = shape.max(Direction.Axis.Y);
                    }
                }
                return pos.getY() + collisionHeight;
            }
            pos = below;
        }
        return Double.NaN;
    }

    public float getVisualRadius() {
        return entityData.get(DATA_RADIUS);
    }

    private LivingEntity resolveOwner() {
        if (owner != null && owner.isAlive()) {
            return owner;
        }
        Entity found = level().getEntity(ownerId);
        if (found instanceof LivingEntity living) {
            owner = living;
        }
        return owner;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, 4.5F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public record Hit(LivingEntity target, float requestedDamage, float actualDamage,
                      boolean killed, DamageSource damageSource) {
    }
}

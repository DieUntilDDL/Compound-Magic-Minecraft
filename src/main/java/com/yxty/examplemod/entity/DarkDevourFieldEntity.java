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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * 黑暗吞噬的短时范围特效：客户端渲染贴地黑幕并生成墨水粒子，不修改任何方块。
 */
public class DarkDevourFieldEntity extends Entity {
    private static final EntityDataAccessor<Float> FIELD_WIDTH =
            SynchedEntityData.defineId(DarkDevourFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FIELD_LENGTH =
            SynchedEntityData.defineId(DarkDevourFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MAX_LIFETIME =
            SynchedEntityData.defineId(DarkDevourFieldEntity.class, EntityDataSerializers.INT);

    public DarkDevourFieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public void setup(Vec3 center, float yaw, float width, float length, int lifetime) {
        setPos(center);
        setYRot(yaw);
        entityData.set(FIELD_WIDTH, Math.max(0.5F, width));
        entityData.set(FIELD_LENGTH, Math.max(0.5F, length));
        entityData.set(MAX_LIFETIME, Math.max(1, lifetime));
        updateEffectBoundingBox();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FIELD_WIDTH, 7.0F);
        entityData.define(FIELD_LENGTH, 10.0F);
        entityData.define(MAX_LIFETIME, 36);
    }

    @Override
    public void tick() {
        super.tick();
        updateEffectBoundingBox();

        if (level().isClientSide) {
            spawnInkParticles();
            return;
        }

        if (tickCount >= getMaxLifetime()) {
            discard();
        }
    }

    private void spawnInkParticles() {
        int particleCount = tickCount < 8 ? 10 : 5;
        Vec3 forward = forwardVector();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);

        for (int i = 0; i < particleCount; i++) {
            double side = (random.nextDouble() - 0.5D) * getFieldWidth();
            double along = (random.nextDouble() - 0.5D) * getFieldLength();
            Vec3 point = position().add(right.scale(side)).add(forward.scale(along));
            double surfaceY = findSurfaceY(point.x, point.z);
            if (Double.isNaN(surfaceY)) {
                continue;
            }

            level().addParticle(
                    ParticleTypes.SQUID_INK,
                    point.x,
                    surfaceY + 0.08D + random.nextDouble() * 0.35D,
                    point.z,
                    (random.nextDouble() - 0.5D) * 0.035D,
                    0.04D + random.nextDouble() * 0.08D,
                    (random.nextDouble() - 0.5D) * 0.035D);
        }
    }

    public double findSurfaceY(double x, double z) {
        BlockPos pos = BlockPos.containing(x, getY() + 5.0D, z);
        int minimumY = (int) Math.floor(getY() - 3.0D);

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
                return pos.getY() + collisionHeight + 0.018D;
            }
            pos = below;
        }
        return Double.NaN;
    }

    public Vec3 forwardVector() {
        double yaw = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private void updateEffectBoundingBox() {
        double radius = Math.sqrt(
                getFieldWidth() * getFieldWidth() * 0.25D
                        + getFieldLength() * getFieldLength() * 0.25D) + 1.0D;
        setBoundingBox(new net.minecraft.world.phys.AABB(
                getX() - radius, getY() - 3.0D, getZ() - radius,
                getX() + radius, getY() + 5.0D, getZ() + radius));
    }

    public float getFieldWidth() {
        return entityData.get(FIELD_WIDTH);
    }

    public float getFieldLength() {
        return entityData.get(FIELD_LENGTH);
    }

    public int getMaxLifetime() {
        return entityData.get(MAX_LIFETIME);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("FieldWidth")) {
            entityData.set(FIELD_WIDTH, tag.getFloat("FieldWidth"));
        }
        if (tag.contains("FieldLength")) {
            entityData.set(FIELD_LENGTH, tag.getFloat("FieldLength"));
        }
        if (tag.contains("MaxLifetime")) {
            entityData.set(MAX_LIFETIME, tag.getInt("MaxLifetime"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("FieldWidth", getFieldWidth());
        tag.putFloat("FieldLength", getFieldLength());
        tag.putInt("MaxLifetime", getMaxLifetime());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

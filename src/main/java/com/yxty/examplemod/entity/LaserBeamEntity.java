package com.yxty.examplemod.entity;

import com.yxty.examplemod.item.WandItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class LaserBeamEntity extends Entity {
    private static final EntityDataAccessor<Float> BEAM_LENGTH = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_WIDTH = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.INT);

    private static final float MAX_RANGE = 32.0f;

    private LivingEntity owner;
    private Entity targetEntity;
    private Entity sourceEntity;
    private Entity hitEntity;
    private Vec3 hitPos;

    private float damagePerTick = 2.0f;
    private int maxLifeTime = 20;
    private boolean requireHold = false;
    private boolean piercing;
    private LaserChargeEntity linkedCharge;

    private transient BiConsumer<Entity, Float> onHitEntityCallback = null;
    private transient Consumer<Vec3> onTickCallback = null;

    public LaserBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
        this.sourceEntity = owner;
        if (owner != null) {
            this.entityData.set(OWNER_ID, owner.getId());
        }
        updatePositionAndRotation();
    }

    public LivingEntity getOwner() {
        return resolveOwner();
    }

    private LivingEntity resolveOwner() {
        if (this.owner != null && this.owner.isAlive()) {
            return this.owner;
        }
        int id = this.entityData.get(OWNER_ID);
        if (id > 0) {
            Entity found = this.level().getEntity(id);
            if (found instanceof LivingEntity living) {
                this.owner = living;
                if (this.sourceEntity == null) {
                    this.sourceEntity = living;
                }
                return living;
            }
        }
        return this.owner;
    }

    /**
     * 启用"实体对实体"模式
     */
    public void setLockOnMode(Entity source, Entity target) {
        this.sourceEntity = source;
        this.targetEntity = target;
        this.owner = (source instanceof LivingEntity) ? (LivingEntity) source : null;
        updatePositionAndRotation();
    }

    public void setDamage(float dmg) {
        this.damagePerTick = dmg;
    }

    public float getDamage() {
        return this.damagePerTick;
    }

    public void setMaxLifeTime(int ticks) {
        this.maxLifeTime = ticks;
    }

    public void setRequireHold(boolean requireHold) {
        this.requireHold = requireHold;
    }

    public void setPiercing(boolean piercing) {
        this.piercing = piercing;
    }

    public void setLinkedCharge(LaserChargeEntity charge) {
        this.linkedCharge = charge;
    }

    public void clearLinkedCharge() {
        this.linkedCharge = null;
    }

    public void setOnHitEntityCallback(BiConsumer<Entity, Float> callback) {
        this.onHitEntityCallback = callback;
    }

    public void setOnTickCallback(Consumer<Vec3> callback) {
        this.onTickCallback = callback;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            updatePositionAndRotation();
            return;
        }

        if (this.tickCount > this.maxLifeTime) {
            finishCasting();
            this.discard();
            return;
        }
        if (this.sourceEntity == null || !this.sourceEntity.isAlive()) {
            this.discard();
            return;
        }
        if (this.requireHold && !ownerStillCasting()) {
            this.discard();
            return;
        }

        updatePositionAndRotation();
        performRayCastAndDamage();
        updateBoundingBox();

        if (this.onTickCallback != null && this.hitPos != null) {
            this.onTickCallback.accept(this.hitPos);
        }
    }

    private void updatePositionAndRotation() {
        this.owner = resolveOwner();
        if (this.sourceEntity == null && this.owner != null) {
            this.sourceEntity = this.owner;
        }
        if (this.sourceEntity == null) {
            return;
        }

        Vec3 startPos = (this.sourceEntity instanceof LivingEntity living)
                ? LaserChargeEntity.attachPos(living)
                : this.sourceEntity.position().add(0, this.sourceEntity.getBbHeight() * 0.5, 0);

        if (this.targetEntity != null && this.targetEntity.isAlive()) {
            Vec3 targetPos = (this.targetEntity instanceof LivingEntity living)
                    ? living.getEyePosition()
                    : this.targetEntity.position().add(0, this.targetEntity.getBbHeight() * 0.5, 0);

            Vec3 dir = targetPos.subtract(startPos);
            double dist = dir.length();
            this.setPos(startPos);

            double d0 = dir.x;
            double d1 = dir.y;
            double d2 = dir.z;
            double d3 = Math.sqrt(d0 * d0 + d2 * d2);

            float yRot = (float) (Math.toDegrees(Math.atan2(d2, d0)) - 90.0D);
            float xRot = (float) (-(Math.toDegrees(Math.atan2(d1, d3))));
            this.setRot(yRot, xRot);
            this.entityData.set(BEAM_LENGTH, (float) Math.min(dist, MAX_RANGE));
            return;
        }

        if (this.owner != null) {
            // 从蓄力球中心射出，避免光束从眼睛穿出蓄力球
            this.setPos(LaserChargeEntity.attachPos(this.owner));
            this.setRot(this.owner.getYRot(), this.owner.getXRot());
        }
    }

    private void performRayCastAndDamage() {
        float currentLen;

        if (this.targetEntity != null) {
            currentLen = this.entityData.get(BEAM_LENGTH);
            if (this.tickCount % 4 == 0 && this.targetEntity instanceof LivingEntity livingTarget) {
                applyHit(livingTarget);
            }
        } else {
            Vec3 start = this.position();
            Vec3 lookVec = this.getLookAngle();
            Vec3 end = start.add(lookVec.scale(MAX_RANGE));

            HitResult blockHit = this.level().clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            double dist = MAX_RANGE;
            if (blockHit.getType() != HitResult.Type.MISS) {
                dist = blockHit.getLocation().distanceTo(start);
            }

            Vec3 effectiveEnd = start.add(lookVec.scale(dist));
            AABB searchBox = new AABB(start, effectiveEnd).inflate(1.0D);
            List<EntityHitResult> entityHits = this.level().getEntities(
                            this, searchBox,
                            e -> !e.isSpectator() && e != this.owner && e.isPickable())
                    .stream()
                    .map(entity -> clipEntity(entity, start, effectiveEnd))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparingDouble(
                            hit -> start.distanceToSqr(hit.getLocation())))
                    .toList();

            if (!entityHits.isEmpty()) {
                EntityHitResult firstHit = entityHits.get(0);
                this.hitPos = firstHit.getLocation();
                this.hitEntity = firstHit.getEntity();
                if (this.tickCount % 4 == 0) {
                    if (piercing) {
                        for (EntityHitResult entityHit : entityHits) {
                            applyHit(entityHit.getEntity());
                        }
                    } else {
                        applyHit(this.hitEntity);
                    }
                }
                if (!piercing) {
                    dist = Math.min(dist, start.distanceTo(firstHit.getLocation()));
                }
            } else {
                this.hitEntity = null;
                this.hitPos = null;
            }
            currentLen = (float) dist;
        }

        this.entityData.set(BEAM_LENGTH, currentLen);
    }

    private Optional<EntityHitResult> clipEntity(Entity candidate, Vec3 start, Vec3 end) {
        AABB bounds = candidate.getBoundingBox().inflate(
                Math.max(0.2D, getBeamWidth() * 0.75D));
        if (bounds.contains(start)) {
            return Optional.of(new EntityHitResult(candidate, start));
        }
        return bounds.clip(start, end).map(location ->
                new EntityHitResult(candidate, location));
    }

    private void applyHit(Entity target) {
        float actualDamage = 0.0f;
        if (target instanceof LivingEntity livingHit) {
            float before = livingHit.getHealth() + livingHit.getAbsorptionAmount();
            livingHit.invulnerableTime = 0;
            livingHit.hurt(this.damageSources().magic(), this.damagePerTick);
            float after = livingHit.getHealth() + livingHit.getAbsorptionAmount();
            actualDamage = Math.max(0.0f, before - after);
        }
        if (this.onHitEntityCallback != null) {
            this.onHitEntityCallback.accept(target, actualDamage);
        }
    }

    private boolean ownerStillCasting() {
        this.owner = resolveOwner();
        if (this.owner == null || !this.owner.isAlive()) {
            return false;
        }
        if (this.owner instanceof Player player) {
            return player.isUsingItem() && player.getUseItem().getItem() instanceof WandItem;
        }
        return true;
    }

    private void updateBoundingBox() {
        Vec3 start = this.position();
        Vec3 end = start.add(this.getLookAngle().scale(Math.max(0.1, getBeamLength())));
        this.setBoundingBox(new AABB(start, end).inflate(Math.max(1.0, getBeamWidth())));
    }

    private void finishCasting() {
        if (this.owner instanceof Player player && player.isUsingItem()) {
            player.stopUsingItem();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.linkedCharge != null && !this.linkedCharge.isRemoved()) {
            LaserChargeEntity charge = this.linkedCharge;
            this.linkedCharge = null;
            charge.clearLinkedLaser();
            charge.discard();
        }
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BEAM_LENGTH, 0.0f);
        this.entityData.define(BEAM_WIDTH, 0.25f);
        this.entityData.define(OWNER_ID, 0);
    }

    public float getBeamLength() {
        return this.entityData.get(BEAM_LENGTH);
    }

    public float getBeamWidth() {
        return this.entityData.get(BEAM_WIDTH);
    }

    public void setBeamWidth(float w) {
        this.entityData.set(BEAM_WIDTH, w);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

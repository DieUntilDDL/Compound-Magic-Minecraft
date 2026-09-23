package com.yxty.examplemod.entity;

import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.item.WandItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.BiConsumer;

public class LaserChargeEntity extends Entity {
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(LaserChargeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FULLY_CHARGED = SynchedEntityData.defineId(LaserChargeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(LaserChargeEntity.class, EntityDataSerializers.INT);

    // 按默认 70° FOV、16:9 估算：垂直半角约 21.5°，球半径 0.25 / 距离 1.05 ≈ 13.4°。
    // 球心放在视线下方 30°，屏幕底部大约只露出球体高度的 20%。
    private static final double ATTACH_DISTANCE = 1.05;
    private static final double ATTACH_DOWN_ANGLE = Math.toRadians(30.0);

    private LivingEntity owner;
    private int maxChargeTime = 20;
    private float finalLaserDamage = 2.0f;
    private int finalLaserDuration = 60;
    private float finalBeamWidth = 0.3f;
    private boolean finalLaserPiercing;
    private LaserBeamEntity spawnedLaser;
    private transient BiConsumer<Entity, Float> onHitEntityCallback;

    public LaserChargeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void setup(LivingEntity owner, int chargeTime, float laserDamage, int laserDuration,
                      float beamWidth, boolean piercing, BiConsumer<Entity, Float> onHit) {
        this.owner = owner;
        this.maxChargeTime = Math.max(1, chargeTime);
        this.finalLaserDamage = laserDamage;
        this.finalLaserDuration = laserDuration;
        this.finalBeamWidth = beamWidth;
        this.finalLaserPiercing = piercing;
        this.onHitEntityCallback = onHit;
        this.entityData.set(OWNER_ID, owner.getId());
        this.setPos(frontOfOwner());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SCALE, 0.1f);
        this.entityData.define(FULLY_CHARGED, false);
        this.entityData.define(OWNER_ID, 0);
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity follow = resolveOwner();
        if (follow != null) {
            this.setPos(attachPos(follow));
        }

        if (level().isClientSide) {
            return;
        }

        if (!ownerStillCasting()) {
            discardWithBeam();
            return;
        }

        if (!isFullyCharged()) {
            float progress = Math.min(1.0f, (float) this.tickCount / (float) this.maxChargeTime);
            this.entityData.set(SCALE, 0.1f + progress * 0.9f);

            if (this.tickCount >= this.maxChargeTime) {
                this.entityData.set(FULLY_CHARGED, true);
                this.entityData.set(SCALE, 1.0f);
                spawnLaser();
            }
        } else if (this.spawnedLaser == null || this.spawnedLaser.isRemoved()) {
            if (this.owner instanceof Player player) {
                player.stopUsingItem();
            }
            this.discard();
        }
    }

    private void spawnLaser() {
        if (this.spawnedLaser != null || this.owner == null) {
            return;
        }

        LaserBeamEntity laser = new LaserBeamEntity(ModEntityTypes.LASER_BEAM.get(), level());
        laser.setOwner(this.owner);
        laser.setDamage(this.finalLaserDamage);
        laser.setMaxLifeTime(this.finalLaserDuration);
        laser.setBeamWidth(this.finalBeamWidth);
        laser.setPiercing(this.finalLaserPiercing);
        laser.setRequireHold(true);
        laser.setLinkedCharge(this);
        laser.setOnHitEntityCallback(this.onHitEntityCallback);
        level().addFreshEntity(laser);
        this.spawnedLaser = laser;

        level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS,
                0.8f,
                0.7f + this.random.nextFloat() * 0.2f);
    }

    /**
     * 摄像机空间挂点：沿视线前进，再沿屏幕下方偏转，使球体始终贴在画面底部。
     * 低头时 camDown 会跟着镜头转，球体不会顶到准星上挡住光束。
     */
    public static Vec3 attachPos(LivingEntity owner) {
        return attachPos(owner, 1.0f);
    }

    public static Vec3 attachPos(LivingEntity owner, float partialTick) {
        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        Vec3 camDown = cameraDown(owner, look);
        double cos = Math.cos(ATTACH_DOWN_ANGLE);
        double sin = Math.sin(ATTACH_DOWN_ANGLE);
        return eye.add(look.scale(cos * ATTACH_DISTANCE).add(camDown.scale(sin * ATTACH_DISTANCE)));
    }

    private static Vec3 cameraDown(LivingEntity owner, Vec3 look) {
        double yawRad = Math.toRadians(owner.getYRot());
        Vec3 right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
        Vec3 camUp = right.cross(look);
        if (camUp.lengthSqr() < 1.0E-6) {
            camUp = new Vec3(0.0, 1.0, 0.0);
        } else {
            camUp = camUp.normalize();
        }
        return camUp.scale(-1.0);
    }

    private Vec3 frontOfOwner() {
        return attachPos(this.owner);
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
                return living;
            }
        }
        return this.owner;
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

    private void discardWithBeam() {
        if (this.spawnedLaser != null && !this.spawnedLaser.isRemoved()) {
            LaserBeamEntity beam = this.spawnedLaser;
            this.spawnedLaser = null;
            beam.clearLinkedCharge();
            beam.discard();
        }
        this.discard();
    }

    public void clearLinkedLaser() {
        this.spawnedLaser = null;
    }

    public float getScale() {
        return this.entityData.get(SCALE);
    }

    public boolean isFullyCharged() {
        return this.entityData.get(FULLY_CHARGED);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.spawnedLaser != null && !this.spawnedLaser.isRemoved()) {
            LaserBeamEntity beam = this.spawnedLaser;
            this.spawnedLaser = null;
            beam.clearLinkedCharge();
            beam.discard();
        }
        super.remove(reason);
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

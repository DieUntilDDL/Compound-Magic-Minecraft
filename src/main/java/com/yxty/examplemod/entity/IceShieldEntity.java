package com.yxty.examplemod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/** 一层围绕受保护者旋转的可视冰盾。攻击拦截由 MagicStatusManager 处理。 */
public class IceShieldEntity extends Entity {
    private static final float ORBIT_SPEED_DEGREES_PER_TICK = 5.5F;
    private static final EntityDataAccessor<Integer> PROTECTED_ID =
            SynchedEntityData.defineId(IceShieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ORBIT_OFFSET =
            SynchedEntityData.defineId(IceShieldEntity.class, EntityDataSerializers.FLOAT);

    private LivingEntity protectedEntity;

    public IceShieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public void setup(LivingEntity protectedEntity, float orbitOffsetDegrees) {
        this.protectedEntity = protectedEntity;
        entityData.set(PROTECTED_ID, protectedEntity.getId());
        setOrbitOffsetDegrees(orbitOffsetDegrees);
        updateOrbitPosition();
    }

    /** 设置该盾牌在整个盾阵中的等分相位。服务端修改后会自动同步到客户端。 */
    public void setOrbitOffsetDegrees(float orbitOffsetDegrees) {
        float normalized = orbitOffsetDegrees % 360.0F;
        entityData.set(ORBIT_OFFSET, normalized < 0.0F ? normalized + 360.0F : normalized);
    }

    @Override
    public void tick() {
        super.tick();
        protectedEntity = resolveProtectedEntity();
        if (protectedEntity == null || !protectedEntity.isAlive()) {
            if (!level().isClientSide || tickCount > 20) {
                discard();
            }
            return;
        }
        updateOrbitPosition();
    }

    private void updateOrbitPosition() {
        if (protectedEntity == null) {
            return;
        }
        // 使用所有冰盾共享的世界时间，避免不同生成时刻导致相位逐渐错开。
        // 7200 tick 后恰好转过110整圈；先取模可避免长时间运行后的浮点精度抖动。
        double angleDegrees = entityData.get(ORBIT_OFFSET)
                + (level().getGameTime() % 7200L) * ORBIT_SPEED_DEGREES_PER_TICK;
        double angle = Math.toRadians(angleDegrees);
        double orbitRadius = Math.max(1.05D, protectedEntity.getBbWidth() * 0.8D + 0.75D);
        Vec3 center = protectedEntity.position().add(
                0.0D, protectedEntity.getBbHeight() * 0.55D, 0.0D);
        setPos(center.x + Math.cos(angle) * orbitRadius,
                center.y,
                center.z + Math.sin(angle) * orbitRadius);
    }

    /**
     * 渲染器使用保护者与冰盾的实际位置计算朝向，避免把公转相位写入实体旋转角，
     * 从而产生网络插值导致的额外自转。
     */
    @Nullable
    public LivingEntity getProtectedEntity() {
        return resolveProtectedEntity();
    }

    private LivingEntity resolveProtectedEntity() {
        if (protectedEntity != null && protectedEntity.isAlive()) {
            return protectedEntity;
        }
        Entity found = level().getEntity(entityData.get(PROTECTED_ID));
        if (found instanceof LivingEntity living) {
            protectedEntity = living;
        }
        return protectedEntity;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PROTECTED_ID, 0);
        entityData.define(ORBIT_OFFSET, 0.0F);
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
}

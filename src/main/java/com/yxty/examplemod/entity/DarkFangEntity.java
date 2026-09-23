package com.yxty.examplemod.entity;

import com.yxty.examplemod.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * 只负责播放原版唤魔者尖牙模型和动画，不执行原版固定 6 点范围伤害。
 * 黑暗吞噬的处决、反噬和击杀归因全部由魔法类负责。
 */
public class DarkFangEntity extends EvokerFangs {
    private int visualWarmupTicks;
    private int visualLifeTicks = 22;
    private boolean attackEventSent;

    public DarkFangEntity(EntityType<? extends EvokerFangs> type, Level level) {
        super(type, level);
    }

    public DarkFangEntity(Level level, double x, double y, double z,
                          float yawRadians, int warmupTicks, LivingEntity owner) {
        this(ModEntityTypes.DARK_FANG.get(), level);
        this.visualWarmupTicks = Math.max(0, warmupTicks);
        this.setOwner(owner);
        this.setYRot(yawRadians * (180.0f / (float) Math.PI));
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            // 客户端沿用原版动画计时和粒子逻辑。
            super.tick();
            return;
        }

        // 服务端不调用 EvokerFangs.tick()，从而完全跳过其范围伤害。
        if (--visualWarmupTicks < 0) {
            if (!attackEventSent) {
                level().broadcastEntityEvent(this, (byte) 4);
                attackEventSent = true;
            }
            if (--visualLifeTicks < 0) {
                discard();
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        visualWarmupTicks = tag.getInt("VisualWarmup");
        visualLifeTicks = tag.contains("VisualLife") ? tag.getInt("VisualLife") : 22;
        attackEventSent = tag.getBoolean("AttackEventSent");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VisualWarmup", visualWarmupTicks);
        tag.putInt("VisualLife", visualLifeTicks);
        tag.putBoolean("AttackEventSent", attackEventSent);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

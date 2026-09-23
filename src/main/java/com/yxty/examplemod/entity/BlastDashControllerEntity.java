package com.yxty.examplemod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.BiConsumer;

/** 分帧推进玩家并在终点短暂停顿的服务端控制实体。 */
public class BlastDashControllerEntity extends Entity {
    private transient Player owner;
    private transient BiConsumer<Player, Vec3> onFinished;
    private Vec3 stepMovement = Vec3.ZERO;
    private Vec3 dashDirection = Vec3.ZERO;
    private Vec3 endPosition = Vec3.ZERO;
    private int dashTicksRemaining;
    private int pauseTicksRemaining;
    private boolean dashFinished;

    public BlastDashControllerEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(Player owner, Vec3 direction, double distance,
                      int dashTicks, int pauseTicks,
                      BiConsumer<Player, Vec3> onFinished) {
        this.owner = owner;
        this.dashDirection = direction.normalize();
        this.dashTicksRemaining = Math.max(1, dashTicks);
        this.pauseTicksRemaining = Math.max(0, pauseTicks);
        this.stepMovement = this.dashDirection.scale(distance / this.dashTicksRemaining);
        this.onFinished = onFinished;
        this.endPosition = owner.position();
        setPos(owner.position());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (owner == null || !owner.isAlive()
                || owner instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()) {
            discard();
            return;
        }

        if (dashTicksRemaining > 0) {
            applyPlayerMotion(stepMovement);
            dashTicksRemaining--;
            setPos(owner.position());
            spawnDashTrail(owner);
            return;
        }

        if (!dashFinished) {
            beginPause();
            return;
        }

        if (pauseTicksRemaining > 0) {
            // 终点停顿期间清除惯性，使爆炸击退方向更清晰。
            applyPlayerMotion(Vec3.ZERO);
            owner.resetFallDistance();
            pauseTicksRemaining--;
            setPos(endPosition);
            return;
        }

        BiConsumer<Player, Vec3> callback = onFinished;
        onFinished = null;
        if (callback != null) {
            callback.accept(owner, endPosition);
        }
        discard();
    }

    private void beginPause() {
        if (dashFinished) {
            return;
        }
        dashFinished = true;
        endPosition = owner.position();
        setPos(endPosition);
        applyPlayerMotion(Vec3.ZERO);
        owner.resetFallDistance();
        if (pauseTicksRemaining <= 0) {
            BiConsumer<Player, Vec3> callback = onFinished;
            onFinished = null;
            if (callback != null) {
                callback.accept(owner, endPosition);
            }
            discard();
        }
    }

    private void applyPlayerMotion(Vec3 motion) {
        owner.setDeltaMovement(motion);
        owner.hasImpulse = true;
        owner.hurtMarked = true;
        if (owner instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
    }

    private void spawnDashTrail(Player player) {
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 trailCenter = player.getBoundingBox().getCenter()
                    .subtract(dashDirection.scale(0.45D));
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    trailCenter.x, trailCenter.y, trailCenter.z,
                    5, 0.16D, 0.22D, 0.16D, 0.025D);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
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

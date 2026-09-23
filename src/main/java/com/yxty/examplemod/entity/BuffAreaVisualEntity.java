package com.yxty.examplemod.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Buff 施加瞬间显示其作用范围的地面粒子区域。 */
public class BuffAreaVisualEntity extends Entity {
    public static final int HOLY = 0;
    public static final int ICE = 1;

    private float radius = 6.0F;
    private int durationTicks = 30;
    private int visualKind = HOLY;

    public BuffAreaVisualEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(Vec3 center, float radius, int durationTicks, int visualKind) {
        setPos(center);
        this.radius = radius;
        this.durationTicks = durationTicks;
        this.visualKind = visualKind;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (tickCount > durationTicks) {
            discard();
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            ParticleOptions groundParticle = visualKind == ICE
                    ? ParticleTypes.SNOWFLAKE
                    : ParticleTypes.WAX_ON;
            serverLevel.sendParticles(groundParticle,
                    getX(), getY() + 0.08D, getZ(),
                    Math.max(16, (int) (radius * 4.0F)),
                    radius * 0.7D, 0.04D, radius * 0.7D, 0.025D);
            if (tickCount % 3 == 0) {
                serverLevel.sendParticles(visualKind == ICE ? ParticleTypes.CLOUD : ParticleTypes.END_ROD,
                        getX(), getY() + 0.25D, getZ(),
                        Math.max(8, (int) (radius * 1.5F)),
                        radius * 0.65D, 0.18D, radius * 0.65D, 0.015D);
            }
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

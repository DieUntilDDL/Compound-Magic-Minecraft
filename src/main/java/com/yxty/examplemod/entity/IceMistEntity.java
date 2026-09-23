package com.yxty.examplemod.entity;

import com.yxty.examplemod.magic.MagicStatusManager;
import com.yxty.examplemod.particle.ModParticleTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 圆柱形三维冰雾区域。 */
public class IceMistEntity extends Entity {
    private LivingEntity owner;
    private int ownerId;
    private float radius = 4.5F;
    private float halfHeight = 2.5F;
    private int durationTicks = 200;
    private int damageInterval = 10;
    private float damage = 1.5F;
    private float slowMultiplier = 0.72F;
    private int freezeThresholdTicks = 60;
    private int freezeDurationTicks = 50;
    private boolean purified;
    private int purifiedDebuffDurationTicks = 300;
    private final Map<UUID, Integer> continuousInsideTicks = new HashMap<>();
    private transient Predicate<LivingEntity> targetFilter = target -> true;
    private transient Consumer<Hit> hitCallback;

    public IceMistEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(LivingEntity owner, Vec3 center, float radius, float halfHeight,
                      int durationTicks, int damageInterval, float damage,
                      float slowMultiplier, int freezeThresholdTicks, int freezeDurationTicks,
                      boolean purified, int purifiedDebuffDurationTicks,
                      Predicate<LivingEntity> targetFilter, Consumer<Hit> hitCallback) {
        this.owner = owner;
        this.ownerId = owner.getId();
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.durationTicks = durationTicks;
        this.damageInterval = damageInterval;
        this.damage = damage;
        this.slowMultiplier = slowMultiplier;
        this.freezeThresholdTicks = freezeThresholdTicks;
        this.freezeDurationTicks = freezeDurationTicks;
        this.purified = purified;
        this.purifiedDebuffDurationTicks = purifiedDebuffDurationTicks;
        this.targetFilter = targetFilter;
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

        spawnMistParticles();
        affectTargets();
    }

    private void affectTargets() {
        AABB area = new AABB(
                getX() - radius, getY() - halfHeight, getZ() - radius,
                getX() + radius, getY() + halfHeight, getZ() + radius);
        Set<UUID> currentlyInside = new HashSet<>();
        DamageSource source = owner.damageSources().indirectMagic(owner, owner);

        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != owner && candidate.isAlive() && targetFilter.test(candidate))) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            currentlyInside.add(target.getUUID());
            Vec3 movement = target.getDeltaMovement();
            target.setDeltaMovement(movement.x * slowMultiplier, movement.y, movement.z * slowMultiplier);
            target.hasImpulse = true;
            target.hurtMarked = true;
            if (target instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }

            if (purified) {
                if (tickCount % 20 == 1
                        || !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        || !target.hasEffect(MobEffects.WEAKNESS)) {
                    target.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, purifiedDebuffDurationTicks, 3,
                            false, false, true), owner);
                    target.addEffect(new MobEffectInstance(
                            MobEffects.WEAKNESS, purifiedDebuffDurationTicks, 2,
                            false, false, true), owner);
                }
                if (tickCount % 10 == 0 && level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ModParticleTypes.WEAKENING_ARROW.get(),
                            target.getX(), target.getY() + target.getBbHeight() + 0.35D,
                            target.getZ(), 2,
                            0.18D, 0.08D, 0.18D, 0.0D);
                }
            }

            if (!MagicStatusManager.isFrozen(target)) {
                int insideTicks = continuousInsideTicks.merge(target.getUUID(), 1, Integer::sum);
                if (insideTicks >= freezeThresholdTicks) {
                    MagicStatusManager.freeze(target, freezeDurationTicks);
                    continuousInsideTicks.put(target.getUUID(), 0);
                }
            }

            if (tickCount % damageInterval == 0) {
                damageTarget(target, source);
            }
        }
        continuousInsideTicks.keySet().retainAll(currentlyInside);
    }

    private void damageTarget(LivingEntity target, DamageSource source) {
        float before = target.getHealth() + target.getAbsorptionAmount();
        target.invulnerableTime = 0;
        boolean accepted = target.hurt(source, damage);
        if (accepted) {
            // 明确同步原版受伤覆盖层；客户端姿态锁会屏蔽动作，但保留红色闪烁。
            target.level().broadcastEntityEvent(target, (byte) 2);
        }
        float after = target.getHealth() + target.getAbsorptionAmount();
        float actualDamage = accepted ? Math.max(0.0F, before - after) : 0.0F;
        if (actualDamage > 0.0F && hitCallback != null) {
            hitCallback.accept(new Hit(target, damage, actualDamage, !target.isAlive(), source));
        }
    }

    private void spawnMistParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY(), getZ(),
                    Math.max(12, (int) (radius * 4.0F)),
                    radius * 0.75D, halfHeight * 0.75D, radius * 0.75D, 0.015D);
        if (tickCount % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() - halfHeight * 0.55D, getZ(),
                    Math.max(6, (int) (radius * 2.0F)),
                    radius * 0.65D, 0.18D, radius * 0.65D, 0.005D);
        }

        // 少量分散在整个圆柱体积内的篝火烟，让三维冰雾都有翻涌层次。
        if (tickCount % 4 == 0) {
            int smokeCount = Math.max(2, (int) Math.ceil(radius * 0.75F));
            for (int i = 0; i < smokeCount; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = radius * Math.sqrt(random.nextDouble()) * 0.95D;
                double smokeX = getX() + Math.cos(angle) * distance;
                double smokeY = getY()
                        + (random.nextDouble() * 2.0D - 1.0D) * halfHeight * 0.95D;
                double smokeZ = getZ() + Math.sin(angle) * distance;

                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        smokeX, smokeY, smokeZ,
                        1, 0.025D, 0.015D, 0.025D, 0.003D);
            }
        }
    }
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

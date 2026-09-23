package com.yxty.examplemod.entity;

import com.yxty.examplemod.particle.LightningParticleOptions;
import com.yxty.examplemod.magic.MagicStatusManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** 以阻尼速度持续聚怪的雷霆风暴区域。 */
public class ThunderStormEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(ThunderStormEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HALF_HEIGHT =
            SynchedEntityData.defineId(ThunderStormEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_DURATION =
            SynchedEntityData.defineId(ThunderStormEntity.class, EntityDataSerializers.INT);
    private LivingEntity owner;
    private int ownerId;
    private float radius = 7.0F;
    private float halfHeight = 5.0F;
    private int durationTicks = 200;
    private int damageInterval = 20;
    private float damage = 5.0F;
    private float maximumPullSpeed = 0.38F;
    private int stunDurationTicks;
    private transient Predicate<LivingEntity> targetFilter = target -> true;
    private transient Consumer<Hit> hitCallback;

    public ThunderStormEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public void setup(LivingEntity owner, Vec3 center, float radius, float halfHeight,
                      int durationTicks, int damageInterval, float damage,
                      float maximumPullSpeed, int stunDurationTicks,
                      Predicate<LivingEntity> targetFilter,
                      Consumer<Hit> hitCallback) {
        this.owner = owner;
        this.ownerId = owner.getId();
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.durationTicks = durationTicks;
        entityData.set(DATA_RADIUS, radius);
        entityData.set(DATA_HALF_HEIGHT, halfHeight);
        entityData.set(DATA_DURATION, durationTicks);
        this.damageInterval = damageInterval;
        this.damage = damage;
        this.maximumPullSpeed = maximumPullSpeed;
        this.stunDurationTicks = Math.max(0, stunDurationTicks);
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
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        if (tickCount > durationTicks) {
            if (stunDurationTicks > 0) {
                stunTargetsInStorm();
            }
            discard();
            return;
        }

        if (tickCount == 1 && stunDurationTicks > 0) {
            stunTargetsInStorm();
        }

        spawnStormParticles();
        pullAndDamageTargets();
    }

    private void stunTargetsInStorm() {
        AABB area = new AABB(
                getX() - radius, getY() - halfHeight, getZ() - radius,
                getX() + radius, getY() + halfHeight, getZ() + radius);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != owner && candidate.isAlive()
                        && targetFilter.test(candidate))) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                MagicStatusManager.stun(target, stunDurationTicks);
            }
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH,
                    getX(), getY(), getZ(), 8,
                    radius * 0.35D, halfHeight * 0.35D, radius * 0.35D, 0.0D);
        }
    }

    private void pullAndDamageTargets() {
        AABB area = new AABB(
                getX() - radius, getY() - halfHeight, getZ() - radius,
                getX() + radius, getY() + halfHeight, getZ() + radius);
        DamageSource source = owner.damageSources().indirectMagic(owner, owner);
        boolean damagePulse = damage > 0.0F && tickCount % damageInterval == 0;

        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != owner && candidate.isAlive() && targetFilter.test(candidate))) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 toCenter = position().subtract(targetCenter);
            double distance = toCenter.length();
            if (distance > radius) {
                dampMovement(target);
                continue;
            }

            double wanderRadius = Math.min(0.9D, Math.max(1.0D, radius * 0.12D));
            if (distance < wanderRadius) {
                wanderNearCenter(target, toCenter, distance, wanderRadius);
            } else {
                // 远处速度受上限约束，近处按距离线性降低；旧速度快速衰减以防越心甩出。
                double desiredSpeed = Math.min(maximumPullSpeed, distance * 0.075D);
                Vec3 desiredVelocity = toCenter.scale(desiredSpeed / distance);
                Vec3 current = target.getDeltaMovement();
                Vec3 blended = current.scale(0.48D).add(desiredVelocity.scale(0.52D));
                applyVelocity(target, blended);
            }

            if (damagePulse) {
                damageTarget(target, source);
            }
        }

        if (damagePulse) {
            level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.35F, 1.35F);
        }
    }

    private void wanderNearCenter(LivingEntity target, Vec3 toCenter,
                                  double distance, double wanderRadius) {
        Vec3 current = target.getDeltaMovement();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double driftSpeed = 0.025D + random.nextDouble() * 0.03D;
        Vec3 randomDrift = new Vec3(
                Math.cos(angle) * driftSpeed,
                0.0D,
                Math.sin(angle) * driftSpeed);

        Vec3 centerCorrection = Vec3.ZERO;
        if (distance > 0.001D) {
            double correctionSpeed = Math.min(0.040D,
                    distance / wanderRadius * 0.032D);
            centerCorrection = toCenter.scale(correctionSpeed / distance);
        }

        Vec3 blended = current.scale(0.62D)
                .add(randomDrift)
                .add(centerCorrection);
        double maximumWanderSpeed = 0.085D;
        if (blended.lengthSqr() > maximumWanderSpeed * maximumWanderSpeed) {
            blended = blended.normalize().scale(maximumWanderSpeed);
        }
        applyVelocity(target, blended);
    }

    private static void dampMovement(LivingEntity target) {
        Vec3 current = target.getDeltaMovement();
        applyVelocity(target, current.scale(0.35D));
    }

    private static void applyVelocity(LivingEntity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hasImpulse = true;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
    }

    private void damageTarget(LivingEntity target, DamageSource source) {
        float before = target.getHealth() + target.getAbsorptionAmount();
        target.invulnerableTime = 0;
        boolean accepted = target.hurt(source, damage);
        float after = target.getHealth() + target.getAbsorptionAmount();
        float actualDamage = accepted ? Math.max(0.0F, before - after) : 0.0F;
        if (actualDamage > 0.0F && hitCallback != null) {
            hitCallback.accept(new Hit(target, damage, actualDamage, !target.isAlive(), source));
        }
    }

    private void spawnStormParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 短寿命闪电持续环绕风暴；每个粒子都拥有独立的纵向拉伸系数。
        for (int index = 0; index < 4; index++) {
            float heightT = random.nextFloat();
            float localHalfSize = visualHalfSize(radius, heightT);
            double centerX = getX() + visualOffsetX(radius, tickCount, heightT, getId());
            double centerZ = getZ() + visualOffsetZ(radius, tickCount, heightT, getId());
            double alongSide = (random.nextDouble() * 2.0D - 1.0D) * localHalfSize;
            double surfaceGap = radius * (0.010D + random.nextDouble() * 0.025D);
            double particleX;
            double particleZ;
            switch (random.nextInt(4)) {
                case 0 -> {
                    particleX = centerX + alongSide;
                    particleZ = centerZ - localHalfSize - surfaceGap;
                }
                case 1 -> {
                    particleX = centerX + localHalfSize + surfaceGap;
                    particleZ = centerZ + alongSide;
                }
                case 2 -> {
                    particleX = centerX + alongSide;
                    particleZ = centerZ + localHalfSize + surfaceGap;
                }
                default -> {
                    particleX = centerX - localHalfSize - surfaceGap;
                    particleZ = centerZ + alongSide;
                }
            }
            double particleY = getY() + Mth.lerp(heightT, -halfHeight, halfHeight);
            float stretch = 0.65F + random.nextFloat() * 1.75F;
            serverLevel.sendParticles(new LightningParticleOptions(stretch),
                    particleX, particleY, particleZ, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
        }

        if (tickCount % damageInterval == 0) {
            // FLASH 的随机偏移必须为零，保证闪光严格位于法阵中心。
            serverLevel.sendParticles(ParticleTypes.FLASH,
                    getX(), getY(), getZ(), 4,
                    0.0D, 0.0D, 0.0D, 0.0D);
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

    public float getVisualRadius() {
        return entityData.get(DATA_RADIUS);
    }

    public float getVisualHalfHeight() {
        return entityData.get(DATA_HALF_HEIGHT);
    }

    public int getVisualDurationTicks() {
        return entityData.get(DATA_DURATION);
    }

    /** 倒金字塔底部较宽、顶部适度外扩，避免上下比例过于悬殊。 */
    public static float visualHalfSize(float radius, float heightT) {
        return radius * (0.24F + Mth.clamp(heightT, 0.0F, 1.0F) * 0.36F);
    }

    /**
     * 所有高度共享同一条二维位移轨迹，但高层读取更早的轨迹时间：
     * 底部先移动，上方方环经过短暂延迟后依次跟随。
     */
    public static float visualOffsetX(float radius, float age, float heightT, int stormSeed) {
        return propagatedOffset(radius, age, heightT, stormSeed, 0);
    }

    public static float visualOffsetZ(float radius, float age, float heightT, int stormSeed) {
        return propagatedOffset(radius, age, heightT, stormSeed, 1);
    }

    private static float propagatedOffset(float radius, float age, float heightT,
                                          int stormSeed, int axis) {
        final float propagationDelayTicks = 8.0F;
        final float keyframeDurationTicks = 12.0F;
        float delayedAge = age - Mth.clamp(heightT, 0.0F, 1.0F) * propagationDelayTicks;
        int keyframe = Mth.floor(delayedAge / keyframeDurationTicks);
        float progress = delayedAge / keyframeDurationTicks - keyframe;
        float smoothProgress = progress * progress * (3.0F - 2.0F * progress);
        float start = signedNoise(stormSeed, keyframe, axis);
        float end = signedNoise(stormSeed, keyframe + 1, axis);
        return Mth.lerp(smoothProgress, start, end) * radius * 0.075F;
    }

    private static float signedNoise(int stormSeed, int keyframe, int axis) {
        int mixed = stormSeed * 0x45d9f3b
                + keyframe * 0x119de1f3
                + axis * 0x27d4eb2d;
        mixed = (mixed ^ (mixed >>> 16)) * 0x45d9f3b;
        mixed ^= mixed >>> 16;
        return ((mixed & 0x7fffffff) / (float) Integer.MAX_VALUE) * 2.0F - 1.0F;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, 7.0F);
        entityData.define(DATA_HALF_HEIGHT, 5.0F);
        entityData.define(DATA_DURATION, 200);
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

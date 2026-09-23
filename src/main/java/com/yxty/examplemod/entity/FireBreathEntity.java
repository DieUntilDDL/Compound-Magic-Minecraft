package com.yxty.examplemod.entity;

import com.yxty.examplemod.item.WandItem;
import com.yxty.examplemod.magic.MagicStatusManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** 持续跟随施法者的火焰扇形；实体本身不可见，视觉由粒子组成。 */
public class FireBreathEntity extends Entity {
    public static final int DAMAGE_INTERVAL = 5;

    private LivingEntity owner;
    private int ownerId;
    private int durationTicks = 120;
    private float range = 8.0F;
    private float halfAngleDegrees = 35.0F;
    private float damage = 2.0F;
    private int fireSeconds = 4;
    private boolean purified;
    private float castingStrength = 1.0F;
    private transient Predicate<LivingEntity> targetFilter = target -> true;
    private transient Consumer<Hit> hitCallback;

    public FireBreathEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(LivingEntity owner, int durationTicks, float range,
                      float halfAngleDegrees, float damage, int fireSeconds, boolean purified,
                      float castingStrength,
                      Predicate<LivingEntity> targetFilter, Consumer<Hit> hitCallback) {
        this.owner = owner;
        this.ownerId = owner.getId();
        this.durationTicks = durationTicks;
        this.range = range;
        this.halfAngleDegrees = halfAngleDegrees;
        this.damage = damage;
        this.fireSeconds = fireSeconds;
        this.purified = purified;
        this.castingStrength = Math.max(0.0F, castingStrength);
        this.targetFilter = targetFilter;
        this.hitCallback = hitCallback;
        setPos(owner.getEyePosition());
    }

    @Override
    public void tick() {
        super.tick();
        owner = resolveOwner();
        if (owner == null || !owner.isAlive() || tickCount > durationTicks || !ownerStillCasting()) {
            discard();
            return;
        }

        setPos(owner.getEyePosition());
        if (level().isClientSide) {
            return;
        }

        spawnConeParticles();
        if (tickCount % DAMAGE_INTERVAL == 0) {
            damageConeTargets();
        }
    }

    private void damageConeTargets() {
        Vec3 origin = owner.getEyePosition().add(owner.getLookAngle().scale(0.45D));
        Vec3 forward = owner.getLookAngle().normalize();
        double minimumDot = Math.cos(Math.toRadians(halfAngleDegrees));
        AABB search = new AABB(origin, origin).inflate(range);
        DamageSource source = owner.damageSources().indirectMagic(owner, owner);

        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, search,
                candidate -> candidate != owner && candidate.isAlive() && targetFilter.test(candidate))) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(origin);
            double distance = toTarget.length();
            if (distance > range || distance < 1.0E-5D
                    || forward.dot(toTarget.scale(1.0D / distance)) < minimumDot) {
                continue;
            }

            float before = target.getHealth() + target.getAbsorptionAmount();
            target.invulnerableTime = 0;
            boolean accepted = target.hurt(source, damage);
            if (purified) {
                MagicStatusManager.applyBlueFlame(target, castingStrength);
            } else {
                target.setSecondsOnFire(fireSeconds);
            }
            float after = target.getHealth() + target.getAbsorptionAmount();
            float actualDamage = accepted ? Math.max(0.0F, before - after) : 0.0F;
            if (actualDamage > 0.0F && hitCallback != null) {
                hitCallback.accept(new Hit(target, damage, actualDamage, !target.isAlive(), source));
            }
        }
    }

    private void spawnConeParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 origin = owner.getEyePosition().add(owner.getLookAngle().scale(0.35D));
        Vec3 forward = owner.getLookAngle().normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-5D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();
        double tangent = Math.tan(Math.toRadians(halfAngleDegrees));

        for (int i = 0; i < 14; i++) {
            double distance = 0.5D + random.nextDouble() * (range - 0.5D);
            double coneRadius = distance * tangent;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radial = Math.sqrt(random.nextDouble()) * coneRadius;
            Vec3 point = origin.add(forward.scale(distance))
                    .add(right.scale(Math.cos(angle) * radial))
                    .add(up.scale(Math.sin(angle) * radial));
            serverLevel.sendParticles(purified ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    point.x, point.y, point.z, 1, 0.03D, 0.03D, 0.03D, 0.01D);
        }
        // SOUL particles contain large skull-like faces and obscure the caster's view.
        // Purified breath already has blue SOUL_FIRE_FLAME particles, so it needs no
        // additional gas particle at the cone origin.
        if (!purified) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    origin.x, origin.y, origin.z, 3,
                    0.12D, 0.12D, 0.12D, 0.02D);
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

    private boolean ownerStillCasting() {
        return !(owner instanceof Player player)
                || player.isUsingItem() && player.getUseItem().getItem() instanceof WandItem;
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

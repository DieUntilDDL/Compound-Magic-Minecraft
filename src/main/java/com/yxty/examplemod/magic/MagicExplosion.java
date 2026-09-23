package com.yxty.examplemod.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 不破坏方块的魔法爆炸结算器。伤害、范围和击退互相独立，不使用原版爆炸伤害公式。
 */
public final class MagicExplosion {
    private static final double MIN_DIRECTION_LENGTH_SQR = 1.0E-6D;

    private MagicExplosion() {
    }

    public static Result explode(Level level, LivingEntity owner, Vec3 center, Settings settings,
                                 Predicate<LivingEntity> damageFilter) {
        if (level.isClientSide) {
            return Result.EMPTY;
        }

        playEffects(level, center, settings.radius());
        DamageSource damageSource = owner.damageSources().explosion(owner, owner);
        AABB bounds = new AABB(center, center).inflate(settings.radius());
        List<Hit> hits = new ArrayList<>();

        for (Entity entity : level.getEntities((Entity) null, bounds,
                candidate -> !candidate.isSpectator() && candidate.isAlive())) {
            Vec3 entityCenter = entity.getBoundingBox().getCenter();
            Vec3 away = entityCenter.subtract(center);
            double distance = away.length();
            if (distance > settings.radius()) {
                continue;
            }

            double falloff = 1.0D - distance / settings.radius();
            Vec3 direction = away.lengthSqr() > MIN_DIRECTION_LENGTH_SQR
                    ? away.normalize()
                    : new Vec3(0.0D, 1.0D, 0.0D);

            boolean isOwner = entity == owner;
            if ((!isOwner || settings.knockbackOwner()) && settings.knockback() > 0.0F) {
                double resistance = entity instanceof LivingEntity living
                        ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                        : 0.0D;
                double ownerMultiplier = isOwner ? settings.ownerKnockbackMultiplier() : 1.0D;
                double impulse = settings.knockback() * ownerMultiplier * falloff
                        * Math.max(0.0D, 1.0D - resistance);
                entity.push(direction.x * impulse, direction.y * impulse, direction.z * impulse);
                entity.hurtMarked = true;
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
                }
            }

            if (!(entity instanceof LivingEntity living)
                    || (isOwner && !settings.damageOwner())
                    || !damageFilter.test(living)
                    || settings.maxDamage() <= 0.0F) {
                continue;
            }

            float requestedDamage = (float) (settings.maxDamage() * falloff);
            float healthBefore = living.getHealth() + living.getAbsorptionAmount();
            living.invulnerableTime = 0;
            boolean accepted = living.hurt(damageSource, requestedDamage);
            float healthAfter = living.getHealth() + living.getAbsorptionAmount();
            float actualDamage = accepted ? Math.max(0.0F, healthBefore - healthAfter) : 0.0F;
            if (actualDamage > 0.0F) {
                hits.add(new Hit(living, requestedDamage, actualDamage, !living.isAlive(), damageSource));
            }
        }

        return new Result(List.copyOf(hits));
    }

    private static void playEffects(Level level, Vec3 center, float radius) {
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.POOF,
                    center.x, center.y, center.z,
                    Math.max(12, (int) (radius * 8.0F)),
                    radius * 0.35D, radius * 0.35D, radius * 0.35D, 0.08D);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    center.x, center.y, center.z,
                    Math.max(8, (int) (radius * 4.0F)),
                    radius * 0.25D, radius * 0.25D, radius * 0.25D, 0.05D);
        }
    }

    public record Settings(float radius, float maxDamage, float knockback,
                           boolean damageOwner, boolean knockbackOwner,
                           float ownerKnockbackMultiplier) {
        public Settings(float radius, float maxDamage, float knockback,
                        boolean damageOwner, boolean knockbackOwner) {
            this(radius, maxDamage, knockback, damageOwner, knockbackOwner, 1.0F);
        }

        public Settings {
            if (radius <= 0.0F) {
                throw new IllegalArgumentException("Explosion radius must be positive");
            }
            if (maxDamage < 0.0F || knockback < 0.0F) {
                throw new IllegalArgumentException("Explosion damage and knockback cannot be negative");
            }
            if (ownerKnockbackMultiplier < 0.0F) {
                throw new IllegalArgumentException("Owner knockback multiplier cannot be negative");
            }
        }
    }

    public record Hit(LivingEntity target, float requestedDamage, float actualDamage,
                      boolean killed, DamageSource damageSource) {
    }

    public record Result(List<Hit> hits) {
        private static final Result EMPTY = new Result(List.of());
    }
}

package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.SanctuaryEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class Sanctuary extends BaseMagic {
    public static final float RADIUS = 4.5F;
    public static final float HALF_HEIGHT = 3.0F;
    public static final int DURATION_TICKS = 180;
    public static final int DAMAGE_INTERVAL = 10;
    public static final float BASE_DAMAGE = 4.0F;
    public static final float SUB_BONUS_DAMAGE = 12.0F;
    public static final float PURIFIED_RADIUS_MULTIPLIER = 1.4142135F;

    @Override
    public MagicKind getKind() {
        return MagicKind.FORMATION;
    }

    @Override
    public void castAsMain(MagicContext context) {
        if (!context.isServerSide()) {
            return;
        }
        dispatchSubMagics(context.withPhase(MagicPhase.CAST_BEGIN));

        Player caster = context.owner();
        Vec3 center = forwardCenter(caster, 4.5D, HALF_HEIGHT * 0.55D);
        int intensity = Math.max(1, context.loadout().count(MagicType.SanctuaryMagic));
        float damage = context.scaleDamage(
                BASE_DAMAGE + Math.max(0, intensity - 1) * 1.5F);
        int duration = DURATION_TICKS + Math.max(0, intensity - 1) * 30;
        boolean purified = context.loadout().homogeneous();
        float radius = RADIUS * (purified ? PURIFIED_RADIUS_MULTIPLIER : 1.0F);

        SanctuaryEntity sanctuary = new SanctuaryEntity(ModEntityTypes.SANCTUARY.get(), context.level());
        sanctuary.setup(caster, center, radius, HALF_HEIGHT,
                duration, DAMAGE_INTERVAL, damage, purified,
                hit -> dispatchHit(context, caster, hit));
        context.level().addFreshEntity(sanctuary);
        context.level().playSound(null, center.x, center.y, center.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75F, 1.25F);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()
                || context.phase() != MagicPhase.ENTITY_HIT
                || !(context.target() instanceof LivingEntity target)
                || !target.isAlive()
                || target.getMobType() != MobType.UNDEAD) {
            return;
        }

        DamageSource source = context.owner().damageSources().indirectMagic(context.owner(), context.owner());
        target.invulnerableTime = 0;
        target.hurt(source, context.scaleDamage(SUB_BONUS_DAMAGE));
        if (context.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    18, target.getBbWidth() * 0.45D, target.getBbHeight() * 0.4D,
                    target.getBbWidth() * 0.45D, 0.04D);
        }
    }

    private void dispatchHit(MagicContext context, Player caster, SanctuaryEntity.Hit hit) {
        MagicContext.DamageData damageData =
                new MagicContext.DamageData(hit.requestedDamage(), hit.damageSource());
        damageData.recordActualDamage(hit.actualDamage());
        MagicContext hitContext = context.toBuilder()
                .eventId(UUID.randomUUID())
                .origin(MagicOrigin.PRIMARY)
                .target(hit.target())
                .directEntity(caster)
                .position(hit.target().position())
                .damage(damageData)
                .hitConfirmed(true)
                .killed(hit.killed())
                .primaryCausedKill(hit.killed())
                .build();
        dispatchEntityOutcome(hitContext);
    }

    private static Vec3 forwardCenter(Player caster, double distance, double height) {
        Vec3 look = caster.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        horizontal = horizontal.lengthSqr() < 1.0E-5D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
        return caster.position().add(horizontal.scale(distance)).add(0.0D, height, 0.0D);
    }
}

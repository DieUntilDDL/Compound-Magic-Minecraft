package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.ThunderStormEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class ThunderStorm extends BaseMagic {
    public static final float MAIN_RADIUS = 7.0F;
    public static final float MAIN_HALF_HEIGHT = 5.0F;
    public static final int MAIN_DURATION_TICKS = 200;
    public static final int DAMAGE_INTERVAL = 20;
    public static final float BASE_DAMAGE = 5.0F;
    public static final float MAIN_MAX_PULL_SPEED = 0.38F;
    public static final int PURIFIED_STUN_DURATION_TICKS = 160;

    public static final float SUB_TRIGGER_CHANCE = 0.35F;
    public static final float SUB_RADIUS = 4.0F;
    public static final float SUB_HALF_HEIGHT = 3.0F;
    public static final int SUB_PULL_DURATION_TICKS = 30;
    public static final float SUB_MAX_PULL_SPEED = 0.42F;

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
        Vec3 center = forwardCenter(caster, 6.0D, 2.0D);
        int intensity = Math.max(1, context.loadout().count(MagicType.ThunderStormMagic));
        float damage = context.scaleDamage(
                BASE_DAMAGE + Math.max(0, intensity - 1) * 1.5F);
        float radius = MAIN_RADIUS + Math.max(0, intensity - 1) * 0.5F;

        ThunderStormEntity storm = createStorm(context, caster, center,
                radius, MAIN_HALF_HEIGHT, MAIN_DURATION_TICKS,
                damage, MAIN_MAX_PULL_SPEED,
                context.loadout().homogeneous() ? PURIFIED_STUN_DURATION_TICKS : 0,
                hit -> dispatchHit(context, caster, hit));
        context.level().addFreshEntity(storm);
        context.level().playSound(null, center.x, center.y, center.z,
                SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 0.65F, 0.8F);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()
                || context.phase() != MagicPhase.ENTITY_KILLED
                || !context.primaryCausedKill()
                || context.target() == null
                || context.level().getRandom().nextFloat() >= SUB_TRIGGER_CHANCE) {
            return;
        }

        Vec3 center = (context.position() != null
                ? context.position()
                : context.target().position()).add(0.0D, 1.0D, 0.0D);
        ThunderStormEntity pullBurst = createStorm(context, context.owner(), center,
                SUB_RADIUS, SUB_HALF_HEIGHT, SUB_PULL_DURATION_TICKS,
                0.0F, SUB_MAX_PULL_SPEED, 0, null);
        context.level().addFreshEntity(pullBurst);
    }

    private static ThunderStormEntity createStorm(
            MagicContext context, Player caster, Vec3 center,
            float radius, float halfHeight, int duration,
            float damage, float pullSpeed, int stunDurationTicks,
            java.util.function.Consumer<ThunderStormEntity.Hit> callback) {
        ThunderStormEntity storm = new ThunderStormEntity(ModEntityTypes.THUNDER_STORM.get(), context.level());
        storm.setup(caster, center, radius, halfHeight, duration,
                DAMAGE_INTERVAL, damage, pullSpeed, stunDurationTicks,
                target -> isEnemy(caster, target), callback);
        return storm;
    }

    private void dispatchHit(MagicContext context, Player caster, ThunderStormEntity.Hit hit) {
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

    private static boolean isEnemy(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isInvulnerable()) {
            return false;
        }
        if (caster.isAlliedTo(target) || target.isAlliedTo(caster)) {
            return false;
        }
        return !(target instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }
}

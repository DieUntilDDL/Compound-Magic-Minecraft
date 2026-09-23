package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.IceMistEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class IceMist extends BaseMagic {
    public static final float MAIN_RADIUS = 4.5F;
    public static final float MAIN_HALF_HEIGHT = 2.5F;
    public static final int MAIN_DURATION_TICKS = 200;
    public static final int DAMAGE_INTERVAL = 10;
    public static final float MAIN_DAMAGE = 1.5F;
    public static final int MAIN_FREEZE_THRESHOLD = 60;
    public static final int MAIN_FREEZE_DURATION = 50;

    public static final float SUB_TRIGGER_CHANCE = 0.15F;
    public static final float SUB_RADIUS = 2.5F;
    public static final float SUB_HALF_HEIGHT = 1.8F;
    public static final int SUB_DURATION_TICKS = 60;
    public static final float SUB_DAMAGE = 1.0F;
    public static final int SUB_FREEZE_THRESHOLD = 40;
    public static final int PURIFIED_DEBUFF_DURATION_TICKS = 300;

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
        Vec3 center = forwardCenter(caster, 5.0D, MAIN_HALF_HEIGHT * 0.55D);
        int intensity = Math.max(1, context.loadout().count(MagicType.IceMistMagic));
        float damage = context.scaleDamage(
                MAIN_DAMAGE + Math.max(0, intensity - 1) * 0.5F);
        int duration = MAIN_DURATION_TICKS + Math.max(0, intensity - 1) * 30;
        boolean purified = context.loadout().homogeneous();

        IceMistEntity mist = createMist(context, caster, center,
                MAIN_RADIUS, MAIN_HALF_HEIGHT, duration, damage,
                MAIN_FREEZE_THRESHOLD, purified,
                hit -> dispatchHit(context, caster, hit));
        context.level().addFreshEntity(mist);
        context.level().playSound(null, center.x, center.y, center.z,
                SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.8F, 0.65F);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()
                || context.phase() != MagicPhase.ENTITY_HIT
                || !(context.target() instanceof LivingEntity target)
                || !target.isAlive()
                || context.level().getRandom().nextFloat() >= SUB_TRIGGER_CHANCE) {
            return;
        }

        Vec3 center = target.position().add(0.0D, SUB_HALF_HEIGHT * 0.55D, 0.0D);
        IceMistEntity mist = createMist(context, context.owner(), center,
                SUB_RADIUS, SUB_HALF_HEIGHT, SUB_DURATION_TICKS,
                context.scaleDamage(SUB_DAMAGE),
                SUB_FREEZE_THRESHOLD, false, null);
        context.level().addFreshEntity(mist);
    }

    private static IceMistEntity createMist(
            MagicContext context, Player caster, Vec3 center,
            float radius, float halfHeight, int duration, float damage,
            int freezeThreshold, boolean purified,
            java.util.function.Consumer<IceMistEntity.Hit> callback) {
        IceMistEntity mist = new IceMistEntity(ModEntityTypes.ICE_MIST.get(), context.level());
        mist.setup(caster, center, radius, halfHeight,
                duration, DAMAGE_INTERVAL, damage, 0.72F,
                freezeThreshold, MAIN_FREEZE_DURATION,
                purified, PURIFIED_DEBUFF_DURATION_TICKS,
                target -> isEnemy(caster, target), callback);
        return mist;
    }

    private void dispatchHit(MagicContext context, Player caster, IceMistEntity.Hit hit) {
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
        if (horizontal.lengthSqr() < 1.0E-5D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }
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

package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.FireBreathEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class FireBreath extends BaseMagic {
    public static final int BASE_DURATION_TICKS = 120;
    public static final float BASE_DAMAGE = 2.0F;
    public static final float DAMAGE_PER_EXTRA_STACK = 0.75F;
    public static final float RANGE = 8.0F;
    public static final float HALF_ANGLE_DEGREES = 35.0F;
    public static final int FIRE_SECONDS = 4;
    public static final float SUB_IGNITE_CHANCE = 0.25F;

    @Override
    public MagicKind getKind() {
        return MagicKind.SUSTAINED;
    }

    @Override
    public int getSustainedUseTicks(ItemStack stack) {
        return BASE_DURATION_TICKS;
    }

    @Override
    public void castAsMain(MagicContext context) {
        if (!context.isServerSide()) {
            return;
        }

        dispatchSubMagics(context.withPhase(MagicPhase.CAST_BEGIN));
        Player caster = context.owner();
        int intensity = Math.max(1, context.loadout().count(MagicType.FireBreathMagic));
        float damage = context.scaleDamage(
                BASE_DAMAGE + Math.max(0, intensity - 1) * DAMAGE_PER_EXTRA_STACK);
        boolean purified = context.loadout().homogeneous();

        FireBreathEntity breath = new FireBreathEntity(ModEntityTypes.FIRE_BREATH.get(), context.level());
        breath.setup(caster, BASE_DURATION_TICKS, RANGE, HALF_ANGLE_DEGREES,
                damage, FIRE_SECONDS, purified, context.castingStrength(),
                target -> isEnemy(caster, target),
                hit -> dispatchHit(context, caster, hit));
        context.level().addFreshEntity(breath);
        context.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 0.75F);
    }

    private void dispatchHit(MagicContext context, Player caster, FireBreathEntity.Hit hit) {
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
                .pulseIndex(hit.target().tickCount / FireBreathEntity.DAMAGE_INTERVAL)
                .build();
        dispatchEntityOutcome(hitContext);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()
                || context.phase() != MagicPhase.ENTITY_HIT
                || !(context.target() instanceof LivingEntity target)
                || !target.isAlive()
                || context.level().getRandom().nextFloat() >= SUB_IGNITE_CHANCE) {
            return;
        }
        if (context.loadout().homogeneous()
                && context.loadout().mainMagicId() == MagicType.FireBreathMagic) {
            MagicStatusManager.applyBlueFlame(target, context.castingStrength());
            return;
        }
        target.setSecondsOnFire(FIRE_SECONDS);
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

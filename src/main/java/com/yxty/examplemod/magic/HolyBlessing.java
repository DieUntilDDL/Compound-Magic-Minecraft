package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.BuffAreaVisualEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class HolyBlessing extends BaseMagic {
    public static final float RANGE = 6.0F;
    public static final int DURATION_TICKS = 600;
    public static final float BASE_DAMAGE_BONUS = 0.20F;
    public static final float BONUS_PER_EXTRA_STACK = 0.08F;
    public static final float BASE_RESISTANCE = 0.20F;
    public static final float RESISTANCE_PER_EXTRA_STACK = 0.06F;

    @Override
    public MagicKind getKind() {
        return MagicKind.BUFF;
    }

    @Override
    public void castAsMain(MagicContext context) {
        if (!context.isServerSide()) {
            return;
        }
        dispatchSubMagics(context.withPhase(MagicPhase.CAST_BEGIN));

        Player caster = context.owner();
        int stacks = Math.max(1, context.loadout().count(MagicType.HolyBlessingMagic));
        float damageBonus = context.scaleBuffValue(
                BASE_DAMAGE_BONUS + Math.max(0, stacks - 1) * BONUS_PER_EXTRA_STACK);
        float resistance = context.scaleBuffValue(
                BASE_RESISTANCE + Math.max(0, stacks - 1) * RESISTANCE_PER_EXTRA_STACK);
        AABB area = caster.getBoundingBox().inflate(RANGE);
        List<LivingEntity> recipients = context.level().getEntitiesOfClass(
                LivingEntity.class, area,
                target -> target.isAlive() && (target == caster
                        || caster.isAlliedTo(target) || target.isAlliedTo(caster)));
        if (!recipients.contains(caster)) {
            recipients.add(caster);
        }

        for (LivingEntity recipient : recipients) {
            applyAndDispatch(context, recipient, damageBonus, resistance);
        }
        spawnAreaVisual(context, caster, RANGE, BuffAreaVisualEntity.HOLY);
        context.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.35F);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()) {
            return;
        }
        int stacks = Math.max(1, context.loadout().count(MagicType.HolyBlessingMagic));
        float damageBonus = context.scaleBuffValue(
                BASE_DAMAGE_BONUS + Math.max(0, stacks - 1) * BONUS_PER_EXTRA_STACK);
        float resistance = context.scaleBuffValue(
                BASE_RESISTANCE + Math.max(0, stacks - 1) * RESISTANCE_PER_EXTRA_STACK);

        if (context.loadout().mainKind() == MagicKind.BUFF
                && context.phase() == MagicPhase.BUFF_APPLIED
                && context.target() instanceof LivingEntity recipient) {
            MagicStatusManager.applyBlessing(
                    recipient, context, DURATION_TICKS, damageBonus, resistance);
        } else if (context.loadout().mainKind() != MagicKind.BUFF
                && context.phase() == MagicPhase.CAST_BEGIN) {
            MagicStatusManager.applyBlessing(
                    context.owner(), context, DURATION_TICKS, damageBonus, resistance);
            spawnAreaVisual(context, context.owner(), 1.5F, BuffAreaVisualEntity.HOLY);
        }
    }

    private void applyAndDispatch(MagicContext context, LivingEntity recipient,
                                  float damageBonus, float resistance) {
        MagicStatusManager.applyBlessing(
                recipient, context, DURATION_TICKS, damageBonus, resistance);
        if (context.loadout().homogeneous()) {
            recipient.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, DURATION_TICKS, 1,
                    false, true, true), context.owner());
        }
        MagicContext appliedContext = context.toBuilder()
                .eventId(UUID.randomUUID())
                .origin(MagicOrigin.PRIMARY)
                .actor(recipient)
                .target(recipient)
                .position(recipient.position())
                .hitConfirmed(true)
                .build();
        dispatchSubMagics(appliedContext.withPhase(MagicPhase.BUFF_APPLIED));
    }

    private static void spawnAreaVisual(MagicContext context, LivingEntity center,
                                        float radius, int visualKind) {
        BuffAreaVisualEntity visual = new BuffAreaVisualEntity(
                ModEntityTypes.BUFF_AREA_VISUAL.get(), context.level());
        visual.setup(center.position(), radius, 30, visualKind);
        context.level().addFreshEntity(visual);
    }
}

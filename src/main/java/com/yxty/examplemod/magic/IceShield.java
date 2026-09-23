package com.yxty.examplemod.magic;

import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.BuffAreaVisualEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class IceShield extends BaseMagic {
    public static final float RANGE = 6.0F;
    public static final int BASE_MAIN_LAYERS = 3;
    public static final int DURATION_TICKS = 600;
    public static final float NON_BUFF_KILL_TRIGGER_CHANCE = 0.35F;

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
        AABB area = caster.getBoundingBox().inflate(RANGE);
        List<LivingEntity> recipients = context.level().getEntitiesOfClass(
                LivingEntity.class, area,
                target -> target.isAlive() && (target == caster
                        || caster.isAlliedTo(target) || target.isAlliedTo(caster)));
        if (!recipients.contains(caster)) {
            recipients.add(caster);
        }

        for (LivingEntity recipient : recipients) {
            MagicStatusManager.addIceShields(
                    recipient, context, BASE_MAIN_LAYERS, DURATION_TICKS);
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

        BuffAreaVisualEntity visual = new BuffAreaVisualEntity(
                ModEntityTypes.BUFF_AREA_VISUAL.get(), context.level());
        visual.setup(caster.position(), RANGE, 30, BuffAreaVisualEntity.ICE);
        context.level().addFreshEntity(visual);
        context.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.9F, 0.8F);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()) {
            return;
        }

        if (context.loadout().mainKind() == MagicKind.BUFF
                && context.phase() == MagicPhase.BUFF_APPLIED
                && context.target() instanceof LivingEntity recipient) {
            MagicStatusManager.addIceShields(recipient, context, 1, DURATION_TICKS);
            return;
        }

        if (context.loadout().mainKind() != MagicKind.BUFF
                && context.phase() == MagicPhase.ENTITY_KILLED
                && context.primaryCausedKill()
                && context.level().getRandom().nextFloat() < NON_BUFF_KILL_TRIGGER_CHANCE) {
            MagicStatusManager.addIceShields(
                    context.owner(), context, 1, DURATION_TICKS);
        }
    }
}

package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.capability.ItemLoadedMagicProvider;
import com.yxty.examplemod.entity.LaserChargeEntity;
import com.yxty.examplemod.item.WandCastingStrength;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class LaserBeam extends BaseMagic {
    public static final int BASE_BEAM_TICKS = 60;
    public static final float HEAL_RATIO_PER_STACK = 0.15f;

    @Override
    public MagicKind getKind() {
        return MagicKind.SUSTAINED;
    }

    @Override
    public boolean startsDuringWindup() {
        return true;
    }

    @Override
    public int getSustainedUseTicks(ItemStack stack) {
        int intensity = stack.getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC)
                .map(cap -> Math.max(1, MagicType.countLoaded(cap.GetLoadedMagic(), MagicType.LaserBeam)))
                .orElse(1);
        return beamDuration(intensity);
    }

    @Override
    public void castAsMain(MagicContext context) {
        Level level = context.level();
        Player caster = context.owner();
        if (level.isClientSide) {
            return;
        }

        // CAST_BEGIN 类副魔法（例如攻击型主魔法携带祝福）每次施法只触发一次。
        dispatchSubMagics(context.withPhase(MagicPhase.CAST_BEGIN));

        int intensity = Math.max(1, context.loadout().count(MagicType.LaserBeam));
        float damage = context.scaleDamage(2.0f * (0.5f + 0.5f * intensity));
        float width = 0.2f + intensity * 0.05f;
        int duration = beamDuration(intensity);
        boolean purified = context.loadout().homogeneous();

        LaserChargeEntity charge = new LaserChargeEntity(ModEntityTypes.LASER_CHARGE.get(), level);
        int chargeTicks = WandCastingStrength.scaleWindupTicks(
                DEFAULT_WINDUP_TICKS, context.castingStrength());
        charge.setup(caster, chargeTicks, damage, duration, width, purified, (hit, dealt) -> {
            boolean killed = hit instanceof LivingEntity living && !living.isAlive();
            MagicContext.DamageData damageData = new MagicContext.DamageData(damage, null);
            damageData.recordActualDamage(dealt);

            MagicContext hitContext = context.toBuilder()
                    .eventId(java.util.UUID.randomUUID())
                    .origin(MagicOrigin.PRIMARY)
                    .target(hit)
                    .position(hit.position())
                    .damage(damageData)
                    .hitConfirmed(true)
                    .killed(killed)
                    .primaryCausedKill(killed && dealt > 0.0f)
                    .build();

            // 激光主槽本身不吸血；只有装入激光副槽后，副槽才会逐层提供吸血。
            // 纯化拥有三个激光副槽，因此会获得三层吸血并同时启用穿透。
            dispatchEntityOutcome(hitContext);
        });
        level.addFreshEntity(charge);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (context.phase() != MagicPhase.AFTER_DAMAGE || context.damage() == null) {
            return;
        }
        if (!context.damage().convertibleDamage()) {
            return;
        }

        // 每次调用只对应一个副槽，始终按单层治疗。
        applyLifesteal(context.level(), context.owner(), context.damage().actualDamage(), 1);
    }

    static void applyLifesteal(Level level, Player caster, float damageDealt, int stacks) {
        if (level.isClientSide || caster == null || !caster.isAlive() || damageDealt <= 0f || stacks <= 0) {
            System.out.println("[LaserHeal] skipped: client=" + level.isClientSide
                    + " caster=" + (caster == null ? "null" : caster.getName().getString())
                    + " alive=" + (caster != null && caster.isAlive())
                    + " dmg=" + damageDealt
                    + " stacks=" + stacks);
            return;
        }

        float heal = damageDealt * HEAL_RATIO_PER_STACK * stacks;
        float before = caster.getHealth();
        caster.heal(heal);
        float after = caster.getHealth();
        String msg = String.format("[LaserHeal] stacks=%d dmg=%.2f heal=%.2f hp %.1f -> %.1f / %.1f",
                stacks, damageDealt, heal, before, after, caster.getMaxHealth());
        System.out.println(msg);
        caster.displayClientMessage(Component.literal(msg), true);

        if (heal > 0f) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    0.35f,
                    1.2f);
        }
    }

    private static int beamDuration(int intensity) {
        return BASE_BEAM_TICKS + Math.max(0, intensity - 1) * 10;
    }
}

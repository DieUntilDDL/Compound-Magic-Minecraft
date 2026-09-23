package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.entity.ThunderChainEntity;
import com.yxty.examplemod.util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

public class ThunderChain extends BaseMagic{

    @Override
    public void castAsMain(MagicContext context) {
        Level level = context.level();
        Player caster = context.owner();
        if (level.isClientSide) {
            return;
        }

        dispatchSubMagics(context.withPhase(MagicPhase.CAST_BEGIN));

        int intensity = Math.max(1, context.loadout().count(MagicType.LightningChain));
        float boltDamage = context.scaleDamage(8 * (0.5f * intensity + 0.5f));
        ThunderChainEntity thunderChain = ThunderChainEntity.spawn_with_face(
                level, caster, caster.getEyePosition().add(0, -0.3, 0),
                caster.getLookAngle(), 0.1f + intensity * 0.05f, boltDamage
        );
        if (thunderChain == null) {
            return;
        }

        if (context.loadout().homogeneous() && context.loadout().mainMagicId() == MagicType.LightningChain) {
            // 四槽闪电共鸣：二次传导替换普通副槽命中分发。
            thunderChain.setOnBeforeHitEntityCallback(targets -> {
                if (!targets.isEmpty() && targets.get(0) instanceof LivingEntity source) {
                    List<LivingEntity> exempt = new ArrayList<>();
                    exempt.add(caster);
                    List<LivingEntity> livingTargets = util.getVisibleTargets(source, level, exempt);
                    for (LivingEntity enemy : livingTargets) {
                        ThunderChainEntity.spawn_between_entity(
                                level, caster, source.getEyePosition().add(0, -0.3, 0),
                                enemy.getEyePosition().add(0, -0.3, 0), 0.2f,
                                context.scaleDamage(10.0F),
                                enemy
                        );
                    }
                }
            });
            return;
        }

        thunderChain.setOnHitEntityCallback((target, actualDamage) -> {
            boolean killed = target instanceof LivingEntity living && !living.isAlive();
            MagicContext.DamageData damageData = new MagicContext.DamageData(boltDamage, null);
            damageData.recordActualDamage(actualDamage);

            MagicContext hitContext = context.toBuilder()
                    .eventId(java.util.UUID.randomUUID())
                    .origin(MagicOrigin.PRIMARY)
                    .target(target)
                    .position(target.position())
                    .damage(damageData)
                    .hitConfirmed(true)
                    .killed(killed)
                    .primaryCausedKill(killed && actualDamage > 0.0f)
                    .build();
            dispatchEntityOutcome(hitContext);
        });
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()
                || context.phase() != MagicPhase.ENTITY_HIT
                || !(context.target() instanceof LivingEntity enemy)) {
            return;
        }

        // 每次调用只代表一个闪电副槽，宽度和伤害固定按一层计算。
        ThunderChainEntity.spawn_between_entity(
                context.level(), context.owner(),
                enemy.getEyePosition().add(0.0f, 30.0f, 0.0f),
                enemy.getEyePosition(),
                0.15f, context.scaleDamage(8.0F),
                enemy
        );
    }
}

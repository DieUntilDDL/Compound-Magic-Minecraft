package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import net.minecraft.world.item.ItemStack;

abstract public class BaseMagic {
    public static final int DEFAULT_WINDUP_TICKS = 40;

    public MagicKind getKind() {
        return MagicKind.INSTANT;
    }

    /** Every non-immediate spell plays this wind-up before its gameplay effect begins. */
    public int getBaseWindupTicks(ItemStack stack) {
        return DEFAULT_WINDUP_TICKS;
    }

    /** Extra hold time after the wind-up; only used by sustained spells. */
    public int getSustainedUseTicks(ItemStack stack) {
        return 0;
    }

    /** 按下使用键时是否跳过蓄力并立即释放。 */
    public boolean castsImmediately() {
        return false;
    }

    /**
     * Laser uses its charge entity as the wind-up visual, so it starts alongside the
     * wand animation while its damaging beam still waits for the wind-up to finish.
     */
    public boolean startsDuringWindup() {
        return false;
    }

    /** 作为主魔法施放。 */
    public abstract void castAsMain(MagicContext context);

    /**
     * 作为副魔法触发。实现类必须检查 context.phase()，只响应自己需要的阶段。
     * 每次调用只代表一个副槽，因此实现内部始终按单层计算。
     */
    public void triggerAsSub(MagicContext context) {}

    /**
     * 按槽位逐个分发副魔法。空槽跳过；重复副魔法不会合并。
     */
    protected final void dispatchSubMagics(MagicContext context) {
        if (!context.isServerSide() || !context.allowSubDispatch()) {
            return;
        }

        for (int slot = 1; slot <= 3; slot++) {
            int magicId = context.loadout().subMagicId(slot);
            if (magicId == MagicType.EmptyMagic) {
                continue;
            }

            BaseMagic subMagic = MagicType.getMagic(magicId);
            if (subMagic != null) {
                subMagic.triggerAsSub(context.forSubSlot(slot));
            }
        }
    }

    /**
     * 主效果完成一次实体伤害后，按固定顺序分发伤害、命中和击杀阶段。
     */
    protected final void dispatchEntityOutcome(MagicContext context) {
        dispatchSubMagics(context.withPhase(MagicPhase.AFTER_DAMAGE));
        dispatchSubMagics(context.withPhase(MagicPhase.ENTITY_HIT));
        if (context.killed() && context.primaryCausedKill()) {
            dispatchSubMagics(context.withPhase(MagicPhase.ENTITY_KILLED));
        }
    }

    /** 供持续 Buff 等外部事件桥接已有法术槽位的实体结算。 */
    public final void dispatchExternalEntityOutcome(MagicContext context) {
        dispatchEntityOutcome(context);
    }

    /** 供外部事件只分发某一个明确阶段，避免重复触发命中阶段。 */
    public final void dispatchExternalPhase(MagicContext context) {
        dispatchSubMagics(context);
    }
}

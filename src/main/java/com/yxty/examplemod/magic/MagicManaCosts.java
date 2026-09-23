package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;

/**
 * 所有主魔法的魔力消耗表。
 * castCost 是每次成功启动/释放时的一次性消耗；perTickCost 仅用于持续魔法。
 */
public final class MagicManaCosts {
    public static final Cost FREE = new Cost(0.0F, 0.0F);

    public static final Cost LIGHTNING_CHAIN = new Cost(18.0F, 0.0F);
    public static final Cost UMBRAL_EROSION_RAY = new Cost(6.0F, 0.80F);
    public static final Cost DARK_DEVOUR = new Cost(35.0F, 0.0F);
    public static final Cost BLAST_DASH = new Cost(16.0F, 0.0F);
    public static final Cost FIRE_BREATH = new Cost(4.0F, 0.40F);
    public static final Cost ICE_MIST = new Cost(26.0F, 0.0F);
    public static final Cost SANCTUARY = new Cost(30.0F, 0.0F);
    public static final Cost THUNDER_STORM = new Cost(40.0F, 0.0F);
    public static final Cost HOLY_BLESSING = new Cost(28.0F, 0.0F);
    public static final Cost ICE_SHIELD = new Cost(22.0F, 0.0F);

    private MagicManaCosts() {
    }

    public static Cost get(int magicId) {
        return switch (magicId) {
            case MagicType.LightningChain -> LIGHTNING_CHAIN;
            case MagicType.LaserBeam -> UMBRAL_EROSION_RAY;
            case MagicType.DarkDevourMagic -> DARK_DEVOUR;
            case MagicType.BlastDashMagic -> BLAST_DASH;
            case MagicType.FireBreathMagic -> FIRE_BREATH;
            case MagicType.IceMistMagic -> ICE_MIST;
            case MagicType.SanctuaryMagic -> SANCTUARY;
            case MagicType.ThunderStormMagic -> THUNDER_STORM;
            case MagicType.HolyBlessingMagic -> HOLY_BLESSING;
            case MagicType.IceShieldMagic -> ICE_SHIELD;
            default -> FREE;
        };
    }

    public record Cost(float castCost, float perTickCost) {
        public Cost {
            castCost = Math.max(0.0F, castCost);
            perTickCost = Math.max(0.0F, perTickCost);
        }
    }
}

package com.yxty.examplemod.client;

import com.yxty.examplemod.capability.ManaData;
import net.minecraft.util.Mth;

/** 客户端 HUD 使用的最近一次服务端魔力快照。 */
public final class ClientManaState {
    private static float mana = ManaData.DEFAULT_MAX_MANA;
    private static float maxMana = ManaData.DEFAULT_MAX_MANA;

    private ClientManaState() {
    }

    public static void update(float current, float maximum) {
        maxMana = Math.max(1.0F, maximum);
        mana = Mth.clamp(current, 0.0F, maxMana);
    }

    public static void reset() {
        mana = ManaData.DEFAULT_MAX_MANA;
        maxMana = ManaData.DEFAULT_MAX_MANA;
    }

    public static float mana() {
        return mana;
    }

    public static float maxMana() {
        return maxMana;
    }
}

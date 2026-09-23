package com.yxty.examplemod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Client-synchronized marker for the purified fire breath's persistent blue flame. */
public final class BlueFlameMobEffect extends MobEffect {
    public BlueFlameMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x168CFF);
    }
}

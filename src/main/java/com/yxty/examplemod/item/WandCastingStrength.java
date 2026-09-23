package com.yxty.examplemod.item;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Persistent per-wand casting multiplier. */
public final class WandCastingStrength {
    public static final float DEFAULT = 1.0F;
    public static final float MIN = 0.25F;
    public static final float MAX = 4.0F;
    private static final String TAG = "CastingStrength";

    private WandCastingStrength() {
    }

    public static float get(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof WandItem wandItem)) {
            return DEFAULT;
        }
        if (stack.getTag() == null || !stack.getTag().contains(TAG)) {
            return wandItem.getBaseCastingStrength();
        }
        return clamp(stack.getTag().getFloat(TAG));
    }

    public static void set(ItemStack stack, float strength) {
        if (stack != null && stack.getItem() instanceof WandItem) {
            stack.getOrCreateTag().putFloat(TAG, clamp(strength));
        }
    }

    public static float clamp(float strength) {
        if (!Float.isFinite(strength)) {
            return DEFAULT;
        }
        return Mth.clamp(strength, MIN, MAX);
    }

    public static int scaleWindupTicks(int baseTicks, float strength) {
        if (baseTicks <= 0) {
            return 0;
        }
        return Math.max(1, Mth.ceil(baseTicks / clamp(strength)));
    }

    public static String format(float strength) {
        return String.format(Locale.ROOT, "%.2f", clamp(strength));
    }
}

package com.yxty.examplemod.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** 玩家持久化魔力数据。数值平衡参数集中放在本类顶部。 */
public final class ManaData {
    public static final float DEFAULT_MAX_MANA = 100.0F;
    public static final int REGEN_DELAY_TICKS = 60;
    public static final float REGEN_PER_TICK = 0.20F;

    private float mana = DEFAULT_MAX_MANA;
    private float maxMana = DEFAULT_MAX_MANA;
    private int regenDelayTicks;

    public float mana() {
        return mana;
    }

    public float maxMana() {
        return maxMana;
    }

    public boolean has(float amount) {
        return amount <= 0.0F || mana + 1.0E-4F >= amount;
    }

    public boolean consume(float amount) {
        float safeAmount = Math.max(0.0F, amount);
        if (!has(safeAmount)) {
            return false;
        }
        if (safeAmount > 0.0F) {
            mana = Mth.clamp(mana - safeAmount, 0.0F, maxMana);
            regenDelayTicks = REGEN_DELAY_TICKS;
        }
        return true;
    }

    /** @return 魔力数值是否发生了变化。 */
    public boolean tickRegeneration() {
        if (regenDelayTicks > 0) {
            regenDelayTicks--;
            return false;
        }
        if (mana >= maxMana) {
            return false;
        }
        float before = mana;
        mana = Math.min(maxMana, mana + REGEN_PER_TICK);
        return Math.abs(before - mana) > 1.0E-4F;
    }

    public void copyFrom(ManaData source) {
        this.mana = source.mana;
        this.maxMana = source.maxMana;
        this.regenDelayTicks = source.regenDelayTicks;
    }

    public void saveNBT(CompoundTag tag) {
        tag.putFloat("Mana", mana);
        tag.putFloat("MaxMana", maxMana);
        tag.putInt("ManaRegenDelay", regenDelayTicks);
    }

    public void loadNBT(CompoundTag tag) {
        maxMana = tag.contains("MaxMana")
                ? Math.max(1.0F, tag.getFloat("MaxMana"))
                : DEFAULT_MAX_MANA;
        mana = tag.contains("Mana")
                ? Mth.clamp(tag.getFloat("Mana"), 0.0F, maxMana)
                : maxMana;
        regenDelayTicks = Math.max(0, tag.getInt("ManaRegenDelay"));
    }
}

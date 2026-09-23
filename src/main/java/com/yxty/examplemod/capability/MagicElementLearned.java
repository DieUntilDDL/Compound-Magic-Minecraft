package com.yxty.examplemod.capability;

import com.yxty.examplemod.MagicType;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

public class MagicElementLearned {
    public static final int MAGIC_COUNT = MagicType.IceShieldMagic + 1;
    private static final int HOLY_BLESSING_KILL_GOAL = 100;

    private final boolean[] learned = new boolean[MAGIC_COUNT];
    private int undeadKills;

    public MagicElementLearned(){
        unlockStartingMagics();
    }

    /** @return true only when this call newly unlocked the magic. */
    public boolean learn(int id) {
        if (id > MagicType.EmptyMagic && id < learned.length && !learned[id]) {
            learned[id] = true;
            return true;
        }
        return false;
    }

    public void reset(int id) {
        if (id >= 0 && id < learned.length) {
            learned[id] = false;
        }
    }

    public void reset() {
        Arrays.fill(learned, false);
        undeadKills = 0;
    }

    public boolean isLearned(int id) {
        if (id >= 0 && id < learned.length) {
            return learned[id];
        }
        return false;
    }

    public int recordUndeadKill() {
        if (undeadKills < HOLY_BLESSING_KILL_GOAL) {
            undeadKills++;
        }
        return undeadKills;
    }

    public int getUndeadKills() {
        return undeadKills;
    }

    public void saveNBT(CompoundTag nbt) {
        int packedData = 0;
        for (int i = 0; i < learned.length; i++) {
            if (learned[i]) {
                packedData |= (1 << i); // 将第 i 位置为 1
            }
        }
        nbt.putInt("LearnedMagics", packedData);
        nbt.putInt("UndeadKills", undeadKills);
    }

    public void loadNBT(CompoundTag nbt) {
        String learnedKey = nbt.contains("LearnedMagics")
                ? "LearnedMagics"
                : nbt.contains("LearnedElements") ? "LearnedElements" : null;
        if (learnedKey == null) {
            return;
        }

        int packedData = nbt.getInt(learnedKey);
        for (int i = 0; i < learned.length; i++) {
            learned[i] = (packedData & (1 << i)) != 0;
        }
        undeadKills = Math.max(0, Math.min(HOLY_BLESSING_KILL_GOAL,
                nbt.getInt("UndeadKills")));
    }

    public void copyFrom(MagicElementLearned source) {
        System.arraycopy(source.learned, 0, this.learned, 0, this.learned.length);
        this.undeadKills = source.undeadKills;
    }

    private void unlockStartingMagics() {
        for (int magicId : MagicType.getStartingMagicIds()) {
            learned[magicId] = true;
        }
    }
}

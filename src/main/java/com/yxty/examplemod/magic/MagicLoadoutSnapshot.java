package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.capability.ItemLoadedMagicProvider;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * 施法开始时的槽位快照。持续实体、法阵和 Buff 不再读取可能已经切换的法杖槽位。
 */
public final class MagicLoadoutSnapshot {
    private static final int SLOT_COUNT = 4;

    private final int[] magicIds;
    private final MagicKind mainKind;
    private final int selectedPreset;
    private final int sameMagicCount;
    private final boolean homogeneous;

    private MagicLoadoutSnapshot(int[] magicIds, MagicKind mainKind, int selectedPreset) {
        this.magicIds = normalize(magicIds);
        this.mainKind = mainKind;
        this.selectedPreset = selectedPreset;

        int mainId = this.magicIds[0];
        int count = 0;
        for (int id : this.magicIds) {
            if (id == mainId) {
                count++;
            }
        }
        this.sameMagicCount = count;
        this.homogeneous = mainId != MagicType.EmptyMagic && count == SLOT_COUNT;
    }

    public static MagicLoadoutSnapshot fromStack(ItemStack stack, MagicKind mainKind) {
        return stack.getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC)
                .map(cap -> new MagicLoadoutSnapshot(
                        cap.GetLoadedMagic(),
                        mainKind,
                        cap.getLoaded_magic_idx()))
                .orElseGet(() -> single(MagicType.EmptyMagic, mainKind));
    }

    public static MagicLoadoutSnapshot single(int mainMagicId, MagicKind mainKind) {
        return new MagicLoadoutSnapshot(
                new int[]{mainMagicId, MagicType.EmptyMagic, MagicType.EmptyMagic, MagicType.EmptyMagic},
                mainKind,
                0);
    }

    private static int[] normalize(int[] source) {
        int[] normalized = new int[SLOT_COUNT];
        Arrays.fill(normalized, MagicType.EmptyMagic);
        if (source != null) {
            System.arraycopy(source, 0, normalized, 0, Math.min(source.length, SLOT_COUNT));
        }
        return normalized;
    }

    public int mainMagicId() {
        return magicIds[0];
    }

    public int subMagicId(int subSlotIndex) {
        if (subSlotIndex < 1 || subSlotIndex >= SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Sub magic slot must be in [1, 3]: " + subSlotIndex);
        }
        return magicIds[subSlotIndex];
    }

    public int[] magicIds() {
        return magicIds.clone();
    }

    public MagicKind mainKind() {
        return mainKind;
    }

    public int selectedPreset() {
        return selectedPreset;
    }

    public int sameMagicCount() {
        return sameMagicCount;
    }

    public boolean homogeneous() {
        return homogeneous;
    }

    public int count(int magicId) {
        int count = 0;
        for (int id : magicIds) {
            if (id == magicId) {
                count++;
            }
        }
        return count;
    }
}

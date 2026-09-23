package com.yxty.examplemod.capability;

import com.yxty.examplemod.MagicType;
import net.minecraft.nbt.CompoundTag;

public class ItemLoadedMagic {
    private int[][] magic_list = new int[5][4];
    private int loaded_magic_idx;

    public ItemLoadedMagic() {
        // 槽位 ID 由 MagicType 统一定义；当前注册范围为 0（空）到 10（冰盾）。
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 4; j++) {
                magic_list[i][j] = MagicType.EmptyMagic;
            }
        }
        // 新制作的法杖五页预设全部为空，必须先在附魔台装配魔法才能施法。
        loaded_magic_idx = 0;
    }

    public void SetMagic(int idx, int main_id, int sub1, int sub2, int sub3) {
        validatePresetIndex(idx);
        magic_list[idx][0] = main_id;
        magic_list[idx][1] = sub1;
        magic_list[idx][2] = sub2;
        magic_list[idx][3] = sub3;
    }
    public int[] GetMagic(int idx){
        validatePresetIndex(idx);
        int[] magic = new int[4];
        magic[0] = magic_list[idx][0];
        magic[1] = magic_list[idx][1];
        magic[2] = magic_list[idx][2];
        magic[3] = magic_list[idx][3];
        return magic;
    }
    public int[] GetLoadedMagic(){
        int[] magic = new int[4];
        magic[0] = magic_list[loaded_magic_idx][0];
        magic[1] = magic_list[loaded_magic_idx][1];
        magic[2] = magic_list[loaded_magic_idx][2];
        magic[3] = magic_list[loaded_magic_idx][3];
        return magic;
    }
    public void saveNBTData(CompoundTag tag) {
        // 将二维数组扁平化为一维数组 (5 * 4 = 20)
        int[] flatArray = new int[20];
        for (int i = 0; i < 5; i++) {
            System.arraycopy(magic_list[i], 0, flatArray, i * 4, 4);
        }
        tag.putIntArray("MagicList", flatArray);
        tag.putInt("LoadedMagicIndex", loaded_magic_idx);
    }

    // --- NBT 读取逻辑 ---
    public void loadNBTData(CompoundTag tag) {
        if (tag.contains("MagicList")) {
            int[] flatArray = tag.getIntArray("MagicList");
            // 确保数据长度匹配，防止数组越界
            if (flatArray.length == 20) {
                for (int i = 0; i < 5; i++) {
                    System.arraycopy(flatArray, i * 4, magic_list[i], 0, 4);
                }
            }
        }
        if (tag.contains("LoadedMagicIndex")) {
            loaded_magic_idx = Math.max(0, Math.min(4, tag.getInt("LoadedMagicIndex")));
        }
    }

    // 建议添加 copyFrom 用于数据同步
    public void copyFrom(ItemLoadedMagic source) {
        for(int i = 0; i < 5; i++) {
            System.arraycopy(source.magic_list[i], 0, this.magic_list[i], 0, 4);
        }
        this.loaded_magic_idx = source.loaded_magic_idx;
    }

    public int getLoaded_magic_idx() {
        return loaded_magic_idx;
    }

    public void setLoaded_magic_idx(int loaded_magic_idx) {
        validatePresetIndex(loaded_magic_idx);
        this.loaded_magic_idx = loaded_magic_idx;
    }

    private static void validatePresetIndex(int idx) {
        if (idx < 0 || idx >= 5) {
            throw new IllegalArgumentException("Magic preset index must be between 0 and 4: " + idx);
        }
    }
}

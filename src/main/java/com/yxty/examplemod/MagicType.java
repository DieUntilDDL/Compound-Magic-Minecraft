package com.yxty.examplemod;

import com.yxty.examplemod.magic.BaseMagic;
import com.yxty.examplemod.magic.BlastDash;
import com.yxty.examplemod.magic.FireBreath;
import com.yxty.examplemod.magic.IceMist;
import com.yxty.examplemod.magic.Sanctuary;
import com.yxty.examplemod.magic.ThunderStorm;
import com.yxty.examplemod.magic.HolyBlessing;
import com.yxty.examplemod.magic.IceShield;
import com.yxty.examplemod.magic.DarkDevour;
import com.yxty.examplemod.magic.LaserBeam;
import com.yxty.examplemod.magic.ThunderChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MagicType {
    public static final Map<Integer, BaseMagic> ALL_MAGIC = new HashMap<>();
    public static final int EmptyMagic = 0;
    public static final int LightningChain = 1;
    public static final int LaserBeam = 2;
    public static final int DarkDevourMagic = 3;
    public static final int BlastDashMagic = 4;
    public static final int FireBreathMagic = 5;
    public static final int IceMistMagic = 6;
    public static final int SanctuaryMagic = 7;
    public static final int ThunderStormMagic = 8;
    public static final int HolyBlessingMagic = 9;
    public static final int IceShieldMagic = 10;
    private static final List<Integer> STARTING_MAGIC_IDS = List.of(
            FireBreathMagic,
            IceShieldMagic,
            LightningChain,
            SanctuaryMagic);

    public static BaseMagic getMagic(int id) {
        return ALL_MAGIC.get(id);
    }

    public static List<Integer> getRegisteredMagicIds() {
        List<Integer> ids = new ArrayList<>(ALL_MAGIC.keySet());
        ids.remove(Integer.valueOf(EmptyMagic));
        Collections.sort(ids);
        return ids;
    }

    public static List<Integer> getStartingMagicIds() {
        return new ArrayList<>(STARTING_MAGIC_IDS);
    }

    public static String getTranslationKey(int id) {
        return switch (id) {
            case EmptyMagic -> "magic.program_magic.empty";
            case LightningChain -> "magic.program_magic.lightning_chain";
            case LaserBeam -> "magic.program_magic.laser_beam";
            case DarkDevourMagic -> "magic.program_magic.dark_devour";
            case BlastDashMagic -> "magic.program_magic.blast_dash";
            case FireBreathMagic -> "magic.program_magic.fire_breath";
            case IceMistMagic -> "magic.program_magic.ice_mist";
            case SanctuaryMagic -> "magic.program_magic.sanctuary";
            case ThunderStormMagic -> "magic.program_magic.thunder_storm";
            case HolyBlessingMagic -> "magic.program_magic.holy_blessing";
            case IceShieldMagic -> "magic.program_magic.ice_shield";
            default -> "magic.program_magic.unknown";
        };
    }

    public static int countLoaded(int[] loaded, int magicId) {
        if (loaded == null) {
            return 0;
        }
        int count = 0;
        for (int id : loaded) {
            if (id == magicId) {
                count++;
            }
        }
        return count;
    }

    public static void init() {
        ALL_MAGIC.clear();
        ALL_MAGIC.put(LightningChain, new ThunderChain());
        ALL_MAGIC.put(LaserBeam, new LaserBeam());
        ALL_MAGIC.put(DarkDevourMagic, new DarkDevour());
        ALL_MAGIC.put(BlastDashMagic, new BlastDash());
        ALL_MAGIC.put(FireBreathMagic, new FireBreath());
        ALL_MAGIC.put(IceMistMagic, new IceMist());
        ALL_MAGIC.put(SanctuaryMagic, new Sanctuary());
        ALL_MAGIC.put(ThunderStormMagic, new ThunderStorm());
        ALL_MAGIC.put(HolyBlessingMagic, new HolyBlessing());
        ALL_MAGIC.put(IceShieldMagic, new IceShield());
    }
}

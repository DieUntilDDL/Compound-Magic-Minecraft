package com.yxty.examplemod;

import com.yxty.examplemod.effect.BlueFlameMobEffect;
import com.yxty.examplemod.effect.HolyBlessingMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers status-effect entries used for vanilla-style HUD and inventory display. */
public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ProgramMagic.MODID);

    public static final RegistryObject<MobEffect> HOLY_BLESSING = MOB_EFFECTS.register(
            "holy_blessing", HolyBlessingMobEffect::new);
    public static final RegistryObject<MobEffect> BLUE_FLAME = MOB_EFFECTS.register(
            "blue_flame", BlueFlameMobEffect::new);

    private ModMobEffects() {
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}

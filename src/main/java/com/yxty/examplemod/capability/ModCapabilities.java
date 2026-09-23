package com.yxty.examplemod.capability;

import com.yxty.examplemod.ProgramMagic;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {
    private ModCapabilities() {
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ItemLoadedMagic.class);
        event.register(MagicElementLearned.class);
        event.register(ManaData.class);
    }
}

package com.yxty.examplemod.event;

import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.client.MagicEnchantmentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Replaces the vanilla enchantment screen with a wand-aware visual wrapper. */
@Mod.EventBusSubscriber(
        modid = ProgramMagic.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class MagicEnchantmentScreenHandler {
    private MagicEnchantmentScreenHandler() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof EnchantmentScreen vanillaScreen)
                || vanillaScreen instanceof MagicEnchantmentScreen
                || Minecraft.getInstance().player == null) {
            return;
        }

        event.setNewScreen(new MagicEnchantmentScreen(
                vanillaScreen.getMenu(),
                Minecraft.getInstance().player.getInventory(),
                vanillaScreen.getTitle()));
    }
}

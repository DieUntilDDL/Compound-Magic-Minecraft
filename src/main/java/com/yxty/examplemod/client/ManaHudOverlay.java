package com.yxty.examplemod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yxty.examplemod.ProgramMagic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ManaHudOverlay {
    private static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath(
            ProgramMagic.MODID, "textures/gui/empty_mana_bar.png");
    private static final ResourceLocation HALF = ResourceLocation.fromNamespaceAndPath(
            ProgramMagic.MODID, "textures/gui/half_mana_bar.png");
    private static final ResourceLocation FULL = ResourceLocation.fromNamespaceAndPath(
            ProgramMagic.MODID, "textures/gui/full_mana_bar.png");

    private static final int ICON_COUNT = 10;
    private static final int ICON_SIZE = 9;
    private static final int ICON_SPACING = 8;

    private ManaHudOverlay() {
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.AIR_LEVEL.id(), "mana_bar", ManaHudOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !gui.shouldDrawSurvivalElements()) {
            return;
        }

        boolean airBarVisible = minecraft.player.isEyeInFluidType(ForgeMod.WATER_TYPE.get())
                || minecraft.player.getAirSupply() < minecraft.player.getMaxAirSupply();
        int right = screenWidth / 2 + 91;
        int y = screenHeight - 49 - (airBarVisible ? 10 : 0);
        float manaPerIcon = ClientManaState.maxMana() / ICON_COUNT;

        gui.setupOverlayRenderState(true, false);
        RenderSystem.enableBlend();
        for (int slot = 0; slot < ICON_COUNT; slot++) {
            float remaining = ClientManaState.mana() - slot * manaPerIcon;
            ResourceLocation texture = remaining >= manaPerIcon - 1.0E-4F
                    ? FULL
                    : remaining >= manaPerIcon * 0.5F ? HALF : EMPTY;
            int x = right - slot * ICON_SPACING - ICON_SIZE;
            graphics.blit(texture, x, y, ICON_SIZE, ICON_SIZE,
                    0.0F, 0.0F, 16, 16, 16, 16);
        }
        RenderSystem.disableBlend();
    }
}

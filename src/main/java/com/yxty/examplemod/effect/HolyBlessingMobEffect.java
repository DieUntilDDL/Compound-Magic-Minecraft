package com.yxty.examplemod.effect;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/**
 * 神圣祝福的原版状态栏显示入口。
 * 实际增伤、减伤仍由 MagicStatusManager 处理，这个效果本身不添加属性。
 */
public final class HolyBlessingMobEffect extends MobEffect {
    private static final int EFFECT_COLOR = 0xF4D76B;

    public HolyBlessingMobEffect() {
        super(MobEffectCategory.BENEFICIAL, EFFECT_COLOR);
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            @Override
            public boolean renderInventoryIcon(MobEffectInstance instance,
                                               EffectRenderingInventoryScreen<?> screen,
                                               GuiGraphics graphics, int x, int y,
                                               int blitOffset) {
                renderHolyLightIcon(graphics, x, y + 7, 1.0F);
                return true;
            }

            @Override
            public boolean renderGuiIcon(MobEffectInstance instance, Gui gui,
                                         GuiGraphics graphics, int x, int y,
                                         float z, float alpha) {
                renderHolyLightIcon(graphics, x + 3, y + 3, alpha);
                return true;
            }
        });
    }

    /** Draws an 18x18 golden holy-light sparkle without requiring a texture file. */
    private static void renderHolyLightIcon(GuiGraphics graphics, int x, int y, float alpha) {
        int darkGold = withAlpha(0xC58A24, alpha);
        int gold = withAlpha(0xF6CB4A, alpha);
        int paleGold = withAlpha(0xFFECA0, alpha);
        int white = withAlpha(0xFFFBE6, alpha);

        // Four long rays.
        graphics.fill(x + 8, y + 1, x + 10, y + 17, darkGold);
        graphics.fill(x + 1, y + 8, x + 17, y + 10, darkGold);
        graphics.fill(x + 8, y + 3, x + 10, y + 15, gold);
        graphics.fill(x + 3, y + 8, x + 15, y + 10, gold);

        // Short diagonal rays make the icon read as holy light instead of a plain cross.
        graphics.fill(x + 4, y + 4, x + 6, y + 6, gold);
        graphics.fill(x + 12, y + 4, x + 14, y + 6, gold);
        graphics.fill(x + 4, y + 12, x + 6, y + 14, gold);
        graphics.fill(x + 12, y + 12, x + 14, y + 14, gold);
        graphics.fill(x + 6, y + 6, x + 12, y + 12, paleGold);
        graphics.fill(x + 8, y + 8, x + 10, y + 10, white);
    }

    private static int withAlpha(int rgb, float alpha) {
        int opacity = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return opacity << 24 | rgb;
    }
}

package com.yxty.examplemod.client;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.item.WandItem;
import com.yxty.examplemod.menu.MagicEnchantmentMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 原版附魔台界面；仅在输入法杖时将附魔词条替换为魔法名称。 */
public final class MagicEnchantmentScreen extends EnchantmentScreen {
    private static final ResourceLocation ENCHANTING_TABLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "textures/gui/container/enchanting_table.png");

    public MagicEnchantmentScreen(EnchantmentMenu menu,
                                  Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        EnchantmentMenu enchantmentMenu = getMenu();
        if (!isWandInput()) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // 先让原版完整绘制附魔台、书本、物品栏和物品提示，但暂时隐藏原版词条。
        int[] savedCosts = enchantmentMenu.costs.clone();
        Arrays.fill(enchantmentMenu.costs, 0);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            System.arraycopy(savedCosts, 0, enchantmentMenu.costs, 0, savedCosts.length);
        }

        renderMagicOptions(graphics, mouseX, mouseY);
        renderMagicTooltip(graphics, mouseX, mouseY);
    }

    private void renderMagicOptions(GuiGraphics graphics, int mouseX, int mouseY) {
        EnchantmentMenu enchantmentMenu = getMenu();
        int lapisCount = enchantmentMenu.getGoldCount();
        boolean creative = minecraft != null && minecraft.player != null
                && minecraft.player.getAbilities().instabuild;

        for (int option = 0; option < 3; option++) {
            int magicId = getMagicOffer(option);
            if (magicId <= MagicType.EmptyMagic) {
                continue;
            }

            int rowX = leftPos + 60;
            int rowY = topPos + 14 + 19 * option;
            int lapisCost = option + 1;
            boolean available = creative || minecraft != null && minecraft.player != null
                    && lapisCount >= lapisCost
                    && minecraft.player.experienceLevel >= MagicEnchantmentMenu.WAND_EXPERIENCE_COST;
            boolean hovering = mouseX >= rowX && mouseX < rowX + 108
                    && mouseY >= rowY && mouseY < rowY + 19;

            int textColor;
            if (!available) {
                graphics.blit(ENCHANTING_TABLE_TEXTURE, rowX, rowY,
                        0, 185, 108, 19);
                graphics.blit(ENCHANTING_TABLE_TEXTURE, rowX + 1, rowY + 1,
                        16 * option, 239, 16, 16);
                textColor = 0x403F30;
            } else {
                graphics.blit(ENCHANTING_TABLE_TEXTURE, rowX, rowY,
                        0, hovering ? 204 : 166, 108, 19);
                graphics.blit(ENCHANTING_TABLE_TEXTURE, rowX + 1, rowY + 1,
                        16 * option, 223, 16, 16);
                textColor = hovering ? 0xFFFF80 : 0x685E4A;
            }

            String cost = Integer.toString(MagicEnchantmentMenu.WAND_EXPERIENCE_COST);
            int costX = rowX + 106 - font.width(cost);
            String name = Component.translatable(MagicType.getTranslationKey(magicId)).getString();
            int nameWidth = Math.max(0, costX - (rowX + 20) - 3);
            String visibleName = font.plainSubstrByWidth(name, nameWidth);
            graphics.drawString(font, visibleName, rowX + 20, rowY + 5, textColor, false);
            graphics.drawString(font, cost, costX, rowY + 11,
                    available ? 0x80A040 : 0x404040, false);
        }
    }

    private void renderMagicTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        EnchantmentMenu enchantmentMenu = getMenu();
        for (int option = 0; option < 3; option++) {
            if (!isHovering(60, 14 + 19 * option, 108, 17, mouseX, mouseY)) {
                continue;
            }

            int magicId = getMagicOffer(option);
            if (magicId <= MagicType.EmptyMagic) {
                return;
            }
            int lapisCost = option + 1;
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(MagicType.getTranslationKey(magicId))
                    .withStyle(ChatFormatting.WHITE));
            tooltip.add(CommonComponents.EMPTY);

            if (!minecraft.player.getAbilities().instabuild) {
                MutableComponent lapisLine = lapisCost == 1
                        ? Component.translatable("container.enchant.lapis.one")
                        : Component.translatable("container.enchant.lapis.many", lapisCost);
                tooltip.add(lapisLine.withStyle(enchantmentMenu.getGoldCount() >= lapisCost
                        ? ChatFormatting.GRAY : ChatFormatting.RED));
                tooltip.add(Component.translatable(
                                "gui.program_magic.enchant_experience_cost",
                                MagicEnchantmentMenu.WAND_EXPERIENCE_COST)
                        .withStyle(minecraft.player.experienceLevel
                                        >= MagicEnchantmentMenu.WAND_EXPERIENCE_COST
                                ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
            tooltip.add(Component.translatable("gui.program_magic.random_sub_magic")
                    .withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
    }

    private int getMagicOffer(int option) {
        return option >= 0 && option < 3
                ? getMenu().enchantClue[option]
                : MagicType.EmptyMagic;
    }

    private boolean isWandInput() {
        return getMenu().getSlot(0).getItem().getItem() instanceof WandItem;
    }
}

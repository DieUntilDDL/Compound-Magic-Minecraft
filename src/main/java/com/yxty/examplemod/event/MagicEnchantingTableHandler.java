package com.yxty.examplemod.event;

import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.menu.MagicEnchantmentMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Replaces the vanilla enchanting-table menu while leaving the block itself untouched. */
@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicEnchantingTableHandler {
    private MagicEnchantingTableHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEnchantingTableUse(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(Blocks.ENCHANTING_TABLE) || event.getEntity().isSpectator()) {
            return;
        }

        // 客户端必须继续走原版交互以发送 UseItemOn 数据包；仅由服务端替换菜单。
        if (event.getLevel().isClientSide) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);

        MenuProvider vanillaProvider = state.getMenuProvider(event.getLevel(), event.getPos());
        Component title = vanillaProvider == null
                ? Component.translatable("container.enchant")
                : vanillaProvider.getDisplayName();
        event.getEntity().openMenu(new SimpleMenuProvider(
                (containerId, inventory, player) -> new MagicEnchantmentMenu(
                        containerId, inventory,
                        ContainerLevelAccess.create(event.getLevel(), event.getPos())),
                title));
    }
}

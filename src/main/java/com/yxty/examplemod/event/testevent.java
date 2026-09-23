package com.yxty.examplemod.event; // 确保包名正确

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.magic.BaseMagic;
import com.yxty.examplemod.magic.MagicContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// 注意：这里 bus = Mod.EventBusSubscriber.Bus.FORGE，因为这是游戏内的交互事件
@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class testevent {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // 1. 只在服务端执行 (生成实体必须在服务端)
        if (event.getLevel().isClientSide) return;

        // 2. 检查只在主手触发 (避免左右手触发两次)
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();

        // 3. 判断手持物品：这里我设置为“烈焰棒”(Blaze Rod)，你可以改成 Stick 或其他
        if (player.getMainHandItem().getItem() == Items.BLAZE_ROD) {
            BaseMagic magic = MagicType.getMagic(MagicType.LightningChain);
            magic.castAsMain(MagicContext.single(
                    event.getLevel(),
                    event.getEntity(),
                    MagicType.LightningChain,
                    magic.getKind()));
        }
    }
}

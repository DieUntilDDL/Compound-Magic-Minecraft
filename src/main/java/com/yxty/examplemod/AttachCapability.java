package com.yxty.examplemod;

import com.yxty.examplemod.capability.ItemLoadedMagicProvider;
import com.yxty.examplemod.capability.MagicElementLearnedProvider;
import com.yxty.examplemod.capability.ManaProvider;
import com.yxty.examplemod.item.WandItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import static com.yxty.examplemod.ProgramMagic.MODID;

public class AttachCapability {
    private static final ResourceLocation PLAYER_LEARNED_MAGIC =
            ResourceLocation.fromNamespaceAndPath(MODID, "learned_magic");
    private static final ResourceLocation PLAYER_MANA =
            ResourceLocation.fromNamespaceAndPath(MODID, "mana");

    public static void attachItemCapability(AttachCapabilitiesEvent<ItemStack> event){
        if(event.getObject().getItem() instanceof WandItem wandItem) {
            event.addCapability(new ResourceLocation(MODID, "modloadedmagic"),
                    new ItemLoadedMagicProvider());
        }
    }

    public static void attachEntityCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            MagicElementLearnedProvider learnedProvider = new MagicElementLearnedProvider();
            event.addCapability(PLAYER_LEARNED_MAGIC, learnedProvider);
            event.addListener(learnedProvider::invalidate);

            ManaProvider manaProvider = new ManaProvider();
            event.addCapability(PLAYER_MANA, manaProvider);
            event.addListener(manaProvider::invalidate);
        }
    }
}

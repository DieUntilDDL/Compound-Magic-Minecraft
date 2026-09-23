package com.yxty.examplemod.event;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.capability.ItemLoadedMagicProvider;
import com.yxty.examplemod.item.WandItem;
import com.yxty.examplemod.item.WandCastingStrength;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

/** Operator-only shortcuts for testing homogeneous (purified) wand loadouts. */
@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicDebugCommand {
    private static final Map<String, Integer> MAGIC_IDS = createMagicIds();
    private static final SimpleCommandExceptionType HOLD_WAND = new SimpleCommandExceptionType(
            Component.translatable("command.program_magic.debug.hold_wand"));
    private static final SimpleCommandExceptionType UNKNOWN_MAGIC = new SimpleCommandExceptionType(
            Component.translatable("command.program_magic.debug.unknown_magic"));

    private MagicDebugCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("magicdebug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("purified")
                        .then(Commands.argument("magic", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(
                                                MAGIC_IDS.keySet(), builder))
                                .executes(context -> setHeldWand(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "magic"))))
                        .then(Commands.literal("all")
                                .executes(context -> giveAllPurifiedWands(
                                        context.getSource().getPlayerOrException()))))
                .then(Commands.literal("strength")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(
                                        WandCastingStrength.MIN, WandCastingStrength.MAX))
                                .executes(context -> setHeldWandStrength(
                                        context.getSource().getPlayerOrException(),
                                        FloatArgumentType.getFloat(context, "value"))))));
    }

    private static int setHeldWand(ServerPlayer player, String magicName)
            throws CommandSyntaxException {
        Integer magicId = MAGIC_IDS.get(magicName);
        if (magicId == null) {
            throw UNKNOWN_MAGIC.create();
        }

        ItemStack wand = player.getMainHandItem();
        if (!(wand.getItem() instanceof WandItem)) {
            wand = player.getOffhandItem();
        }
        if (!(wand.getItem() instanceof WandItem) || !configureCurrentPreset(wand, magicId)) {
            throw HOLD_WAND.create();
        }

        // Capability-only mutations are not always noticed by the inventory's normal
        // stack comparison. This revision tag guarantees an immediate client slot sync.
        wand.getOrCreateTag().putLong("MagicDebugRevision", player.level().getGameTime());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable(
                "command.program_magic.debug.purified_set",
                Component.translatable(MagicType.getTranslationKey(magicId))));
        return 1;
    }

    private static int giveAllPurifiedWands(ServerPlayer player) {
        int given = 0;
        for (Map.Entry<String, Integer> entry : MAGIC_IDS.entrySet()) {
            int magicId = entry.getValue();
            ItemStack wand = new ItemStack(ProgramMagic.ADVANCED_WAND.get());
            if (!configureCurrentPreset(wand, magicId)) {
                continue;
            }
            wand.setHoverName(Component.translatable(
                            "command.program_magic.debug.purified_wand_name",
                            Component.translatable(MagicType.getTranslationKey(magicId)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            if (!player.getInventory().add(wand)) {
                player.drop(wand, false);
            }
            given++;
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        int result = given;
        player.sendSystemMessage(Component.translatable(
                "command.program_magic.debug.purified_all", result));
        return result;
    }

    private static int setHeldWandStrength(ServerPlayer player, float strength)
            throws CommandSyntaxException {
        ItemStack wand = heldWand(player);
        WandCastingStrength.set(wand, strength);
        markHeldWandChanged(player, wand);
        player.sendSystemMessage(Component.translatable(
                "command.program_magic.debug.strength_set",
                WandCastingStrength.format(WandCastingStrength.get(wand))));
        return 1;
    }

    private static ItemStack heldWand(ServerPlayer player) throws CommandSyntaxException {
        ItemStack wand = player.getMainHandItem();
        if (!(wand.getItem() instanceof WandItem)) {
            wand = player.getOffhandItem();
        }
        if (!(wand.getItem() instanceof WandItem)) {
            throw HOLD_WAND.create();
        }
        return wand;
    }

    private static void markHeldWandChanged(ServerPlayer player, ItemStack wand) {
        wand.getOrCreateTag().putLong("MagicDebugRevision", player.level().getGameTime());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static boolean configureCurrentPreset(ItemStack wand, int magicId) {
        return wand.getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC)
                .map(loadedMagic -> {
                    int preset = loadedMagic.getLoaded_magic_idx();
                    loadedMagic.SetMagic(
                            preset, magicId, magicId, magicId, magicId);
                    return true;
                })
                .orElse(false);
    }

    private static Map<String, Integer> createMagicIds() {
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put("lightning_chain", MagicType.LightningChain);
        ids.put("laser_beam", MagicType.LaserBeam);
        ids.put("dark_devour", MagicType.DarkDevourMagic);
        ids.put("blast_dash", MagicType.BlastDashMagic);
        ids.put("fire_breath", MagicType.FireBreathMagic);
        ids.put("ice_mist", MagicType.IceMistMagic);
        ids.put("sanctuary", MagicType.SanctuaryMagic);
        ids.put("thunder_storm", MagicType.ThunderStormMagic);
        ids.put("holy_blessing", MagicType.HolyBlessingMagic);
        ids.put("ice_shield", MagicType.IceShieldMagic);
        return ids;
    }
}

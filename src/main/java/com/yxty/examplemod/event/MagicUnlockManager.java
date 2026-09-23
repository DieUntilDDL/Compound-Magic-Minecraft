package com.yxty.examplemod.event;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.capability.MagicElementLearned;
import com.yxty.examplemod.capability.MagicElementLearnedProvider;
import com.yxty.examplemod.capability.ManaData;
import com.yxty.examplemod.capability.ManaProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Server-authoritative magic progression and unlock conditions. */
@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicUnlockManager {
    private static final int HOLY_BLESSING_KILL_GOAL = 100;

    private MagicUnlockManager() {
    }

    public static List<Integer> getUnlockedMagicIds(Player player) {
        MagicElementLearned learned = getLearnedData(player);
        List<Integer> pool = MagicType.getRegisteredMagicIds();
        if (learned == null) {
            pool.retainAll(MagicType.getStartingMagicIds());
            return pool;
        }
        pool.removeIf(magicId -> !learned.isLearned(magicId));
        return pool;
    }

    public static boolean isUnlocked(Player player, int magicId) {
        MagicElementLearned learned = getLearnedData(player);
        return learned != null
                ? learned.isLearned(magicId)
                : MagicType.getStartingMagicIds().contains(magicId);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        MagicElementLearned oldData = getLearnedData(event.getOriginal());
        MagicElementLearned newData = getLearnedData(event.getEntity());
        if (oldData != null && newData != null) {
            newData.copyFrom(oldData);
        }
        ManaData oldMana = event.getOriginal().getCapability(ManaProvider.PLAYER_MANA)
                .resolve().orElse(null);
        ManaData newMana = event.getEntity().getCapability(ManaProvider.PLAYER_MANA)
                .resolve().orElse(null);
        if (oldMana != null && newMana != null) {
            newMana.copyFrom(oldMana);
        }
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(findResponsiblePlayer(event.getSource()) instanceof ServerPlayer player)) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (victim instanceof EnderDragon) {
            unlock(player, MagicType.LaserBeam,
                    "message.program_magic.unlock.laser", ChatFormatting.DARK_PURPLE);
        }
        if (victim instanceof Ghast
                && event.getSource().getDirectEntity() instanceof LargeFireball) {
            unlock(player, MagicType.BlastDashMagic,
                    "message.program_magic.unlock.blast_dash", ChatFormatting.GOLD);
        }
        if (victim instanceof Evoker) {
            unlock(player, MagicType.DarkDevourMagic,
                    "message.program_magic.unlock.dark_devour", ChatFormatting.DARK_PURPLE);
        }

        if (victim.getMobType() == MobType.UNDEAD) {
            MagicElementLearned learned = getLearnedData(player);
            if (learned != null
                    && !learned.isLearned(MagicType.HolyBlessingMagic)
                    && learned.recordUndeadKill() >= HOLY_BLESSING_KILL_GOAL) {
                unlock(player, MagicType.HolyBlessingMagic,
                        "message.program_magic.unlock.holy_blessing", ChatFormatting.YELLOW);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0) {
            return;
        }

        MagicElementLearned learned = getLearnedData(player);
        if (learned == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!learned.isLearned(MagicType.ThunderStormMagic) && level.isThundering()) {
            unlock(player, MagicType.ThunderStormMagic,
                    "message.program_magic.unlock.thunder_storm", ChatFormatting.AQUA);
        }

        if (!learned.isLearned(MagicType.IceMistMagic)) {
            BlockPos feet = player.blockPosition();
            boolean insidePowderSnow = level.getBlockState(feet).is(Blocks.POWDER_SNOW);
            if (insidePowderSnow && level.getBiome(feet).value().coldEnoughToSnow(feet)) {
                unlock(player, MagicType.IceMistMagic,
                        "message.program_magic.unlock.ice_mist", ChatFormatting.BLUE);
            }
        }
    }

    private static boolean unlock(ServerPlayer player, int magicId,
                                  String messageKey, ChatFormatting magicColor) {
        MagicElementLearned learned = getLearnedData(player);
        if (learned == null || !learned.learn(magicId)) {
            return false;
        }

        Component magicName = Component.translatable(MagicType.getTranslationKey(magicId))
                .withStyle(magicColor, ChatFormatting.BOLD);
        Component message = Component.translatable(
                        messageKey, player.getDisplayName(), magicName)
                .withStyle(ChatFormatting.GRAY);
        player.server.getPlayerList().broadcastSystemMessage(message, false);
        return true;
    }

    private static MagicElementLearned getLearnedData(Player player) {
        return player.getCapability(MagicElementLearnedProvider.MAGIC_ELEMENT_LEARNED)
                .resolve()
                .orElse(null);
    }

    private static Player findResponsiblePlayer(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}

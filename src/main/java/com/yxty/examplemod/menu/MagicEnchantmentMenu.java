package com.yxty.examplemod.menu;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.capability.ItemLoadedMagic;
import com.yxty.examplemod.capability.ItemLoadedMagicProvider;
import com.yxty.examplemod.event.MagicUnlockManager;
import com.yxty.examplemod.item.WandItem;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.List;

/**
 * 原版附魔菜单的兼容扩展：普通物品保持原版逻辑，法杖改为随机装配魔法。
 */
public final class MagicEnchantmentMenu extends EnchantmentMenu {
    public static final int WAND_EXPERIENCE_COST = 3;
    public static final float PURIFICATION_CHANCE = 0.05F;
    private static final int OPTION_COUNT = 3;

    private final ContainerLevelAccess magicAccess;
    private final Player enchantingPlayer;
    private final RandomSource offerRandom = RandomSource.create();
    private int offerSeed;

    public MagicEnchantmentMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public MagicEnchantmentMenu(int containerId, Inventory inventory,
                                ContainerLevelAccess access) {
        super(containerId, inventory, access);
        this.magicAccess = access;
        this.enchantingPlayer = inventory.player;
        this.offerSeed = inventory.player.getEnchantmentSeed();
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack input = getSlot(0).getItem();
        if (input.getItem() instanceof WandItem) {
            rebuildWandOffers();
        } else {
            rebuildVanillaOffers(input);
        }
    }

    private void rebuildWandOffers() {
        resetOffers();
        List<Integer> pool = MagicUnlockManager.getUnlockedMagicIds(enchantingPlayer);
        if (pool.isEmpty()) {
            broadcastChanges();
            return;
        }

        offerRandom.setSeed(offerSeed);
        // 同一次刷新不重复展示主魔法，但每一个位置都从完整魔法池等概率产生。
        for (int i = pool.size() - 1; i > 0; i--) {
            int swapIndex = offerRandom.nextInt(i + 1);
            int value = pool.get(i);
            pool.set(i, pool.get(swapIndex));
            pool.set(swapIndex, value);
        }

        for (int option = 0; option < Math.min(OPTION_COUNT, pool.size()); option++) {
            costs[option] = WAND_EXPERIENCE_COST;
            enchantClue[option] = pool.get(option);
            levelClue[option] = 1;
        }
        broadcastChanges();
    }

    /** 原样保留原版物品的书架强度、候选等级和 Forge 事件。 */
    private void rebuildVanillaOffers(ItemStack input) {
        if (input.isEmpty() || !input.isEnchantable()) {
            resetOffers();
            broadcastChanges();
            return;
        }

        magicAccess.execute((level, tablePos) -> {
            float enchantPower = 0.0F;
            for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
                if (EnchantmentTableBlock.isValidBookShelf(level, tablePos, offset)) {
                    BlockPos shelfPos = tablePos.offset(offset);
                    enchantPower += level.getBlockState(shelfPos)
                            .getEnchantPowerBonus(level, shelfPos);
                }
            }

            offerRandom.setSeed(offerSeed);
            for (int option = 0; option < OPTION_COUNT; option++) {
                costs[option] = EnchantmentHelper.getEnchantmentCost(
                        offerRandom, option, (int) enchantPower, input);
                enchantClue[option] = -1;
                levelClue[option] = -1;
                if (costs[option] < option + 1) {
                    costs[option] = 0;
                }
                costs[option] = ForgeEventFactory.onEnchantmentLevelSet(
                        level, tablePos, option, (int) enchantPower, input, costs[option]);
            }

            for (int option = 0; option < OPTION_COUNT; option++) {
                if (costs[option] <= 0) {
                    continue;
                }
                List<EnchantmentInstance> candidates = getVanillaEnchantments(
                        input, option, costs[option]);
                if (!candidates.isEmpty()) {
                    EnchantmentInstance clue = candidates.get(offerRandom.nextInt(candidates.size()));
                    enchantClue[option] = BuiltInRegistries.ENCHANTMENT.getId(clue.enchantment);
                    levelClue[option] = clue.level;
                }
            }
            broadcastChanges();
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int option) {
        if (option < 0 || option >= OPTION_COUNT) {
            Util.logAndPauseIfInIde(player.getName() + " pressed invalid enchantment button: " + option);
            return false;
        }
        return getSlot(0).getItem().getItem() instanceof WandItem
                ? enchantWand(player, option)
                : enchantVanillaItem(player, option);
    }

    private boolean enchantWand(Player player, int option) {
        ItemStack wand = getSlot(0).getItem();
        ItemStack lapis = getSlot(1).getItem();
        int lapisCost = option + 1;
        int mainMagicId = getMagicOffer(option);
        boolean creative = player.getAbilities().instabuild;

        if (!(wand.getItem() instanceof WandItem)
                || costs[option] <= 0
                || MagicType.getMagic(mainMagicId) == null
                || !MagicUnlockManager.isUnlocked(player, mainMagicId)
                || (!creative && (lapis.getCount() < lapisCost
                || player.experienceLevel < WAND_EXPERIENCE_COST))) {
            return false;
        }

        magicAccess.execute((level, tablePos) -> {
            ItemStack currentWand = getSlot(0).getItem();
            ItemStack currentLapis = getSlot(1).getItem();
            if (currentWand != wand
                    || !(currentWand.getItem() instanceof WandItem)
                    || getMagicOffer(option) != mainMagicId
                    || !MagicUnlockManager.isUnlocked(player, mainMagicId)
                    || (!creative && (currentLapis.getCount() < lapisCost
                    || player.experienceLevel < WAND_EXPERIENCE_COST))) {
                return;
            }

            ItemLoadedMagic loadedMagic = currentWand
                    .getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC)
                    .resolve().orElse(null);
            List<Integer> pool = MagicUnlockManager.getUnlockedMagicIds(player);
            if (loadedMagic == null || pool.isEmpty()) {
                return;
            }

            long subSeed = ((long) offerSeed << 32)
                    ^ (long) mainMagicId * 0x9E3779B97F4A7C15L
                    ^ (long) option * 0xC2B2AE3D27D4EB4FL;
            RandomSource subRandom = RandomSource.create(subSeed);
            int sub1;
            int sub2;
            int sub3;
            if (subRandom.nextFloat() < PURIFICATION_CHANCE) {
                sub1 = mainMagicId;
                sub2 = mainMagicId;
                sub3 = mainMagicId;
            } else {
                sub1 = pool.get(subRandom.nextInt(pool.size()));
                sub2 = pool.get(subRandom.nextInt(pool.size()));
                sub3 = pool.get(subRandom.nextInt(pool.size()));

                // 普通随机分支不额外产生“全同”组合，使纯化概率稳定在约 5%。
                if (sub1 == mainMagicId && sub2 == mainMagicId && sub3 == mainMagicId
                        && pool.size() > 1) {
                    int replacementIndex = subRandom.nextInt(pool.size() - 1);
                    int replacement = pool.get(replacementIndex);
                    if (replacement == mainMagicId) {
                        replacement = pool.get(pool.size() - 1);
                    }
                    sub3 = replacement;
                }
            }
            loadedMagic.SetMagic(loadedMagic.getLoaded_magic_idx(),
                    mainMagicId, sub1, sub2, sub3);

            if (!creative) {
                currentLapis.shrink(lapisCost);
                if (currentLapis.isEmpty()) {
                    getSlot(1).set(ItemStack.EMPTY);
                }
            }
            player.onEnchantmentPerformed(currentWand, WAND_EXPERIENCE_COST);
            player.awardStat(Stats.ENCHANT_ITEM);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ENCHANTED_ITEM.trigger(
                        serverPlayer, currentWand, WAND_EXPERIENCE_COST);
            }

            offerSeed = player.getEnchantmentSeed();
            getSlot(0).setChanged();
            getSlot(1).setChanged();
            slotsChanged(null);
            level.playSound(null, tablePos, SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS, 1.0F,
                    level.random.nextFloat() * 0.1F + 0.9F);
        });
        return true;
    }

    /** Copy of the vanilla click path, using this menu's synchronized seed. */
    private boolean enchantVanillaItem(Player player, int option) {
        ItemStack input = getSlot(0).getItem();
        ItemStack lapis = getSlot(1).getItem();
        int lapisCost = option + 1;
        boolean creative = player.getAbilities().instabuild;
        if ((!creative && lapis.getCount() < lapisCost)
                || costs[option] <= 0
                || input.isEmpty()
                || (!creative && (player.experienceLevel < lapisCost
                || player.experienceLevel < costs[option]))) {
            return false;
        }

        magicAccess.execute((level, tablePos) -> {
            List<EnchantmentInstance> enchantments = getVanillaEnchantments(
                    input, option, costs[option]);
            if (enchantments.isEmpty()) {
                return;
            }

            player.onEnchantmentPerformed(input, lapisCost);
            ItemStack enchantedStack = input;
            boolean book = input.is(Items.BOOK);
            if (book) {
                enchantedStack = new ItemStack(Items.ENCHANTED_BOOK);
                CompoundTag tag = input.getTag();
                if (tag != null) {
                    enchantedStack.setTag(tag.copy());
                }
                getSlot(0).set(enchantedStack);
            }

            for (EnchantmentInstance enchantment : enchantments) {
                if (book) {
                    EnchantedBookItem.addEnchantment(enchantedStack, enchantment);
                } else {
                    enchantedStack.enchant(enchantment.enchantment, enchantment.level);
                }
            }

            if (!creative) {
                lapis.shrink(lapisCost);
                if (lapis.isEmpty()) {
                    getSlot(1).set(ItemStack.EMPTY);
                }
            }
            player.awardStat(Stats.ENCHANT_ITEM);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ENCHANTED_ITEM.trigger(serverPlayer, enchantedStack, lapisCost);
            }

            offerSeed = player.getEnchantmentSeed();
            getSlot(0).setChanged();
            getSlot(1).setChanged();
            slotsChanged(null);
            level.playSound(null, tablePos, SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS, 1.0F,
                    level.random.nextFloat() * 0.1F + 0.9F);
        });
        return true;
    }

    private List<EnchantmentInstance> getVanillaEnchantments(
            ItemStack stack, int option, int cost) {
        offerRandom.setSeed((long) offerSeed + option);
        List<EnchantmentInstance> enchantments = EnchantmentHelper.selectEnchantment(
                offerRandom, stack, cost, false);
        if (stack.is(Items.BOOK) && enchantments.size() > 1) {
            enchantments.remove(offerRandom.nextInt(enchantments.size()));
        }
        return enchantments;
    }

    private void resetOffers() {
        for (int option = 0; option < OPTION_COUNT; option++) {
            costs[option] = 0;
            enchantClue[option] = -1;
            levelClue[option] = -1;
        }
    }

    public int getMagicOffer(int option) {
        return option >= 0 && option < OPTION_COUNT ? enchantClue[option] : MagicType.EmptyMagic;
    }

    public boolean isWandInput() {
        return getSlot(0).getItem().getItem() instanceof WandItem;
    }

    @Override
    public int getEnchantmentSeed() {
        return offerSeed;
    }
}

package com.yxty.examplemod.item;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.capability.ItemLoadedMagicProvider;
import com.yxty.examplemod.capability.ManaManager;
import com.yxty.examplemod.magic.BaseMagic;
import com.yxty.examplemod.magic.MagicContext;
import com.yxty.examplemod.magic.MagicKind;
import com.yxty.examplemod.magic.MagicLoadoutSnapshot;
import com.yxty.examplemod.magic.MagicManaCosts;
import com.yxty.examplemod.renderer.WandItemRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class WandItem extends Item implements GeoItem{

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final int CAST_DURATION = 40;
    private static final String SUSTAINED_CAST_STARTED_TAG = "SustainedCastStarted";
    private static final String MAGIC_SHARE_TAG = "ProgramMagicLoadout";

    // 【核心新增】用于区分这是哪把法杖的专属 ID
    private final String wandId;
    private final float baseCastingStrength;

    // 构造函数：每种法杖同时声明自己的资源 ID 和固有施法强度。
    public WandItem(Properties properties, String wandId, float baseCastingStrength) {
        super(properties);
        this.wandId = wandId;
        this.baseCastingStrength = WandCastingStrength.clamp(baseCastingStrength);
    }

    // 暴露一个 getter 方法，供后面的 Model 类读取
    public String getWandId() {
        return this.wandId;
    }

    public float getBaseCastingStrength() {
        return this.baseCastingStrength;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String castAnimName = "animation." + this.wandId + ".cast";
        String idleAnimName = "animation." + this.wandId + ".idle";

        controllers.add(new AnimationController<>(this, "controller", 5, state -> {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            ItemDisplayContext perspective = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
            ItemStack renderedStack = state.getData(DataTickets.ITEMSTACK);

            // 同一件物品会同时出现在手中和物品栏里。只有真正的手持渲染实例才播放施法动画，
            // GUI、掉落物、展示框等场景始终保持 idle，避免物品栏图标跟着施法摆动。
            if (isHandRenderPerspective(perspective)
                    && player != null
                    && player.isUsingItem()
                    && player.getUseItem() == renderedStack) {
                state.setControllerSpeed(WandCastingStrength.get(renderedStack));
                return state.setAndContinue(RawAnimation.begin().thenPlay(castAnimName));
            }

            state.setControllerSpeed(WandCastingStrength.DEFAULT);
            return state.setAndContinue(RawAnimation.begin().thenPlay(idleAnimName));
        }));
    }

    @Override
    public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
        return true;
    }

    /**
     * 客户端的原版附魔菜单会用这个判断决定是否保留服务端同步来的选项。
     * 实际点击仍由 MagicEnchantmentMenu 接管，不会给法杖添加普通附魔。
     */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    /** 将法术 Capability 纳入容器槽位的服务端到客户端同步。 */
    @Override
    public @Nullable CompoundTag getShareTag(ItemStack stack) {
        CompoundTag vanillaTag = super.getShareTag(stack);
        CompoundTag shareTag = vanillaTag == null ? new CompoundTag() : vanillaTag.copy();
        stack.getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC).ifPresent(loaded -> {
            CompoundTag magicTag = new CompoundTag();
            loaded.saveNBTData(magicTag);
            shareTag.put(MAGIC_SHARE_TAG, magicTag);
        });
        return shareTag;
    }

    /** 接收槽位更新时，同时刷新客户端用于悬停提示的法术 Capability。 */
    @Override
    public void readShareTag(ItemStack stack, @Nullable CompoundTag receivedTag) {
        CompoundTag vanillaTag = null;
        CompoundTag magicTag = null;
        if (receivedTag != null) {
            CompoundTag copy = receivedTag.copy();
            if (copy.contains(MAGIC_SHARE_TAG, Tag.TAG_COMPOUND)) {
                magicTag = copy.getCompound(MAGIC_SHARE_TAG).copy();
                copy.remove(MAGIC_SHARE_TAG);
            }
            if (!copy.isEmpty()) {
                vanillaTag = copy;
            }
        }

        super.readShareTag(stack, vanillaTag);
        if (magicTag != null) {
            CompoundTag finalMagicTag = magicTag;
            stack.getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC)
                    .ifPresent(loaded -> loaded.loadNBTData(finalMagicTag));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private WandItemRenderer renderer;
            private final HumanoidModel.ArmPose castPose = HumanoidModel.ArmPose.create(
                    "PROGRAM_MAGIC_WAND_CAST",
                    false,
                    (model, entity, arm) -> {
                        // xRot=-90° 表示手臂水平向前；叠加头部角度后，手臂会跟随玩家瞄准方向。
                        var castingArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                        castingArm.xRot = model.head.xRot - (float) Math.PI / 2.0F;
                        castingArm.yRot = model.head.yRot;
                        castingArm.zRot = 0.0F;
                    });

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new WandItemRenderer();
                return this.renderer;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                if (entity.isUsingItem()
                        && entity.getUsedItemHand() == hand
                        && entity.getUseItem() == stack) {
                    return this.castPose;
                }
                return null;
            }
        });
    }

    public static boolean isHandRenderPerspective(ItemDisplayContext perspective) {
        return perspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || perspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                        "tooltip.program_magic.casting_strength",
                        WandCastingStrength.format(WandCastingStrength.get(stack)))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        int[] loaded = stack.getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC)
                .map(cap -> cap.GetLoadedMagic())
                .orElse(null);
        if (loaded == null || loaded.length < 4
                || loaded[0] == MagicType.EmptyMagic) {
            tooltip.add(Component.translatable("tooltip.program_magic.no_magic")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("tooltip.program_magic.main_magic",
                        magicName(loaded[0]))
                .withStyle(ChatFormatting.AQUA));
        for (int slot = 1; slot < 4; slot++) {
            tooltip.add(Component.translatable("tooltip.program_magic.sub_magic",
                            slot, magicName(loaded[slot]))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (loaded[0] == loaded[1] && loaded[0] == loaded[2] && loaded[0] == loaded[3]) {
            tooltip.add(Component.translatable("tooltip.program_magic.purified")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    // ==========================================
    // 2. Minecraft 物品使用逻辑 (长按蓄力系统)
    // ==========================================

    // 规定这个物品可以被"持续使用"多久
    @Override
    public int getUseDuration(ItemStack stack) {
        BaseMagic magic = getMainMagic(stack);
        if (magic != null) {
            int windup = WandCastingStrength.scaleWindupTicks(
                    magic.getBaseWindupTicks(stack), WandCastingStrength.get(stack));
            return magic.getKind() == MagicKind.SUSTAINED
                    ? windup + magic.getSustainedUseTicks(stack)
                    : windup;
        }
        return CAST_DURATION;
    }

    // 屏蔽原版的吃东西/拉弓动画，交给我们的 GeckoLib 处理
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    // 【阶段一】玩家刚刚按下右键：开始播放施法动画，并进入读条状态
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BaseMagic magic = getMainMagic(itemStack);
        if (magic == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.program_magic.wand_no_magic")
                                .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(itemStack);
        }

        int mainMagicId = getMainMagicId(itemStack);
        MagicManaCosts.Cost manaCost = MagicManaCosts.get(mainMagicId);
        float minimumMana = manaCost.castCost()
                + (magic.getKind() == MagicKind.SUSTAINED ? manaCost.perTickCost() : 0.0F);
        if (!level.isClientSide && !ManaManager.has(player, minimumMana)) {
            reportInsufficientMana(player);
            return InteractionResultHolder.fail(itemStack);
        }

        if (magic.castsImmediately()) {
            if (!level.isClientSide) {
                if (ManaManager.tryConsume(player, manaCost.castCost())) {
                    castAndReport(level, player, itemStack, magic);
                    player.getCooldowns().addCooldown(this, 20);
                } else {
                    reportInsufficientMana(player);
                }
            }
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
        }

        itemStack.getOrCreateTag().remove(SUSTAINED_CAST_STARTED_TAG);
        // 【核心桥梁】这行代码会通知服务器和客户端：开始双端同步读条！
        player.startUsingItem(hand);

        // 暗蚀射线的蓄力球本身就是前摇视觉，因此与法杖动画同时开始；
        // 伤害射线仍由蓄力实体等到同一段前摇结束后才生成。
        if (!level.isClientSide
                && magic.getKind() == MagicKind.SUSTAINED
                && magic.startsDuringWindup()) {
            if (ManaManager.tryConsume(player, manaCost.castCost())) {
                itemStack.getOrCreateTag().putBoolean(SUSTAINED_CAST_STARTED_TAG, true);
                castAndReport(level, player, itemStack, magic);
            } else {
                player.stopUsingItem();
                reportInsufficientMana(player);
                return InteractionResultHolder.fail(itemStack);
            }
        }

        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack,
                          int remainingUseDuration) {
        if (!level.isClientSide && livingEntity instanceof Player player) {
            BaseMagic magic = getMainMagic(stack);
            if (magic != null && magic.getKind() == MagicKind.SUSTAINED) {
                int mainMagicId = getMainMagicId(stack);
                MagicManaCosts.Cost manaCost = MagicManaCosts.get(mainMagicId);
                int windup = WandCastingStrength.scaleWindupTicks(
                        magic.getBaseWindupTicks(stack), WandCastingStrength.get(stack));
                int elapsed = getUseDuration(stack) - remainingUseDuration;
                boolean started = stack.getOrCreateTag()
                        .getBoolean(SUSTAINED_CAST_STARTED_TAG);
                if (!started && elapsed >= windup) {
                    if (!ManaManager.tryConsume(player, manaCost.castCost())) {
                        stopForInsufficientMana(player, stack);
                        return;
                    }
                    stack.getOrCreateTag().putBoolean(SUSTAINED_CAST_STARTED_TAG, true);
                    castAndReport(level, player, stack, magic);
                    started = true;
                }
                if (started && elapsed >= windup && manaCost.perTickCost() > 0.0F
                        && !ManaManager.tryConsume(player, manaCost.perTickCost())) {
                    stopForInsufficientMana(player, stack);
                    return;
                }
            }
        }
        super.onUseTick(level, livingEntity, stack, remainingUseDuration);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entityLiving) {
        if (!level.isClientSide && entityLiving instanceof Player player) {
            BaseMagic magic = getMainMagic(stack);
            if (magic != null && magic.getKind() != MagicKind.SUSTAINED) {
                MagicManaCosts.Cost manaCost = MagicManaCosts.get(getMainMagicId(stack));
                if (ManaManager.tryConsume(player, manaCost.castCost())) {
                    castAndReport(level, player, stack, magic);
                    player.getCooldowns().addCooldown(this, 20);
                } else {
                    reportInsufficientMana(player);
                }
            } else if (magic != null) {
                player.getCooldowns().addCooldown(this, 20);
            }
        }
        stack.getOrCreateTag().remove(SUSTAINED_CAST_STARTED_TAG);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        // 激发类：中途松手不释放。持续类：松手立刻停激光，实体下一 tick 检测到 isUsingItem==false 后自毁。
        if (!level.isClientSide && entityLiving instanceof Player player) {
            BaseMagic magic = getMainMagic(stack);
            if (magic != null && magic.getKind() == MagicKind.SUSTAINED) {
                player.getCooldowns().addCooldown(this, 20);
            }
        }
        stack.getOrCreateTag().remove(SUSTAINED_CAST_STARTED_TAG);
        super.releaseUsing(stack, level, entityLiving, timeLeft);
    }

    private static BaseMagic getMainMagic(ItemStack stack) {
        return MagicType.getMagic(getMainMagicId(stack));
    }

    private static int getMainMagicId(ItemStack stack) {
        return stack.getCapability(ItemLoadedMagicProvider.ITEM_LOADED_MAGIC)
                .map(cap -> {
                    int[] loaded = cap.GetLoadedMagic();
                    return loaded.length > 0 ? loaded[0] : MagicType.EmptyMagic;
                })
                .orElse(MagicType.EmptyMagic);
    }

    private static void stopForInsufficientMana(Player player, ItemStack stack) {
        stack.getOrCreateTag().remove(SUSTAINED_CAST_STARTED_TAG);
        player.stopUsingItem();
        reportInsufficientMana(player);
    }

    private static void reportInsufficientMana(Player player) {
        player.displayClientMessage(
                Component.translatable("message.program_magic.insufficient_mana")
                        .withStyle(ChatFormatting.AQUA), true);
    }

    private static void castAndReport(Level level, Player player, ItemStack stack, BaseMagic magic) {
        MagicContext context = MagicContext.forWand(level, player, stack, magic);
        reportLoadout(player, context);
        magic.castAsMain(context);
    }

    private static void reportLoadout(Player player, MagicContext context) {
        MagicLoadoutSnapshot loadout = context.loadout();
        player.sendSystemMessage(Component.translatable(
                "message.program_magic.cast_debug",
                magicName(loadout.mainMagicId()),
                magicName(loadout.subMagicId(1)),
                magicName(loadout.subMagicId(2)),
                magicName(loadout.subMagicId(3)),
                WandCastingStrength.format(context.castingStrength()))
                .withStyle(ChatFormatting.AQUA));
    }

    private static Component magicName(int magicId) {
        return Component.translatable(MagicType.getTranslationKey(magicId));
    }
}

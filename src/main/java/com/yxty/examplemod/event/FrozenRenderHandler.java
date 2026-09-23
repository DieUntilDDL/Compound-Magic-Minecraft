package com.yxty.examplemod.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.magic.MagicStatusManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** 在被冻结生物的模型周围绘制使用原版方块贴图的大小不一冰块。 */
@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class FrozenRenderHandler {
    private static final Map<LivingEntity, FrozenPose> FROZEN_POSES = new WeakHashMap<>();
    private static final IceChunk[] CHUNKS = {
            new IceChunk(-0.58F, 0.18F, 0.08F, 0.82F, -12.0F),
            new IceChunk(0.55F, 0.34F, -0.10F, 1.02F, 18.0F),
            new IceChunk(-0.42F, 0.62F, -0.28F, 0.70F, 31.0F),
            new IceChunk(0.30F, 0.86F, 0.25F, 0.58F, -27.0F)
    };

    private FrozenRenderHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!MagicStatusManager.isMagicFreezeVisual(entity)) {
            FROZEN_POSES.remove(entity);
            return;
        }

        FrozenPose pose = FROZEN_POSES.computeIfAbsent(entity, FrozenPose::capture);
        // Living tick 被取消后原版不会递减 hurtTime；这里手动推进它，保留正常时长的红色受伤闪烁。
        if (entity.hurtTime > 0) {
            entity.hurtTime--;
        }
        spawnFrozenSnowflakes(entity);
        pose.apply(entity);
        MagicStatusManager.tickClientFreezeVisual(entity);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (MagicStatusManager.isMagicFreezeVisual(entity)) {
            FROZEN_POSES.computeIfAbsent(entity, FrozenPose::capture).apply(entity);
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!MagicStatusManager.isMagicFreezeVisual(entity)) {
            return;
        }

        float width = Math.max(0.6F, entity.getBbWidth());
        float height = Math.max(0.8F, entity.getBbHeight());
        float baseSize = Mth.clamp(Math.max(width * 0.62F, height * 0.26F), 0.46F, 1.0F);
        PoseStack poseStack = event.getPoseStack();
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

        for (IceChunk chunk : CHUNKS) {
            float size = baseSize * chunk.size();
            poseStack.pushPose();
            poseStack.translate(
                    chunk.x() * width,
                    chunk.y() * height,
                    chunk.z() * width);
            poseStack.mulPose(Axis.YP.rotationDegrees(chunk.yRotation()));
            poseStack.scale(size, size, size);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            BlockState state = Blocks.ICE.defaultBlockState();
            blockRenderer.renderSingleBlock(
                    state, poseStack, event.getMultiBufferSource(),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    private static void spawnFrozenSnowflakes(LivingEntity entity) {
        if (entity.level().getGameTime() % 2L != 0L) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            double x = entity.getX()
                    + (entity.getRandom().nextDouble() - 0.5D) * entity.getBbWidth() * 1.7D;
            double y = entity.getY() + entity.getRandom().nextDouble() * entity.getBbHeight();
            double z = entity.getZ()
                    + (entity.getRandom().nextDouble() - 0.5D) * entity.getBbWidth() * 1.7D;
            entity.level().addParticle(ParticleTypes.SNOWFLAKE,
                    x, y, z, 0.0D, 0.012D, 0.0D);
        }
    }

    private record IceChunk(float x, float y, float z, float size,
                            float yRotation) {
    }

    /** 固定所有会参与原版生物模型动画计算的公开状态。 */
    private record FrozenPose(int tickCount, float yRot, float xRot,
                              float bodyRot, float headRot, Pose pose) {
        private static FrozenPose capture(LivingEntity entity) {
            // 连续更新两次可同时把 speed 和 speedOld 清零，同时保留当前步行动画位置。
            entity.walkAnimation.update(0.0F, 1.0F);
            entity.walkAnimation.update(0.0F, 1.0F);
            return new FrozenPose(entity.tickCount, entity.getYRot(), entity.getXRot(),
                    entity.yBodyRot, entity.yHeadRot, entity.getPose());
        }

        private void apply(LivingEntity entity) {
            entity.tickCount = tickCount;
            entity.setYRot(yRot);
            entity.yRotO = yRot;
            entity.setXRot(xRot);
            entity.xRotO = xRot;
            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = bodyRot;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = headRot;
            entity.setPose(pose);
            entity.walkAnimation.setSpeed(0.0F);
            entity.attackAnim = 0.0F;
            entity.oAttackAnim = 0.0F;
            entity.swinging = false;
            entity.swingTime = 0;
            entity.deathTime = 0;
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }
}

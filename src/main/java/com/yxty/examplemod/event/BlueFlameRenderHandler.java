package com.yxty.examplemod.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.client.ClientBlueFlameState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Uses vanilla's animated entity-fire geometry with recolored fire atlas sprites. */
@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class BlueFlameRenderHandler {
    private static final ResourceLocation BLUE_FIRE_0 =
            ResourceLocation.fromNamespaceAndPath(
                    ProgramMagic.MODID, "block/blue_fire_0");
    private static final ResourceLocation BLUE_FIRE_1 =
            ResourceLocation.fromNamespaceAndPath(
                    ProgramMagic.MODID, "block/blue_fire_1");

    private BlueFlameRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!ClientBlueFlameState.isActive(entity) || entity.isInvisible()) {
            return;
        }

        TextureAtlasSprite fire0 = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLUE_FIRE_0);
        TextureAtlasSprite fire1 = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLUE_FIRE_1);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        float scale = entity.getBbWidth() * 1.4F;
        poseStack.scale(scale, scale, scale);
        float halfWidth = 0.5F;
        float heightRemaining = entity.getBbHeight() / scale;
        float verticalOffset = 0.0F;
        float depth = 0.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(
                -Minecraft.getInstance().gameRenderer.getMainCamera().getYRot()));
        poseStack.translate(0.0F, 0.0F,
                -0.3F + (float) ((int) heightRemaining) * 0.02F);

        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(
                Sheets.cutoutBlockSheet());
        int layer = 0;
        while (heightRemaining > 0.0F) {
            TextureAtlasSprite sprite = layer % 2 == 0 ? fire0 : fire1;
            float uLeft = sprite.getU0();
            float vTop = sprite.getV0();
            float uRight = sprite.getU1();
            float vBottom = sprite.getV1();
            if (layer / 2 % 2 == 0) {
                float swap = uRight;
                uRight = uLeft;
                uLeft = swap;
            }
            PoseStack.Pose pose = poseStack.last();
            flameVertex(pose, consumer, halfWidth, -verticalOffset, depth, uRight, vBottom);
            flameVertex(pose, consumer, -halfWidth, -verticalOffset, depth, uLeft, vBottom);
            flameVertex(pose, consumer, -halfWidth, 1.4F - verticalOffset, depth, uLeft, vTop);
            flameVertex(pose, consumer, halfWidth, 1.4F - verticalOffset, depth, uRight, vTop);
            heightRemaining -= 0.45F;
            verticalOffset -= 0.45F;
            halfWidth *= 0.9F;
            depth += 0.03F;
            layer++;
        }
        poseStack.popPose();
    }

    private static void flameVertex(PoseStack.Pose pose, VertexConsumer consumer,
                                    float x, float y, float z, float u, float v) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(240)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}

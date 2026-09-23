package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yxty.examplemod.entity.DarkDevourFieldEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 将黑暗吞噬范围投影为贴合地形的纯黑色网格。 */
public class DarkDevourFieldRenderer extends EntityRenderer<DarkDevourFieldEntity> {
    private static final ResourceLocation UNUSED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final double TILE_SIZE = 0.75D;
    private static final double TILE_OVERLAP = 0.04D;

    public DarkDevourFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DarkDevourFieldEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();
        Vec3 origin = entity.getPosition(partialTick);
        Vec3 forward = entity.forwardVector();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);

        double halfWidth = entity.getFieldWidth() * 0.5D;
        double halfLength = entity.getFieldLength() * 0.5D;
        int alpha = calculateAlpha(entity);

        for (double along = -halfLength; along < halfLength; along += TILE_SIZE) {
            double tileLength = Math.min(TILE_SIZE + TILE_OVERLAP, halfLength - along + TILE_OVERLAP);
            for (double side = -halfWidth; side < halfWidth; side += TILE_SIZE) {
                double tileWidth = Math.min(TILE_SIZE + TILE_OVERLAP, halfWidth - side + TILE_OVERLAP);
                double centerAlong = along + Math.min(TILE_SIZE, halfLength - along) * 0.5D;
                double centerSide = side + Math.min(TILE_SIZE, halfWidth - side) * 0.5D;
                Vec3 tileCenter = origin
                        .add(forward.scale(centerAlong))
                        .add(right.scale(centerSide));
                double surfaceY = entity.findSurfaceY(tileCenter.x, tileCenter.z);
                if (Double.isNaN(surfaceY)) {
                    continue;
                }

                Vec3 center = new Vec3(tileCenter.x, surfaceY, tileCenter.z);
                Vec3 sideOffset = right.scale(tileWidth * 0.5D);
                Vec3 alongOffset = forward.scale(tileLength * 0.5D);
                addVertex(matrix, consumer, center.subtract(sideOffset).subtract(alongOffset).subtract(origin), alpha);
                addVertex(matrix, consumer, center.add(sideOffset).subtract(alongOffset).subtract(origin), alpha);
                addVertex(matrix, consumer, center.add(sideOffset).add(alongOffset).subtract(origin), alpha);
                addVertex(matrix, consumer, center.subtract(sideOffset).add(alongOffset).subtract(origin), alpha);
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static int calculateAlpha(DarkDevourFieldEntity entity) {
        float fadeIn = Math.min(1.0F, entity.tickCount / 4.0F);
        float remaining = entity.getMaxLifetime() - entity.tickCount;
        float fadeOut = Math.min(1.0F, remaining / 8.0F);
        return (int) (255.0F * Math.max(0.0F, Math.min(fadeIn, fadeOut)));
    }

    private static void addVertex(Matrix4f matrix, VertexConsumer consumer, Vec3 position, int alpha) {
        consumer.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color(0, 0, 0, alpha)
                .endVertex();
    }

    @Override
    public boolean shouldRender(DarkDevourFieldEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DarkDevourFieldEntity entity) {
        return UNUSED_TEXTURE;
    }
}

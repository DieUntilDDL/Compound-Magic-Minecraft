package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yxty.examplemod.entity.SanctuaryEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 在圣域有效范围内绘制贴合地面的全亮金黄色光域。 */
public final class SanctuaryRenderer extends EntityRenderer<SanctuaryEntity> {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final double TILE_SIZE = 0.55D;
    private static final double TILE_OVERLAP = 0.035D;

    public SanctuaryRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SanctuaryEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float radius = entity.getVisualRadius();
        if (radius <= 0.0F) {
            return;
        }

        Vec3 origin = entity.getPosition(partialTick);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(
                RenderType.entityTranslucentEmissive(WHITE_TEXTURE));
        float age = entity.tickCount + partialTick;
        float pulse = 0.92F + Mth.sin(age * 0.10F) * 0.08F;
        double radiusSquared = radius * radius;

        for (double localX = -radius; localX < radius; localX += TILE_SIZE) {
            double tileWidth = Math.min(TILE_SIZE, radius - localX);
            double centerX = localX + tileWidth * 0.5D;
            for (double localZ = -radius; localZ < radius; localZ += TILE_SIZE) {
                double tileLength = Math.min(TILE_SIZE, radius - localZ);
                double centerZ = localZ + tileLength * 0.5D;
                double distanceSquared = centerX * centerX + centerZ * centerZ;
                if (distanceSquared > radiusSquared) {
                    continue;
                }

                double surfaceY = entity.findSurfaceY(
                        origin.x + centerX, origin.z + centerZ);
                if (Double.isNaN(surfaceY)) {
                    continue;
                }

                float radial = (float) (Math.sqrt(distanceSquared) / radius);
                int alpha = Mth.clamp((int) ((68.0F - radial * 42.0F) * pulse), 18, 72);
                float minX = (float) (localX - TILE_OVERLAP);
                float maxX = (float) (localX + tileWidth + TILE_OVERLAP);
                float minZ = (float) (localZ - TILE_OVERLAP);
                float maxZ = (float) (localZ + tileLength + TILE_OVERLAP);
                float localY = (float) (surfaceY - origin.y + 0.012D);
                addGroundQuad(matrix, consumer, minX, maxX, localY, minZ, maxZ, alpha);
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void addGroundQuad(Matrix4f matrix, VertexConsumer consumer,
                                      float minX, float maxX, float y,
                                      float minZ, float maxZ, int alpha) {
        addVertex(matrix, consumer, minX, y, minZ, 0.0F, 0.0F, alpha);
        addVertex(matrix, consumer, minX, y, maxZ, 0.0F, 1.0F, alpha);
        addVertex(matrix, consumer, maxX, y, maxZ, 1.0F, 1.0F, alpha);
        addVertex(matrix, consumer, maxX, y, minZ, 1.0F, 0.0F, alpha);
    }

    private static void addVertex(Matrix4f matrix, VertexConsumer consumer,
                                  float x, float y, float z,
                                  float u, float v, int alpha) {
        consumer.vertex(matrix, x, y, z)
                .color(255, 220, 72, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public boolean shouldRender(SanctuaryEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SanctuaryEntity entity) {
        return WHITE_TEXTURE;
    }
}

package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yxty.examplemod.entity.ThunderStormEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** 由分层长方体方环组成、棱角清楚的倒金字塔形雷霆风暴。 */
public class ThunderStormRenderer extends EntityRenderer<ThunderStormEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final int LAYER_COUNT = 16;
    private static final int ALPHA = 128;

    public ThunderStormRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ThunderStormEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float radius = Math.max(0.5F, entity.getVisualRadius());
        float halfHeight = Math.max(0.5F, entity.getVisualHalfHeight());
        float age = entity.tickCount + partialTick;
        float layerHeight = halfHeight * 2.0F / LAYER_COUNT;

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));

        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            float heightT = (layer + 0.5F) / LAYER_COUNT;
            float halfSize = ThunderStormEntity.visualHalfSize(radius, heightT);
            float wallThickness = radius * (0.055F + heightT * 0.015F);
            float centerX = ThunderStormEntity.visualOffsetX(radius, age, heightT, entity.getId());
            float centerZ = ThunderStormEntity.visualOffsetZ(radius, age, heightT, entity.getId());
            float y0 = -halfHeight + layer * layerHeight;
            // 轻微纵向重叠，让相邻方环在逐层外扩时仍保持连接。
            float y1 = y0 + layerHeight * 1.04F;
            renderSquareRing(matrix, consumer, centerX, centerZ,
                    y0, y1, halfSize, wallThickness, age, heightT,
                    layer, entity.getId());
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** 四个轴对齐长方体拼成一个方环；角部互不重叠，棱角不会被混色抹平。 */
    private static void renderSquareRing(Matrix4f matrix, VertexConsumer consumer,
                                         float centerX, float centerZ,
                                         float minY, float maxY,
                                         float halfSize, float thickness,
                                         float age, float heightT,
                                         int layer, int stormSeed) {
        float minX = centerX - halfSize;
        float maxX = centerX + halfSize;
        float minZ = centerZ - halfSize;
        float maxZ = centerZ + halfSize;
        float innerMinX = minX + thickness;
        float innerMaxX = maxX - thickness;
        float innerMinZ = minZ + thickness;
        float innerMaxZ = maxZ - thickness;

        // 北、东、南、西四条长方体保持世界坐标方向不变。
        renderCuboid(matrix, consumer,
                minX, minY, minZ, maxX, maxY, innerMinZ,
                rotatingSideShade(age, heightT, layer, 0, stormSeed), ALPHA);
        renderCuboid(matrix, consumer,
                innerMaxX, minY, innerMinZ, maxX, maxY, innerMaxZ,
                rotatingSideShade(age, heightT, layer, 1, stormSeed), ALPHA);
        renderCuboid(matrix, consumer,
                minX, minY, innerMaxZ, maxX, maxY, maxZ,
                rotatingSideShade(age, heightT, layer, 2, stormSeed), ALPHA);
        renderCuboid(matrix, consumer,
                minX, minY, innerMinZ, innerMinX, maxY, innerMaxZ,
                rotatingSideShade(age, heightT, layer, 3, stormSeed), ALPHA);
    }

    /**
     * 明亮方向高速绕中心旋转，但每个长方体具有独立、稳定的随机灰度偏差和相位扰动。
     * 这样仍能感到颜色在环绕中心运动，同时不会形成过于规则的明暗螺旋。
     */
    private static int rotatingSideShade(float age, float heightT, int layer,
                                         int side, int stormSeed) {
        float shadeNoise = colorNoise(stormSeed, layer, side, 0);
        float phaseNoise = colorNoise(stormSeed, layer, side, 1) - 0.5F;
        float lightAngle = age * 0.48F - heightT * Mth.TWO_PI * 1.35F
                + phaseNoise * 1.10F;
        float sideAngle = side * Mth.HALF_PI - Mth.HALF_PI;
        float wave = 0.5F + 0.5F * Mth.cos(sideAngle - lightAngle);
        int shade = 62 + (int) (wave * 62.0F + shadeNoise * 54.0F);
        return Mth.clamp(shade, 62, 178);
    }

    private static float colorNoise(int stormSeed, int layer, int side, int channel) {
        int mixed = stormSeed * 0x45d9f3b
                + layer * 0x119de1f3
                + side * 0x27d4eb2d
                + channel * 0x165667b1;
        mixed = (mixed ^ (mixed >>> 16)) * 0x45d9f3b;
        mixed ^= mixed >>> 16;
        return (mixed & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    private static void renderCuboid(Matrix4f matrix, VertexConsumer consumer,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ,
                                     int shade, int alpha) {
        // 北面与南面。
        addQuad(matrix, consumer,
                minX, minY, minZ, maxX, minY, minZ,
                maxX, maxY, minZ, minX, maxY, minZ, shade, alpha);
        addQuad(matrix, consumer,
                maxX, minY, maxZ, minX, minY, maxZ,
                minX, maxY, maxZ, maxX, maxY, maxZ, shade, alpha);
        // 西面与东面。
        addQuad(matrix, consumer,
                minX, minY, maxZ, minX, minY, minZ,
                minX, maxY, minZ, minX, maxY, maxZ, shade, alpha);
        addQuad(matrix, consumer,
                maxX, minY, minZ, maxX, minY, maxZ,
                maxX, maxY, maxZ, maxX, maxY, minZ, shade, alpha);
        // 顶面与底面让每条边拥有明确厚度。
        addQuad(matrix, consumer,
                minX, maxY, minZ, maxX, maxY, minZ,
                maxX, maxY, maxZ, minX, maxY, maxZ, shade, alpha);
        addQuad(matrix, consumer,
                minX, minY, maxZ, maxX, minY, maxZ,
                maxX, minY, minZ, minX, minY, minZ, shade, alpha);
    }

    private static void addQuad(Matrix4f matrix, VertexConsumer consumer,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                int shade, int alpha) {
        addVertex(matrix, consumer, x1, y1, z1, 0.0F, 1.0F, shade, alpha);
        addVertex(matrix, consumer, x2, y2, z2, 1.0F, 1.0F, shade, alpha);
        addVertex(matrix, consumer, x3, y3, z3, 1.0F, 0.0F, shade, alpha);
        addVertex(matrix, consumer, x4, y4, z4, 0.0F, 0.0F, shade, alpha);
    }

    private static void addVertex(Matrix4f matrix, VertexConsumer consumer,
                                  float x, float y, float z, float u, float v,
                                  int shade, int alpha) {
        consumer.vertex(matrix, x, y, z)
                .color(shade, shade, shade, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public boolean shouldRender(ThunderStormEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ThunderStormEntity entity) {
        return TEXTURE;
    }
}

package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yxty.examplemod.entity.ThunderChainEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class ThunderChainRenderer extends EntityRenderer<ThunderChainEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("program_magic", "textures/entity/thunder_chain.png");

    public ThunderChainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ThunderChainEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 1. 计算折线路径
        entity.computeSegments(partialTick);
        List<Vec3> points = entity.segments;
        if (points.size() < 2) return;

        VertexConsumer consumer = buffer.getBuffer(MyRenderType.THUNDER_GLOW);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        float width = entity.getWidth();

        // 闪烁与淡出逻辑
        float alphaFloat;
        int tick = entity.tickCount;
        if (tick < 5) {
            alphaFloat = 1.0f - (tick / 20.0f);
        } else {
            float fadeProgress = (tick - 5) / 5f;
            alphaFloat = 1.0f - fadeProgress;
        }
        int alpha = (int)(Math.max(0, Math.min(1, alphaFloat)) * 255);

        Vec3 entityPos = entity.getPosition(partialTick);

        // 2. 遍历绘制
        for (int i = 0; i < points.size() - 1; i++) {
            // 获取相对于实体的本地坐标
            Vec3 p1 = points.get(i).subtract(entityPos);
            Vec3 p2 = points.get(i + 1).subtract(entityPos);

            // --- 核心修改：延伸逻辑 ---
            // 计算方向向量
            Vec3 dir = p2.subtract(p1).normalize();

            // 延伸系数：设置为宽度的一半多一点，确保旋转连接处完全覆盖，没有缝隙
            double extension = width * 0.5;

            // 向两端延伸
            Vec3 extendedP1 = p1;
            if (i > 0) {
                extendedP1 = p1.subtract(dir.scale(extension));
            }
            Vec3 extendedP2 = p2;
            if (i < points.size() - 2) {
                extendedP2 = p2.add(dir.scale(extension));
            }
            // 绘制长方体段
            drawBoxSegment(matrix, consumer, extendedP1, extendedP2, width, alpha);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
    @Override
    public boolean shouldRender(ThunderChainEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }
    /**
     * 绘制长方体段 (Box Segment)
     * 使用 Billboard 逻辑计算基础法线，确保闪电总是“面对”摄像机，但具有 3D 厚度
     */
    private void drawBoxSegment(Matrix4f matrix, VertexConsumer consumer, Vec3 start, Vec3 end, float width, int alpha) {
        Vec3 dir = end.subtract(start).normalize();

        // 1. 计算 Right 向量 (闪电的宽度方向)
        Vec3 right;

        // 判断 start 是否非常接近原点 (说明这段闪电连接着摄像机/眼睛)
        if (start.lengthSqr() < 1.0E-6) {
            // 【关键修复】
            // 如果起点在眼睛里，闪电的方向(dir)和视线方向是平行的。
            // 此时不能用视线叉乘，必须手动指定一个“上”向量来计算右向量。
            // 我们选世界坐标的 Y轴 (0,1,0)。如果闪电也是垂直的，就选 X轴 (1,0,0)。
            if (Math.abs(dir.y) > 0.99) {
                right = dir.cross(new Vec3(1, 0, 0)).normalize().scale(width / 2.0);
            } else {
                right = dir.cross(new Vec3(0, 1, 0)).normalize().scale(width / 2.0);
            }
        } else {
            // 正常情况：使用视线方向 (start点指向摄像机)
            Vec3 viewDir = start.scale(-1).normalize();

            // 检查 dir 和 viewDir 是否平行 (点积接近 1 或 -1) 防止叉积为零
            // 这种情况通常很少见，但在某些极端角度可能发生
            double dot = dir.dot(viewDir);
            if (Math.abs(dot) > 0.99) {
                // 如果平行，回落到使用世界坐标轴
                if (Math.abs(dir.y) > 0.99) {
                    right = dir.cross(new Vec3(1, 0, 0)).normalize().scale(width / 2.0);
                } else {
                    right = dir.cross(new Vec3(0, 1, 0)).normalize().scale(width / 2.0);
                }
            } else {
                right = dir.cross(viewDir).normalize().scale(width / 2.0);
            }
        }

        // 2. 计算 Up 向量 (闪电的厚度方向)
        // 既然有了确定的 dir 和 right，up 就很简单了
        Vec3 up = dir.cross(right).normalize().scale(width / 2.0);

        // --- 以下顶点计算和绘制逻辑保持不变 ---
        Vec3 s_TR = start.add(right).add(up);
        Vec3 s_TL = start.subtract(right).add(up);
        Vec3 s_BL = start.subtract(right).subtract(up);
        Vec3 s_BR = start.add(right).subtract(up);

        Vec3 e_TR = end.add(right).add(up);
        Vec3 e_TL = end.subtract(right).add(up);
        Vec3 e_BL = end.subtract(right).subtract(up);
        Vec3 e_BR = end.add(right).subtract(up);

        addQuad(matrix, consumer, s_TL, s_TR, e_TR, e_TL, alpha);
        addQuad(matrix, consumer, s_BR, s_BL, e_BL, e_BR, alpha);
        addQuad(matrix, consumer, s_BL, s_TL, e_TL, e_BL, alpha);
        addQuad(matrix, consumer, s_TR, s_BR, e_BR, e_TR, alpha);

        addQuad(matrix, consumer, s_TR, s_TL, s_BL, s_BR, alpha);
        addQuad(matrix, consumer, e_TL, e_TR, e_BR, e_BL, alpha);
    }

    // 辅助方法：绘制一个四边形
    private void addQuad(Matrix4f matrix, VertexConsumer consumer, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4, int alpha) {
        addVertex(matrix, consumer, v1, 0, 0, alpha);
        addVertex(matrix, consumer, v2, 1, 0, alpha);
        addVertex(matrix, consumer, v3, 1, 1, alpha);
        addVertex(matrix, consumer, v4, 0, 1, alpha);
    }

    private void addVertex(Matrix4f matrix, VertexConsumer consumer, Vec3 pos, float u, float v, int alpha) {
        consumer.vertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880) // 全亮
                .normal(0, 1, 0)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ThunderChainEntity entity) {
        return TEXTURE;
    }

    private static abstract class MyRenderType extends RenderType {
        public MyRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        public static final RenderType THUNDER_GLOW = create(
                "thunder_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(ThunderChainRenderer.TEXTURE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false)
        );
    }
}
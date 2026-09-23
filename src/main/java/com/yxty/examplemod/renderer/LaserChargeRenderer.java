package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.yxty.examplemod.entity.LaserChargeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class LaserChargeRenderer extends EntityRenderer<LaserChargeEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("program_magic", "textures/entity/laser_beam.png");

    public LaserChargeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LaserChargeEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        LivingEntity owner = entity.getOwner();
        if (owner != null) {
            Vec3 attach = LaserChargeEntity.attachPos(owner, partialTick);
            Vec3 renderPos = entity.getPosition(partialTick);
            poseStack.translate(attach.x - renderPos.x, attach.y - renderPos.y, attach.z - renderPos.z);
        }

        float scale = entity.getScale();
        poseStack.scale(scale, scale, scale);

        float time = entity.tickCount + partialTick;
        float spin = entity.isFullyCharged() ? 2.2F : 1.0F;

        // 贴图路径：assets/program_magic/textures/entity/laser_charge.png
        VertexConsumer vertexconsumer = buffer.getBuffer(MyRenderType.MAGIC_GLOW);

        // 1. 核心立方体
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 5.0F * spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 3.0F * spin));
        drawCube(poseStack, vertexconsumer, 0.25F);
        poseStack.popPose();

        // 2. 外壳立方体：蓄力完成后不再变大，只加快旋转
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * -3.0F * spin + 33.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * 4.0F * spin));
        float pulse = entity.isFullyCharged() ? 1.0F : 1.0F + (float) Math.sin(time * 0.1) * 0.05F;
        poseStack.scale(pulse, pulse, pulse);
        drawCube(poseStack, vertexconsumer, 0.25F);
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void drawCube(PoseStack poseStack, VertexConsumer consumer, float size) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();

        // Top
        vertex(consumer, matrix4f, matrix3f, -size, size, -size, 0, 0, 0, 1, 0);
        vertex(consumer, matrix4f, matrix3f, -size, size, size, 0, 1, 0, 1, 0);
        vertex(consumer, matrix4f, matrix3f, size, size, size, 1, 1, 0, 1, 0);
        vertex(consumer, matrix4f, matrix3f, size, size, -size, 1, 0, 0, 1, 0);

        // Bottom
        vertex(consumer, matrix4f, matrix3f, -size, -size, size, 0, 0, 0, -1, 0);
        vertex(consumer, matrix4f, matrix3f, -size, -size, -size, 0, 1, 0, -1, 0);
        vertex(consumer, matrix4f, matrix3f, size, -size, -size, 1, 1, 0, -1, 0);
        vertex(consumer, matrix4f, matrix3f, size, -size, size, 1, 0, 0, -1, 0);

        // North
        vertex(consumer, matrix4f, matrix3f, -size, -size, -size, 0, 1, 0, 0, -1);
        vertex(consumer, matrix4f, matrix3f, -size, size, -size, 0, 0, 0, 0, -1);
        vertex(consumer, matrix4f, matrix3f, size, size, -size, 1, 0, 0, 0, -1);
        vertex(consumer, matrix4f, matrix3f, size, -size, -size, 1, 1, 0, 0, -1);

        // South
        vertex(consumer, matrix4f, matrix3f, -size, size, size, 0, 0, 0, 0, 1);
        vertex(consumer, matrix4f, matrix3f, -size, -size, size, 0, 1, 0, 0, 1);
        vertex(consumer, matrix4f, matrix3f, size, -size, size, 1, 1, 0, 0, 1);
        vertex(consumer, matrix4f, matrix3f, size, size, size, 1, 0, 0, 0, 1);

        // West
        vertex(consumer, matrix4f, matrix3f, -size, -size, size, 0, 1, -1, 0, 0);
        vertex(consumer, matrix4f, matrix3f, -size, size, size, 0, 0, -1, 0, 0);
        vertex(consumer, matrix4f, matrix3f, -size, size, -size, 1, 0, -1, 0, 0);
        vertex(consumer, matrix4f, matrix3f, -size, -size, -size, 1, 1, -1, 0, 0);

        // East
        vertex(consumer, matrix4f, matrix3f, size, size, size, 0, 0, 1, 0, 0);
        vertex(consumer, matrix4f, matrix3f, size, -size, size, 0, 1, 1, 0, 0);
        vertex(consumer, matrix4f, matrix3f, size, -size, -size, 1, 1, 1, 0, 0);
        vertex(consumer, matrix4f, matrix3f, size, size, -size, 1, 0, 1, 0, 0);
    }

    private void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                        float x, float y, float z,
                        float u, float v,
                        float nx, float ny, float nz) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(LaserChargeEntity entity) {
        return TEXTURE;
    }

    // --- 关键修改：内部类继承 RenderType 以访问 protected 常量 ---
    private static abstract class MyRenderType extends RenderType {
        // 构造函数是必须的，虽然我们不会实例化它
        public MyRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        // 在这里可以合法访问 protected static final 常量
        public static final RenderType MAGIC_GLOW = create(
                "magic_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER) // 访问 Shader
                        .setTextureState(new TextureStateShard(LaserChargeRenderer.TEXTURE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE) // 关键：写入深度，解决水渲染问题
                        .createCompositeState(false)
        );
    }
}
package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.yxty.examplemod.entity.LaserBeamEntity;
import com.yxty.examplemod.entity.LaserChargeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class LaserBeamRenderer extends EntityRenderer<LaserBeamEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("program_magic", "textures/entity/laser_beam.png");

    public LaserBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LaserBeamEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float length = entity.getBeamLength();
        float width = entity.getBeamWidth() / 2.0f;

        poseStack.pushPose();

        LivingEntity owner = entity.getOwner();
        float lerpYRot;
        float lerpXRot;
        if (owner != null) {
            Vec3 attach = LaserChargeEntity.attachPos(owner, partialTick);
            Vec3 renderPos = entity.getPosition(partialTick);
            poseStack.translate(attach.x - renderPos.x, attach.y - renderPos.y, attach.z - renderPos.z);
            lerpYRot = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
            lerpXRot = Mth.lerp(partialTick, owner.xRotO, owner.getXRot());
        } else {
            lerpYRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            lerpXRot = Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-lerpYRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(lerpXRot));

        // 贴图路径：assets/program_magic/textures/entity/laser_beam.png
        VertexConsumer vertexconsumer = buffer.getBuffer(MyRenderType.MAGIC_GLOW);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();

        drawBeamBox(vertexconsumer, matrix4f, matrix3f, width, length);
        drawBeamBox(vertexconsumer, matrix4f, matrix3f, width * 0.4f, length);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void drawBeamBox(VertexConsumer vertexconsumer, Matrix4f matrix4f, Matrix3f matrix3f, float width, float length) {
        vertex(vertexconsumer, matrix4f, matrix3f, -width, width, 0, 0, 0);
        vertex(vertexconsumer, matrix4f, matrix3f, width, width, 0, 1, 0);
        vertex(vertexconsumer, matrix4f, matrix3f, width, width, length, 1, length);
        vertex(vertexconsumer, matrix4f, matrix3f, -width, width, length, 0, length);

        vertex(vertexconsumer, matrix4f, matrix3f, -width, -width, length, 0, length);
        vertex(vertexconsumer, matrix4f, matrix3f, width, -width, length, 1, length);
        vertex(vertexconsumer, matrix4f, matrix3f, width, -width, 0, 1, 0);
        vertex(vertexconsumer, matrix4f, matrix3f, -width, -width, 0, 0, 0);

        vertex(vertexconsumer, matrix4f, matrix3f, width, -width, 0, 0, 0);
        vertex(vertexconsumer, matrix4f, matrix3f, width, -width, length, 1, length);
        vertex(vertexconsumer, matrix4f, matrix3f, width, width, length, 1, length);
        vertex(vertexconsumer, matrix4f, matrix3f, width, width, 0, 0, 0);

        vertex(vertexconsumer, matrix4f, matrix3f, -width, width, 0, 0, 0);
        vertex(vertexconsumer, matrix4f, matrix3f, -width, width, length, 1, length);
        vertex(vertexconsumer, matrix4f, matrix3f, -width, -width, length, 1, length);
        vertex(vertexconsumer, matrix4f, matrix3f, -width, -width, 0, 0, 0);
    }

    private void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public boolean shouldRender(LaserBeamEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(LaserBeamEntity entity) {
        return TEXTURE;
    }

    // --- 内部类继承 RenderType ---
    private static abstract class MyRenderType extends RenderType {
        public MyRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        public static final RenderType MAGIC_GLOW = create(
                "magic_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(LaserBeamRenderer.TEXTURE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE) // 访问成功
                        .createCompositeState(false)
        );
    }
}
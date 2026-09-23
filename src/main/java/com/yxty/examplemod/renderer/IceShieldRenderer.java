package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yxty.examplemod.entity.IceShieldEntity;
import com.yxty.examplemod.model.IceShieldModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class IceShieldRenderer extends EntityRenderer<IceShieldEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/ice.png");
    private final IceShieldModel model;

    public IceShieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new IceShieldModel(context.bakeLayer(IceShieldModel.LAYER));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(IceShieldEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        LivingEntity protectedEntity = entity.getProtectedEntity();
        if (protectedEntity != null) {
            double shieldX = Mth.lerp(partialTick, entity.xo, entity.getX());
            double shieldZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
            double protectedX = Mth.lerp(partialTick, protectedEntity.xo, protectedEntity.getX());
            double protectedZ = Mth.lerp(partialTick, protectedEntity.zo, protectedEntity.getZ());
            double radialX = shieldX - protectedX;
            double radialZ = shieldZ - protectedZ;

            // 模型的局部 Z 轴是盾面的法向。将它直接对齐到玩家—冰盾径向，
            // 因而盾牌只随公转改变朝向，不再围绕自身额外旋转。
            if (radialX * radialX + radialZ * radialZ > 1.0E-8D) {
                float radialYaw = (float) Math.toDegrees(Math.atan2(radialX, radialZ));
                poseStack.mulPose(Axis.YP.rotationDegrees(radialYaw));
            }
        }
        poseStack.scale(1.15F, -1.15F, -1.15F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.renderToBuffer(poseStack, consumer, 15728880, OverlayTexture.NO_OVERLAY,
                0.72F, 0.9F, 1.0F, 0.78F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(IceShieldEntity entity) {
        return TEXTURE;
    }
}

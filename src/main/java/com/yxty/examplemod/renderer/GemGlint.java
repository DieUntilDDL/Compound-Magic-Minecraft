package com.yxty.examplemod.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yxty.examplemod.item.WandItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class GemGlint extends GeoRenderLayer<WandItem> {

    public GemGlint(GeoRenderer<WandItem> entityRendererIn) {
        super(entityRendererIn);
    }
    @Override
    public void render(PoseStack poseStack, WandItem animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {

        // 1. 精准抓取你在 Blockbench 里命名的宝石骨骼
        // 确保这里的 "gem" 和你动画 JSON 里写的骨骼名字一模一样！
        GeoBone gemBone = bakedModel.getBone("gem_spin").orElse(null);

        if (gemBone != null) {
            // 2. 获取原版“附魔紫光”的专属渲染材质（极其关键，绝对不能用传进来的 renderType！）
            RenderType glintType = RenderType.armorEntityGlint();

            // 3. 向系统申请一个专门画紫光的图层画笔
            VertexConsumer glintBuffer = bufferSource.getBuffer(glintType);

            // 4. 仅仅用紫光画笔，单独渲染这一个骨骼！参数顺序千万别错！
            getRenderer().renderCubesOfBone(poseStack, gemBone, glintBuffer, packedLight, packedOverlay, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}

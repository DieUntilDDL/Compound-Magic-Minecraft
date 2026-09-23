package com.yxty.examplemod.event;

import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.client.particle.LightningParticle;
import com.yxty.examplemod.client.particle.WeakeningArrowParticle;
import com.yxty.examplemod.particle.ModParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.yxty.examplemod.renderer.LaserBeamRenderer;
import com.yxty.examplemod.renderer.LaserChargeRenderer;
import com.yxty.examplemod.renderer.ThunderChainRenderer;
import com.yxty.examplemod.renderer.DarkDevourFieldRenderer;
import com.yxty.examplemod.renderer.IceShieldRenderer;
import com.yxty.examplemod.renderer.SanctuaryRenderer;
import com.yxty.examplemod.renderer.ThunderStormRenderer;
import com.yxty.examplemod.model.IceShieldModel;
import net.minecraft.client.renderer.entity.EvokerFangsRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;


@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientListener {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.LASER_BEAM.get(), LaserBeamRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.LASER_CHARGE.get(), LaserChargeRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.THUNDER_CHAIN.get(), ThunderChainRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.DARK_FANG.get(), EvokerFangsRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.DARK_DEVOUR_FIELD.get(), DarkDevourFieldRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.BLAST_DASH_CONTROLLER.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.FIRE_BREATH.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.ICE_MIST.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SANCTUARY.get(), SanctuaryRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.THUNDER_STORM.get(), ThunderStormRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.BUFF_AREA_VISUAL.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.ICE_SHIELD.get(), IceShieldRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticleTypes.LIGHTNING.get(), LightningParticle.Provider::new);
        event.registerSpriteSet(ModParticleTypes.WEAKENING_ARROW.get(),
                WeakeningArrowParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(IceShieldModel.LAYER, IceShieldModel::createBodyLayer);
    }
}

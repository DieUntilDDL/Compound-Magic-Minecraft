package com.yxty.examplemod.particle;

import com.mojang.serialization.Codec;
import com.yxty.examplemod.ProgramMagic;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ProgramMagic.MODID);

    public static final RegistryObject<ParticleType<LightningParticleOptions>> LIGHTNING =
            PARTICLE_TYPES.register("lightning", () ->
                    new ParticleType<>(false, LightningParticleOptions.DESERIALIZER) {
                        @Override
                        public Codec<LightningParticleOptions> codec() {
                            return LightningParticleOptions.CODEC;
                        }
                    });

    public static final RegistryObject<SimpleParticleType> WEAKENING_ARROW =
            PARTICLE_TYPES.register("weakening_arrow", () -> new SimpleParticleType(false));

    private ModParticleTypes() {
    }

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}

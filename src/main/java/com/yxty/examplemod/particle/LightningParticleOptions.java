package com.yxty.examplemod.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import java.util.Locale;

/** 粒子生成参数；stretch 为闪电图片的纵向拉伸系数。 */
public record LightningParticleOptions(float stretch) implements ParticleOptions {
    public static final Codec<LightningParticleOptions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.FLOAT.fieldOf("stretch")
                            .forGetter(LightningParticleOptions::stretch))
                    .apply(instance, LightningParticleOptions::new));

    public static final Deserializer<LightningParticleOptions> DESERIALIZER =
            new Deserializer<>() {
                @Override
                public LightningParticleOptions fromCommand(
                        ParticleType<LightningParticleOptions> type,
                        StringReader reader) throws CommandSyntaxException {
                    reader.expect(' ');
                    return new LightningParticleOptions(reader.readFloat());
                }

                @Override
                public LightningParticleOptions fromNetwork(
                        ParticleType<LightningParticleOptions> type,
                        FriendlyByteBuf buffer) {
                    return new LightningParticleOptions(buffer.readFloat());
                }
            };

    public LightningParticleOptions {
        stretch = Mth.clamp(stretch, 0.25F, 4.0F);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticleTypes.LIGHTNING.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeFloat(stretch);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f",
                BuiltInRegistries.PARTICLE_TYPE.getKey(getType()), stretch);
    }
}

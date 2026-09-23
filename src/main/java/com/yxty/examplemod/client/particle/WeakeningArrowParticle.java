package com.yxty.examplemod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A short-lived, full-bright downward arrow used by purified ice mist. */
public final class WeakeningArrowParticle extends TextureSheetParticle {
    private WeakeningArrowParticle(ClientLevel level, double x, double y, double z,
                                   double xSpeed, double ySpeed, double zSpeed,
                                   SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.lifetime = 24;
        this.quadSize = 0.28F;
        this.gravity = 0.0F;
        this.friction = 0.9F;
        this.hasPhysics = false;
        this.xd = xSpeed;
        this.yd = 0.008D + ySpeed;
        this.zd = zSpeed;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float progress = (float) age / (float) lifetime;
        this.alpha = progress < 0.2F
                ? Mth.clamp(progress / 0.2F, 0.0F, 1.0F)
                : Mth.clamp((1.0F - progress) / 0.25F, 0.0F, 1.0F);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new WeakeningArrowParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}

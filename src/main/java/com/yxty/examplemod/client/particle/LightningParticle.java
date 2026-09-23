package com.yxty.examplemod.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yxty.examplemod.particle.LightningParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 无厚度的镜头朝向闪电平面。
 * 约1 tick从左向右显现，完整保持约8 tick，再约1 tick从右向左消失。
 */
public class LightningParticle extends TextureSheetParticle {
    private static final int LIFETIME_TICKS = 10;
    private final float stretch;

    private LightningParticle(LightningParticleOptions options, ClientLevel level,
                              double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed,
                              SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.stretch = options.stretch();
        this.lifetime = LIFETIME_TICKS;
        this.quadSize = 0.22F + random.nextFloat() * 0.10F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.alpha = 1.0F;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        pickSprite(sprites);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        float progress = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        float visibleLeft;
        float visibleRight;
        if (progress < 0.10F) {
            visibleLeft = 0.0F;
            visibleRight = smoothStep(progress / 0.10F);
        } else if (progress < 0.90F) {
            visibleLeft = 0.0F;
            visibleRight = 1.0F;
        } else {
            visibleLeft = 0.0F;
            visibleRight = 1.0F - smoothStep((progress - 0.90F) / 0.10F);
        }
        if (visibleRight - visibleLeft < 0.002F) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        float renderX = (float) (Mth.lerp(partialTick, xo, x) - cameraPosition.x());
        float renderY = (float) (Mth.lerp(partialTick, yo, y) - cameraPosition.y());
        float renderZ = (float) (Mth.lerp(partialTick, zo, z) - cameraPosition.z());
        Quaternionf rotation = new Quaternionf(camera.rotation());
        rotation.rotateZ(Mth.lerp(partialTick, oRoll, roll));

        float halfWidth = getQuadSize(partialTick);
        float halfHeight = halfWidth * stretch;
        float leftX = Mth.lerp(visibleLeft, -halfWidth, halfWidth);
        float rightX = Mth.lerp(visibleRight, -halfWidth, halfWidth);
        Vector3f[] vertices = {
                new Vector3f(leftX, -halfHeight, 0.0F),
                new Vector3f(leftX, halfHeight, 0.0F),
                new Vector3f(rightX, halfHeight, 0.0F),
                new Vector3f(rightX, -halfHeight, 0.0F)
        };
        for (Vector3f vertex : vertices) {
            vertex.rotate(rotation);
            vertex.add(renderX, renderY, renderZ);
        }

        // 同时裁切平面宽度和UV，避免只显示局部纹理时被重新拉伸到整张平面。
        float textureLeft = Mth.lerp(visibleLeft, sprite.getU1(), sprite.getU0());
        float textureRight = Mth.lerp(visibleRight, sprite.getU1(), sprite.getU0());
        float topV = sprite.getV0();
        float bottomV = sprite.getV1();
        int light = getLightColor(partialTick);
        addVertex(consumer, vertices[0], textureLeft, bottomV, light);
        addVertex(consumer, vertices[1], textureLeft, topV, light);
        addVertex(consumer, vertices[2], textureRight, topV, light);
        addVertex(consumer, vertices[3], textureRight, bottomV, light);
    }

    private void addVertex(VertexConsumer consumer, Vector3f position,
                           float u, float v, int light) {
        consumer.vertex(position.x(), position.y(), position.z())
                .uv(u, v)
                .color(rCol, gCol, bCol, alpha)
                .uv2(light)
                .endVertex();
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<LightningParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(LightningParticleOptions options,
                                       ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new LightningParticle(options, level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}

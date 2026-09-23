package com.yxty.examplemod.client;

import com.yxty.examplemod.ModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/** Short client cache refreshed by the server while a target carries purified fire. */
@OnlyIn(Dist.CLIENT)
public final class ClientBlueFlameState {
    private static final long PACKET_GRACE_TICKS = 60L;
    private static final Map<Integer, Long> ACTIVE_UNTIL = new HashMap<>();
    private static ClientLevel cachedLevel;

    private ClientBlueFlameState() {
    }

    public static void update(int entityId, boolean active) {
        ClientLevel level = Minecraft.getInstance().level;
        switchLevelIfNeeded(level);
        if (level == null || !active) {
            ACTIVE_UNTIL.remove(entityId);
            return;
        }
        ACTIVE_UNTIL.put(entityId, level.getGameTime() + PACKET_GRACE_TICKS);
    }

    public static boolean isActive(LivingEntity entity) {
        ClientLevel level = Minecraft.getInstance().level;
        switchLevelIfNeeded(level);
        if (entity.hasEffect(ModMobEffects.BLUE_FLAME.get())) {
            return true;
        }
        if (level == null) {
            return false;
        }
        Long activeUntil = ACTIVE_UNTIL.get(entity.getId());
        if (activeUntil == null) {
            return false;
        }
        if (level.getGameTime() > activeUntil) {
            ACTIVE_UNTIL.remove(entity.getId());
            return false;
        }
        return true;
    }

    private static void switchLevelIfNeeded(ClientLevel level) {
        if (cachedLevel != level) {
            ACTIVE_UNTIL.clear();
            cachedLevel = level;
        }
    }
}

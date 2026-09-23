package com.yxty.examplemod.capability;

import com.yxty.examplemod.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** 服务端魔力读写入口；所有消耗都通过这里完成并同步给客户端。 */
public final class ManaManager {
    private ManaManager() {
    }

    public static boolean has(Player player, float amount) {
        return player.getCapability(ManaProvider.PLAYER_MANA)
                .map(data -> data.has(amount))
                .orElse(false);
    }

    public static boolean tryConsume(Player player, float amount) {
        if (player.level().isClientSide) {
            return true;
        }
        ManaData data = get(player);
        if (data == null || !data.consume(amount)) {
            if (player instanceof ServerPlayer serverPlayer && data != null) {
                ModNetworking.syncMana(serverPlayer, data);
            }
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetworking.syncMana(serverPlayer, data);
        }
        return true;
    }

    public static void tick(ServerPlayer player) {
        ManaData data = get(player);
        if (data == null) {
            return;
        }
        boolean changed = data.tickRegeneration();
        if (changed && (player.tickCount % 5 == 0 || data.mana() >= data.maxMana())) {
            ModNetworking.syncMana(player, data);
        }
    }

    public static void sync(ServerPlayer player) {
        ManaData data = get(player);
        if (data != null) {
            ModNetworking.syncMana(player, data);
        }
    }

    public static ManaData get(Player player) {
        return player.getCapability(ManaProvider.PLAYER_MANA).resolve().orElse(null);
    }
}

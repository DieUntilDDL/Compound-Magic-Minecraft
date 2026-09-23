package com.yxty.examplemod.network;

import com.yxty.examplemod.client.ClientBlueFlameState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Explicit visual-state synchronization for the persistent purified flame. */
public record BlueFlameSyncPacket(int entityId, boolean active) {
    public static void encode(BlueFlameSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeBoolean(packet.active);
    }

    public static BlueFlameSyncPacket decode(FriendlyByteBuf buffer) {
        return new BlueFlameSyncPacket(buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(BlueFlameSyncPacket packet,
                              Supplier<NetworkEvent.Context> ignoredContext) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientBlueFlameState.update(packet.entityId, packet.active));
    }
}

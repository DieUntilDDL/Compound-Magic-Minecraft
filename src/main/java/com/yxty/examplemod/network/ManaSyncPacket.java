package com.yxty.examplemod.network;

import com.yxty.examplemod.client.ClientManaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ManaSyncPacket(float mana, float maxMana) {
    public static void encode(ManaSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.mana);
        buffer.writeFloat(packet.maxMana);
    }

    public static ManaSyncPacket decode(FriendlyByteBuf buffer) {
        return new ManaSyncPacket(buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(ManaSyncPacket packet,
                              Supplier<NetworkEvent.Context> ignoredContext) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientManaState.update(packet.mana, packet.maxMana));
    }
}

package com.yxty.examplemod.network;

import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.capability.ManaData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Network messages whose state must be visible to the client renderer. */
public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ProgramMagic.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);
    private static int nextMessageId;

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.messageBuilder(
                        BlueFlameSyncPacket.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlueFlameSyncPacket::encode)
                .decoder(BlueFlameSyncPacket::decode)
                .consumerMainThread(BlueFlameSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(
                        ManaSyncPacket.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ManaSyncPacket::encode)
                .decoder(ManaSyncPacket::decode)
                .consumerMainThread(ManaSyncPacket::handle)
                .add();
    }

    public static void syncBlueFlame(LivingEntity entity, boolean active) {
        if (entity.level().isClientSide) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new BlueFlameSyncPacket(entity.getId(), active));
    }

    public static void syncMana(ServerPlayer player, ManaData mana) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ManaSyncPacket(mana.mana(), mana.maxMana()));
    }
}

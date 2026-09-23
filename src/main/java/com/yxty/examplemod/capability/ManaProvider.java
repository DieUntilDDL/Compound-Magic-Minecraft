package com.yxty.examplemod.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ManaProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<ManaData> PLAYER_MANA =
            CapabilityManager.get(new CapabilityToken<>() {});

    private ManaData backend;
    private final LazyOptional<ManaData> optional = LazyOptional.of(this::createBackend);

    private ManaData createBackend() {
        if (backend == null) {
            backend = new ManaData();
        }
        return backend;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(
            @NotNull Capability<T> capability, @Nullable Direction side) {
        return capability == PLAYER_MANA ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        createBackend().saveNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        createBackend().loadNBT(tag);
    }

    public void invalidate() {
        optional.invalidate();
    }
}

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

public class MagicElementLearnedProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<MagicElementLearned> MAGIC_ELEMENT_LEARNED = CapabilityManager.get(new CapabilityToken<>() {});

    private MagicElementLearned backend = null;
    private final LazyOptional<MagicElementLearned> optional = LazyOptional.of(this::createBackend);

    private MagicElementLearned createBackend() {
        if (this.backend == null) {
            this.backend = new MagicElementLearned();
        }
        return this.backend;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == MAGIC_ELEMENT_LEARNED) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createBackend().saveNBT(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createBackend().loadNBT(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}

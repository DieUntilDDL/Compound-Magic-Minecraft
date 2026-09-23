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

public class ItemLoadedMagicProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    // 1. 注册 Capability Token
    public static final Capability<ItemLoadedMagic> ITEM_LOADED_MAGIC = CapabilityManager.get(new CapabilityToken<>() {});

    private ItemLoadedMagic backend = null;
    private final LazyOptional<ItemLoadedMagic> optional = LazyOptional.of(this::createBackend);
    public ItemLoadedMagicProvider(){
        this.backend = new ItemLoadedMagic();
    }

    private ItemLoadedMagic createBackend() {
        if (this.backend == null) {
            this.backend = new ItemLoadedMagic();
        }
        return this.backend;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ITEM_LOADED_MAGIC) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createBackend().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createBackend().loadNBTData(nbt);
    }
}
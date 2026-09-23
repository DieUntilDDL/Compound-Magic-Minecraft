package com.yxty.examplemod.model;

import com.yxty.examplemod.item.WandItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class WandItemModel extends GeoModel<WandItem> {
    @Override
    public ResourceLocation getModelResource(WandItem object) {
        return new ResourceLocation("program_magic", "geo/" + object.getWandId() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WandItem object) {
        return new ResourceLocation("program_magic", "textures/item/" + object.getWandId() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(WandItem object) {
        return new ResourceLocation("program_magic", "animations/" + object.getWandId() + ".animation.json");
    }
}

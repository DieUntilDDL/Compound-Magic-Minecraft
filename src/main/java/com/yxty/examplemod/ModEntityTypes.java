package com.yxty.examplemod;

import com.yxty.examplemod.entity.DarkFangEntity;
import com.yxty.examplemod.entity.DarkDevourFieldEntity;
import com.yxty.examplemod.entity.BlastDashControllerEntity;
import com.yxty.examplemod.entity.FireBreathEntity;
import com.yxty.examplemod.entity.IceMistEntity;
import com.yxty.examplemod.entity.SanctuaryEntity;
import com.yxty.examplemod.entity.ThunderStormEntity;
import com.yxty.examplemod.entity.BuffAreaVisualEntity;
import com.yxty.examplemod.entity.IceShieldEntity;
import com.yxty.examplemod.entity.LaserBeamEntity;
import com.yxty.examplemod.entity.LaserChargeEntity;
import com.yxty.examplemod.entity.ThunderChainEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ProgramMagic.MODID);
    public static final RegistryObject<EntityType<LaserBeamEntity>> LASER_BEAM = ENTITY_TYPES.register("laser_beam",
            () -> EntityType.Builder.<LaserBeamEntity>of(LaserBeamEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("laser_beam"));
    public static final RegistryObject<EntityType<LaserChargeEntity>> LASER_CHARGE = ENTITY_TYPES.register("laser_charge",
            () ->EntityType.Builder.of(LaserChargeEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("laser_charge"));
    public static final RegistryObject<EntityType<ThunderChainEntity>> THUNDER_CHAIN = ENTITY_TYPES.register("thunder_chain",
            () ->EntityType.Builder.of(ThunderChainEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("thunder_chain"));
    public static final RegistryObject<EntityType<DarkFangEntity>> DARK_FANG = ENTITY_TYPES.register("dark_fang",
            () -> EntityType.Builder.<DarkFangEntity>of(DarkFangEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.8f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("dark_fang"));
    public static final RegistryObject<EntityType<DarkDevourFieldEntity>> DARK_DEVOUR_FIELD = ENTITY_TYPES.register("dark_devour_field",
            () -> EntityType.Builder.<DarkDevourFieldEntity>of(DarkDevourFieldEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("dark_devour_field"));
    public static final RegistryObject<EntityType<BlastDashControllerEntity>> BLAST_DASH_CONTROLLER = ENTITY_TYPES.register("blast_dash_controller",
            () -> EntityType.Builder.<BlastDashControllerEntity>of(BlastDashControllerEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build("blast_dash_controller"));
    public static final RegistryObject<EntityType<FireBreathEntity>> FIRE_BREATH = ENTITY_TYPES.register("fire_breath",
            () -> EntityType.Builder.<FireBreathEntity>of(FireBreathEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("fire_breath"));
    public static final RegistryObject<EntityType<IceMistEntity>> ICE_MIST = ENTITY_TYPES.register("ice_mist",
            () -> EntityType.Builder.<IceMistEntity>of(IceMistEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("ice_mist"));
    public static final RegistryObject<EntityType<SanctuaryEntity>> SANCTUARY = ENTITY_TYPES.register("sanctuary",
            () -> EntityType.Builder.<SanctuaryEntity>of(SanctuaryEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("sanctuary"));
    public static final RegistryObject<EntityType<ThunderStormEntity>> THUNDER_STORM = ENTITY_TYPES.register("thunder_storm",
            () -> EntityType.Builder.<ThunderStormEntity>of(ThunderStormEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .build("thunder_storm"));
    public static final RegistryObject<EntityType<BuffAreaVisualEntity>> BUFF_AREA_VISUAL = ENTITY_TYPES.register("buff_area_visual",
            () -> EntityType.Builder.<BuffAreaVisualEntity>of(BuffAreaVisualEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("buff_area_visual"));
    public static final RegistryObject<EntityType<IceShieldEntity>> ICE_SHIELD = ENTITY_TYPES.register("ice_shield",
            () -> EntityType.Builder.<IceShieldEntity>of(IceShieldEntity::new, MobCategory.MISC)
                    .sized(0.55f, 1.15f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("ice_shield"));
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}

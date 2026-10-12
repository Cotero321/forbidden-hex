package com.example.akaishi.forbidden.brewing;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class BrewingEntities {
    public static RegistrySupplier<EntityType<BrewingPotionProjectile>> POTION;
    public static RegistrySupplier<EntityType<BrewingEffectCloud>> CLOUD;

    private BrewingEntities() { }

    public static void register(EntityType.EntityFactory<BrewingPotionProjectile> factory,
                                EntityType.EntityFactory<BrewingEffectCloud> cloudFactory) {
        CLOUD = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ENTITY_TYPE)
                .register(BrewingItems.id("brewing_cloud"), () -> EntityType.Builder
                        .of(cloudFactory, MobCategory.MISC).sized(6, 0.5F)
                        .clientTrackingRange(10).updateInterval(10)
                        .build(BrewingItems.id("brewing_cloud").toString()));
        POTION = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ENTITY_TYPE)
                .register(BrewingItems.id("brewing_potion"), () -> EntityType.Builder
                        .of(factory, MobCategory.MISC).sized(0.25F, 0.25F)
                        .clientTrackingRange(4).updateInterval(10)
                        .build(BrewingItems.id("brewing_potion").toString()));
    }
}

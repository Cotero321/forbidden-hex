package com.example.akaishi.forbidden.forge;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.ForbiddenIds;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;

@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID)
public final class ForbiddenRegistryMigration {
    private ForbiddenRegistryMigration() {
    }

    @SubscribeEvent
    public static void missingMappings(MissingMappingsEvent event) {
        remap(event, Registries.ITEM, ForgeRegistries.ITEMS);
        remap(event, Registries.BLOCK, ForgeRegistries.BLOCKS);
        remap(event, Registries.BLOCK_ENTITY_TYPE, ForgeRegistries.BLOCK_ENTITY_TYPES);
        remap(event, Registries.ENTITY_TYPE, ForgeRegistries.ENTITY_TYPES);
        remap(event, Registries.MENU, ForgeRegistries.MENU_TYPES);
        remap(event, Registries.MOB_EFFECT, ForgeRegistries.MOB_EFFECTS);
        remap(event, Registries.POTION, ForgeRegistries.POTIONS);
        remap(event, Registries.SOUND_EVENT, ForgeRegistries.SOUND_EVENTS);
        remap(event, Registries.RECIPE_SERIALIZER, ForgeRegistries.RECIPE_SERIALIZERS);
        remap(event, Registries.PARTICLE_TYPE, ForgeRegistries.PARTICLE_TYPES);
    }

    private static <T> void remap(MissingMappingsEvent event,
            ResourceKey<? extends Registry<T>> registryKey, IForgeRegistry<T> registry) {
        for (MissingMappingsEvent.Mapping<T> mapping : event.getAllMappings(registryKey)) {
            ResourceLocation oldId = mapping.getKey();
            ResourceLocation newId = ForbiddenIds.current(oldId);
            if (!newId.equals(oldId) && registry.containsKey(newId)) {
                mapping.remap(registry.getValue(newId));
            }
        }
    }
}

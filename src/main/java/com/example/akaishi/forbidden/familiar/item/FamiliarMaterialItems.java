package com.example.akaishi.forbidden.familiar.item;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Formal item identities for the first fifteen familiar-workbench materials. */
public final class FamiliarMaterialItems {
    private static final String MATERIAL_TAG = "FamiliarMaterial";
    private static final String BIOMASS_COST_TAG = "BiomassCost";
    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    private FamiliarMaterialItems() {
    }

    public static void register() {
        definition("starfallen_iron", "Starfallen Iron", 3);
        definition("flawless_silver", "Flawless Silver", 3);
        definition("void_obsidian", "Void Obsidian", 4);
        definition("aurum", "Aurum", 4);
        definition("alchemical_white_salt", "Alchemical White Salt", 3);
        definition("moonlight_brocade", "Moonlight Brocade", 4);
        definition("windwhisper_thread", "Windwhisper Thread", 3);
        definition("hardened_leather", "Hardened Leather", 3);
        definition("abyssal_soft_hide", "Abyssal Soft Hide", 4);
        definition("soul_bone_block", "Soul Bone Block", 4);
        definition("carbonized_flesh", "Carbonized Flesh", 3);
        definition("active_protoplasm", "Active Protoplasm", 4);
        definition("resilient_vine_core", "Resilient Vine Core", 3);
        definition("dream_crystal", "Dream Crystal", 5);
        definition("antimagic_crystal", "Antimagic Crystal", 5);
    }

    private static void definition(String id, String displayName, int biomassCost) {
        var items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        RegistrySupplier<Item> item = items.register(new ResourceLocation(AkaishiMod.MOD_ID, id),
                () -> new Item(new Item.Properties().stacksTo(1)));
        DEFINITIONS.put(id, new Definition(id, displayName, biomassCost, item));
    }

    public static Map<String, Definition> definitions() {
        return Map.copyOf(DEFINITIONS);
    }

    public static Definition definition(String id) {
        return id == null ? null : DEFINITIONS.get(id.toLowerCase(Locale.ROOT));
    }

    public static RegistrySupplier<Item> item(String id) {
        Definition definition = definition(id);
        return definition == null ? null : definition.item();
    }

    public static ItemStack stack(String id) {
        Definition definition = definition(id);
        if (definition == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(definition.item().get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(MATERIAL_TAG, definition.id());
        tag.putInt(BIOMASS_COST_TAG, definition.biomassCost());
        return stack;
    }

    public static boolean isMaterial(ItemStack stack) {
        return materialId(stack) != null && biomassCost(stack) >= 3 && biomassCost(stack) <= 5;
    }

    /** Returns a stable material id, accepting both formal items and legacy NBT-marked stacks. */
    public static String materialId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            String legacy = tag.getString(MATERIAL_TAG);
            if (!legacy.isBlank() && DEFINITIONS.containsKey(legacy.toLowerCase(Locale.ROOT))) {
                return legacy.toLowerCase(Locale.ROOT);
            }
        }
        for (Definition definition : DEFINITIONS.values()) {
            if (stack.is(definition.item().get())) return definition.id();
        }
        return null;
    }

    public static int biomassCost(ItemStack stack) {
        String id = materialId(stack);
        if (id == null) return 0;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(BIOMASS_COST_TAG)) {
            int tagged = tag.getInt(BIOMASS_COST_TAG);
            if (tagged >= 3 && tagged <= 5) return tagged;
        }
        Definition definition = definition(id);
        return definition == null ? 0 : definition.biomassCost();
    }

    public record Definition(String id, String displayName, int biomassCost, RegistrySupplier<Item> item) {
    }
}

package com.example.akaishi.forbidden.brewing;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BrewingRecipeIO {
    private BrewingRecipeIO() { }

    static CompoundTag tag(JsonObject json, String key) {
        if (!json.has(key)) return new CompoundTag();
        try {
            return TagParser.parseTag(GsonHelper.getAsString(json, key));
        } catch (CommandSyntaxException exception) {
            throw new JsonSyntaxException("Invalid brewing " + key, exception);
        }
    }

    static ItemStack stack(JsonObject json) {
        ItemStack stack = ShapedRecipe.itemStackFromJson(json);
        if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()) {
            throw new JsonSyntaxException("Invalid brewing output count");
        }
        if (json.has("nbt")) stack.setTag(tag(json, "nbt"));
        return stack;
    }

    static BrewingPotionData potion(JsonObject json) {
        List<MobEffectInstance> effects = new ArrayList<>();
        for (var entry : GsonHelper.getAsJsonArray(json, "effects")) {
            JsonObject effect = entry.getAsJsonObject();
            ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(effect, "effect"));
            if (!BuiltInRegistries.MOB_EFFECT.containsKey(id)) throw new JsonSyntaxException("Unknown brewing effect " + id);
            MobEffect type = BuiltInRegistries.MOB_EFFECT.get(id);
            effects.add(new MobEffectInstance(type, GsonHelper.getAsInt(effect, "duration"),
                    GsonHelper.getAsInt(effect, "amplifier", 0)));
        }
        try {
            return new BrewingPotionData(GsonHelper.getAsInt(json, "capacity"),
                    GsonHelper.getAsInt(json, "strength"), GsonHelper.getAsInt(json, "duration"),
                    BrewingPotionData.Delivery.valueOf(GsonHelper.getAsString(json, "delivery", "drink").toUpperCase(Locale.ROOT)),
                    GsonHelper.getAsBoolean(json, "hide_particles", false),
                    GsonHelper.getAsBoolean(json, "pressure_plates", false), effects);
        } catch (IllegalArgumentException exception) {
            throw new JsonSyntaxException("Invalid brewing potion result", exception);
        }
    }
}

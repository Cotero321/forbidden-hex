package com.example.akaishi.forbidden.transmutation.life_furnace;

import com.example.akaishi.forbidden.transmutation.LifeFurnaceRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Biomass carries the source entity type in its stack NBT for template-specific processing. */
public final class BiomassItem extends Item {
    public BiomassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (stack.hasTag()) {
            String type = stack.getTag().getString(LifeFurnaceRules.BIOMASS_ENTITY_TYPE_TAG);
            if (!type.isBlank()) {
                ResourceLocation id = ResourceLocation.tryParse(type);
                Component name = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                        ? BuiltInRegistries.ENTITY_TYPE.get(id).getDescription() : Component.literal(type);
                tooltip.add(Component.translatable("item.forbidden_hex.biomass.entity_type", name));
            }
        }
    }
}

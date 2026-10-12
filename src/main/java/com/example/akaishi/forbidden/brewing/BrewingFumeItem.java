package com.example.akaishi.forbidden.brewing;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BrewingFumeItem extends Item {
    public static final String FUME_ID = "forbidden_hex_fume";

    public BrewingFumeItem() {
        super(new Properties().stacksTo(16));
    }

    public static ItemStack create(ResourceLocation kind) {
        ItemStack stack = new ItemStack(BrewingItems.FUME.get());
        stack.getOrCreateTag().putString(FUME_ID, kind.toString());
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation kind = stack.hasTag() ? ResourceLocation.tryParse(stack.getTag().getString(FUME_ID)) : null;
        return kind == null ? super.getName(stack)
                : Component.translatable("fume." + kind.getNamespace() + "." + kind.getPath().replace('/', '.'));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.forbidden_hex.brewing.fume"));
    }
}

package com.example.akaishi.forbidden.familiar.item;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateId;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/** Formal item identities for the six familiar templates.
 *
 * The legacy NBT marker is kept on generated stacks so old datapacks and saved inputs remain
 * compatible while registered items provide a stable recipe and creative-tab identity.
 */
public final class FamiliarTemplateItems {
    private static final String TEMPLATE_TAG = "FamiliarTemplate";
    private static final Map<FamiliarTemplateId, RegistrySupplier<Item>> ITEMS =
            new EnumMap<>(FamiliarTemplateId.class);

    private FamiliarTemplateItems() {
    }

    public static void register() {
        var items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        for (FamiliarTemplateId template : FamiliarTemplateId.values()) {
            if (!template.isConfigured()) continue;
            ITEMS.put(template, items.register(
                    new ResourceLocation(AkaishiMod.MOD_ID, "familiar_template_" + template.id()),
                    () -> new Item(new Item.Properties().stacksTo(1))));
        }
    }

    public static RegistrySupplier<Item> item(FamiliarTemplateId template) {
        return ITEMS.get(template);
    }

    public static ItemStack stack(FamiliarTemplateId template) {
        RegistrySupplier<Item> supplier = item(template);
        if (supplier == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(supplier.get());
        stack.getOrCreateTag().putString(TEMPLATE_TAG, template.id());
        return stack;
    }

    public static FamiliarTemplateId templateId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return FamiliarTemplateId.NONE;
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            FamiliarTemplateId legacy = FamiliarTemplateId.fromId(tag.getString(TEMPLATE_TAG));
            if (legacy != FamiliarTemplateId.NONE) return legacy;
        }
        for (Map.Entry<FamiliarTemplateId, RegistrySupplier<Item>> entry : ITEMS.entrySet()) {
            if (stack.is(entry.getValue().get())) return entry.getKey();
        }
        return FamiliarTemplateId.NONE;
    }
}

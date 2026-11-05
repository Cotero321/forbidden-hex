package com.example.akaishi.forbidden.altar;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.block.AkaishiBlockRegistrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SourceAltarContent {
    public static RegistrySupplier<Block> SOURCE_ALTAR;
    public static RegistrySupplier<Block> OFFERING_PEDESTAL;
    private static final Map<String, RegistrySupplier<Block>> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, RegistrySupplier<Item>> ITEMS = new LinkedHashMap<>();

    private SourceAltarContent() { }

    public static void register() {
        var blocks = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        SOURCE_ALTAR = AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_source_altar", SourceAltarBlock::new);
        OFFERING_PEDESTAL = AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_offering_pedestal", OfferingPedestalBlock::new);
        BLOCKS.put("source_altar", SOURCE_ALTAR);
        BLOCKS.put("offering_pedestal", OFFERING_PEDESTAL);
        for (String name : new String[]{"candelabrum", "large_candelabrum", "sacred_candelabrum"}) {
            BLOCKS.put(name, AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_" + name,
                    () -> new SourceCandelabrumBlock(name)));
        }
        var items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        for (String name : new String[]{"creeper_heart", "charged_creeper_heart", "abyssal_heart", "witch_core", "chalice",
                "filled_chalice", "immortality_chalice", "philosophers_stone", "ritual_dagger"}) {
            ITEMS.put(name, items.register(id(name), () -> new Item(new Item.Properties())));
        }
    }
    public static ResourceLocation id(String name) { return new ResourceLocation(SourceAltarRules.custom(name)); }
    public static Item item(String name) { return ITEMS.get(name).get(); }
    public static Block block(String name) { return BLOCKS.get(name).get(); }
    public static void addCreativeItems(CreativeModeTab.Output output) {
        BLOCKS.values().forEach(block -> output.accept(block.get()));
        ITEMS.values().forEach(item -> output.accept(item.get()));
    }
}

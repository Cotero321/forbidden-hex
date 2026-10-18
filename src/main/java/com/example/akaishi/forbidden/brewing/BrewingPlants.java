package com.example.akaishi.forbidden.brewing;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.block.AkaishiBlockRegistrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BrewingPlants {
    public static final List<String> HERBS = List.of("mandrake", "henbane", "belladonna", "datura", "aconite", "foxglove",
            "hellroot", "colchicum", "yew", "ergot", "parasitic_vine", "ember_moss", "glint_weed");
    public static final List<String> TREES = List.of("hawthorn", "rowan", "alder", "elder", "willow");
    private static final Map<String, RegistrySupplier<Block>> CROPS = new LinkedHashMap<>();
    private static final Map<String, RegistrySupplier<Block>> SAPLINGS = new LinkedHashMap<>();
    private static final Map<String, RegistrySupplier<Block>> LOGS = new LinkedHashMap<>();
    private static final Map<String, RegistrySupplier<Block>> LEAVES = new LinkedHashMap<>();

    private BrewingPlants() { }

    public static void register() {
        var blocks = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        for (String herb : HERBS) {
            CROPS.put(herb, AkaishiBlockRegistrar.registerBlockOnly(blocks, "forbidden_hex_" + herb + "_crop",
                    () -> new CropBlock(BlockBehaviour.Properties.copy(Blocks.WHEAT)) {
                        @Override protected ItemLike getBaseSeedId() { return seed(herb); }
                    }));
        }
        for (String tree : TREES) {
            LOGS.put(tree, AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_" + tree + "_log",
                    () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG))));
            LEAVES.put(tree, AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_" + tree + "_leaves",
                    () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES))));
            SAPLINGS.put(tree, AkaishiBlockRegistrar.registerBlockOnly(blocks, "forbidden_hex_" + tree + "_sapling",
                    () -> new SaplingBlock(new AbstractTreeGrower() {
                        @Override protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) {
                            return ResourceKey.create(Registries.CONFIGURED_FEATURE, BrewingItems.id(tree + "_tree"));
                        }
                    }, BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING))));
        }
    }

    public static Block crop(String name) { return CROPS.get(name).get(); }
    public static Block sapling(String name) { return SAPLINGS.get(name).get(); }
    public static Map<String, RegistrySupplier<Block>> logs() { return Map.copyOf(LOGS); }
    public static Map<String, RegistrySupplier<Block>> leaves() { return Map.copyOf(LEAVES); }

    public static Item seed(String name) {
        return switch (name) {
            case "mandrake" -> BrewingItems.MANDRAKE_SEEDS.get();
            case "henbane" -> BrewingItems.HENBANE_SEEDS.get();
            case "belladonna" -> BrewingItems.BELLADONNA_SEEDS.get();
            case "datura" -> BrewingItems.DATURA_SEEDS.get();
            case "aconite" -> BrewingItems.ACONITE_SEEDS.get();
            case "foxglove" -> BrewingItems.FOXGLOVE_SEEDS.get();
            case "hellroot" -> BrewingItems.HELLROOT_SEEDS.get();
            case "colchicum" -> BrewingItems.COLCHICUM_SEEDS.get();
            case "yew" -> BrewingItems.YEW_SEEDS.get();
            case "ergot" -> BrewingItems.ERGOT_SEEDS.get();
            case "parasitic_vine" -> BrewingItems.PARASITIC_VINE_SEEDS.get();
            case "ember_moss" -> BrewingItems.EMBER_MOSS_SEEDS.get();
            case "glint_weed" -> BrewingItems.GLINT_WEED_SEEDS.get();
            default -> throw new IllegalArgumentException("Unknown brewing plant " + name);
        };
    }
}

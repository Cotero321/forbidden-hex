package com.example.akaishi.forbidden.altar;

import com.example.akaishi.forbidden.brewing.BrewingPlants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 源能以半点为单位；所有容量、倍率和白名单以本表为唯一来源。 */
public final class SourceAltarRules {
    public static final int RADIUS = 64;
    public static final int BASE_HEIGHT = 10;
    public static final int EXTENDED_HEIGHT = 14;
    public static final int PEDESTAL_RADIUS = 8;
    public static final int PEDESTAL_HEIGHT = 2;
    public static final int SCAN_BUDGET = 8192;
    public static final long MAX_UNITS = 2_000_000_000_000L;
    private static final Map<String, Entry> ENVIRONMENT = environment();
    private static final Map<String, Integer> HEADS = Map.of("skeleton_skull", 2, "zombie_head", 2,
            "creeper_head", 2, "piglin_head", 3, "wither_skeleton_skull", 4, "player_head", 5);
    private static final Map<String, Integer> LIGHTS = Map.of("minecraft:lantern", 2, "minecraft:soul_lantern", 3,
            "minecraft:end_rod", 4, custom("candelabrum"), 5, custom("large_candelabrum"), 7,
            custom("sacred_candelabrum"), 10);
    private static final Map<String, Integer> IMPLEMENTS = Map.of(custom("chalice"), 3, custom("filled_chalice"), 4,
            custom("immortality_chalice"), 6, custom("philosophers_stone"), 4);
    private static final Map<String, Integer> OFFERINGS = Map.of("minecraft:nether_star", 4, "minecraft:heart_of_the_sea", 3,
            custom("abyssal_heart"), 6, "minecraft:enchanted_golden_apple", 4,
            custom("charged_creeper_heart"), 4, custom("witch_core"), 4);

    private SourceAltarRules() { }

    public record Entry(String key, int points, int limit) { }

    public record Snapshot(long capacityUnits, long recoveryUnits, int headMultiplier, int implementHalves,
                           int lightMultiplier, int offeringMask, int duplicateMask, int pedestalCount, int height) {
        public int capacityMultiplierHalves() { return headMultiplier * implementHalves; }
    }

    public static String custom(String name) { return "forbidden_hex:forbidden_hex_" + name; }
    public static String itemId(ItemStack stack) { return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    public static String blockId(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }
    public static Entry entry(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return null;
        return ENVIRONMENT.get(blockId(state.getBlock()));
    }
    public static Map<String, Entry> environmentEntries() { return Map.copyOf(ENVIRONMENT); }
    public static int offeringHalves(ItemStack stack) { return OFFERINGS.getOrDefault(itemId(stack), 0); }
    public static int implementHalves(ItemStack stack) { return IMPLEMENTS.getOrDefault(itemId(stack), 2); }
    public static boolean isDagger(ItemStack stack) { return itemId(stack).equals(custom("ritual_dagger")); }
    public static String normalizeHead(String id) {
        return id.replace("_wall_skull", "_skull").replace("_wall_head", "_head");
    }
    public static int headMultiplier(String id) {
        if (!id.startsWith("minecraft:")) return 1;
        return HEADS.getOrDefault(normalizeHead(id).substring("minecraft:".length()), 1);
    }
    public static int lightMultiplier(String id) { return LIGHTS.getOrDefault(id, 1); }
    public static boolean isDisplayBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        String id = blockId(item.getBlock());
        return headMultiplier(id) > 1 || lightMultiplier(id) > 1
                || id.equals("minecraft:dragon_egg") || normalizeHead(id).equals("minecraft:dragon_head");
    }
    public static String format(long halfUnits) {
        return halfUnits / 2 + (halfUnits % 2 == 0 ? "" : ".5");
    }

    /** 环境与陈列共用一份累计器，所有配额在归并后按种类独立封顶。 */
    public static final class Accumulator {
        private final Map<String, Integer> counts = new HashMap<>();
        private final Map<String, Integer> displayCounts = new HashMap<>();
        private long environmentPoints;
        private int hearts;
        private int beacons;
        private int anchors;
        private boolean egg;
        private boolean dragon;
        private int head = 1;
        private int implement = 2;
        private int light = 1;
        private boolean dagger;

        public void environment(BlockState state) { environment(state, 1); }
        public void environment(BlockState state, int delta) {
            Entry entry = entry(state);
            if (entry != null) {
                int before = counts.getOrDefault(entry.key(), 0);
                int after = Math.max(0, before + delta);
                if (after == 0) counts.remove(entry.key()); else counts.put(entry.key(), after);
                environmentPoints += (long) (Math.min(after, entry.limit()) - Math.min(before, entry.limit())) * entry.points();
            }
            String id = normalizeHead(blockId(state.getBlock()));
            if (headMultiplier(id) > 1 || lightMultiplier(id) > 1 || id.equals("minecraft:dragon_egg") || id.equals("minecraft:dragon_head")) {
                int after = Math.max(0, displayCounts.getOrDefault(id, 0) + delta);
                if (after == 0) displayCounts.remove(id); else displayCounts.put(id, after);
            }
        }
        public void activeBeacon() { activeBeacon(1); }
        public void activeBeacon(int delta) { beacons = Math.max(0, beacons + delta); }
        public void chargedAnchor() { chargedAnchor(1); }
        public void chargedAnchor(int delta) { anchors = Math.max(0, anchors + delta); }
        public Accumulator copy() {
            Accumulator copy = new Accumulator();
            copy.counts.putAll(counts);
            copy.displayCounts.putAll(displayCounts);
            copy.environmentPoints = environmentPoints;
            copy.beacons = beacons;
            copy.anchors = anchors;
            return copy;
        }
        public void mergeEnvironment(Accumulator other) {
            other.counts.forEach((key, count) -> {
                Entry entry = ENVIRONMENT.get(key);
                int before = counts.getOrDefault(key, 0);
                counts.put(key, before + count);
                environmentPoints += (long) (Math.min(before + count, entry.limit()) - Math.min(before, entry.limit())) * entry.points();
            });
            other.displayCounts.forEach((id, count) -> displayCounts.merge(id, count, Integer::sum));
            beacons += other.beacons;
            anchors += other.anchors;
        }
        public void display(ItemStack stack) {
            if (stack.isEmpty()) return;
            String id = itemId(stack);
            if (id.equals(custom("creeper_heart"))) hearts = Math.min(4, hearts + stack.getCount());
            implement = Math.max(implement, implementHalves(stack));
            dagger |= isDagger(stack);
            if (isDisplayBlock(stack)) displayBlock(blockId(((BlockItem) stack.getItem()).getBlock()));
        }
        private void displayBlock(String id) {
            head = Math.max(head, headMultiplier(id));
            light = Math.max(light, lightMultiplier(id));
            egg |= id.equals("minecraft:dragon_egg");
            dragon |= normalizeHead(id).equals("minecraft:dragon_head");
        }
        public Snapshot finish(List<ItemStack> offerings) {
            int bestHead = head;
            int bestLight = light;
            for (String id : displayCounts.keySet()) {
                bestHead = Math.max(bestHead, headMultiplier(id));
                bestLight = Math.max(bestLight, lightMultiplier(id));
            }
            long base = 100 + environmentPoints + (egg || displayCounts.containsKey("minecraft:dragon_egg") ? 800 : 0)
                    + (dragon || displayCounts.containsKey("minecraft:dragon_head") ? 500 : 0);
            long capacity = base * bestHead * implement + hearts * 500L + Math.min(4, beacons) * 800L + Math.min(2, anchors) * 400L;
            long recovery = bestLight * 2L;
            Set<String> seen = new HashSet<>();
            int mask = 0;
            int duplicates = 0;
            for (int i = 0; i < Math.min(4, offerings.size()); i++) {
                ItemStack stack = offerings.get(i);
                int factor = offeringHalves(stack);
                if (factor == 0) continue;
                if (!seen.add(itemId(stack))) { duplicates |= 1 << i; continue; }
                mask |= 1 << i;
                recovery = recovery * factor / 2;
            }
            return new Snapshot(Math.min(MAX_UNITS, capacity), recovery, bestHead, implement, bestLight, mask, duplicates,
                    Math.min(4, offerings.size()), dagger ? EXTENDED_HEIGHT : BASE_HEIGHT);
        }
    }

    private static Map<String, Entry> environment() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        add(entries, 50, 1, "grass_block", "farmland");
        add(entries, 50, 2, "mycelium", "end_stone", "mossy_cobblestone", "brown_mushroom_block", "red_mushroom_block",
                "podzol", "amethyst_block", "chorus_plant");
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")) {
            add(entries, 50, 2, wood + "_log", wood + "_leaves");
            add(entries, 30, 2, wood.equals("mangrove") ? "mangrove_propagule" : wood + "_sapling");
        }
        add(entries, 30, 1, "grass");
        alias(entries, "tall_grass", "grass");
        add(entries, 30, 2, "sea_pickle", "kelp", "pumpkin", "melon", "lily_pad", "vine");
        alias(entries, "kelp_plant", "kelp");
        for (String coral : List.of("tube", "brain", "bubble", "fire", "horn")) {
            add(entries, 30, 2, coral + "_coral");
            for (String suffix : List.of("_coral_block", "_coral_fan", "_coral_wall_fan"))
                alias(entries, coral + suffix, coral + "_coral");
        }
        add(entries, 30, 2, "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
                "sunflower", "lilac", "rose_bush", "peony", "torchflower", "pitcher_plant");
        for (String tree : BrewingPlants.TREES) {
            customEntry(entries, tree + "_log", 3, 20);
            customEntry(entries, tree + "_leaves", 3, 20);
            customEntry(entries, tree + "_sapling", 2, 30);
        }
        add(entries, 25, 3, "wheat", "carrots", "potatoes", "beetroots", "cocoa", "nether_wart", "sweet_berry_bush",
                "melon_stem", "pumpkin_stem", "torchflower_crop", "pitcher_crop");
        alias(entries, "attached_melon_stem", "melon_stem");
        alias(entries, "attached_pumpkin_stem", "pumpkin_stem");
        for (String herb : BrewingPlants.HERBS) customEntry(entries, herb + "_crop", 3, 25);
        add(entries, 10, 2, "soul_sand", "sea_lantern", "prismarine", "ochre_froglight", "verdant_froglight", "pearlescent_froglight");
        add(entries, 10, 3, "crying_obsidian", "budding_amethyst");
        return Map.copyOf(entries);
    }
    private static void add(Map<String, Entry> map, int limit, int points, String... names) {
        for (String name : names) map.put("minecraft:" + name, new Entry("minecraft:" + name, points, limit));
    }
    private static void alias(Map<String, Entry> map, String name, String canonical) {
        map.put("minecraft:" + name, map.get("minecraft:" + canonical));
    }
    private static void customEntry(Map<String, Entry> map, String name, int points, int limit) {
        map.put(custom(name), new Entry(custom(name), points, limit));
    }
}

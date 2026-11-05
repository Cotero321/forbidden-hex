package com.example.akaishi.forbidden.alchemy;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.block.AkaishiBlockRegistrar;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AlchemyContent {
    public enum Kind {
        MANUAL_CRUSHER("manual_crusher", "手动粉碎器", true, false),
        SOURCE_CRUSHER("source_crusher", "源能粉碎器", true, true),
        DISTILLER("distiller", "蒸馏塔", false, false),
        SOURCE_DISTILLER("source_distiller", "源能蒸馏塔", false, true);
        private final String id, chinese;
        private final boolean crusher, powered;
        Kind(String id, String chinese, boolean crusher, boolean powered) { this.id = id; this.chinese = chinese; this.crusher = crusher; this.powered = powered; }
        public String id() { return id; }
        public String chinese() { return chinese; }
        public boolean crusher() { return crusher; }
        public boolean powered() { return powered; }
    }
    public static final Map<Kind, RegistrySupplier<Block>> BLOCKS = new LinkedHashMap<>();
    public static RegistrySupplier<BlockEntityType<AlchemyMachineBlockEntity>> MACHINE_ENTITY;
    public static RegistrySupplier<MenuType<AlchemyMenu>> MENU;
    public static RegistrySupplier<RecipeType<AlchemyRecipe>> RECIPES;
    public static RegistrySupplier<RecipeSerializer<AlchemyRecipe>> SERIALIZER;
    public static RegistrySupplier<RecipeSerializer<AlchemyUpgradeRecipe>> UPGRADE_SERIALIZER;
    private AlchemyContent() { }
    public static void register() {
        AlchemyMaterials.register();
        var blocks = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        for (Kind kind : Kind.values()) BLOCKS.put(kind, AkaishiBlockRegistrar.registerMachineBlock(blocks,
                "forbidden_hex_" + kind.id(), () -> new AlchemyMachineBlock(kind)));
        MACHINE_ENTITY = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK_ENTITY_TYPE).register(
                AlchemyMaterials.id("alchemy_machine"), () -> BlockEntityType.Builder.of(AlchemyMachineBlockEntity::new,
                        BLOCKS.values().stream().map(RegistrySupplier::get).toArray(Block[]::new)).build(null));
        MENU = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.MENU).register(AlchemyMaterials.id("alchemy_machine"),
                () -> MenuRegistry.ofExtended((id, inventory, buffer) -> new AlchemyMenu(id, inventory, buffer.readBlockPos())));
        RECIPES = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.RECIPE_TYPE).register(
                AlchemyMaterials.id("alchemy"), () -> new RecipeType<AlchemyRecipe>() { @Override public String toString() { return AlchemyMaterials.id("alchemy").toString(); } });
        SERIALIZER = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.RECIPE_SERIALIZER).register(
                AlchemyMaterials.id("alchemy"), AlchemyRecipe.Serializer::new);
        UPGRADE_SERIALIZER = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.RECIPE_SERIALIZER).register(
                AlchemyMaterials.id("alchemy_upgrade"), AlchemyUpgradeRecipe.Serializer::new);
    }
    public static void addCreativeItems(CreativeModeTab.Output output) {
        BLOCKS.values().forEach(block -> output.accept(block.get()));
        AlchemyMaterials.addCreativeItems(output);
    }
}

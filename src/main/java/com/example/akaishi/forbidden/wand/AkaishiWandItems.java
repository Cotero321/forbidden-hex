package com.example.akaishi.forbidden.wand;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 法杖域物品注册（RULES §1 域拆分）：法杖本体 1 ＋ 模块 68（端 31 / 柄 20 / 核 17）。
 *
 * <p><b>为什么遍历表注册</b>：与 {@link com.example.akaishi.forbidden.item.AkaishiMagicItems}
 * 同一口径 —— 模块的 id、段位、词条全部已在三张表里，注册只是"把每行投影成一个物品"，
 * 加模块只改表，本类零改动（开闭原则）。
 *
 * <p>资源链（RULES §3）：id {@code forbidden_hex_wand_*} 与
 * {@code assets/forbidden_hex/textures/item/<id>.png}、{@code assets/forbidden_hex/models/item/<id>.json}
 * 严格同名；双语 lang 键 {@code item.forbidden_hex.<id>}（+ {@code .short} 短名键供法杖名组句）。
 */
public final class AkaishiWandItems {

    /** 法杖本体注册 id */
    public static final String WAND_ID = "forbidden_hex_wand";

    /** 法杖本体（堆叠 1：它是账本与法术槽的载体，不是散货） */
    public static RegistrySupplier<Item> wand;

    /** 模块 id → 物品（插入序 = 三表原稿序） */
    private static final Map<String, RegistrySupplier<Item>> PARTS = new LinkedHashMap<>();

    private AkaishiWandItems() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用（须排在创造栏注册之前） */
    public static void register() {
        wand = item(WAND_ID, () -> new WandItem(new Item.Properties().stacksTo(1)));
        for (WandPartDef def : WandTipTable.all().values()) {
            registerPart(def);
        }
        for (WandPartDef def : WandHandleTable.all().values()) {
            registerPart(def);
        }
        for (WandPartDef def : WandCoreTable.all().values()) {
            registerPart(def);
        }
    }

    private static void registerPart(WandPartDef def) {
        PARTS.put(def.id(), item(def.id(), () -> new WandPartItem(def, new Item.Properties().stacksTo(16))));
    }

    /** 按模块 id 取物品；未知 id 返回 {@code null}（调用方决定回退，禁止静默造物） */
    public static RegistrySupplier<Item> part(String id) {
        return PARTS.get(id);
    }

    /** 全部模块物品（插入序 = 三表原稿序） */
    public static Map<String, RegistrySupplier<Item>> parts() {
        return Collections.unmodifiableMap(PARTS);
    }

    /** 组装一根法杖（配方装配与调试命令共用；不校验段位合法性 —— 表外 id 在 tooltip 层自然暴露） */
    public static ItemStack createWand(String tipId, String handleId, String coreId) {
        ItemStack stack = new ItemStack(wand.get());
        WandItem.setParts(stack, tipId, handleId, coreId);
        return stack;
    }

    /** 镶嵌页写入咒核用的安全口径：只接受真咒核物品（空白核/墨/毛笔都不算法术） */
    public static boolean isSpellCore(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof com.example.akaishi.forbidden.item.MagicCoreItem;
    }

    /** 咒核物品的持久化 tag（镶嵌页装卸共用） */
    public static CompoundTag saveSpell(ItemStack spell) {
        CompoundTag tag = new CompoundTag();
        spell.save(tag);
        return tag;
    }

    private static RegistrySupplier<Item> item(String id, Supplier<Item> factory) {
        return RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, id), factory);
    }
}

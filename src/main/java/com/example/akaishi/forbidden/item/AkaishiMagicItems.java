package com.example.akaishi.forbidden.item;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.ForbiddenIds;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 魔法域物品注册：空墨水瓶 + 11 色墨。
 *
 * <p><b>为什么墨不逐个写字段</b>：11 色墨是<b>同一张表的投影</b> —— 它们的 id、识别色、
 * 数值全部已经由 {@link MagicInkTable} 唯一持有。若在这里再抄 11 遍字段，就会出现
 * 第二份真源：将来加一种墨必须同时改表与改本类，忘一处即"表里有、物品没有"的静默缺口。
 * 故本类改为<b>遍历表来注册</b>，加墨只改表（开闭原则）。
 *
 * <p><b>为什么空瓶单独一个字段</b>：它不是墨（不在墨表内），而是墨的<b>基底容器</b>，
 * 归属不同，故独立持有。
 *
 * <p><b>命名与资源链一致性</b>（RULES §3）：物品 id 为 {@code forbidden_hex_ink_bottle} 与
 * {@code forbidden_hex_ink_<墨id>}，与已落盘的
 * {@code assets/forbidden_hex/textures/item/<id>.png} 和 {@code assets/forbidden_hex/models/item/<id>.json}
 * <b>逐字同名</b> —— 改名必须三处同步，否则就是缺图死链。
 */
public final class AkaishiMagicItems {

    /** 空墨水瓶注册 id（墨的基底容器） */
    public static final String INK_BOTTLE_ID = "forbidden_hex_ink_bottle";

    /** 魔法毛笔注册 id（载术法器：法术的书写与施放载体） */
    public static final String MAGIC_BRUSH_ID = "forbidden_hex_magic_brush";

    /**
     * 毛笔的耐久上限（= 可书写/施放的次数）。
     *
     * <p><b>200 与墨的 50 不是同一种刻度</b>：墨是<b>耗材</b>（一瓶用完即弃），
     * 毛笔是<b>法器</b>（反复使用的工具，只磨损不消耗）。故毛笔的耐久远高于单瓶墨，
     * 换算出"一支笔配约 4 瓶墨"的用量关系。
     */
    public static final int BRUSH_DURABILITY = 200;

    /** 墨物品 id 前缀：完整 id = 前缀 + 墨 id（如 {@code forbidden_hex_ink_ember}） */
    public static final String INK_ID_PREFIX = "forbidden_hex_ink_";
    public static final String MAGIC_CORE_ID_PREFIX = "forbidden_hex_magic_core_";
    public static final String BLANK_MAGIC_CORE_ID = "forbidden_hex_magic_core_blank";

    /**
     * 一滴墨的耐久上限（= 一瓶可用次数）。
     *
     * <p>耐久在 MC 里是<b>通用的"还能用几次"载体</b>：设了它，物品就会自动带耐久条、
     * 每次消耗走同一套原版扣减与碎裂表现，不必自造 NBT 计次。代价是原版会
     * <b>把堆叠上限强制为 1</b> —— 这对墨恰好是想要的语义：墨是<b>一瓶一瓶</b>的，
     * 不是可以叠成一摞的散货。
     */
    public static final int INK_DURABILITY = 50;

    /** 空墨水瓶：无液体。将来的装填入口（墨水瓶 + 墨料 ⇒ 墨）以此为基底 */
    public static RegistrySupplier<Item> inkBottle;

    /** 魔法毛笔：载术法器，将来的施法载体 */
    public static RegistrySupplier<Item> magicBrush;
    public static RegistrySupplier<Item> blankMagicCore;
    private static final Map<MagicSource, RegistrySupplier<Item>> MAGIC_CORES = new LinkedHashMap<>();

    /** 墨 id → 物品（插入序 = {@link MagicInkTable} 的解锁梯队序，创造栏直接沿用） */
    private static final Map<String, RegistrySupplier<Item>> INKS = new LinkedHashMap<>();

    private AkaishiMagicItems() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用（须排在创造栏注册之前） */
    public static void register() {
        // 法器在前、耗材在后：毛笔是"拿在手里的东西"，创造栏里排最前符合取用直觉
        magicBrush = item(MAGIC_BRUSH_ID, () -> new Item(brushProperties()));
        blankMagicCore = item(BLANK_MAGIC_CORE_ID, () -> new Item(new Item.Properties().stacksTo(16)));
        for (MagicSource source : MagicSource.values()) {
            MAGIC_CORES.put(source, item(MAGIC_CORE_ID_PREFIX + source.id(), () -> new MagicCoreItem(source)));
        }
        inkBottle = item(INK_BOTTLE_ID, () -> new Item(bottleProperties()));
        for (String inkId : MagicInkTable.all().keySet()) {
            INKS.put(inkId, item(INK_ID_PREFIX + ForbiddenIds.inkName(inkId), () -> new Item(inkProperties())));
        }
    }

    /**
     * 毛笔的物品属性：耐久 {@value #BRUSH_DURABILITY}。
     *
     * <p>与墨同样走原版耐久（自带耐久条与碎裂表现），但它是<b>法器</b>：
     * 不随手丢弃，用旧了可以修（原版铁砧/合并语义天然可用）。
     */
    private static Item.Properties brushProperties() {
        return new Item.Properties().durability(BRUSH_DURABILITY);
    }

    /**
     * 空瓶的物品属性：可堆叠 16。
     *
     * <p><b>与墨刻意不同</b>：空瓶是<b>容器</b>（装填的基底，可反复拿来用），不是耗材，
     * 故不给耐久、保留堆叠 —— 否则"囤一批空瓶备用"这件事就做不到了。
     */
    private static Item.Properties bottleProperties() {
        return new Item.Properties().stacksTo(16);
    }

    /**
     * 墨的物品属性：耐久 {@value #INK_DURABILITY}，耗尽即碎裂。
     *
     * <p>不另设 rarity：解锁梯队（Lv1~Lv6）已经由配方与习得门槛表达，
     * 再叠一层名字颜色只会与前者重复表达同一件事。
     */
    private static Item.Properties inkProperties() {
        return new Item.Properties().durability(INK_DURABILITY);
    }

    public static RegistrySupplier<Item> core(MagicSource source) {
        return MAGIC_CORES.get(source);
    }

    public static Map<MagicSource, RegistrySupplier<Item>> cores() {
        return Collections.unmodifiableMap(MAGIC_CORES);
    }

    /** 按墨 id 取物品；未知 id 返回 {@code null}（调用方决定回退口径，禁止静默造物） */
    public static RegistrySupplier<Item> ink(String inkId) {
        return INKS.get(inkId);
    }

    /** 全部墨物品（插入序 = 解锁梯队序） */
    public static Map<String, RegistrySupplier<Item>> inks() {
        return Collections.unmodifiableMap(INKS);
    }

    /** 注册物品（generated 模型引用 textures/item/&lt;id&gt;.png，与 id 严格同名） */
    private static RegistrySupplier<Item> item(String id, Supplier<Item> factory) {
        return RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, id), factory);
    }
}

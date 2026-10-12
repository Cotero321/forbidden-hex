package com.example.akaishi.forbidden.wand;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.akaishi.forbidden.wand.WandMod.EXTRA_POOL;
import static com.example.akaishi.forbidden.wand.WandMod.EFFICACY;
import static com.example.akaishi.forbidden.wand.WandMod.POST_CAST_REFUND;
import static com.example.akaishi.forbidden.wand.WandMod.REGEN_EFF;

/**
 * <b>杖柄表</b>（法杖记忆 §3，20 行）：通用 6（只给额外池）＋ 源杖柄 4×3 ＋ 高阶 2。
 */
public final class WandHandleTable {

    private static final Map<String, WandPartDef> ALL = new LinkedHashMap<>();

    static {
        // ---- §3.1 通用杖柄（6）：加到全部源的额外池（定案 §10.4）----
        general("forbidden_hex_wand_handle_wood", 1.05f);
        general("forbidden_hex_wand_handle_bone", 1.10f);
        general("forbidden_hex_wand_handle_iron", 1.15f);
        general("forbidden_hex_wand_handle_gold", 1.20f);
        general("forbidden_hex_wand_handle_diamond", 1.25f);
        general("forbidden_hex_wand_handle_netherite", 1.30f);

        // ---- §3.2 源杖柄（12）：额外池 ＋ 该源恢复效率 ----
        source("forbidden_hex_wand_handle_san_rough", "san", 1.20f, 1.08f);
        source("forbidden_hex_wand_handle_san", "san", 1.35f, 1.15f);
        source("forbidden_hex_wand_handle_san_perfect", "san", 1.55f, 1.25f);
        source("forbidden_hex_wand_handle_life_rough", "life", 1.20f, 1.08f);
        source("forbidden_hex_wand_handle_life", "life", 1.35f, 1.15f);
        source("forbidden_hex_wand_handle_life_perfect", "life", 1.55f, 1.25f);
        source("forbidden_hex_wand_handle_dark_rough", "dark", 1.20f, 1.08f);
        source("forbidden_hex_wand_handle_dark", "dark", 1.35f, 1.15f);
        source("forbidden_hex_wand_handle_dark_perfect", "dark", 1.55f, 1.25f);
        source("forbidden_hex_wand_handle_white_rough", "white", 1.20f, 1.08f);
        source("forbidden_hex_wand_handle_white", "white", 1.35f, 1.15f);
        source("forbidden_hex_wand_handle_white_perfect", "white", 1.55f, 1.25f);

        // ---- §3.3 高阶通用杖柄（2）----
        // 蓄：主池满时自然恢复改道流入额外池（行为旗标，非数值）
        ALL.put("forbidden_hex_wand_handle_store", WandPartDef.of("forbidden_hex_wand_handle_store",
                WandPartDef.Kind.HANDLE, "", "", "",
                EnumSet.of(WandFlag.OVERFLOW_TO_EXTRA), EXTRA_POOL, 1.50f, EFFICACY, 1.06f));
        // 循：施法后额外池恢复该次消耗的 20%
        ALL.put("forbidden_hex_wand_handle_cycle", WandPartDef.of("forbidden_hex_wand_handle_cycle",
                WandPartDef.Kind.HANDLE, "", "", "", EnumSet.noneOf(WandFlag.class),
                EXTRA_POOL, 1.50f, POST_CAST_REFUND, 0.20f, EFFICACY, 1.06f));
    }

    private static void general(String id, float extraPoolMul) {
        ALL.put(id, WandPartDef.of(id, WandPartDef.Kind.HANDLE, "", "", "",
                EnumSet.noneOf(WandFlag.class), EXTRA_POOL, extraPoolMul, EFFICACY, 1.03f));
    }

    private static void source(String id, String sourceId, float extraPoolMul, float regenMul) {
        ALL.put(id, WandPartDef.of(id, WandPartDef.Kind.HANDLE, "", sourceId, "",
                EnumSet.noneOf(WandFlag.class), EXTRA_POOL, extraPoolMul, REGEN_EFF, regenMul, EFFICACY, 1.05f));
    }

    /** 全表（原稿序） */
    public static Map<String, WandPartDef> all() {
        return Collections.unmodifiableMap(ALL);
    }

    /** 按 id 取定义；未知 id 返回 {@code null} */
    public static WandPartDef get(String id) {
        return ALL.get(id);
    }

    /** 表内条目数（自检断言用，应为 20） */
    public static int size() {
        return ALL.size();
    }
}

package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.ForbiddenIds;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.akaishi.forbidden.wand.WandMod.*;

/**
 * <b>杖端表</b>（法杖记忆 §2，31 行）：基础 8 ＋ 墨偏向 11 ＋ 源偏向 4 ＋ 通用 8。
 *
 * <p>行序 = 原稿序（创造栏与手册目录沿用）。数值为系数（1 = 无修正）；
 * 「消耗减少-10%」类原稿双重否定已归一为 {@code COST, 0.90f}。
 */
public final class WandTipTable {

    private static final Map<String, WandPartDef> ALL = new LinkedHashMap<>();

    static {
        // ---- §2.1 基础杖端（8）----
        basic("forbidden_hex_wand_tip_copper");
        basic("forbidden_hex_wand_tip_iron", COST, 0.98f);
        basic("forbidden_hex_wand_tip_gold", COOLDOWN, 0.98f, CAST_TIME, 0.98f, COST, 0.97f);
        basic("forbidden_hex_wand_tip_diamond", COST, 0.95f);
        basic("forbidden_hex_wand_tip_netherite", COOLDOWN, 0.97f, CAST_TIME, 0.97f, COST, 0.95f);
        // 紫水晶：唯一的惩罚项（消耗 +3%），有意保留（定案 5.4）
        basic("forbidden_hex_wand_tip_amethyst", COOLDOWN, 0.90f, CAST_TIME, 0.90f, COST, 1.03f);
        basic("forbidden_hex_wand_tip_echo", COOLDOWN, 0.88f, CAST_TIME, 0.88f, COST, 0.98f);
        basic("forbidden_hex_wand_tip_nether_star", COOLDOWN, 0.90f, CAST_TIME, 0.90f, COST, 0.90f);

        // ---- §2.2 墨偏向杖端（11）：冷却/施法 -10% 全局；该墨符文能耗 85% 逐词（定案 4.3）----
        for (String inkId : com.example.akaishi.forbidden.magic.table.MagicInkTable.order()) {
            String itemId = "forbidden_hex_wand_tip_ink_" + ForbiddenIds.inkName(inkId);
            ALL.put(itemId,
                    WandPartDef.of(itemId, WandPartDef.Kind.TIP,
                            inkId, "", "", EnumSet.noneOf(WandFlag.class),
                            COOLDOWN, 0.90f, CAST_TIME, 0.90f, INK_COST, 0.85f, EFFICACY, 1.04f));
        }

        // ---- §2.3 源偏向杖端（4）：该源消耗 -20% / 冷却 -15% / 效果加成（整句，定案 §10.3）----
        source("forbidden_hex_wand_tip_source_san", "san", "mind", 1.18f);
        source("forbidden_hex_wand_tip_source_life", "life", "heal", 1.20f);
        source("forbidden_hex_wand_tip_source_dark", "dark", "debuff", 1.20f);
        source("forbidden_hex_wand_tip_source_white", "white", "defense", 1.20f);

        // ---- §2.4 通用杖端（8）----
        basic("forbidden_hex_wand_tip_balance", COST, 0.85f, COOLDOWN, 0.90f);
        basic("forbidden_hex_wand_tip_swift", COOLDOWN, 0.75f);
        // 渊池：+500 按绝对值加到**额外池**容量（Owner 2026-10-06 修正落点；原稿「渊」改名）
        basic("forbidden_hex_wand_tip_pool", EXTRA_POOL_FLAT, 500f, COOLDOWN, 0.90f);
        basic("forbidden_hex_wand_tip_quiet",
                HEAT_DISSIPATION, 1.50f, HEAT_THRESHOLD, 1.20f, COOLDOWN, 0.90f, COST, 0.90f);
        basic("forbidden_hex_wand_tip_far", RANGE, 1.25f, COOLDOWN, 0.90f, COST, 0.90f);
        basic("forbidden_hex_wand_tip_close", RANGE, 0.75f, COOLDOWN, 0.85f, CAST_TIME, 0.85f, COST, 0.80f);
        basic("forbidden_hex_wand_tip_wide", RADIUS, 1.25f, COOLDOWN, 0.90f, COST, 0.90f);
        basic("forbidden_hex_wand_tip_sharp", RADIUS, 0.75f, COOLDOWN, 0.82f, CAST_TIME, 0.82f, COST, 0.80f);
    }

    private static void basic(String id, Object... modPairs) {
        ALL.put(id, WandPartDef.of(id, WandPartDef.Kind.TIP, "", "", "",
                EnumSet.noneOf(WandFlag.class), appendEfficacy(modPairs, 1.04f)));
    }

    private static Object[] appendEfficacy(Object[] modPairs, float efficacy) {
        Object[] result = java.util.Arrays.copyOf(modPairs, modPairs.length + 2);
        result[modPairs.length] = EFFICACY;
        result[modPairs.length + 1] = efficacy;
        return result;
    }

    private static void source(String id, String sourceId, String powerScope, float powerMul) {
        ALL.put(id, WandPartDef.of(id, WandPartDef.Kind.TIP, "", sourceId, powerScope,
                EnumSet.noneOf(WandFlag.class),
                SOURCE_COST, 0.80f, SOURCE_COOLDOWN, 0.85f, EFFICACY, 1.06f, POWER, powerMul));
    }

    /** 全表（原稿序） */
    public static Map<String, WandPartDef> all() {
        return Collections.unmodifiableMap(ALL);
    }

    /** 按 id 取定义；未知 id 返回 {@code null} */
    public static WandPartDef get(String id) {
        return ALL.get(id);
    }

    /** 表内条目数（自检断言用，应为 31） */
    public static int size() {
        return ALL.size();
    }
}

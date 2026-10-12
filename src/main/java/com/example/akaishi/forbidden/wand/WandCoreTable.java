package com.example.akaishi.forbidden.wand;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.akaishi.forbidden.wand.WandMod.*;

/**
 * <b>杖核表</b>（法杖记忆 §4，17 行）：通用 3×3 ＋ 专精 4 ＋ 后期 4。
 *
 * <p>专精/后期各行按原稿逐条录入：黑瞳核的「消耗/冷却」未标注源限定，
 * 按字面落为<b>全局</b>词条（灰骨/绿髓才用源限定词条）。
 */
public final class WandCoreTable {

    private static final Map<String, WandPartDef> ALL = new LinkedHashMap<>();

    static {
        // ---- §4.1 速率核（消耗换冷却）----
        core("forbidden_hex_wand_core_rate_rough", COST, 1.05f, COOLDOWN, 0.92f, EFFICACY, 1.08f);
        core("forbidden_hex_wand_core_rate", COST, 1.10f, COOLDOWN, 0.85f, EFFICACY, 1.12f);
        core("forbidden_hex_wand_core_rate_perfect", COST, 1.15f, COOLDOWN, 0.78f, EFFICACY, 1.16f);
        // ---- §4.1 转耗核（冷却换消耗）----
        core("forbidden_hex_wand_core_convert_rough", COOLDOWN, 1.05f, COST, 0.92f, EFFICACY, 1.08f);
        core("forbidden_hex_wand_core_convert", COOLDOWN, 1.10f, COST, 0.85f, EFFICACY, 1.12f);
        core("forbidden_hex_wand_core_convert_perfect", COOLDOWN, 1.15f, COST, 0.78f, EFFICACY, 1.16f);
        // ---- §4.1 换域核（施法距离换法术范围）----
        core("forbidden_hex_wand_core_domain_rough", RANGE, 0.92f, RADIUS, 1.12f, EFFICACY, 1.08f);
        core("forbidden_hex_wand_core_domain", RANGE, 0.85f, RADIUS, 1.25f, EFFICACY, 1.12f);
        core("forbidden_hex_wand_core_domain_perfect", RANGE, 0.78f, RADIUS, 1.38f, EFFICACY, 1.16f);

        // ---- §4.2 专精杖核（4）----
        specialized("forbidden_hex_wand_core_san", "san", "mind",
                EXTRA_POOL, 0.85f, SOURCE_COST, 0.90f, EFFICACY, 1.16f, POWER, 1.14f);
        specialized("forbidden_hex_wand_core_life", "life", "heal",
                COOLDOWN, 1.08f, SOURCE_COST, 0.90f, EFFICACY, 1.14f, POWER, 1.12f);
        specialized("forbidden_hex_wand_core_dark", "dark", "debuff",
                COST, 0.95f, COOLDOWN, 0.88f, EFFICACY, 1.16f, POWER, 1.14f);
        specialized("forbidden_hex_wand_core_white", "white", "defense",
                REGEN_EFF, 0.95f, EXTRA_POOL, 1.20f, EFFICACY, 1.14f, POWER, 1.12f);

        // ---- §4.3 后期杖核（4）----
        ALL.put("forbidden_hex_wand_core_perpetual", WandPartDef.of("forbidden_hex_wand_core_perpetual",
                WandPartDef.Kind.CORE, "", "", "",
                EnumSet.of(WandFlag.MAIN_REGEN_DOUBLE_WHEN_EXTRA_EMPTY),
                MAIN_REGEN, 0.70f, EXTRA_POOL, 1.50f, EFFICACY, 1.20f));
        ALL.put("forbidden_hex_wand_core_tidal", WandPartDef.of("forbidden_hex_wand_core_tidal",
                WandPartDef.Kind.CORE, "", "", "", EnumSet.noneOf(WandFlag.class),
                EXTRA_POOL, 0.70f, REGEN_EFF, 1.50f, EXTRA_REGEN_INTERVAL, 3f, EFFICACY, 1.15f));
        ALL.put("forbidden_hex_wand_core_collapse", WandPartDef.of("forbidden_hex_wand_core_collapse",
                WandPartDef.Kind.CORE, "", "", "", EnumSet.noneOf(WandFlag.class),
                DEFENSE_COST, 1.40f, ATTACK_COOLDOWN, 0.70f, THIRD_COOLDOWN, 0.80f, EFFICACY, 1.22f));
        ALL.put("forbidden_hex_wand_core_bulwark", WandPartDef.of("forbidden_hex_wand_core_bulwark",
                WandPartDef.Kind.CORE, "", "", "", EnumSet.noneOf(WandFlag.class),
                ATTACK_COST, 1.40f, DEFENSE_COOLDOWN, 0.70f, THIRD_POWER, 1.10f, EFFICACY, 1.18f));
    }

    private static void core(String id, Object... modPairs) {
        ALL.put(id, WandPartDef.of(id, WandPartDef.Kind.CORE, "", "", "",
                EnumSet.noneOf(WandFlag.class), modPairs));
    }

    private static void specialized(String id, String sourceId, String powerScope, Object... modPairs) {
        ALL.put(id, WandPartDef.of(id, WandPartDef.Kind.CORE, "", sourceId, powerScope,
                EnumSet.noneOf(WandFlag.class), modPairs));
    }

    /** 全表（原稿序） */
    public static Map<String, WandPartDef> all() {
        return Collections.unmodifiableMap(ALL);
    }

    /** 按 id 取定义；未知 id 返回 {@code null} */
    public static WandPartDef get(String id) {
        return ALL.get(id);
    }

    /** 表内条目数（自检断言用，应为 17） */
    public static int size() {
        return ALL.size();
    }
}

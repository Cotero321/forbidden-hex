package com.example.akaishi.forbidden.magic.table;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>墨表</b>：11 种内置墨的静态表（设计记忆 §3.1）。
 *
 * <p><b>⚠ 混墨（{@code 混}）的随机尚未实现</b>：§3.1 定它的消耗倍率在 {@code 0.6~1.5}、
 * 冷却在 {@code 2~6s} 之间随机。当前表里填的是<b>区间中值占位</b>（消耗 1.00 / 冷却 4.0s），
 * 随机化落点在结算层（未排期）。区间常量见 {@link #HUN_COST_MIN} 等。
 *
 * <p><b>识别色是本表新增的一列</b>：设计记忆 §3.1 只有属性与四数值列，没有颜色。
 * 但墨色是<b>跨系统共用色</b>（法阵每层染色、投射物着色、HUD、手册条目），
 * 不在此处锁死，各子系统就会各选一套而"撞脸"（违 RULES §7「不同体系必须可区分」）。
 * 故本轮<b>首轮锁定</b>，**色值待美术阶段复核，改这一处即全系统生效**。
 *
 * <p><b>为什么行序不是设计记忆的书写序</b>：按"解锁梯队"排（§10：Lv1 炽/壤/飓 →
 * Lv2 蔓/蚀 → Lv3 序/混 → Lv4 渊 → Lv5 耀/黯 → Lv6 星），调试输出与手册目录即可同序。
 */
public final class MagicInkTable {

    /** 混墨的消耗倍率随机区间（§3.1；**随机化尚未实现**，当前取中值） */
    public static final float HUN_COST_MIN = 0.60f;
    public static final float HUN_COST_MAX = 1.50f;
    /** 混墨的冷却随机区间（秒 · §3.1；**随机化尚未实现**，当前取中值） */
    public static final float HUN_COOLDOWN_MIN = 2.0f;
    public static final float HUN_COOLDOWN_MAX = 6.0f;

    private static final Map<String, MagicInkDef> BY_ID;
    /** 表序（= 插入序 = 解锁梯队序）：粒子形态等按下标取资源的场合必须用它对齐 */
    private static final List<String> ORDER;

    static {
        Map<String, MagicInkDef> m = new LinkedHashMap<>();

        // ---- Lv1 解锁 ----
        m.put(MagicIds.Ink.CHI, new MagicInkDef(MagicIds.Ink.CHI, "炽", "火 / 爆燃",
                1.30f, 4.0f, 0.4f, 20, 0xFF6A2A, false, "贵 · 快出手 · 中远（爆发）"));
        m.put(MagicIds.Ink.RANG, new MagicInkDef(MagicIds.Ink.RANG, "壤", "大地 / 稳固",
                0.85f, 3.5f, 0.7f, 10, 0x8C6239, false, "便宜 · 短距（自护为主）"));
        m.put(MagicIds.Ink.JU, new MagicInkDef(MagicIds.Ink.JU, "飓", "风 / 动量",
                0.70f, 2.0f, 0.3f, 22, 0x6FD3E8, false, "最便宜 · 最快冷 · 最快出手（位移）"));

        // ---- Lv2 解锁 ----
        m.put(MagicIds.Ink.MAN, new MagicInkDef(MagicIds.Ink.MAN, "蔓", "生长 / 缠绕",
                0.85f, 3.0f, 0.8f, 14, 0x4CAF50, false, "便宜 · 中慢（持续铺场）"));
        m.put(MagicIds.Ink.SHI, new MagicInkDef(MagicIds.Ink.SHI, "蚀", "腐蚀 / 衰败",
                0.75f, 3.0f, 0.8f, 16, 0x7E8C2E, false, "最便宜（持续削弱）"));

        // ---- Lv3 解锁 ----
        m.put(MagicIds.Ink.XU, new MagicInkDef(MagicIds.Ink.XU, "序", "秩序 / 规则",
                1.15f, 4.5f, 1.1f, 28, 0xB9C6D8, false, "贵 · 最慢释放 · 极远（精确控制）"));
        m.put(MagicIds.Ink.HUN, new MagicInkDef(MagicIds.Ink.HUN, "混", "混乱 / 随机",
                1.00f, 4.0f, 0.8f, 18, 0xC64BC6, true,
                "全随机（消耗 ×0.6~1.5、冷却 2~6s）"));

        // ---- Lv4 解锁 ----
        m.put(MagicIds.Ink.YUAN, new MagicInkDef(MagicIds.Ink.YUAN, "渊", "深渊 / 虚空",
                1.20f, 5.5f, 1.0f, 12, 0x1B2740, false, "贵 · 慢 · 短（沉重压制）"));

        // ---- Lv5 解锁 ----
        m.put(MagicIds.Ink.YAO, new MagicInkDef(MagicIds.Ink.YAO, "耀", "光 / 神圣",
                1.10f, 4.0f, 0.9f, 24, 0xFFE07A, false, "中贵 · 远（净化）"));
        m.put(MagicIds.Ink.AN, new MagicInkDef(MagicIds.Ink.AN, "黯", "暗 / 堕落",
                1.05f, 3.5f, 0.6f, 18, 0x5A2070, false, "中 · 快出手（侵蚀）"));

        // ---- Lv6 解锁 ----
        m.put(MagicIds.Ink.XING, new MagicInkDef(MagicIds.Ink.XING, "星", "星 / 星界",
                1.40f, 7.0f, 1.5f, 32, 0x7A6FE8, false, "最贵 · 最长冷 · 最慢 · 最远（独占）"));

        BY_ID = Collections.unmodifiableMap(m);
        ORDER = List.copyOf(m.keySet());
    }

    private MagicInkTable() {
    }

    /** 全表（**按解锁梯队排序**，供自检 / 调试 / 手册目录遍历） */
    public static Map<String, MagicInkDef> all() {
        return BY_ID;
    }

    /** 按 id 取定义；未知 id 返回 {@code null} */
    public static MagicInkDef get(String id) {
        return BY_ID.get(id);
    }

    /** 表内条目数（自检断言用） */
    public static int size() {
        return BY_ID.size();
    }

    /** 表序 id 列表（= 插入序）；粒子 json 的贴图序必须与本序一致 */
    public static List<String> order() {
        return ORDER;
    }

    /** 表序下标；未知 id 返回 {@code -1}（调用端回退到源形态） */
    public static int indexOf(String id) {
        return id == null ? -1 : ORDER.indexOf(id);
    }
}

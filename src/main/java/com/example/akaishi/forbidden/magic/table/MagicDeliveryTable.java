package com.example.akaishi.forbidden.magic.table;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <b>X 表</b>：交付方式的静态表（7 种 · 设计记忆 §19.3 + §21.2）。
 *
 * <p>与 {@link MagicEffectTable} 同为魔法侧的<b>依赖根</b>：词表以 {@code (E, X)} 对定位，
 * 公式读它的四个修正系数，{@code E × X} 合法性校验（§21.4）也以它的 id 集为域。
 *
 * <p><b>系数量级不均是刻意的</b>：X3 引导的消耗系数只有 0.30，因为它<b>按秒</b>计价
 * （{@link MagicDeliveryDef.Pricing#PER_SECOND}），3 秒引导的实付即与一次直射相当；
 * X7 连发同理按发计价。若把它们按"每次"读就会严重误判 —— 所以 {@code pricing} 必须与
 * {@code costMul} 一起读，不能拆开用。
 *
 * <p><b>X 不修改威力 / 持续 / 半径</b>：§21.2 的四列只有消耗 / 冷却 / 释放 / 距离。
 * §21.2 还特意注明 X6 的修正取中性，正是为了"避免与 E 基准里已含的复现收益双重计价"。
 */
public final class MagicDeliveryTable {

    private static final Map<String, MagicDeliveryDef> BY_ID;

    /** 连发每发之间的固定间隔（秒 · §19.3 / §21.5 规则 3：墨不参与） */
    public static final float VOLLEY_INTERVAL_SECONDS = 0.1f;

    static {
        Map<String, MagicDeliveryDef> m = new LinkedHashMap<>();

        // X1 = 基准（全 1.00），是一切比较的零点
        m.put(MagicIds.Delivery.INSTANT, new MagicDeliveryDef(
                MagicIds.Delivery.INSTANT, "直射", "点一下", "同帧判定、0 前摇", "命中顿帧", "干脆",
                1.00f, MagicDeliveryDef.Pricing.PER_CAST, 1.00f, 1.00f, 1.00f, false, ""));

        // 飞行段可被躲 / 可被击落 ⇒ 略降消耗；但射得远、起手更久
        m.put(MagicIds.Delivery.PROJECTILE, new MagicDeliveryDef(
                MagicIds.Delivery.PROJECTILE, "投射", "蓄力 → 放", "飞行 0.5~2s", "轨迹 + 落点", "有期待、可被躲",
                0.95f, MagicDeliveryDef.Pricing.PER_CAST, 0.90f, 1.20f, 1.30f, false,
                "飞行段可被躲 / 可被击落"));

        // 按秒计价 ⇒ 3 秒 ≈ 0.90 单次价；代价是"可被打断"与最慢起手
        m.put(MagicIds.Delivery.CHANNEL, new MagicDeliveryDef(
                MagicIds.Delivery.CHANNEL, "引导", "按住不放", "持续 tick、可被打断", "持续耗能 + 束流音", "沉重、有风险",
                0.30f, MagicDeliveryDef.Pricing.PER_SECOND, 1.10f, 1.40f, 1.00f, true,
                "松手立即停；引导上限 6s（§21.1 暂定值）"));

        // 慢结算 ⇒ 折价
        m.put(MagicIds.Delivery.ATTACH, new MagicDeliveryDef(
                MagicIds.Delivery.ATTACH, "附着", "点一下", "立刻挂载、之后 tick 结算", "挂上那声「叮」", "埋伏、慢热",
                0.85f, MagicDeliveryDef.Pricing.PER_CAST, 1.05f, 1.00f, 1.00f, false, ""));

        // 区域收益（一次覆盖多目标）⇒ 最贵，且必须走近（距离 ×0.85）
        m.put(MagicIds.Delivery.PLACE, new MagicDeliveryDef(
                MagicIds.Delivery.PLACE, "布场", "点一下（选点）", "放置后持续生效", "先落点、后发挥", "布局、控位",
                1.20f, MagicDeliveryDef.Pricing.PER_CAST, 1.15f, 1.10f, 0.85f, false, ""));

        // 复现收益已含在 TEMPORAL 的 E 基准（110）里 ⇒ X 修正取中性，避免双重计价
        m.put(MagicIds.Delivery.ECHO, new MagicDeliveryDef(
                MagicIds.Delivery.ECHO, "回响", "点一下", "延迟 1~3s 复现", "无即时反馈 → 延迟爆发", "悬念、赌",
                1.00f, MagicDeliveryDef.Pricing.PER_CAST, 0.95f, 0.90f, 1.00f, false,
                "`TEMPORAL` 仅允许此 X（§21.4 规则 2）"));

        // 按发计价 ⇒ 3 发 ≈ 1.20 单次价换 3 次命中；代价是冷却 / 前摇最贵
        m.put(MagicIds.Delivery.VOLLEY, new MagicDeliveryDef(
                MagicIds.Delivery.VOLLEY, "连发", "点一下", "N 发序列、间隔 " + VOLLEY_INTERVAL_SECONDS + "s",
                "连发节奏", "爽快、宣泄",
                0.40f, MagicDeliveryDef.Pricing.PER_SHOT, 1.25f, 1.25f, 0.90f, true,
                "首发前摇 = 释放时间；上限 10 发（§21.1 暂定值）；间隔固定，墨不参与"));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private MagicDeliveryTable() {
    }

    /** 全表（插入序固定，供自检 / 调试遍历） */
    public static Map<String, MagicDeliveryDef> all() {
        return BY_ID;
    }

    /** 按 id 取定义；未知 id 返回 {@code null} */
    public static MagicDeliveryDef get(String id) {
        return BY_ID.get(id);
    }

    /** 表内条目数（自检断言用） */
    public static int size() {
        return BY_ID.size();
    }
}

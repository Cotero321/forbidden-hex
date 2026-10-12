package com.example.akaishi.forbidden.wand;

/**
 * 法杖模块的<b>统一修正词条</b>（法杖记忆 §10.1 基数表的代码投影）。
 *
 * <p><b>为什么用枚举 + 数值对，而不是给每段定义一大串字段</b>：68 件模块里
 * 「一段多修正」的组合很碎（"寂"一段就带 4 项），宽记录要么字段爆炸、要么
 * 位置参数易错序。词条化后：定义行是 {@code (WandMod, 数值)…} 变长对，
 * 合成引擎只认词条不看段 —— 加新模块不用改引擎（开闭原则）。
 *
 * <p><b>作用域（scope）</b>：多数词条全局生效；少数按条件生效 ——
 * {@link #INK_COST} 逐词比对墨、{@link #SOURCE_COST}/{@link #SOURCE_COOLDOWN}
 * 整句比对源（定案 §10.3）、{@link #ATTACK_*}/{@link #DEFENSE_*} 比对法术类别、
 * {@link #POWER} 是源偏向杖端的效果威力加成（作用域存模块定义上）。
 */
public enum WandMod {

    /** 全局：魔法消耗系数 */
    COST,
    /** 全局：冷却系数 */
    COOLDOWN,
    /** 全局：施法时间系数 */
    CAST_TIME,
    /** 全局：施法距离系数 */
    RANGE,
    /** 全局：法术半径系数 */
    RADIUS,

    /** 绝对值：加到<b>额外池</b>容量（渊池杖端 +500；不是百分比 —— Owner 2026-10-06 修正落点） */
    EXTRA_POOL_FLAT,
    /** 全局：过热热值消散系数（寂杖端） */
    HEAT_DISSIPATION,
    /** 全局：过热阈值系数（寂杖端，比例乘在 HEAT 阈值上 —— 定案 14） */
    HEAT_THRESHOLD,

    /** 逐词：该墨符文的施法能耗系数（墨偏向杖端 0.85） */
    INK_COST,
    /** 整句：该源消耗系数（源偏向杖端 / 专精杖核） */
    SOURCE_COST,
    /** 整句：该源冷却系数 */
    SOURCE_COOLDOWN,
    /** 整句：部件基础效能系数，影响执行器读取的效果强度，不影响持续时间、范围或传送距离 */
    EFFICACY,
    /** 整句：源限定效果威力系数（源偏向杖端 / 专精杖核） */
    POWER,

    /** 额外池上限系数（杖柄 / 专精与后期杖核；基础容量 = 主池 × 10% —— 定案 §0-9） */
    EXTRA_POOL,
    /** 额外池恢复效率系数（乘每 5 秒的 10 点基准 —— 定案 §0-7） */
    REGEN_EFF,
    /** 主池自然恢复系数（永动核 -30%） */
    MAIN_REGEN,
    /** 额外池恢复间隔（秒；0 = 用全局 5 秒。潮汐核 = 3） */
    EXTRA_REGEN_INTERVAL,
    /** 施法后：额外池恢复该次消耗的系数（循杖柄 0.20） */
    POST_CAST_REFUND,

    /** 攻击类法术：消耗系数（崩解核 1.40 / 壁垒核防御侧对偶） */
    ATTACK_COST,
    /** 防御类法术：消耗系数 */
    DEFENSE_COST,
    /** 攻击类法术：冷却系数 */
    ATTACK_COOLDOWN,
    /** 防御类法术：冷却系数 */
    DEFENSE_COOLDOWN,

    /** 30s 连击第 3 次：冷却再乘（崩解核 0.80 —— 定案 3.4） */
    THIRD_COOLDOWN,
    /** 30s 连击第 3 次（限防御类）：效果乘（壁垒核 1.15） */
    THIRD_POWER;

    /** 词条默认值：系数类 1、绝对/间隔/返还类 0（引擎读缺省词条时用它） */
    public float defaultValue() {
        return switch (this) {
            case EXTRA_POOL_FLAT, EXTRA_REGEN_INTERVAL, POST_CAST_REFUND -> 0f;
            default -> 1f;
        };
    }

    /** 词条是否参与「先乘后夹取」（绝对值词条不夹取 —— 定案 §10.2-2） */
    public boolean multiplicative() {
        return this != EXTRA_POOL_FLAT && this != EXTRA_REGEN_INTERVAL && this != POST_CAST_REFUND;
    }
}

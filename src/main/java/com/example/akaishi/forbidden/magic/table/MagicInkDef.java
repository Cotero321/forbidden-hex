package com.example.akaishi.forbidden.magic.table;

/**
 * 魔法墨的静态定义（一行 = 一种墨 · 设计记忆 §3.1）。
 *
 * <p>墨在此有<b>两个身份</b>：既是一件物品（11 件，镶嵌消耗用），也是数值 profile
 * （消耗倍率 / 冷却 / 释放时间 / 距离）。本定义只描述后者 —— 物品引用由 P3 的镶嵌层承担。
 *
 * @param attribute 属性语义（火 / 爆燃 等），同时是覆盖墨「局部属性」的判定依据（§3.2）
 * @param costMul   消耗倍率（与 §8.5 连乘）
 * @param cooldown  冷却基准（秒）
 * @param castTime  释放时间基准（秒；即 X1 口径的前摇）
 * @param range     距离基准（格；**规模修正不放大**，§8.4）
 * @param rgb       识别色（0xRRGGBB）—— 法阵每层染色、投射物着色、HUD 共用，故必须唯一
 * @param randomized 是否随机墨（混）：数值取 {@link MagicInkTable} 的区间常量而非固定值
 * @param feel      手感画像（调试 / 手册用）
 */
public record MagicInkDef(
        String id,
        String label,
        String attribute,
        float costMul,
        float cooldown,
        float castTime,
        int range,
        int rgb,
        boolean randomized,
        String feel) {

    /** 是否为「星墨」（独占：不可与其他墨同句，故星句 K 恒 0 —— §3.1 / §21.3） */
    public boolean exclusive() {
        return MagicIds.Ink.XING.equals(id);
    }
}

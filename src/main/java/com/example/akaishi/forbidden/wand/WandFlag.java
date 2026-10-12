package com.example.akaishi.forbidden.wand;

/**
 * 法杖模块的<b>开关型机制</b>（数值词条表达不了的"行为改变"）。
 *
 * <p><b>为什么单独一个枚举而不是塞进 {@link WandMod}</b>：这些不是"乘在某个基数上"的数值，
 * 而是改变结算流程的开关（蓄=恢复改道、永动=条件翻倍）。数值走词条、行为走旗标，两类互不污染。
 */
public enum WandFlag {

    /** 蓄杖柄：主池满时，自然恢复的池量自动流入额外池（定案 §10.4） */
    OVERFLOW_TO_EXTRA,
    /** 永动核：额外池耗尽时，主池自然恢复速度翻倍（定案 §4.3） */
    MAIN_REGEN_DOUBLE_WHEN_EXTRA_EMPTY
}

package com.example.akaishi.forbidden.codex;

/**
 * 禁忌秘典的「知识三级」：简单的想法 / 疯狂的想法 / 禁忌的知识。
 *
 * <p><b>为什么是三级而不是权重表</b>：秘典的分类同时承担两个职责 —— 界面的页签分组，
 * 以及"读到哪一层该付出什么代价"的叙事分级。三级写成枚举后，页签顺序 =
 * {@code values()} 顺序（同一个真源），新增一级只需在枚举里追加，界面与语言键自动跟随。
 *
 * <p>语言键由枚举名派生，禁止在界面里硬编码分类名。
 */
public enum CodexTier {

    /** 简单的想法：任何人都能读懂的念头 */
    SIMPLE("simple"),
    /** 疯狂的想法：读下去会开始怀疑自己 */
    MAD("mad"),
    /** 禁忌的知识：这一层的东西本来不该被人知道 */
    FORBIDDEN("forbidden");

    private final String key;

    CodexTier(String key) {
        this.key = key;
    }

    /** 分类 id（用于语言键与数据标识） */
    public String id() {
        return key;
    }

    /** 分类显示名语言键 */
    public String nameKey() {
        return "codex.forbidden_hex.tier." + key;
    }
}

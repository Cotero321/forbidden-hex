package com.example.akaishi.forbidden.api.magic;

/**
 * 玩家魔法状态的<b>只读快照</b>（不可变）。
 *
 * <p>每次调用都会返回一个新的、与状态脱钩的实例 —— 调用方拿到的值不会随后续变化而改变，
 * 因此可以安全地跨线程读取（渲染线程读它不会有并发问题）。
 *
 * <p><b>为什么不用实现层的枚举表示源</b>：见 {@code package-info}；源以 id 字符串给出，
 * 调用方若需要显示名或识别色，走 {@link IMagicService} 的查询方法。
 *
 * <p><b>为什么不含 SAN</b>：理智值属理智系统的契约（{@code api.sanity}），
 * 魔法侧只在扣费时调用它；把它复制进本快照会产生"两个真源"。
 *
 * @param level        魔法师等级（1~6）
 * @param wordLimit    当前等级允许的句内词数上限（1 / 2 / 3 / 5 / 7 / 8）
 * @param poolLife     生命池（绿源）
 * @param poolDark     暗魔池（黑源）
 * @param poolWhite    白魔池（白源）
 * @param poolCapacity 当前等级的池容量上限（1000 + 200 × (level − 1)）
 * @param heat         过载热值（0~100）
 * @param overloaded   是否正处于过载罚站（权威值；<b>不要用 {@code heat >= 100} 代替</b>，
 *                     罚站期间热值会被降到 100 以下）
 * @param sourceId     当前源 id
 */
public record MagicValues(
        int level,
        int wordLimit,
        float poolLife,
        float poolDark,
        float poolWhite,
        float poolCapacity,
        float heat,
        boolean overloaded,
        String sourceId) {

    /** 三池索引总数（与 {@link #pool(int)} 的取值范围一致） */
    public static final int POOL_COUNT = 3;

    /**
     * 等级 → 句内词数上限（设计记忆 §10，v10 修订：<b>1 / 2 / 3 / 5 / 7 / 8</b>）。
     *
     * <p><b>为什么这条规则住在 api 而不是实现</b>：它是对外可见的**规则**（界面要据此限制输入、
     * 附属要据此做 UI），若放在实现层，api 的兜底实现就得反向引用实现类 ——
     * 那就违背了"api 不依赖实现"。放在这里，实现层反过来引用它，依赖方向才是对的。
     *
     * <p>越界等级按端点夹取（不抛异常）：调用方可能是老档或附属传错值。
     */
    public static int wordLimitForLevel(int level) {
        int[] table = { 1, 2, 3, 5, 7, 8 };
        int index = Math.max(0, Math.min(table.length - 1, level - 1));
        return table[index];
    }

    /**
     * 魔法服务未安装时的兜底快照（Lv1、三池与热值全 0、默认源）。
     *
     * <p>与 {@code SanityValues.ZERO} 同一形态：门面在模块缺席时返回它，
     * 保证 {@code values(player)} <b>永不返回 null</b>，附属的读代码不会 NPE，
     * 只会读到"这个玩家没有魔力"。
     */
    public static final MagicValues ABSENT = new MagicValues(
            1, 1, 0f, 0f, 0f, 0f, 0f, false, "forbidden_hex:life");

    /** 按索引读三池（0 = 生命 / 1 = 黑暗 / 2 = 白色）；越界返回 0 */
    public float pool(int index) {
        return switch (index) {
            case 0 -> poolLife;
            case 1 -> poolDark;
            case 2 -> poolWhite;
            default -> 0f;
        };
    }
}

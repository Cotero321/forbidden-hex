package com.example.akaishi.forbidden.magic.exec.effect;

/**
 * 效果执行器族的<b>注册入口</b>（设计记忆 §19.5 / §19.2 阶段③）。
 *
 * <p>19 条 E 按<b>语义家族</b>分成五个文件，而不是 19 个平铺的小类 ——
 * 同一族的执行器共享"换算口径"（例如"高血量目标需 P ≥ 1.3"、"每级 4 HP"），
 * 放在一起才能在改口径时一眼看全。文件粒度按"族"而不是"条"，是刻意的。
 *
 * <p><b>尚未注册的 5 条</b>（由 {@code MagicExecBuiltins.DEFERRED_EFFECTS} 显式列出）：
 * {@code FIELD} / {@code SUMMON} / {@code VESSEL} / {@code LINK} / {@code TEMPORAL} ——
 * 它们都需要<b>世界里的载体</b>（场地实体 / 召唤物 / 承载物 / 连接关系 / 因果绑定），
 * 不是"用一个原版效果顶上"能糊过去的。
 */
public final class MagicExecutors {

    private MagicExecutors() {
    }

    /** 注册全部已实现的执行器 */
    public static void register() {
        DamageExecutors.register();
        BreakExecutors.register();
        MotionExecutors.register();
        StatusExecutors.register();
        MindExecutors.register();
        LinkExecutors.register();
        FieldExecutors.register();
    }
}

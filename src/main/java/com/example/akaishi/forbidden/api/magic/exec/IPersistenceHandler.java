package com.example.akaishi.forbidden.api.magic.exec;

/**
 * 阶段 ④ 持续与二次负载（设计记忆 §19.2 / §19.6）。
 *
 * <p>由<b>结构段（C 组）</b>的词驱动：脉冲 / 衰减 / 衍生 / 场地残留 / 环绕。
 * 与 {@link IDeliverer} 的分工 ——
 * 投送决定"起手几段怎么送"，<b>持续决定"送完之后还留着什么"</b>。前者是一次性时序，
 * 后者是"施法者已经不用管了、它自己还在跑"的那部分。
 *
 * <p><b>谁来调 {@link #tick}</b>：施法状态机（逐 tick 推进，§21.1）。
 * 因此本接口<b>不</b>自己开 ticker、不自己注册事件 —— 只实现"推进一格"的纯逻辑，
 * 这样它的正确性可在无世界环境下逐格验证。
 *
 * <p><b>与取消的约定</b>：返回 {@code false} 表示本持续已结束，状态机停止再调它；
 * 若玩家中途死亡 / 换维度 / 主动打断，状态机直接停止调用（不再回调本接口）。
 */
public interface IPersistenceHandler {

    /** 本处理器负责的结构词 id（注册表按它索引） */
    String structureId();

    /**
     * 推进一格（每秒或每 tick，由状态机决定节拍）。
     *
     * @param context      施法上下文
     * @param elapsedTicks 自本持续开始以来的游戏刻（从 0 起算，便于按时间衰减）
     * @param refire       需要"再来一次本段效果"时调用它（脉冲 / 复现）
     * @return {@code true} = 还需继续；{@code false} = 本持续结束
     */
    boolean tick(MagicContext context, int elapsedTicks, MagicDeliveryAction refire);
}

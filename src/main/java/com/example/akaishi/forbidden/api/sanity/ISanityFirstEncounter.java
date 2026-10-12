package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

/**
 * 首见事件：玩家第一次接触某事物时的一次性理智反馈（上限/认知变化）。
 *
 * <p><b>为什么是"注册条目 + 核心记档"而不是"附属自己记档"</b>：
 * 首见的本质是"一次性"，需要一份与玩家绑定的已触发记录。如果让每个附属自己存，
 * 就会出现：卸载附属后记录丢失、玩家再接触时又触发一次（可刷）；或者附属把状态塞进玩家 NBT 里互不兼容。
 * 统一由核心按 id 记档，附属只声明"什么算首见、首见给什么"。
 *
 * <p><b>存档容错承诺（给内部实现层的硬约束）</b>：
 * <ul>
 *   <li>{@link #id()} 会被写进玩家存档（已触发集合）；</li>
 *   <li>载入存档时遇到<b>当前未注册</b>的 id（附属被卸载 / 改 id / 版本回退），
 *       必须<b>原样保留</b>该 id —— 既不清档、不删除、不报警告刷屏，也不得因无法解析而抛异常中断登录；</li>
 *   <li>同理，已注册但存档里没有的条目，按"尚未触发"处理即可；</li>
 *   <li>换言之：存档格式必须允许多余的未知 id 长期驻留，后续该附属重新装回时能自然接续。</li>
 * </ul>
 *
 * <p>触发路径有两条，互为补充：
 * <ol>
 *   <li><b>声明式</b>：核心每次环境结算调用 {@link #test(SanityContext)}，命中且未触发过则结算；</li>
 *   <li><b>程序化</b>：附属在自己的事件里调 {@link ISanityService#reportFirstEncounter}，
 *       适合无法被环境轮询表达的首见（自定义交互、击杀、完成任务等）。</li>
 * </ol>
 */
public interface ISanityFirstEncounter {

    /** 条目 id，必须带自己的命名空间。该 id 会进玩家存档，<b>一旦发布不得再改</b>（改了等于全员重触发） */
    ResourceLocation id();

    /**
     * 声明式判定：该玩家此刻是否"接触到了"本条目内容（不判定是否首次，首次由核心查档）。
     *
     * <p>只读上下文、禁止副作用；抛异常由结算层隔离（本次视为未命中）。
     */
    boolean test(SanityContext ctx);

    /** 触发时对理智上限的一次性增减（正=增益） */
    float sancDelta();

    /** 触发时对认知值的一次性增减（正=增益） */
    float cogDelta();

    /** 双语 lang 键（例如 {@code message.forbidden_hex.first_encounter.xxx}），用于首见提示；不得留空，否则注册被拒 */
    String langKey();
}

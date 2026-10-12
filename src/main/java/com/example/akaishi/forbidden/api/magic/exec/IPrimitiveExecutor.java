package com.example.akaishi.forbidden.api.magic.exec;

import net.minecraft.world.entity.Entity;

/**
 * 阶段 ③ 效果执行（设计记忆 §19.2 / §19.5）。
 *
 * <p>一行 = 一条 E（**20 条**，见 §19.5：19 条拆自 §8.2 的 20 原语 + v10 新增 {@code WARD}）。
 * 执行器是<b>唯一被允许真正改变实体状态</b>的地方（掉血 / 上效果 / 位移…），
 * 前三阶段与后一阶段都只做"选人、择时、计时"。
 *
 * <p><b>只做单体</b>：编排器负责遍历目标集合，执行器只需处理"<b>一个</b>目标"。
 * 这样多目标语义（上限 8、链跳去重）与效果语义彻底分开 ——
 * 想给某条 E 加"对召唤物无效"，不必碰目标逻辑。
 *
 * <p><b>幂等与容错</b>：执行器应容忍"目标已死 / 已移除"，
 * 且<b>不得抛出异常</b>（单个目标的失败必须不中断同一批其余目标）。
 */
public interface IPrimitiveExecutor {

    /** 本执行器负责的效果语义 E id（注册表按它索引） */
    String effectId();

    /**
     * 对<b>单个</b>目标施加本效果。
     *
     * @param context 施法上下文（威力 / 持续 / 半径 / 源 / 词 都在这里）
     * @param target  这一个目标；<b>唯一的例外</b>是 {@link #worksWithoutTarget()} 为真且
     *                目标集合为空时（见该方法），此时传 {@code null} ——
     *                实现一律用 {@code instanceof} 取值，天然可容忍
     */
    void apply(MagicContext context, Entity target);

    default boolean applyResult(MagicContext context, Entity target) {
        apply(context, target);
        return false; // 未声明实际成功的旧执行器不触发命中反馈。
    }

    /**
     * 本效果能否在<b>没有任何实体目标</b>时照常执行。
     *
     * <p><b>为什么需要它</b>：对物类效果（开锁 / 破方块）本来就常常"眼前没有实体"，
     * 而管线的默认行为是"目标集合为空 ⇒ 不调用执行器" —— 那会让这类效果<b>永远打不到方块</b>。
     * 声明为 {@code true} 后，管线会在目标集合为空时<b>补调一次</b>（{@code target} 传 {@code null}），
     * 由实现自己按 {@code context} 的原点与视线去取方块。
     *
     * <p>默认 {@code false}：绝大多数效果必须有目标，凭空调用只会产生"打到空气"的怪现象。
     */
    default boolean worksWithoutTarget() {
        return false;
    }

    /**
     * 本效果是否需要<b>一次看到整批目标</b>。
     *
     * <p><b>为什么需要它</b>：绝大多数效果是"逐个作用于每个目标"（掉血 / 上状态），
     * 管线逐目标调用 {@link #apply} 即可，且逐目标的异常隔离天然可用。
     * 但有一类效果<b>天生是多目标语义</b> —— {@code LINK} 要把目标<b>串成一条链</b>
     * （§4.2 丝，闪电链式传导），逐个调用时它根本看不到"链上还有谁"。
     *
     * <p>返回 {@code true} 后，管线改为<b>一次性</b>调用 {@link #applyAll}，
     * 不再调用 {@link #apply}；此时逐目标的异常隔离由实现自己负责（整批失败由管线记录）。
     *
     * <p>默认 {@code false}：保持"逐目标 + 单点容错"这条对绝大多数执行器更安全的路径。
     */
    default boolean handlesWholeTargetSet() {
        return false;
    }

    /**
     * 对<b>整批</b>目标施加效果（仅当 {@link #handlesWholeTargetSet()} 为真时会被调用）。
     *
     * @param targets 本段解析出的全部目标（已过滤，非 null，非空）
     */
    default int applyAllResult(MagicContext context, java.util.List<Entity> targets) {
        applyAll(context, targets);
        return 0;
    }

    default void applyAll(MagicContext context, java.util.List<Entity> targets) {
        // 默认不做事：只有声明 handlesWholeTargetSet 的实现才需要它
    }
}

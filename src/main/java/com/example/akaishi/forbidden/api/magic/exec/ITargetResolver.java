package com.example.akaishi.forbidden.api.magic.exec;

import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * 阶段 ① 目标解析（设计记忆 §19.2 / §36.1）。
 *
 * <p><b>职责边界</b>：只负责"圈出哪些实体"，<b>不</b>负责造成任何效果 ——
 * 效果是 {@link IPrimitiveExecutor} 的事。一个解析器错了只会"打错人"，
 * 不会"打错伤害"，这让两类 bug 可分离定位。
 *
 * <p>实现须自行落实 §36.1 的五条细则：友伤极性、PVP 开关、上限截断（近距离优先）、
 * 视线遮挡、隐匿豁免。{@link MagicTargetQuery} 已经把"是否要求视线 / 是否含隐匿 / PVP 开关"
 * 作为<b>入参</b>交给实现，实现只需照参数执行，不要自行写死。
 *
 * <p>返回<b>不得为 null</b>；无目标时返回空列表。
 */
public interface ITargetResolver {

    /** 本解析器负责的形状 G（注册表按它索引，一个形状至多一个解析器） */
    MagicShape shape();

    /**
     * 解析目标集合。
     *
     * @param context 施法上下文（含施法者、原点、词与数值）
     * @param query   本次解析的形状与过滤参数
     * @return 已过滤并截断的目标；不稳定顺序不影响正确性，但<b>近距离优先</b>便于省略时的截断可预期
     */
    List<Entity> resolve(MagicContext context, MagicTargetQuery query);
}

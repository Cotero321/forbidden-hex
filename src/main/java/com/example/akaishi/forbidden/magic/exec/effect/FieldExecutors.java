package com.example.akaishi.forbidden.magic.exec.effect;

import com.example.akaishi.forbidden.api.magic.exec.IPrimitiveExecutor;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import net.minecraft.world.entity.Entity;

/**
 * <b>场域族</b>（设计记忆 §19.5 {@code FIELD}：「区域持续作用」）。
 *
 * <p><b>口径</b>：{@code FIELD} 与 {@code DAMAGE} 共用同一段结算体
 * （见 {@link ExecSupport#dealDamage}）—— 作为原语它们都是"掉血"。
 * 两者的差别<b>不在执行器</b>：
 * <ul>
 *   <li><b>形状</b>：{@code FIELD} 是区域（G = 球形 / 区域），{@code DAMAGE} 多为单体 —— 由阶段①承担；</li>
 *   <li><b>节奏</b>：{@code FIELD} 天生是<b>每秒一次</b>的（由 X5 的法阵每秒重放，
 *       或 X3 引导的逐段结算驱动），故它的量化单位是 {@code HP_PER_SEC}；</li>
 *   <li>而这正是为什么 {@code FIELD} 单独成族：<b>它离开"每秒重放"就没有意义</b>，
 *       而那段"每秒重放"的责任在法阵实体（§36.2），不在它自己。</li>
 * </ul>
 */
public final class FieldExecutors {

    private FieldExecutors() {
    }

    /** 注册本族 */
    public static void register() {
        MagicExecRegistry.register(new Field());
    }

    /** {@code FIELD}：区域持续作用（威力轴单位 = HP_PER_SEC） */
    static final class Field implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.FIELD;
        }

        @Override
        public void apply(MagicContext context, Entity target) {
            ExecSupport.dealDamage(context, target);
        }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            return ExecSupport.dealDamage(context, target);
        }
    }
}

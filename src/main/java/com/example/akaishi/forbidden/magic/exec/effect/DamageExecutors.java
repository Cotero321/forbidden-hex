package com.example.akaishi.forbidden.magic.exec.effect;

import com.example.akaishi.forbidden.api.magic.exec.IPrimitiveExecutor;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import net.minecraft.world.entity.Entity;

/**
 * <b>伤害族</b>（设计记忆 §19.5）。
 *
 * <p>{@code DAMAGE} 是 §19.5 里"合并"的产物：原 §8.2 的 1/2/3/4 号（直射 / 引导 / 附着 / 连锁）
 * 效果相同、差别全在交付方式，故这里<b>只有一条实现</b> ——
 * 同一个执行器被 X1 调一次、被 X3 每秒调一次、被 X7 每 0.1s 调一次，手感差异全由状态机给出。
 *
 * <p>结算体与 {@code FIELD} 共用 {@link ExecSupport#dealDamage}（见该方法的注释）。
 */
public final class DamageExecutors {

    private DamageExecutors() {
    }

    /** 注册本族（由 {@code MagicExecutors} 调用） */
    public static void register() {
        MagicExecRegistry.register(new Damage());
    }

    /** {@code DAMAGE}：造成伤害（威力轴 = HP） */
    static final class Damage implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.DAMAGE;
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

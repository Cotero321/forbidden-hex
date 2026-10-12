package com.example.akaishi.forbidden.magic.exec.effect;

import com.example.akaishi.forbidden.api.magic.exec.IPrimitiveExecutor;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * <b>状态族</b>（设计记忆 §19.5）：{@code BIND} / {@code WEAKEN} / {@code BOOST} / {@code WARD}。
 *
 * <p>四条 E 都用<b>原版状态效果</b>承载（§11 明许："能用原版的用原版"）：
 * 束缚 → 缓慢、失真 → 虚弱、隐匿 → 隐身。等级由 P 倍数推（{@link ExecSupport#levelFromRatio}：
 * 每翻一倍升一级），持续由持续轴推（{@link ExecSupport#durationTicks}）。
 *
 * <p><b>缺自定义效果造成的三处打折</b>（§11 的 4 个 MobEffect 尚未注册）：
 * <ul>
 *   <li>「跳禁」—— 1.20.1 无法用原版效果<b>取消</b>跳跃（{@code MobEffects.JUMP} 只加不减），
 *       故 {@code BIND} 只能靠缓慢逼近"走不动"，跳仍可跳；</li>
 *   <li>「封印」—— {@code BREAK_UNLOCK} 的"解法术封印"没有目标可解；</li>
 *   <li>「破绽」—— {@code WEAKEN} 无法叠加"受击伤害提高"这一层（原版虚弱只降攻击力）。</li>
 * </ul>
 * 这三条要等自定义 MobEffect 落地（需注册 + 材质 + 双语，属 RULES §7「先图后码」）。
 */
public final class StatusExecutors {

    /** 禁锢用的缓慢等级下界（IV 级 ≈ −60% 速度） */
    private static final int BIND_BASE_AMPLIFIER = 3;

    /** 禁锢用的缓慢等级上界（VI 级 ≈ −90% 速度，是"走不动"的实用极限） */
    private static final int BIND_MAX_AMPLIFIER = 5;

    /** 吸收效果每级提供的吸收量（原版固定 4 HP）—— {@code WARD} 的 HP→等级换算用 */
    private static final float ABSORPTION_HP_PER_LEVEL = 4f;

    private StatusExecutors() {
    }

    /** 注册本族 */
    public static void register() {
        MagicExecRegistry.register(new Bind());
        MagicExecRegistry.register(new Weaken());
        MagicExecRegistry.register(new Boost());
        MagicExecRegistry.register(new Ward());
    }

    /**
     * {@code BIND}：禁锢（§4.1 钉 / 蛇；§19.5 v10 起「印」并入）。
     *
     * <p>用缓慢逼近"禁止移动"：P 越大等级越高（IV ~ VI）。
     * <b>跳跃无法禁止</b>（见类注释），这是本轮已知的打折点。
     *
     * <p><b>P 门槛</b>：§8.2 写"Boss（>100 HP）需 P ≥ 1.3"。
     */
    static final class Bind implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.BIND;
        }

        @Override
        public void apply(MagicContext context, Entity target) { applyResult(context, target); }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)
                    || !ExecSupport.meetsHighHealthGate(context, effectId(), living)) {
                return false;
            }
            int amplifier = Math.min(BIND_MAX_AMPLIFIER,
                    BIND_BASE_AMPLIFIER + ExecSupport.levelFromRatio(ExecSupport.pRatio(context, effectId())));
            return ExecSupport.apply(living, MobEffects.MOVEMENT_SLOWDOWN, amplifier,
                    ExecSupport.durationTicks(context), context.caster());
        }
    }

    /**
     * {@code WEAKEN}：削弱属性 / 放大弱点（§4.1 曲 / 隙 / 斜 / 顶）。
     *
     * <p>虚弱降攻击力 + 挖掘疲劳降效率，对应"−20% 属性"的两种读法。
     */
    static final class Weaken implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.WEAKEN;
        }

        @Override
        public void apply(MagicContext context, Entity target) { applyResult(context, target); }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)) return false;
            int amplifier = ExecSupport.levelFromRatio(ExecSupport.pRatio(context, effectId()));
            int ticks = ExecSupport.durationTicks(context);
            boolean weakness = ExecSupport.apply(living, MobEffects.WEAKNESS, amplifier, ticks, context.caster());
            boolean fatigue = ExecSupport.apply(living, MobEffects.DIG_SLOWDOWN, amplifier, ticks, context.caster());
            return weakness || fatigue;
        }
    }

    /**
     * {@code BOOST}：增幅 / 治疗（§4.1 峰；§8.2「+20% 或治疗 6 HP」）。
     *
     * <p>"或"在本实现里取<b>同时</b>：力量对应"增幅"，生命恢复对应"治疗"。
     * 理由：两者都是正向、不冲突，且分开判"该给哪个"需要读目标血线，
     * 那会让同一个法术在不同目标上变成两种效果 —— 玩家难以预期。
     */
    static final class Boost implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.BOOST;
        }

        @Override
        public void apply(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)) {
                return;
            }
            applyResult(context, target);
        }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)
                    || !com.example.akaishi.forbidden.magic.exec.resolve.MagicResolvers.allied(context.caster(), target)) return false;
            int amplifier = ExecSupport.levelFromRatio(ExecSupport.pRatio(context, effectId()));
            int ticks = ExecSupport.durationTicks(context);
            boolean boost = ExecSupport.apply(living, MobEffects.DAMAGE_BOOST, amplifier, ticks, context.caster());
            boolean regen = ExecSupport.apply(living, MobEffects.REGENERATION, amplifier, ticks, context.caster());
            return boost || regen;
        }
    }

    /**
     * {@code WARD}：护盾 / 吸收伤害（v10 新增，无历史原语）。
     *
     * <p>用原版吸收：每级 4 HP，故等级 = {@code round(威力 ÷ 4) − 1}。
     * §8.2 给的基准是"吸收 6 HP"，落在 4 与 8 之间 —— 取整到 8 是首轮妥协（待标定）。
     */
    static final class Ward implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.WARD;
        }

        @Override
        public void apply(MagicContext context, Entity target) {
            applyResult(context, target);
        }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)
                    || !com.example.akaishi.forbidden.magic.exec.resolve.MagicResolvers.allied(context.caster(), target)) return false;
            float hp = ExecSupport.power(context);
            if (hp <= 0f) return false;
            int amplifier = Math.max(0, Math.min(ExecSupport.MAX_AMPLIFIER,
                    Math.round(hp / ABSORPTION_HP_PER_LEVEL) - 1));
            float before = living.getAbsorptionAmount();
            boolean applied = ExecSupport.apply(living, MobEffects.ABSORPTION, amplifier,
                    ExecSupport.durationTicks(context), context.caster());
            if (applied) living.setAbsorptionAmount(Math.max(before, hp));
            return applied;
        }
    }
}

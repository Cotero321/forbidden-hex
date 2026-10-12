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
 * <b>心智与感知族</b>（设计记忆 §19.5）：{@code MIND} / {@code SENSE} / {@code CONCEAL}。
 *
 * <p>三条 E 都在"信息"上做文章：让目标变糊涂、让目标被发现、让目标看不见。
 * 共同点是<b>都与血量无关</b> —— 全是 {@code TIER} / {@code FACTOR} 单位，
 * 量化值走 {@code magnitude}（P4 的量化轴），不走威力轴。
 */
public final class MindExecutors {

    /** {@code MIND} 的高档门槛：§8.2「P < 1.3 低档 / P ≥ 1.3 高档（失控）」 */
    private static final float MIND_HIGH_TIER_RATIO = 1.3f;

    /** {@code SENSE} 的档位下限：§8.2「P 0.8 方位 / 1.0 轮廓 / 1.2+ 详情」 */
    private static final float SENSE_TIER_DETAIL_RATIO = 1.2f;

    /** 低档感知的持续倍率（"只知方位"比"看得见轮廓"短一半） */
    private static final float SENSE_LOW_TIER_DURATION_MULTIPLIER = 0.5f;

    private MindExecutors() {
    }

    /** 注册本族 */
    public static void register() {
        MagicExecRegistry.register(new Mind());
        MagicExecRegistry.register(new Sense());
        MagicExecRegistry.register(new Conceal());
    }

    /**
     * {@code MIND}：心智（疯狂 / 认知偏离）（§4.4 偏）。
     *
     * <p>两档（§8.2 的 {@code TIERED}）：
     * <ul>
     *   <li><b>低档</b>（P &lt; 1.3）：反胃 —— 视野扭曲 + 操作方向错乱；</li>
     *   <li><b>高档</b>（P ≥ 1.3）：追加黑暗 —— 直接剥夺视野，对应"失控"。</li>
     * </ul>
     * <b>未覆盖</b>：§11 的「疯狂」自定义效果（真正的失控行为）尚未注册，故高档止步于"夺视野"。
     */
    static final class Mind implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.MIND;
        }

        @Override
        public void apply(MagicContext context, Entity target) { applyResult(context, target); }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)) return false;
            int ticks = ExecSupport.durationTicks(context);
            boolean changed = ExecSupport.apply(living, MobEffects.CONFUSION, 0, ticks, context.caster());
            if (ExecSupport.pRatio(context, effectId()) >= MIND_HIGH_TIER_RATIO) {
                changed |= ExecSupport.apply(living, MobEffects.DARKNESS, 0, ticks, context.caster());
            }
            return changed;
        }
    }

    /**
     * {@code SENSE}：感知 / 暴露（§4.1 标 / 旗；§4.2 迹）。
     *
     * <p>用原版发光承载 —— 它恰好就是"穿墙可见的轮廓"，与三种档位的差异只是<b>持续多久</b>：
     * 低档（P &lt; 1.2）只给一半时长（"勉强知道方位"），高档给满（"看清详情"）。
     *
     * <p><b>待补</b>：§8.2 的三档还区分"方位 / 轮廓 / 详情"，那需要 HUD 上的额外显示层
     * （方位箭头与详情文本），属界面部分，全系统统一后做。
     */
    static final class Sense implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.SENSE;
        }

        @Override
        public void apply(MagicContext context, Entity target) { applyResult(context, target); }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)) return false;
            int ticks = ExecSupport.durationTicks(context);
            if (ExecSupport.pRatio(context, effectId()) < SENSE_TIER_DETAIL_RATIO) {
                ticks = Math.max(ExecSupport.MIN_EFFECT_TICKS,
                        Math.round(ticks * SENSE_LOW_TIER_DURATION_MULTIPLIER));
            }
            return ExecSupport.apply(living, MobEffects.GLOWING, 0, ticks, context.caster());
        }
    }

    /**
     * {@code CONCEAL}：隐匿（§4.4 角）。
     *
     * <p>用原版隐身。§8.2 的量化规则是"被察觉距离 = 16 ÷ P" ——
     * 那是<b>检测侧</b>的规则（别的东西来找你时要按这个半径判定），
     * 本执行器只负责把"隐身"挂上；检测侧的收窄要与 §36.1 细则 5 的隐匿判据合并处理。
     */
    static final class Conceal implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.CONCEAL;
        }

        @Override
        public void apply(MagicContext context, Entity target) { applyResult(context, target); }

        @Override
        public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)) return false;
            return ExecSupport.apply(living, MobEffects.INVISIBILITY, 0,
                    ExecSupport.durationTicks(context), context.caster());
        }
    }
}

package com.example.akaishi.forbidden.magic.exec.effect;

import com.example.akaishi.forbidden.api.magic.exec.IPrimitiveExecutor;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.link.MagicLinks;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>连接族</b>（设计记忆 §4.2 丝 / 根；§19.5 {@code LINK}）。
 *
 * <p><b>口径（Owner 裁定：类似闪电链）</b>：本次解析出的目标<b>串成一条链</b>，
 * 链上伤害<b>一环一环跳</b>过去 —— 不是"全部同时吃"，也不是"施法者↔某一个"。
 * 机制与生命周期见 {@link MagicLinks}。
 *
 * <p>这也解释了为什么本执行器必须声明 {@link IPrimitiveExecutor#handlesWholeTargetSet()}：
 * "建链"这件事<b>天生需要一次看到全部目标</b>，逐目标调用时它看不到"链上还有谁"。
 */
public final class LinkExecutors {

    private LinkExecutors() {
    }

    /** 注册本族 */
    public static void register() {
        MagicExecRegistry.register(new Link());
    }

    /** {@code LINK}：连接 / 传导 —— 闪电链式（§8.2 的量化单位是 {@code HP_PER_SEC}） */
    static final class Link implements IPrimitiveExecutor {

        @Override
        public String effectId() {
            return MagicIds.Effect.LINK;
        }

        @Override
        public boolean handlesWholeTargetSet() {
            return true;
        }

        /**
         * 本效果不参与"逐目标"路径 —— 建链必须看整批。
         * 管线在 {@link #handlesWholeTargetSet()} 为真时只调 {@link #applyAll}，故此方法不会被调用；
         * 保留一个空实现是为了不给出"单目标也能用"的错误暗示。
         */
        @Override
        public void apply(MagicContext context, Entity target) {
            // 见方法注释：整批路径由 applyAll 负责
        }

        @Override
        public void applyAll(MagicContext context, List<Entity> targets) {
            applyAllResult(context, targets);
        }

        @Override
        public int applyAllResult(MagicContext context, List<Entity> targets) {
            if (!(context.level() instanceof ServerLevel level)) {
                return 0;
            }
            // 链是"目标之间"的连接（闪电链），施法者不入链 —— 否则一次单体施法就变成"连自己"
            List<Entity> ring = new ArrayList<>(targets.size());
            for (Entity target : targets) {
                if (target != null && target.isAlive() && target != context.caster()) {
                    ring.add(target);
                }
            }
            // §4.2「连接两方」：少于两环就没有链可言
            if (ring.size() < MagicLinks.MIN_RINGS) {
                return 0;
            }
            // 每跳伤害取威力轴（LINK 的单位是 HP_PER_SEC ⇒ 即"每秒传导多少 HP"）
            // ★v20：LINK 每跳附加主伤害基准的 10%（见 ExecSupport.primaryDamageBonus）
            float damagePerPulse = ExecSupport.power(context) + ExecSupport.primaryDamageBonus();
            return MagicLinks.create(level, ring, damagePerPulse,
                    ExecSupport.durationTicks(context), context.caster(), SpellVisual.from(context));
        }
    }
}

package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.api.magic.SpellSnapshot;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import com.example.akaishi.forbidden.magic.exec.MagicExecBuiltins;
import com.example.akaishi.forbidden.magic.exec.MagicPipeline;
import com.example.akaishi.forbidden.magic.exec.MagicTargeting;
import com.example.akaishi.forbidden.magic.table.MagicDeliveryTable;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.example.akaishi.forbidden.magic.visual.SpellVisualSpecs;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行层骨架的<b>可执行不变量</b>（设计记忆 §19.2 / §21.1 / §21.6 / §36.1）。
 *
 * <p><b>为什么只查"纯函数"而不查状态机推进</b>：状态机的逐 tick 推进依赖
 * {@code ServerPlayer} / {@code ServerLevel}，而本类在无世界环境下运行（与
 * {@code MagicFormulaSelfCheck} / {@code MagicJudgeSelfCheck} 同一纪律）。
 * 因此这里只验<b>能在纸面判定对错</b>的那部分：形状映射、查询参数、分段计价、会话不变量、
 * 中断返还真值表、注册表空安全、<b>内置内容覆盖度</b>。逐 tick 行为留给实机核验。
 *
 * <p>返回违例清单（空 = 全通过），由平台侧 {@code AkaishiMagicRegistryChecker} 汇总。
 */
public final class MagicCastSelfCheck {

    private static final String INSTANT = MagicIds.Delivery.INSTANT;
    private static final String CHANNEL = MagicIds.Delivery.CHANNEL;
    private static final String PLACE = MagicIds.Delivery.PLACE;

    private MagicCastSelfCheck() {
    }

    public static void main(String[] args) {
        MagicExecBuiltins.register();
        var failures = inspect();
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.println("MagicCastSelfCheck: PASS");
    }

    /** @return 违例说明清单；空表示通过 */
    public static List<String> inspect() {
        List<String> out = new ArrayList<>();
        out.addAll(MagicPaymentSelfCheck.inspect());
        checkShapeMapping(out);
        checkQueryFlags(out);
        var allies = MagicTargetQuery.of(null, Vec3.ZERO, MagicShape.AREA).forAllies()
                .withOrigin(new Vec3(1, 2, 3)).withRadius(4).ignoringLineOfSight().includingHidden();
        if (!allies.alliesOnly()) out.add("防御查询复制丢失友方过滤");
        var impact = context(fixedWords(), INSTANT, 1f).withImpact(null, new Vec3(4, 5, 6), new Vec3(0, 0, 1))
                .withOrigin(new Vec3(7, 8, 9));
        if (!new Vec3(0, 0, 1).equals(impact.impactDirection())) out.add("投送上下文复制丢失碰撞方向");
        var state = new com.example.akaishi.forbidden.magic.MagicState();
        state.unlockWord(MagicWord.TOU.id());
        for (int i = 0; i < 3; i++) state.setPool(i, state.poolCapacity());
        float poolBefore = state.pool(0), heatBefore = state.heat();
        var decision = com.example.akaishi.forbidden.magic.MagicServiceImpl.instance().decide(state, 0,
                List.of(MagicWord.TOU.id()), "chi", 0, 0, 0, Float.MAX_VALUE, false);
        if (!decision.ok() || state.pool(0) != poolBefore || state.heat() != heatBefore)
            out.add("decide改变支付余额或热量/基础句拒绝");
        for (var pair : List.of(List.of("tou", "zhao_cover"), List.of("zhao_cover", "tou"),
                List.of("si", "ji_trace"), List.of("ji_trace", "si"))) {
            var rejected = com.example.akaishi.forbidden.magic.MagicServiceImpl.instance().decide(state, 0,
                    pair, "chi", 0, 0, 0, Float.MAX_VALUE, false);
            if (rejected.result() != com.example.akaishi.forbidden.api.magic.CastResult.CONFLICT
                    || state.pool(0) != poolBefore || state.heat() != heatBefore)
                out.add("服务端冲突两方向拒绝/不收费 " + pair);
        }
        checkSegmentPricing(out);
        checkSessionInvariants(out);
        checkInterruptPolicy(out);
        checkRegistryNullSafety(out);
        checkEffectResolution(out);
        checkBuiltinCoverage(out);
        checkVisualSpecs(out);
        checkVisualRoundTrip(out);
        return out;
    }

    private static void checkVisualRoundTrip(List<String> out) {
        SpellVisual original = SpellVisual.assemble(MagicWord.LIU.id(), MagicIds.Delivery.INSTANT,
                "chi", "san", 4f, 6f, 2f,
                List.of(MagicWord.LIU.id(), MagicWord.JIAN.id()));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.write(buf);
        SpellVisual restored = SpellVisual.read(buf);
        if (!original.effectId().equals(restored.effectId())
                || !original.sourceId().equals(restored.sourceId())
                || !original.wordIds().equals(restored.wordIds())) {
            out.add("视觉快照 roundtrip 丢失源 ID 或词模块");
        }
        if (original.glyphs().codePointCount(0, original.glyphs().length())
                != original.wordIds().size()) {
            out.add("视觉快照 Unicode glyph 数量与词模块不一致");
        }
    }

    // ==================== ⑨ 视觉规格覆盖度（§7 先图后码 / §19.5 / §19.3） ====================

    /**
     * 核对"每条 E / 每种 X / 每个源 都有视觉规格"。
     *
     * <p><b>为什么这也是"静默失效"</b>：视觉表缺一行不会有任何报错 ——
     * {@code SpellVisualSpecs} 会回退到 DAMAGE 的形态，于是那条法术<b>看起来像伤害法术</b>。
     * 这种"画错但不报错"比崩溃更难发现，必须在启动时就拦下。
     */
    private static void checkVisualSpecs(List<String> out) {
        out.addAll(SpellVisualSpecs.inspect());
    }

    // ==================== ① 形状 G 的映射（§4.2 / §19.2） ====================

    private static void checkShapeMapping(List<String> out) {
        expect(out, "隙 ⇒ 单体", MagicShape.SINGLE, shapeOf(MagicWord.XI.id()));
        expect(out, "根 ⇒ 链跳", MagicShape.CHAIN, shapeOf(MagicWord.GEN.id()));
        expect(out, "丝 ⇒ 链跳", MagicShape.CHAIN, shapeOf(MagicWord.SI.id()));
        expect(out, "迹 ⇒ 球形", MagicShape.SPHERE, shapeOf(MagicWord.JI_TRACE.id()));
        // 无作用点词时由 X 反推
        expect(out, "无 B 词 + 引导 ⇒ 射线", MagicShape.RAY,
                MagicTargeting.shapeOf(List.of(MagicWord.YIN.id()), CHANNEL));
        expect(out, "无 B 词 + 布场 ⇒ 区域", MagicShape.AREA,
                MagicTargeting.shapeOf(List.of(MagicWord.YIN.id()), PLACE));
        expect(out, "无 B 词 + 直射 ⇒ 单体", MagicShape.SINGLE,
                MagicTargeting.shapeOf(List.of(MagicWord.YIN.id()), INSTANT));
    }

    private static MagicShape shapeOf(String pointWordId) {
        return MagicTargeting.shapeOf(List.of(pointWordId), INSTANT);
    }

    // ==================== ② 查询参数（§36.1 五条细则） ====================

    private static void checkQueryFlags(List<String> out) {
        // 细则 4：默认要求视线；「门」「折」豁免
        if (!query(fixedWords(), INSTANT).requireLineOfSight()) {
            out.add("§36.1-4 默认应要求视线，实际被豁免");
        }
        if (query(List.of(MagicWord.YIN.id(), MagicWord.MEN.id()), INSTANT).requireLineOfSight()) {
            out.add("§36.1-4 「门」应豁免视线遮挡");
        }
        if (query(List.of(MagicWord.YIN.id(), MagicWord.ZHE.id()), INSTANT).requireLineOfSight()) {
            out.add("§36.1-4 「折」应豁免视线遮挡");
        }
        // 细则 5：默认不含隐匿者；「迹」「标」豁免
        if (query(fixedWords(), INSTANT).includeHidden()) {
            out.add("§36.1-5 默认应排除隐匿目标");
        }
        if (!query(List.of(MagicWord.YIN.id(), MagicWord.JI_TRACE.id()), INSTANT).includeHidden()) {
            out.add("§36.1-5 「迹」应纳入隐匿目标");
        }
        if (!query(List.of(MagicWord.YIN.id(), MagicWord.BIAO.id()), INSTANT).includeHidden()) {
            out.add("§36.1-5 「标」应纳入隐匿目标");
        }
        // 细则 3：单体系默认 1 个，「叉」逐个递增，且不超过硬上限 8
        int single = query(List.of(MagicWord.YIN.id()), INSTANT).effectiveLimit();
        if (single != 1) {
            out.add("§36.1-3 单体系默认目标数应为 1，实际 " + single);
        }
        int oneFork = query(List.of(MagicWord.YIN.id(), MagicWord.CHA.id()), INSTANT).effectiveLimit();
        if (oneFork != 2) {
            out.add("§36.1-3 一个「叉」应把单体上限抬到 2，实际 " + oneFork);
        }
        List<String> manyForks = new ArrayList<>();
        manyForks.add(MagicWord.YIN.id());
        for (int i = 0; i < 20; i++) {
            manyForks.add(MagicWord.CHA.id());
        }
        if (query(manyForks, INSTANT).effectiveLimit() > MagicTargetQuery.DEFAULT_LIMIT) {
            out.add("§36.1-3 「叉」堆叠后不得超过硬上限 " + MagicTargetQuery.DEFAULT_LIMIT);
        }
        // 细则 3：区域系固定硬上限（不随「叉」增长）
        int area = query(List.of(MagicWord.YIN.id()), PLACE).effectiveLimit();
        if (area != MagicTargetQuery.DEFAULT_LIMIT) {
            out.add("§36.1-3 区域系上限应固定为 " + MagicTargetQuery.DEFAULT_LIMIT + "，实际 " + area);
        }
    }

    private static List<String> fixedWords() {
        return List.of(MagicWord.YIN.id(), MagicWord.LIU.id());
    }

    private static MagicTargetQuery query(List<String> words, String deliveryId) {
        return MagicTargeting.queryOf(context(words, deliveryId, 1f), true);
    }

    // ==================== ③ 分段计价（§21.2 / §21.6） ====================

    private static void checkSegmentPricing(List<String> out) {
        SpellSnapshot singles = snapshot(INSTANT, 1f, 600f);
        if (singles.segments() != 1) {
            out.add("§21.2 单次型（系数 1）应只有 1 段");
        }
        if (Math.abs(singles.segmentCost() - 600f) > 1e-3f) {
            out.add("§21.6 单次型每段价应等于总价");
        }
        SpellSnapshot channel = snapshot(CHANNEL, 3f, 600f);
        if (channel.segments() != 3) {
            out.add("§21.2 引导 3 秒应有 3 段，实际 " + channel.segments());
        }
        if (Math.abs(channel.segmentCost() - 200f) > 1e-3f) {
            out.add("§21.6 引导每段价应为 总价 ÷ 段数，实际 " + channel.segmentCost());
        }
        // 系数非正时不得除零
        SpellSnapshot degenerate = snapshot(INSTANT, 0f, 500f);
        if (!Float.isFinite(degenerate.segmentCost()) || degenerate.segments() < 1) {
            out.add("§21.6 系数非正时每段价必须有限且段数 ≥1");
        }
    }

    // ==================== ④ 会话不变量（§21.1 / §21.6） ====================

    private static void checkSessionInvariants(List<String> out) {
        // 单次型：1 段，建会话即已预扣首段，且不是持续型
        MagicCastSession single = new MagicCastSession(
                context(fixedWords(), INSTANT, 1f), null, 100L, 1, 600f, 1, List.of(), 20f);
        if (single.state() != MagicCastState.CHARGING) {
            out.add("§21.1 新建会话应处于前摇");
        }
        if (single.segmentsPaid() != 1) {
            out.add("§21.6 进入前摇时应已预扣首段");
        }
        if (single.continuous()) {
            out.add("§21.1 单次型不应被判为持续型");
        }
        if (single.hasMoreSegments()) {
            out.add("§21.6 单次型预扣后不应还有待结算段");
        }
        if (single.firstFired()) {
            out.add("§21.1 新建会话的首段不应被标记为已生效");
        }

        // 持续型：3 段，预扣 1 段，还剩 2 段
        MagicCastSession channel = new MagicCastSession(
                context(fixedWords(), CHANNEL, 3f), null, 100L, 3, 200f, 20, List.of(), 20f);
        if (!channel.continuous() || !channel.hasMoreSegments()) {
            out.add("§21.6 引导 3 段预扣 1 段后应还剩 2 段待结算");
        }
        channel.markSegmentPaid();
        channel.markSegmentPaid();
        if (channel.hasMoreSegments()) {
            out.add("§21.6 付满 3 段后不应还有待结算段");
        }
    }

    // ==================== ⑤ 中断返还真值表（§21.1 终止源清单） ====================

    private static void checkInterruptPolicy(List<String> out) {
        // 自然终止（含超时 / 扣不出）一律不返还
        if (MagicCastInterrupt.NATURAL.refunds()
                || MagicCastInterrupt.OVER_LIMIT.refunds()
                || MagicCastInterrupt.INSUFFICIENT.refunds()) {
            out.add("§21.1 自然终止类（放完 / 超时 / 扣不出）不应返还");
        }
        // 被动打断一律返还 50%
        if (!MagicCastInterrupt.DEATH.refunds()
                || !MagicCastInterrupt.LOGOUT.refunds()
                || !MagicCastInterrupt.HIT.refunds()) {
            out.add("§21.1 被动打断（死亡 / 换维度 / 受击）应返还 50%");
        }
        if (MagicCastManager.REFUND_RATIO != 0.5f) {
            out.add("§21.6 返还比例应为 50%");
        }
    }

    // ==================== ⑥ 注册表空安全 ====================

    private static void checkRegistryNullSafety(List<String> out) {
        if (MagicExecRegistry.effect(null) != null
                || MagicExecRegistry.delivery(null) != null
                || MagicExecRegistry.target(null) != null
                || MagicExecRegistry.persistence(null) != null) {
            out.add("§19.2 注册表对 null 键必须返回 null，不得抛异常");
        }
    }

    // ==================== ⑦ 核心词 → E 的解析（§20.7） ====================

    private static void checkEffectResolution(List<String> out) {
        String effect = MagicPipeline.effectIdOf(context(List.of(MagicWord.LIU.id()), INSTANT, 1f));
        if (!MagicIds.Effect.DAMAGE.equals(effect)) {
            out.add("§20.7 「流」应解析为 E=damage，实际 " + effect);
        }
    }

    // ==================== ⑧ 内置内容覆盖度（§11 / §19.2） ====================

    /**
     * 核对"每个 G / X / E 要么已注册，要么在待办表里"。
     *
     * <p><b>为什么这条最重要</b>：执行层的失败方式是<b>静默的</b> —— 缺一个投送器，
     * 表现只是"这次施法什么都没发生"，不报错、不掉日志（管线刻意安静跳过）。
     * 没有这条核对，漏注册能一路混到实机才发现。
     */
    private static void checkBuiltinCoverage(List<String> out) {
        if (!MagicExecBuiltins.installed()) {
            out.add("执行层内置内容未安装：MagicExecBuiltins.register() 未被调用");
            return;
        }
        // 形状 G：7 种必须全有解析器（没有"待办"这一说 —— 圈人是所有法术的公共前提）
        for (MagicShape shape : MagicShape.values()) {
            if (MagicExecRegistry.target(shape) == null) {
                out.add("形状 G 缺解析器：" + shape);
            }
        }
        // 投送方式 X：已注册，或明列在 DEFERRED_DELIVERIES
        for (String deliveryId : MagicDeliveryTable.all().keySet()) {
            if (MagicExecRegistry.delivery(deliveryId) == null
                    && !MagicExecBuiltins.DEFERRED_DELIVERIES.contains(deliveryId)) {
                out.add("投送方式 X 既未注册也不在待办表：" + deliveryId);
            }
        }
        // 效果 E：已注册，或明列在 DEFERRED_EFFECTS
        for (String effectId : MagicEffectTable.all().keySet()) {
            // TOLL 是参数修改器、且不可单独作核心（§28.2 缺陷 4）⇒ 永远不会成为管线的 E，无需执行器
            if (MagicIds.Effect.TOLL.equals(effectId)) {
                continue;
            }
            if (MagicExecRegistry.effect(effectId) == null
                    && !MagicExecBuiltins.DEFERRED_EFFECTS.contains(effectId)) {
                out.add("效果 E 既未注册也不在待办表：" + effectId);
            }
        }
        // 注册进来的 id 必须真的存在于 E 表（挡住"注册时把 id 拼错"这类静默失效）
        for (String effectId : MagicExecRegistry.effectIds()) {
            if (MagicEffectTable.get(effectId) == null) {
                out.add("注册了 E 表里不存在的执行器 id：" + effectId);
            }
        }
        // 同理：投送器与解析器也不能注册进表外的 id / 形状
        for (String deliveryId : MagicExecRegistry.deliveryIds()) {
            if (MagicDeliveryTable.get(deliveryId) == null) {
                out.add("注册了 X 表里不存在的投送器 id：" + deliveryId);
            }
        }
    }

    // ==================== 工具 ====================

    /** 造一个最小的执行上下文（caster 与 level 都为空 —— 本自检不碰世界） */
    private static MagicContext context(List<String> words, String deliveryId, float pricingCoefficient) {
        return new MagicContext(
                null,
                null,
                Vec3.ZERO,
                "san",
                "chi",
                List.of("chi"),
                words,
                6,
                snapshot(deliveryId, pricingCoefficient, 600f));
    }

    /** 核心词固定取「流」（E=damage），便于形状反推与 E 解析的断言稳定 */
    private static SpellSnapshot snapshot(String deliveryId, float pricingCoefficient, float cost) {
        return new SpellSnapshot(
                2, MagicWord.LIU.id(), deliveryId, "san",
                cost, cost / 100f, 8f, 2.8f, 12f, 30f, 3f, 2f, 4f, 0, 0, pricingCoefficient);
    }

    private static void expect(List<String> out, String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            out.add(label + "：期望 " + expected + "，实际 " + actual);
        }
    }
}

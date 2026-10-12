package com.example.akaishi.forbidden.magic.formula;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.magic.cast.PaymentPlan;
import com.example.akaishi.forbidden.magic.table.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.List;

/** 只读生产计划；不重新计算词权重、载体倍率或支付。 */
public final class SpellPlanTooltips {
    private SpellPlanTooltips() {}
    public static void append(List<Component> lines, SpellPlan plan, boolean expanded, float extra) {
        for (var op : plan.operations()) {
            String value = value(op);
            lines.add(key("operation", Component.translatable("item.forbidden_hex.magic_core.op." + op.effectId()), value));
            if (expanded) {
                lines.add(key("shape", fmt(op.durationSeconds()), fmt(op.radius())));
                int pulses = com.example.akaishi.forbidden.magic.exec.MagicPulseTiming.perSegment(plan, op);
                lines.add(key("pulses", pulses));
                if (op.effectId().equals(MagicIds.Effect.WARD)) lines.add(key("ward_capacity"));
                else if (op.power() > 0f) lines.add(key("upper", fmt(op.power() * plan.schedule().segments()
                        * pulses * (op.traits().contains("followup_hit") ? 2 : 1))));
                if (op.effectId().equals(MagicIds.Effect.LINK)) lines.add(key("link_limits",
                        com.example.akaishi.forbidden.magic.link.MagicLinks.MIN_RINGS,
                        Math.min(plan.target().limit(), com.example.akaishi.forbidden.magic.link.MagicLinks.MAX_RINGS),
                        com.example.akaishi.forbidden.magic.link.MagicLinks.RING_DELAY_TICKS));
                if (List.of("bind", "move", "gather").contains(op.effectId())) lines.add(key("health_gate"));
                if (op.power() <= 0f) lines.add(key("limit." + op.effectId()));
                for (String trait : op.traits()) lines.add(key("trait." + trait));
            }
        }
        lines.add(key("cost", fmt(plan.cost().total()), fmt(plan.cost().segmentCost())));
        lines.add(key("schedule", plan.schedule().segments(), fmt(plan.schedule().intervalSeconds())));
        lines.add(key("time", fmt(plan.delivery().cooldownSeconds()), fmt(plan.delivery().castTimeSeconds()), fmt(plan.delivery().range())));
        if (!expanded) return;
        var source = MagicSource.byIdOrDefault(plan.input().sourceId());
        var payment = PaymentPlan.calculate(source, plan.cost().segmentCost(), extra);
        lines.add(key("payment", fmt(payment.extra()), fmt(payment.main()), fmt(payment.sanity()), fmt(payment.health())));
        lines.add(key("payment_rule"));
        lines.add(key("targets", plan.target().limit()));
        lines.add(key("friendly"));
        if (plan.features().contains("star_exclusive")) lines.add(key("star"));
        lines.add(key("baseline"));
    }
    public static String briefValue(EffectOperation operation) { return value(operation); }

    private static String value(EffectOperation op) {
        var effect = MagicEffectTable.get(op.effectId());
        float ratio = effect == null || effect.baseP() <= 0 ? 0 : op.magnitude() / effect.baseP();
        return switch (op.effectId()) {
            case "damage", "ward", "field", "link" -> fmt(op.power()) + " HP";
            case "boost", "weaken" -> Component.translatable("item.forbidden_hex.magic_core.plan.status",
                    Math.min(4, Math.max(0, (int)Math.floor(ratio) - 1)) + 1).getString();
            case "bind" -> Component.translatable("item.forbidden_hex.magic_core.plan.status_level",
                    Math.min(5, 3 + com.example.akaishi.forbidden.magic.exec.ExecSupport.levelFromRatio(ratio)) + 1).getString();
            case "mind" -> Component.translatable("item.forbidden_hex.magic_core.plan.mind_actual", ratio >= 1.3f ? 2 : 1).getString();
            case "sense", "conceal" -> Component.translatable("item.forbidden_hex.magic_core.plan.status_level", 1).getString();
            case "gather" -> fmt(Math.min(3f, Math.max(0.02f, op.magnitude()))) + " "
                    + Component.translatable("item.forbidden_hex.magic_core.plan.velocity").getString();
            case "break_unlock", "break_terminate", "break_shield" -> Integer.toString(
                    com.example.akaishi.forbidden.magic.exec.ExecSupport.layersFromRatio(ratio));
            case "move" -> fmt((float)Math.min(1.2, Math.max(0.1, op.magnitude() * 0.12))) + " "
                    + Component.translatable("item.forbidden_hex.magic_core.plan.velocity").getString();
            default -> fmt(op.magnitude()) + " " + Component.translatable("item.forbidden_hex.magic_core.plan.unit." + op.unit()).getString();
        };
    }
    private static Component key(String suffix, Object... args) {
        return Component.translatable("item.forbidden_hex.magic_core.plan." + suffix, args).withStyle(ChatFormatting.GRAY);
    }
    private static String fmt(float value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}

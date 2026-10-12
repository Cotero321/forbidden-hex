package com.example.akaishi.forbidden.magic.exec;

import com.example.akaishi.forbidden.api.magic.EffectOperation;
import com.example.akaishi.forbidden.api.magic.SpellPlan;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.link.MagicLinks;
import com.example.akaishi.forbidden.magic.table.MagicIds;

/** 执行和提示共用寿命与首跳边界。 */
public final class MagicPulseTiming {
    public static final int ATTACHED_INTERVAL = 20;
    public static final int ATTACHED_MAX_TICKS = 600;
    private MagicPulseTiming() {}

    public static int durationTicks(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f) return ExecSupport.MIN_EFFECT_TICKS;
        return (int) Math.max(ExecSupport.MIN_EFFECT_TICKS,
                Math.min(ExecSupport.MAX_EFFECT_TICKS, Math.round(seconds * 20f)));
    }

    public static int attachedTicks(float seconds) {
        return Math.min(ATTACHED_MAX_TICKS, seconds > 0f ? durationTicks(seconds) : 100);
    }

    public static int pulses(int life, int first, int interval, boolean inclusiveExpiry) {
        int last = life - (inclusiveExpiry ? 0 : 1);
        return last < first ? 0 : 1 + (last - first) / interval;
    }

    public static int linkPulses(float seconds) {
        return pulses(Math.max(MagicLinks.PULSE_INTERVAL_TICKS, durationTicks(seconds)),
                1, MagicLinks.PULSE_INTERVAL_TICKS, true);
    }

    public static int perSegment(SpellPlan plan, EffectOperation op) {
        String delivery = plan.delivery().deliveryId();
        if (MagicIds.Delivery.PLACE.equals(delivery)) {
            float seconds = plan.operations().stream().map(EffectOperation::durationSeconds).max(Float::compare).orElse(0f);
            int life = Math.max(SpellCircleEntity.PULSE_INTERVAL_TICKS,
                    Math.min(SpellCircleEntity.MAX_LIFE_TICKS, durationTicks(seconds)));
            int circles = pulses(life, 1, SpellCircleEntity.PULSE_INTERVAL_TICKS, false);
            return circles * (MagicIds.Effect.LINK.equals(op.effectId()) ? linkPulses(op.durationSeconds()) : 1);
        }
        if (MagicIds.Effect.LINK.equals(op.effectId()))
            return linkPulses(op.durationSeconds());
        boolean attached = MagicIds.Delivery.ATTACH.equals(delivery)
                && (MagicIds.Effect.DAMAGE.equals(op.effectId()) || MagicIds.Effect.FIELD.equals(op.effectId()));
        boolean field = MagicIds.Effect.FIELD.equals(op.effectId()) && !MagicIds.Delivery.CHANNEL.equals(delivery);
        return attached || field ? pulses(attachedTicks(op.durationSeconds()), 0, ATTACHED_INTERVAL, false) : 1;
    }
}

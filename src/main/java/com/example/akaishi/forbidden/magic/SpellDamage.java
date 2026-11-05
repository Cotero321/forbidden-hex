package com.example.akaishi.forbidden.magic;

import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** 主墨、灰源精神追加与真实命中的统一结算入口。 */
public final class SpellDamage {
    private SpellDamage() {
    }

    public static float sanBonusRatio(float powerRatio) {
        return Float.isFinite(powerRatio) ? Math.max(0f, Math.min(0.1f * powerRatio, 0.4f)) : 0f;
    }

    /** 读取本次效果的威力倍率，不把 FIELD/LINK 的固定附加值当成倍率。 */
    public static float powerRatio(MagicContext context) {
        var snapshot = context.snapshot();
        if (snapshot.operation() != null) return ExecSupport.pRatio(context, snapshot.operation().effectId());
        MagicWord word = MagicWord.byId(snapshot.coreWordId());
        return word == null || word.effect() == null ? 0f : ExecSupport.pRatio(context, word.effect());
    }

    public static boolean hurt(MagicContext context, LivingEntity target, float amount) {
        return hurt(target, context.caster(), context.mainInkId(), context.sourceId(), amount, powerRatio(context));
    }

    /**
     * 主墨伤害与 SAN 追加合为一次 hurt；事件只对各分量应用对应防御。
     * 星墨主体与追加均为精神伤害，同次结算只应用一次精神减免。
     */
    public static boolean hurt(LivingEntity target, Entity caster, String mainInk, String source,
            float amount, float powerRatio) {
        if (target.level().isClientSide || !Float.isFinite(amount) || amount <= 0f) return false;
        float bonus = "san".equals(source) ? sanBonusRatio(powerRatio) : 0f;
        float total = amount * (1f + bonus);
        if (!Float.isFinite(total)) return false;
        float psychicFraction = "xing".equals(mainInk) ? 1f : bonus / (1f + bonus);
        return target.hurt(MagicDamageTypes.source(target.level(), caster, mainInk, psychicFraction), total);
    }
}

package com.example.akaishi.forbidden.magic.exec;

import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.magic.MagicDamageTypes;
import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 执行器的<b>共用换算</b>（设计记忆 §8.2 / §19.5）。
 *
 * <p><b>为什么要有这个类</b>：19 个执行器里有十几处重复的换算 ——
 * "持续秒数 → 刻数"、"量化值 → 效果等级"、"按视线取方块"。各写一遍的结果是
 * <b>同一件事在不同法术里有不同的边界行为</b>（有的夹 0、有的不夹；有的 ×20 取整、有的截断），
 * 而那种差异没人能记住、也没人会去测。
 */
public final class ExecSupport {

    /** 效果最短持续：1 秒 —— 低于它玩家看不见任何反馈（等于没生效） */
    public static final int MIN_EFFECT_TICKS = 20;

    /** 效果最长持续：5 分钟 —— 防御性上限，防坏公式产出"几百年的禁锢" */
    public static final int MAX_EFFECT_TICKS = 6000;

    /** MobEffect 等级上限（V 级）—— 再高只是数字，手感上无差别 */
    public static final int MAX_AMPLIFIER = 4;

    private ExecSupport() {
    }

    /** 持续刻数：由持续轴（秒）换算，夹到 [1 秒, 5 分钟] */
    public static int durationTicks(MagicContext context) {
        return MagicPulseTiming.durationTicks(context.snapshot().durationSeconds());
    }

    /** 威力（HP）—— 只对 HP / HP每秒 类 E 有意义 */
    public static float power(MagicContext context) {
        float value = context.snapshot().power();
        return Float.isFinite(value) && value > 0f ? value : 0f;
    }

    /**
     * <b>DAMAGE 基准值</b>（v20）：用于 FIELD / LINK 的"主伤害百分比附加"。
     *
     * <p>当核心 E 是 FIELD 或 LINK 时，每次结算（每秒 / 每跳）在自身威力之上
     * 额外造成 {@code damageBase × BONUS_PERCENT} 的伤害 ——
     * 即"持续 / 传导伤害从核心伤害基准中汲取一部分"。
     *
     * <p>为什么不直接把 power（威力轴输出）拿来按比例分：power 已经经过了
     * 词权重、规模修正、阶位系数、源倾向等乘区，拿它做百分比会因多词法术的 power
     * 膨胀而使附加伤害失控。取<b>基准值</b>（6 HP）则附加伤害恒为 0.6 HP/次，
     * 只随 DAMAGE baseP 本身调整而变化。
     */
    public static final float PRIMARY_BONUS_PERCENT = 0.10f;

    /**
     * 附加伤害：{@code MagicEffectTable.get(DAMAGE).baseP × PRIMARY_BONUS_PERCENT}。
     * 仅在核心 E 为 FIELD 或 LINK 时调用 —— DAMAGE 本身不需要附加。
     */
    public static float primaryDamageBonus() {
        MagicEffectDef damageDef = MagicEffectTable.get(MagicIds.Effect.DAMAGE);
        return damageDef != null ? damageDef.baseP() * PRIMARY_BONUS_PERCENT : 0f;
    }

    /**
     * 判断本句的核心 E 是否为持续 / 传导类（FIELD / LINK），
     * 以决定是否附加主伤害百分比。
     */
    public static boolean isFieldOrLinkCore(MagicContext context) {
        String coreWordId = context.snapshot().coreWordId();
        if (coreWordId == null) return false;
        MagicWord core = MagicWord.byId(coreWordId);
        if (core == null) return false;
        String effectId = core.effect();
        return MagicIds.Effect.FIELD.equals(effectId) || MagicIds.Effect.LINK.equals(effectId);
    }

    /**
     * <b>本次施法的 P 倍数</b>：量化值 ÷ 该 E 的基准。
     *
     * <p>这是把"设计表里的 P"还原出来的入口 —— §8.2 的规则大量写作
     * 「层数 = ⌊P⌋」「P ≥ 1.3 才吃 Boss」「P 0.8 方位 / 1.0 轮廓」，
     * 而 {@code magnitude} 已经是 P × 基准值，故除以基准即得 P。
     */
    public static float pRatio(MagicContext context, String effectId) {
        MagicEffectDef def = MagicEffectTable.get(effectId);
        float base = def == null ? 1f : def.baseP();
        float magnitude = context.snapshot().magnitude();
        if (base <= 0f || !Float.isFinite(magnitude)) {
            return 1f;
        }
        return Math.max(0f, magnitude / base);
    }

    /** 由 P 倍数推 MobEffect 等级：每翻一倍升一级，夹到 [0, IV] */
    public static int levelFromRatio(float pRatio) {
        if (!Float.isFinite(pRatio) || pRatio < 1f) {
            return 0;
        }
        int level = (int) Math.floor(pRatio) - 1;
        return Math.max(0, Math.min(MAX_AMPLIFIER, level));
    }

    /** 由 P 倍数推层数：{@code ⌊P⌋}，至少 1（§8.2 的 COUNT 类规则） */
    public static int layersFromRatio(float pRatio) {
        if (!Float.isFinite(pRatio) || pRatio <= 0f) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(pRatio));
    }

    /** 施加一个状态效果（失败静默 —— 目标可能已移除 / 免疫） */
    public static boolean apply(LivingEntity target, MobEffect effect, int amplifier, int ticks,
            Entity source) {
        return target.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amplifier)), source);
    }

    /** 该目标的最大生命值是否属于"高血量目标"（§8.2 多处写「高血量目标需 P ≥ 1.3」） */
    public static boolean isHighHealth(LivingEntity target) {
        return target.getMaxHealth() > 100f;
    }

    /** P 门槛：高血量目标需要 P ≥ 1.3，普通目标一律放行（§8.2 的 BIND / MOVE / GATHER 规则） */
    public static boolean meetsHighHealthGate(MagicContext context, String effectId, LivingEntity target) {
        return !isHighHealth(target) || pRatio(context, effectId) >= 1.3f;
    }

    /** 句内是否含某词（措辞 / 豁免判断） */
    public static boolean hasWord(MagicContext context, MagicWord word) {
        return context.hasWord(word.id());
    }

    /**
     * 对单个目标结算一次法术伤害（{@code DAMAGE} 与 {@code FIELD} 共用）。
     *
     * <p><b>为什么这两条 E 共用同一段身体</b>：{@code DAMAGE} 与 {@code FIELD}
     * 作为<b>原语</b>都是"掉血"，差别不在效果本身，而在
     * <b>形状（单体 vs 区域）</b>与<b>节奏（一次 vs 每秒）</b> ——
     * 前者由 G 承担、后者由 X 与驱动它的场地/引导承担。
     * 写两份只会让"改了一处忘了另一处"再次发生。
     *
     * @return 是否真的结算了（目标不是生物 / 威力为 0 时为 false）
     */
    public static boolean dealDamage(MagicContext context, Entity target) {
        if (context.level() == null || !(target instanceof LivingEntity living)) {
            return false;
        }
        float amount = power(context);
        if (amount <= 0f) {
            return false;
        }
        // ★v20：核心 E 为 FIELD 时，每次结算（每秒）附加主伤害基准的 10%
        if (isFieldOrLinkCore(context)) {
            amount += primaryDamageBonus();
        }
        boolean piercing = context.snapshot().operation() != null
                ? context.snapshot().operation().traits().contains("piercing")
                        || context.snapshot().operation().traits().contains("san_mental_piercing")
                : hasWord(context, MagicWord.JIAN) || hasWord(context, MagicWord.ZHEN)
                        || "san".equals(context.sourceId());
        boolean mental = "san".equals(context.sourceId());
        var source = mental ? MagicDamageTypes.psychic(context.level(), context.caster())
                : piercing ? context.level().damageSources().indirectMagic(context.caster(), context.caster())
                : MagicDamageTypes.source(context.level(), context.caster());
        boolean hit = living.hurt(source, amount);
        boolean followup = context.snapshot().plan() != null
                ? context.snapshot().operation() != null
                        && context.snapshot().operation().traits().contains("followup_hit")
                : hasWord(context, MagicWord.BENG);
        if (hit && followup) {
            int invulnerable = living.invulnerableTime;
            living.invulnerableTime = 0;
            living.hurt(source, amount);
            living.invulnerableTime = invulnerable;
        }
        return hit;
    }

    /** 沿视线取方块（用于开锁 / 破方块这类"对物"效果）；未命中返回 {@code null} */
    public static BlockHitResult aimBlock(MagicContext context, double range) {
        Level level = context.level();
        Entity caster = context.caster();
        if (level == null || caster == null) {
            return null;
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().normalize().scale(Math.max(1d, range)));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** 把目标推开 / 拉向某个点；力度用"格/tick 速度"表达并夹取，防止把实体弹上天 */
    public static void shove(Entity target, Vec3 direction, double strength) {
        double clamped = Math.max(0d, Math.min(3d, strength));
        Vec3 push = direction.normalize().scale(clamped);
        target.setDeltaMovement(target.getDeltaMovement().add(push.x, Math.min(0.4d, push.y), push.z));
        target.hurtMarked = true;
    }
}

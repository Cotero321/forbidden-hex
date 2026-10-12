package com.example.akaishi.forbidden.magic.formula;

import com.example.akaishi.forbidden.magic.table.MagicSource;

/**
 * P4 公式层返回的不可变数值快照。
 *
 * @param wordCount          总词数（N，上限 8）
 * @param overlayInkCount    K：逐词覆盖墨种数（主墨不计）
 * @param mainInk            主墨 id
 * @param source             实际使用的源
 * @param delivery           交付方式 id（取核心词的 X）
 * @param coreWordId         核心词 id（§1 规则选出）
 * @param wordSynergyCount   生效的词协同条数（§24.2，0~2）
 * @param synergyCostMul     词协同累乘的消耗倍率
 * @param synergyPowerMul    词协同累乘的输出倍率
 * @param cost               总消耗（池量）
 * @param sanityCost         折算的 SAN（= cost ÷ 100，0.1 步进）
 * @param cooldownSeconds    冷却（秒）
 * @param castTimeSeconds    释放时间 / 前摇（秒）
 * @param range              距离（格）
 * @param power              威力轴输出（单位随 E 的 Unit；非生命值类 E 不产威力）
 * @param durationSeconds    持续轴输出（秒）
 * @param radius             半径轴输出（格）
 * @param magnitude          <b>量化值</b>：核心 E 在<b>自身单位</b>下的数字（§8.2 的 baseP 经
 *                           词权重 / 角色权重 / 同源衰减 / 源倾向 / 规模修正 / 阶位系数后）。
 *                           单位由核心 E 的 {@code Unit} 决定（百分数 / 格 / 层数 / 档位 / 系数）。
 *                           非 HP 类 E（削弱 / 位移 / 破防…）的**唯一量化通道** —— 威力轴只收 HP。
 * @param powerTier          威力轴的阶位强度系数（按威力轴贡献词数取）
 * @param sourcePowerMultiplier 源的 P 倾向
 * @param sourceCostMultiplier  源的消耗系数
 * @param pricingCoefficient X 的计价系数（单次 1 / 引导秒数 / 连发发数）
 */
public record MagicFormulaResult(
        int wordCount,
        int overlayInkCount,
        String mainInk,
        MagicSource source,
        String delivery,
        String coreWordId,
        int wordSynergyCount,
        float synergyCostMul,
        float synergyPowerMul,
        float cost,
        float sanityCost,
        float cooldownSeconds,
        float castTimeSeconds,
        float range,
        float power,
        float durationSeconds,
        float radius,
        float magnitude,
        float powerTier,
        float sourcePowerMultiplier,
        float sourceCostMultiplier,
        float pricingCoefficient) {
    public MagicFormulaResult adjusted(com.example.akaishi.forbidden.magic.cast.CastAdjustments adjustments) {
        if (adjustments == null) return this;
        var core = com.example.akaishi.forbidden.magic.table.MagicWord.byId(coreWordId);
        var effect = com.example.akaishi.forbidden.magic.table.MagicEffectTable.get(core.effect());
        float costMul = adjustments.costMul(source, wordCount);
        float powerMul = adjustments.powerMul(core.effect(), effect.category(), adjustments.thirdCast());
        return new MagicFormulaResult(wordCount, overlayInkCount, mainInk, source, delivery, coreWordId,
                wordSynergyCount, synergyCostMul, synergyPowerMul, cost * costMul, sanityCost * costMul,
                cooldownSeconds * adjustments.cooldownMul(source, effect.category(), adjustments.thirdCast()),
                castTimeSeconds * adjustments.castTimeMul(), range * adjustments.rangeMul(),
                power * powerMul, durationSeconds, radius * adjustments.radiusMul(), magnitude * powerMul,
                powerTier, sourcePowerMultiplier, sourceCostMultiplier, pricingCoefficient);
    }
}

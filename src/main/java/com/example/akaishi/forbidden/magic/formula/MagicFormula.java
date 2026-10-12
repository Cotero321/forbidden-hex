package com.example.akaishi.forbidden.magic.formula;

import com.example.akaishi.forbidden.magic.judge.MagicValidation;
import com.example.akaishi.forbidden.magic.judge.MagicParser;
import com.example.akaishi.forbidden.magic.judge.ParsedSpell;
import com.example.akaishi.forbidden.magic.table.MagicDeliveryDef;
import com.example.akaishi.forbidden.magic.table.MagicDeliveryTable;
import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicScalingTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicSynergyTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P4 纯公式层：把静态表与判定结果转换为一次施法的可执行数值快照。
 *
 * <p>本类不碰玩家、世界、NBT、网络或注册表，只接受不可变的输入并返回结果 ——
 * 因此既能在无 Minecraft 世界的情况下批量验算 1~8 词，也能让 P3 服务层复用同一公式。
 *
 * <h3>结算口径（对应 §8.4 / §8.5 / §21.2 / §23.4 / §24.2）</h3>
 * <ol>
 *   <li><b>角色权重</b>（§8.5 的「角色权重」，v18 首次落地）：核心词 {@code 1.00}；
 *       修饰词 {@code 0.25}；<b>第二核心</b> {@code 0.50}（§23.4）。</li>
 *   <li><b>词协同</b>（§24.2 规则 6「合成一条」）：该段<b>只出一份贡献</b>（首词），
 *       次词并入；额外乘协同自带的 {@code powerMul} 与 {@code costMul}。</li>
 *   <li><b>双核心</b>：仅<b>核心词所在段</b>适用；该段第二个词取第二核心权重。
 *       与协同<b>互斥</b>（§24.2 阶梯表：有协同走协同，无协同才走双核）。</li>
 *   <li><b>轴各算各的</b>：输出轴的规模修正与阶位系数按<b>该轴贡献词数</b>取，
 *       消耗 / 冷却 / 释放按<b>总词数</b>取（§28.2 缺陷 1 的修复）。</li>
 *   <li><b>同源衰减</b>（§26.3）：按 E 分组，组内按出现序取
 *       {@link MagicScalingTable#SAME_SOURCE_DECAY}。</li>
 *   <li><b>单位隔离</b>（§28.2 缺陷 2）：威力轴只收生命值类，持续 / 半径轴各按自己的门槛收。</li>
 * </ol>
 *
 * <p><b>本类不做什么</b>：协同的<b>机制细节</b>（无视护甲、能量转移、范围化…）由 P5 的
 * 执行器按协同名实现；这里只算"几份贡献、各占多重、花多少"。
 */
public final class MagicFormula {

    private MagicFormula() {
    }

    /**
     * 便捷入口：自动解析并按"无墨集合"判定（语义与主入口完全一致，仅省去手工两步）。
     * 供自检与调试命令使用 —— 不会产生与主入口不同的行为。
     */
    public static MagicFormulaResult calculate(List<MagicWord> words, String mainInk, MagicSource source,
            int overlayInks, float channelSeconds, int volleyShots) {
        ParsedSpell spell = MagicParser.parse(words);
        MagicValidation validation = com.example.akaishi.forbidden.magic.judge.MagicValidator
                .validate(spell, List.of());
        return calculate(spell, validation, mainInk, source, overlayInks, channelSeconds, volleyShots);
    }

    /**
     * 主入口：结算一次施法。
     *
     * @param spell          解析结果（须 {@link ParsedSpell#ok()}）
     * @param validation     判定结果（须 {@link MagicValidation#isOk()}）—— 提供生效的协同清单
     * @param mainInk        主墨 id
     * @param source         魔力源
     * @param overlayInks    K：逐词覆盖墨的种数，主墨不计（§21.3）
     * @param channelSeconds X3 引导秒数；非 X3 传 0
     * @param volleyShots    X7 发数；非 X7 传 0
     */
    public static MagicFormulaResult calculate(ParsedSpell spell, MagicValidation validation,
            String mainInk, MagicSource source, int overlayInks, float channelSeconds, int volleyShots) {
        if (spell == null || !spell.ok()) {
            throw new IllegalArgumentException(
                    "spell is not castable: " + (spell == null ? "null" : spell.error()));
        }
        if (validation == null || !validation.isOk()) {
            throw new IllegalArgumentException(
                    "validation is not OK: " + (validation == null ? "null" : validation.verdict()));
        }

        MagicInkDef ink = requireInk(mainInk);
        MagicSource actualSource = source == null ? MagicSource.DEFAULT : source;
        int k = Math.max(0, overlayInks);
        int wordCount = Math.min(MagicScalingTable.MAX_WORDS, spell.wordCount());
        boolean doubleCoreAllowed = wordCount >= MagicScalingTable.SECOND_CORE_MIN_WORDS;

        // 生效的协同：按段建索引（同段至多 1 对，§24.2 规则 4）
        Map<MagicWord.Role, MagicSynergyTable.WordSynergy> synergyByRole =
                new EnumMap<>(MagicWord.Role.class);
        float synergyCostMul = 1f;
        float synergyPowerMul = 1f;
        for (MagicSynergyTable.WordSynergy synergy : validation.wordSynergies()) {
            synergyByRole.put(synergy.role(), synergy);
            synergyCostMul *= synergy.costMul();
            synergyPowerMul *= synergy.powerMul();
        }

        MagicWord core = spell.core();
        MagicWord.Role coreRole = core.role();
        // 核心 E 的**自身单位**与量化底数：非 HP 单位（PERCENT / CELLS / COUNT / TIER / FACTOR）
        // 不走威力轴（§28.2 缺陷 2 的修复），但没有量化通道就无法被执行器消费 ——
        // 故另算一条 magnitude（见返回值说明）
        MagicEffectDef coreEffect = requireEffect(core.effect());
        float coreBase = 0f;
        int coreUnitCount = 0;

        // 三个输出轴各自维护「和」与「贡献词数」—— 按轴计数，不用总词数（§28.2 缺陷 1）
        Map<String, Integer> effectOrdinals = new HashMap<>();
        float powerAxis = 0f;
        int powerCount = 0;
        float durationAxis = 0f;
        int durationCount = 0;
        float radiusAxis = 0f;
        int radiusCount = 0;
        float baseCost = 0f;
        String primaryDeliveryId = core.delivery();

        for (MagicWord.Role role : MagicWord.Role.values()) {
            List<MagicWord> segment = spell.segmentOf(role);
            MagicSynergyTable.WordSynergy synergy = synergyByRole.get(role);
            boolean isCoreSegment = role == coreRole;

            for (int i = 0; i < segment.size(); i++) {
                MagicWord word = segment.get(i);
                if (word.isModifier()) {
                    // 叉 / 层 是参数修改器：不贡献轴，其消耗溢价由 §8.6 单列
                    continue;
                }

                MagicEffectDef effect = requireEffect(word.effect());
                MagicDeliveryDef delivery = word.delivery() == null ? null : requireDelivery(word.delivery());

                // 消耗按「该词全额」计入（§23.4：第二核心的消耗不打折）
                baseCost += effect.cost() * word.wP();

                float weight = roleWeight(word, i, core, isCoreSegment, doubleCoreAllowed, synergy != null);
                if (weight <= 0f) {
                    continue;
                }
                // 协同段：把该段的机制倍率乘在贡献上
                if (synergy != null) {
                    weight *= synergy.powerMul();
                }

                int ordinal = effectOrdinals.getOrDefault(effect.id(), 0);
                effectOrdinals.put(effect.id(), ordinal + 1);
                float decay = MagicScalingTable.sameSourceDecay(ordinal);
                float contribution = weight * decay;

                if ((effect.unit() == MagicEffectDef.Unit.HP || effect.unit() == MagicEffectDef.Unit.HP_PER_SEC)
                        && effect.category() == coreEffect.category()) {
                    // ★v20 百分比加成模式：powerAxis 只取核心词的 baseP 作为基底，
                    // 修饰词不再把各自的 baseP 叠入 powerAxis。
                    // 多词的威力加成由 powerScale（POWER_BONUS_PER_WORD）承担：每多一个
                    // 威力轴贡献词给核心 +15%。故此处只统计 powerCount（贡献词数），
                    // 不再 += effect.baseP() × word.wP() × contribution。
                    if (word == core) {
                        powerAxis = effect.baseP() * word.wP() * contribution;
                    }
                    powerCount++;
                }
                if (effect.duration() > 0f) {
                    durationAxis += effect.duration() * word.wD() * contribution;
                    durationCount++;
                }
                if (effect.radius() > 0f) {
                    radiusAxis += effect.radius() * word.wR() * contribution;
                    radiusCount++;
                }
                // 量化轴：按「与核心 E 同单位」的贡献词数计（与三条轴同一套计数规则）
                if (effect.unit() == coreEffect.unit() && effect.category() == coreEffect.category()) {
                    coreUnitCount++;
                }
                if (word == core) {
                    coreBase = effect.baseP() * word.wP() * contribution;
                }
            }
        }

        // E 与 X 同取指定主词，增加修饰不能更换交付。
        if (primaryDeliveryId == null) {
            primaryDeliveryId = core.delivery();
        }
        if (primaryDeliveryId == null) {
            throw new IllegalArgumentException(
                    "no executable delivery in sentence: TOLL-type words (yuan/hen) are parameter modifiers, "
                            + "they cannot stand alone as a core");
        }
        MagicDeliveryDef delivery = requireDelivery(primaryDeliveryId);

        float costScale = MagicScalingTable.costScale(wordCount, k);
        float cooldownScale = MagicScalingTable.cooldownScale(wordCount, k);
        float castTimeScale = MagicScalingTable.castTimeScale(wordCount, k);

        float powerScale = MagicScalingTable.powerScale(powerCount, k);
        float durationScale = MagicScalingTable.durationScale(durationCount);
        float powerTier = MagicScalingTable.tierMultiplier(powerCount);
        float durationTier = MagicScalingTable.tierMultiplier(durationCount);
        float radiusTier = MagicScalingTable.tierMultiplier(radiusCount);

        float sourcePower = sourcePowerMultiplier(actualSource);
        float sourceCost = sourceCostMultiplier(actualSource);
        float pricing = pricingCoefficient(delivery, channelSeconds, volleyShots);
        float cost = baseCost * ink.costMul() * costScale * sourceCost * delivery.costMul()
                * synergyCostMul * pricing;
        if (spell.allWords().contains(MagicWord.CHA)) cost *= 1.2f;
        float cooldown = ink.cooldown() * cooldownScale * delivery.cooldownMul();
        float castTime = ink.castTime() * castTimeScale * delivery.castTimeMul();
        float range = ink.range() * delivery.rangeMul();
        // X 不修改威力 / 持续 / 半径（§21.2 只有消耗 / 冷却 / 释放 / 距离四列）
        float power = powerAxis * sourcePower * powerScale * powerTier;
        if (com.example.akaishi.forbidden.magic.table.MagicIds.Effect.DAMAGE.equals(core.effect())
                && spell.allWords().contains(MagicWord.XI)) {
            power *= 1f + requireEffect(MagicWord.XI.effect()).baseP() / 100f;
        }
        float duration = durationAxis * durationScale * durationTier;
        float radius = radiusAxis * radiusTier;
        // 量化值：核心 E 在**自身单位**下的数字（单位由核心 E 的 Unit 决定，见 MagicEffectTable）。
        // 为什么需要它：威力轴只收 HP / HP_PER_SEC（缺陷 2 的修复），
        // 于是「−20% 属性」「位移 6 格」「破 1 层防护」这类非 HP 量化**一个都传不出去**，
        // 执行器只能靠猜。magnitude 把这条路补上，且与三条轴用同一套规模 / 阶位规则。
        float magnitude = coreBase * sourcePower
                * MagicScalingTable.powerScale(coreUnitCount, k)
                * MagicScalingTable.tierMultiplier(coreUnitCount);

        return new MagicFormulaResult(wordCount, k, mainInk, actualSource, delivery.id(), core.id(),
                validation.wordSynergies().size(), synergyCostMul, synergyPowerMul,
                cost, cost / 100f, cooldown, castTime, range, power, duration, radius, magnitude,
                powerTier, sourcePower, sourceCost, pricing);
    }

    /**
     * 词的角色权重（§8.5「角色权重」+ §23.4 + §24.2 规则 6）。
     *
     * <p><b>"合成一条"的准确含义</b>：协同段的次词<b>不再升格为第二核心</b>，而是回到普通修饰权重。
     *
     * <p>⚠ 早期实现把协同段的次词权重记作 <b>0</b>（"彻底并入"），那会造成一个荒唐结果 ——
     * `钉 + 蛇` 在 2 词时（协同未解锁）控制 4.03s，到 4 词阶协同解锁后反而<b>掉回 3.00s</b>：
     * 加了词、解锁了能力，效果却变差。故改为"次词按修饰权重出力"，
     * 协同的价值由 {@code powerMul} 与 P5 执行器的机制（如「锢缚」取 max）承担。
     *
     * @param index         该词在本段内的下标
     * @param isCoreSegment 本段是否含核心词
     * @param canDouble     词数是否达到双核解锁阶
     * @param hasSynergy    本段是否命中协同
     */
    private static float roleWeight(MagicWord word, int index, MagicWord core,
            boolean isCoreSegment, boolean canDouble, boolean hasSynergy) {
        // 全局核心恒为 1.0 —— 用 word == core 判定而不是 index == 0，
        // 因为核心是该段首个「可作核心」的词，段首可能是被跳过的参数修改器（叉 / 层）
        if (word == core) {
            return MagicScalingTable.ROLE_WEIGHT_CORE;
        }
        // 协同段：次词不再升格为第二核心（"合成一条"），回到修饰权重
        if (hasSynergy) {
            return MagicScalingTable.ROLE_WEIGHT_MODIFIER;
        }
        // 无协同的核心段，在 6+ 词阶把次词升为第二核心
        // 非主词保持纯修饰，不因词数升为没有独立执行效果的第二核心。
        return MagicScalingTable.ROLE_WEIGHT_MODIFIER;
    }

    private static MagicInkDef requireInk(String id) {
        MagicInkDef result = MagicInkTable.get(id);
        if (result == null) {
            throw new IllegalArgumentException("unknown ink: " + id);
        }
        return result;
    }

    private static MagicEffectDef requireEffect(String id) {
        MagicEffectDef result = MagicEffectTable.get(id);
        if (result == null) {
            throw new IllegalArgumentException("unknown effect: " + id);
        }
        return result;
    }

    private static MagicDeliveryDef requireDelivery(String id) {
        MagicDeliveryDef result = MagicDeliveryTable.get(id);
        if (result == null) {
            throw new IllegalArgumentException("unknown delivery: " + id);
        }
        return result;
    }

    /**
     * 源的 P 倾向。
     *
     * <p><b>待细化（P5）</b>：设计记忆 §2 的「攻击类 / 防御类」细分需要先知道目标的敌我语义
     * （黑源打法防目标要打折、白源反之），而目标解析属 P5。此处先按源的**主倾向**给常数。
     */
    private static float sourcePowerMultiplier(MagicSource source) {
        return switch (source) {
            case SAN -> 1.3f;
            case LIFE -> 1.1f;
            case DARK -> 1.2f;
            case WHITE -> 1.2f;
        };
    }

    /** 源的消耗系数：黑白的防御 / 攻击细分留到目标语义判定层，P4 先用支付基准。 */
    private static float sourceCostMultiplier(MagicSource source) {
        return switch (source) {
            case SAN -> 1.0f;
            case LIFE -> 0.9f;
            case DARK, WHITE -> 1.0f;
        };
    }

    private static float pricingCoefficient(MagicDeliveryDef delivery, float channelSeconds, int volleyShots) {
        return switch (delivery.pricing()) {
            case PER_CAST -> 1f;
            // **至少 1 段**：调用方漏传 / 传 0 时若按 0 计，整段消耗会归零 ——
            // 那是一个"免费大招"的静默漏洞（比照 §21.6 的"不允许透支"同口径：宁可少算，不可白送）
            case PER_SECOND -> Math.max(1f, channelSeconds);
            case PER_SHOT -> Math.max(1, volleyShots);
        };
    }
}

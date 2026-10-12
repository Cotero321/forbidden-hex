package com.example.akaishi.forbidden.magic.formula;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.magic.judge.MagicValidator;
import com.example.akaishi.forbidden.magic.judge.MagicValidation;
import com.example.akaishi.forbidden.magic.table.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 无世界、无主词的组合编译；旧执行链可逐步消费此计划。 */
public final class SpellPlanCompiler {
    private SpellPlanCompiler() {}

    public static SpellCompileResult compile(SpellInput input) {
        List<CompileDiagnostic> diagnostics = new ArrayList<>();
        List<MagicWord> words = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        Set<String> inks = new LinkedHashSet<>();
        if (input.words().size() > 8 || input.wordInks().size() > 8
                || input.words().size() != input.wordInks().size())
            error(diagnostics, "slot_layout", -1, "Words/WordInks must align within eight slots");
        if (hasErrors(diagnostics)) return new SpellCompileResult(null, diagnostics);
        MagicSource source = null;
        for (MagicSource candidate : MagicSource.values())
            if (candidate.id().equals(input.sourceId())) source = candidate;
        if (source == null) error(diagnostics, "unknown_source", -1, input.sourceId());
        if (MagicInkTable.get(input.mainInkId()) == null)
            error(diagnostics, "unknown_ink", -1, input.mainInkId());
        else inks.add(input.mainInkId());
        if (!Float.isFinite(input.channelSeconds()) || input.channelSeconds() < 0 || input.channelSeconds() > 6
                || input.volleyShots() < 0 || input.volleyShots() > 10)
            error(diagnostics, "schedule_bounds", -1, "channel=0..6 seconds; volley=0..10 shots");
        for (int slot = 0; slot < input.words().size(); slot++) {
            String id = input.words().get(slot);
            if (id.isEmpty()) continue;
            MagicWord word = MagicWord.byId(id);
            if (word == null) {
                error(diagnostics, "unknown_word", slot, id);
                continue;
            }
            words.add(word);
            slots.add(slot);
            String ink = inkAt(input, slot);
            if (MagicInkTable.get(ink) == null) error(diagnostics, "unknown_ink", slot, ink);
            else inks.add(ink);
        }
        MagicValidation validation = MagicValidator.validateComposition(words, inks);
        if (!validation.isOk()) {
            if (validation.conflicts().isEmpty()) error(diagnostics, "syntax", -1, validation.detail());
            else for (MagicValidation.Conflict conflict : validation.conflicts())
                error(diagnostics, "conflict_" + conflict.kind().name().toLowerCase(java.util.Locale.ROOT),
                        -1, conflict.detail());
        }
        for (int i = 0; i < words.size(); i++) {
            MagicWord word = words.get(i);
            if (unsupported(word)) error(diagnostics, "unsupported_word", slots.get(i), word.id());
            if (word == MagicWord.CHA)
                info(diagnostics, "parameter_fork", slots.get(i), "target limit +1; cost x1.2");
        }
        if (inks.contains(MagicIds.Ink.XING) && source != MagicSource.SAN)
            error(diagnostics, "star_requires_san", -1, "xing requires san source");
        if (!words.isEmpty() && words.stream().allMatch(w -> w.effect() == null))
            error(diagnostics, "no_effect", -1, "parameters require a supported effect operation");
        String deliveryId = resolveDelivery(words, diagnostics);
        boolean chainTarget = words.contains(MagicWord.SI) || words.contains(MagicWord.GEN) || words.contains(MagicWord.LIAN);
        boolean sphereTarget = words.contains(MagicWord.JI_TRACE);
        boolean singleTarget = words.contains(MagicWord.XI) || words.contains(MagicWord.ZHEN) || words.contains(MagicWord.FENG_SEAM);
        if ((chainTarget ? 1 : 0) + (sphereTarget ? 1 : 0) + (singleTarget ? 1 : 0) > 1)
            error(diagnostics, "target_conflict", -1, "incompatible target shapes");
        if (hasErrors(diagnostics)) return new SpellCompileResult(null, diagnostics);

        Set<String> features = new LinkedHashSet<>();
        features.add("composition_execution");
        if (source == MagicSource.SAN) {
            features.add("san_power_1_3");
            features.add("san_mental_piercing");
            info(diagnostics, "san_mental_piercing", -1, "SAN damage uses registered forbidden_hex:psychic: bypasses armor, resistance and enchantments; retains absorption, invulnerability and COG psychic guard");
        }
        if (inks.contains(MagicIds.Ink.XING)) {
            features.add("star_exclusive");
            features.add("star_damage_1_75");
            info(diagnostics, "star_damage_1_75", -1, "exclusive SAN ink: damaging HP operations x1.75; existing ink cost, cooldown and range retained");
        }
        if (inks.contains(MagicIds.Ink.HUN))
            info(diagnostics, "mixed_ink_fixed_baseline", -1, "random ink uses existing fixed baseline");
        int overlays = Math.max(0, inks.size() - 1);
        MagicDeliveryDef delivery = MagicDeliveryTable.get(deliveryId);
        MagicInkDef mainInk = MagicInkTable.get(input.mainInkId());
        boolean channel = MagicIds.Delivery.CHANNEL.equals(deliveryId);
        boolean volley = MagicIds.Delivery.VOLLEY.equals(deliveryId);
        float seconds = channel ? (input.channelSeconds() == 0f ? 6f : Math.max(1f, input.channelSeconds())) : 0f;
        int shots = volley ? (input.volleyShots() == 0 ? 5 : input.volleyShots()) : 1;
        float pricing = channel ? seconds : shots;
        int segments = channel ? Math.max(1, Math.round(seconds)) : shots;
        SchedulePlan schedule = new SchedulePlan(segments, channel ? 1f : volley ? 0.1f : 0f,
                seconds, shots, words.contains(MagicWord.TOU) && words.contains(MagicWord.BENG) ? 1 : 0,
                delivery.continuous());
        boolean starInk = inks.contains(MagicIds.Ink.XING);
        List<EffectOperation> operations = operations(words, slots, overlays, source, validation, starInk);
        if (MagicIds.Delivery.ATTACH.equals(deliveryId)) {
            operations = operations.stream().map(op -> MagicIds.Effect.DAMAGE.equals(op.effectId())
                    ? new EffectOperation(op.effectId(), op.unit(), op.category(), op.slots(), op.power(), op.magnitude(),
                            Math.max(5f, op.durationSeconds()), op.radius(), op.traits()) : op).toList();
        }
        info(diagnostics, "builtin_baseline", -1,
                "operations use current primitive consumers; word-specific unimplemented design traits are not implied");
        TargetPlan target = target(words, deliveryId);
        DeliveryPlan deliveryPlan = new DeliveryPlan(deliveryId, mainInk.range() * delivery.rangeMul(),
                mainInk.cooldown() * MagicScalingTable.cooldownScale(words.size(), overlays) * delivery.cooldownMul(),
                mainInk.castTime() * MagicScalingTable.castTimeScale(words.size(), overlays) * delivery.castTimeMul());
        List<CostBreakdown.WordCost> costs = new ArrayList<>();
        float baseCost = 0f;
        for (int i = 0; i < words.size(); i++) {
            MagicWord word = words.get(i);
            String ink = inkAt(input, slots.get(i));
            float cost = word == MagicWord.CHA ? 0f
                    : MagicEffectTable.get(word.effect()).cost() * word.wP() * MagicInkTable.get(ink).costMul();
            baseCost += cost;
            costs.add(new CostBreakdown.WordCost(slots.get(i), word.id(), ink, cost));
        }
        float scale = MagicScalingTable.costScale(words.size(), overlays) * (source == MagicSource.LIFE ? 0.9f : 1f)
                * (words.contains(MagicWord.CHA) ? 1.2f : 1f);
        float synergyCost = 1f;
        for (MagicSynergyTable.WordSynergy synergy : validation.wordSynergies()) synergyCost *= synergy.costMul();
        scale *= synergyCost;
        float total = baseCost * scale * delivery.costMul() * pricing;
        CostBreakdown cost = new CostBreakdown(costs, baseCost, scale, delivery.costMul(), pricing,
                total, total / 100f, total / segments);
        return new SpellCompileResult(new SpellPlan(input, operations, target, deliveryPlan, schedule, cost, features), diagnostics);
    }

    private static List<EffectOperation> operations(List<MagicWord> words, List<Integer> slots,
                                                   int overlays, MagicSource source, MagicValidation validation, boolean starInk) {
        List<EffectOperation> result = new ArrayList<>();
        Set<String> effects = new LinkedHashSet<>();
        for (MagicWord word : words) if (word.effect() != null) effects.add(word.effect());
        for (String id : effects) {
            MagicEffectDef effect = MagicEffectTable.get(id);
            List<MagicWord> family = words.stream().filter(w -> id.equals(w.effect()))
                    .sorted(Comparator.comparingDouble(MagicWord::wP).reversed().thenComparing(MagicWord::id)).toList();
            List<Integer> familySlots = new ArrayList<>();
            float magnitude = 0f, duration = 0f, radius = 0f;
            for (int i = 0; i < family.size(); i++) {
                MagicWord word = family.get(i);
                familySlots.add(slots.get(words.indexOf(word)));
                // ★v20 百分比加成模式：magnitude 只取首词（i==0）的 baseP 作为基底，
                // 其余同类词不再叠加 baseP，而是通过 powerScale（POWER_BONUS_PER_WORD）给百分比加成。
                // 持续轴 / 半径轴仍沿用加法叠加（只有威力轴改为百分比加成）。
                float weight = (i == 0 ? 1f : 0.25f) * MagicScalingTable.sameSourceDecay(i);
                for (MagicSynergyTable.WordSynergy synergy : validation.wordSynergies()) {
                    if (synergy.role() == word.role()) weight *= synergy.powerMul();
                }
                if (i == 0) {
                    magnitude = effect.baseP() * word.wP() * weight;
                }
                duration += effect.duration() * word.wD() * weight;
                radius += effect.radius() * word.wR() * weight;
            }
            int count = family.size();
            float sourcePower = switch (source) { case SAN -> 1.3f; case LIFE -> 1.1f; case DARK, WHITE -> 1.2f; };
            magnitude *= sourcePower * MagicScalingTable.powerScale(count, overlays) * MagicScalingTable.tierMultiplier(count);
            duration *= MagicScalingTable.durationScale(count) * MagicScalingTable.tierMultiplier(count);
            radius *= MagicScalingTable.tierMultiplier(count);
            Set<String> traits = new LinkedHashSet<>();
            boolean damaging = MagicIds.Effect.DAMAGE.equals(id) || MagicIds.Effect.FIELD.equals(id)
                    || MagicIds.Effect.LINK.equals(id);
            if (damaging && source == MagicSource.SAN) traits.add("san_mental_piercing");
            if (damaging && (words.contains(MagicWord.JIAN) || words.contains(MagicWord.ZHEN))) traits.add("piercing");
            if (damaging && starInk) {
                magnitude *= 1.75f;
                traits.add("star_damage_1_75");
            }
            if (MagicIds.Effect.FIELD.equals(id)) traits.add("field_pulse_1hz");
            if (MagicIds.Effect.DAMAGE.equals(id) && words.stream().anyMatch(w -> MagicIds.Delivery.ATTACH.equals(w.delivery())))
                traits.add("attached_dot_1hz");
            if (MagicIds.Effect.DAMAGE.equals(id)) {
                if (words.contains(MagicWord.JIAN) || words.contains(MagicWord.ZHEN)) traits.add("piercing");
                if (words.contains(MagicWord.TOU) && words.contains(MagicWord.BENG)) traits.add("followup_hit");
            }
            if (MagicIds.Effect.BREAK_TERMINATE.equals(id)) {
                if (words.contains(MagicWord.JING)) traits.add("purify_negative");
                if (words.contains(MagicWord.DUAN)) traits.add("terminate_beneficial");
            }
            boolean hp = effect.unit() == MagicEffectDef.Unit.HP || effect.unit() == MagicEffectDef.Unit.HP_PER_SEC;
            result.add(new EffectOperation(id, effect.unit().name(), effect.category().name(), familySlots,
                    hp ? magnitude : 0f, magnitude, duration, radius, traits));
        }
        return result;
    }

    private static String resolveDelivery(List<MagicWord> words, List<CompileDiagnostic> diagnostics) {
        Set<String> required = new LinkedHashSet<>();
        boolean guidedVolley = words.contains(MagicWord.LIU) && words.contains(MagicWord.BENG);
        for (MagicWord word : words) {
            if (word.delivery() == null || MagicIds.Delivery.INSTANT.equals(word.delivery())) continue;
            if (word == MagicWord.BENG && (words.contains(MagicWord.TOU) || guidedVolley)) continue;
            required.add(word.delivery());
        }
        if (required.size() > 1) error(diagnostics, "delivery_conflict", -1, String.join(" vs ", required));
        return guidedVolley ? MagicIds.Delivery.CHANNEL
                : required.isEmpty() ? MagicIds.Delivery.INSTANT : required.iterator().next();
    }

    private static TargetPlan target(List<MagicWord> words, String delivery) {
        String shape = MagicIds.Delivery.INSTANT.equals(delivery)
                && words.stream().allMatch(w -> MagicIds.Effect.WARD.equals(w.effect()) || w == MagicWord.CHA) ? "SELF"
                : MagicIds.Delivery.PLACE.equals(delivery) ? "AREA"
                : words.contains(MagicWord.SI) || words.contains(MagicWord.GEN) || words.contains(MagicWord.LIAN) ? "CHAIN"
                : words.contains(MagicWord.JI_TRACE) ? "SPHERE"
                : MagicIds.Delivery.CHANNEL.equals(delivery) ? "RAY" : "SINGLE";
        return new TargetPlan(shape, "SINGLE".equals(shape) ? (words.contains(MagicWord.CHA) ? 2 : 1) : 8,
                !words.contains(MagicWord.MEN) && !words.contains(MagicWord.ZHE),
                words.contains(MagicWord.JI_TRACE) || words.contains(MagicWord.BIAO));
    }

    private static boolean unsupported(MagicWord word) {
        // ★v20：原 8 个 unsupported 词已全部更换 E/X 并迁入 B 组，不再有不支持的词
        return false;
    }

    private static String inkAt(SpellInput input, int slot) {
        return slot < input.wordInks().size() && !input.wordInks().get(slot).isEmpty()
                ? input.wordInks().get(slot) : input.mainInkId();
    }

    private static boolean hasErrors(List<CompileDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(d -> d.severity() == CompileDiagnostic.Severity.ERROR);
    }
    private static void error(List<CompileDiagnostic> out, String code, int slot, String detail) {
        out.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, code, slot, detail));
    }
    private static void info(List<CompileDiagnostic> out, String code, int slot, String detail) {
        out.add(new CompileDiagnostic(CompileDiagnostic.Severity.INFO, code, slot, detail));
    }
}

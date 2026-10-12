package com.example.akaishi.forbidden.magic.formula;

import com.example.akaishi.forbidden.api.magic.*;

import java.util.ArrayList;
import java.util.List;

public final class SpellPlanSelfCheck {
    private SpellPlanSelfCheck() {}

    private static SpellCompileResult compile(String ink, String source, String... words) {
        return SpellPlanCompiler.compile(new SpellInput(List.of(words),
                java.util.Collections.nCopies(words.length, ink), ink, source, 0, 0));
    }

    public static List<String> inspect() {
        List<String> failures = new ArrayList<>();
        check(!compile("chi", "life").ok(), "empty accepted", failures);
        rejected(compile("chi", "life", "cha"), "no_effect", failures);
        rejected(SpellPlanCompiler.compile(new SpellInput(List.of("tou"), List.of(), "chi", "life", 0, 0)),
                "slot_layout", failures);
        var single = compile("chi", "life", "tou");
        check(single.ok() && single.plan().operations().size() == 1
                && single.plan().delivery().deliveryId().equals("projectile"), "single projectile", failures);
        var damage = compile("chi", "life", "tou", "jian", "beng");
        check(damage.ok() && damage.plan().operations().get(0).traits().contains("piercing")
                && damage.plan().schedule().followupHits() == 1
                && damage.plan().delivery().deliveryId().equals("projectile"), "piercing followup", failures);
        var ward = compile("rang", "life", "hu", "zhao_cover");
        check(ward.ok() && ward.plan().operations().size() == 1
                && ward.plan().operations().get(0).effectId().equals("ward")
                && ward.plan().target().shape().equals("AREA"), "ward cover", failures);
        var link = compile("chi", "life", "si", "lian");
        check(link.ok() && link.plan().operations().size() == 2
                && link.plan().target().shape().equals("CHAIN"), "link ripple", failures);
        rejected(compile("chi", "life", "tou", "zhao_cover"), "delivery_conflict", failures);
        rejected(compile("chi", "life", "si", "ji_trace"), "target_conflict", failures);
        rejected(compile("chi", "life", "tou", "ceng"), "unsupported_word", failures);
        rejected(compile("chi", "life", "tou", "yuan"), "unsupported_word", failures);
        rejected(compile("chi", "life", "duan", "si"), "conflict_word_axis", failures);
        rejected(compile("xing", "life", "tou"), "star_requires_san", failures);
        var star = compile("xing", "san", "tou");
        check(star.ok() && star.plan().features().contains("star_exclusive")
                && star.plan().features().contains("san_mental_piercing")
                && star.plan().features().contains("star_damage_1_75")
                && star.plan().operations().get(0).traits().contains("san_mental_piercing"), "star SAN features", failures);
        var san = compile("chi", "san", "tou");
        check(Math.abs(star.plan().operations().get(0).power() / san.plan().operations().get(0).power() - 1.75f) < 0.0001f,
                "star output multiplier", failures);
        var guidedVolley = compile("chi", "life", "liu", "beng");
        check(guidedVolley.ok() && guidedVolley.plan().delivery().deliveryId().equals("channel")
                && guidedVolley.plan().schedule().segments() == 6
                && guidedVolley.plan().operations().get(0).traits().contains("followup_hit"),
                "guided volley", failures);
        check(compile("chi", "life", "liu").plan().schedule().segments() == 6, "default channel segments", failures);
        check(compile("chi", "life", "beng").plan().schedule().segments() == 5, "default volley segments", failures);
        var reversed = compile("chi", "life", "beng", "jian", "tou");
        check(damage.ok() && reversed.ok()
                && damage.plan().delivery().equals(reversed.plan().delivery())
                && Math.abs(damage.plan().operations().get(0).power() - reversed.plan().operations().get(0).power()) < 0.0001f,
                "slot order changed output", failures);
        var sparse = SpellPlanCompiler.compile(new SpellInput(List.of("", "tou", "", "jian", "", "", "", ""),
                List.of("", "chi", "", "ju", "", "", "", ""), "chi", "life", 0, 0));
        check(sparse.ok() && sparse.plan().input().words().size() == 8
                && sparse.plan().cost().words().get(1).slot() == 3
                && sparse.plan().cost().words().get(1).inkId().equals("ju"), "sparse word ink alignment", failures);
        var mixed = compile("chi", "life", "tou", "pian");
        check(mixed.ok() && mixed.plan().operations().size() == 2
                && mixed.plan().operations().stream().filter(o -> o.effectId().equals("mind"))
                .allMatch(o -> o.power() == 0 && o.magnitude() > 0), "mixed units lost", failures);
        rejected(SpellPlanCompiler.compile(new SpellInput(List.of("tou", "jian"), List.of("xing", "chi"),
                "xing", "san", 0, 0)), "conflict_star_exclusive", failures);
        for (String word : List.of("qi_open", "duan", "qian", "qu", "biao", "ding_nail", "lie", "zhe",
                "ji_draw", "she", "jiao_cross", "qi_flag", "jing", "xi", "gen", "zhen", "ji_trace",
                "feng_seam", "ji_base", "wo", "dian", "wa", "jie", "men", "xuan", "huan", "gu",
                "bo", "pian", "jiao_angle", "feng_peak", "xie", "ding_cap"))
            check(compile("chi", "life", word).ok(), "supported word rejected: " + word, failures);
        for (int count : new int[]{2, 5, 8}) {
            var input = List.of("tou", "jian", "zhen", "xi", "cha", "dian", "wa", "qu").subList(0, count);
            var result = compile("chi", "life", input.toArray(String[]::new));
            check(result.ok(), "legal full sentence " + count + result.diagnostics(), failures);
            if (result.ok()) {
                var plan = result.plan();
                check(plan.cost().words().size() == count && plan.operations().stream().anyMatch(op -> op.power() > 0),
                        "per-word cost/output " + count, failures);
                check(Math.abs(plan.cost().segmentCost() * plan.schedule().segments() - plan.cost().total()) < 0.001f,
                        "cost conservation " + count, failures);
            }
        }
        rejected(compile("chi", "life", "zhao_cover", "tou"), "delivery_conflict", failures);
        rejected(compile("chi", "life", "ji_trace", "si"), "target_conflict", failures);
        check(com.example.akaishi.forbidden.magic.exec.MagicPulseTiming.pulses(20, 1, 20, false) == 1
                && com.example.akaishi.forbidden.magic.exec.MagicPulseTiming.pulses(21, 1, 20, false) == 1
                && com.example.akaishi.forbidden.magic.exec.MagicPulseTiming.pulses(21, 1, 20, true) == 2,
                "expiry first pulse boundary", failures);
        var dot = compile("chi", "san", "feng_seam").plan();
        check(com.example.akaishi.forbidden.magic.exec.MagicPulseTiming.perSegment(dot, dot.operations().get(0)) == 5,
                "attached five pulses", failures);
        return failures;
    }

    private static void rejected(SpellCompileResult result, String code, List<String> failures) {
        check(!result.ok() && result.plan() == null && result.diagnostics().stream().anyMatch(d -> d.code().equals(code)),
                "missing rejection " + code, failures);
    }
    private static void check(boolean condition, String detail, List<String> failures) {
        if (!condition) failures.add(detail);
    }
    public static void main(String[] args) {
        List<String> failures = inspect();
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.println("SpellPlanSelfCheck: PASS");
    }
}

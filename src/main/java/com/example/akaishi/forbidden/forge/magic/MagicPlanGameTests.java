package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter;
import com.example.akaishi.forbidden.api.magic.SpellPlans;
import com.example.akaishi.forbidden.api.magic.SpellInput;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class MagicPlanGameTests {
    private MagicPlanGameTests() {
    }

    @GameTest(template = "brewing_empty")
    public static void spell_plan_contracts(GameTestHelper helper) {
        helper.assertTrue(AkaishiMagicRegistryChecker.run() == 1, "Magic aggregate self-checks failed; see AkaishiMagic log");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spell_followup_applies_second_hit(GameTestHelper helper) {
        for (List<String> words : List.of(List.of("liu", "beng"), List.of("tou", "beng"),
                List.of("liu"), List.of("beng"))) {
            var result = SpellPlans.compile(new SpellInput(words, Collections.nCopies(words.size(), "chi"),
                    "chi", "life", 0f, 0));
            helper.assertTrue(result.ok(), "Cannot compile followup regression " + words);
            var plan = result.plan();
            var operation = plan.operations().get(0);
            Cow caster = new Cow(EntityType.COW, helper.getLevel());
            Cow target = new Cow(EntityType.COW, helper.getLevel());
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200d);
            target.setHealth(200f);
            try {
                MagicContext context = new MagicContext(caster, helper.getLevel(), caster.position(), "life", "chi",
                        List.of("chi"), words, 6, SpellPlanAdapter.snapshot(plan, operation));
                float before = target.getHealth();
                int hitCount = words.size() == 2 ? 2 : 1;
                float expected = ExecSupport.power(context) * hitCount;
                helper.assertTrue(ExecSupport.dealDamage(context, target), "No real hit for " + words);
                helper.assertTrue(Math.abs(before - target.getHealth() - expected) < 0.001f,
                        "Wrong real followup damage for " + words + ": " + (before - target.getHealth()) + " != " + expected);
                helper.assertTrue(target.invulnerableTime > 0, "Followup removed normal invulnerability for " + words);
            } finally {
                target.discard();
                caster.discard();
            }
        }
        helper.succeed();
    }
}

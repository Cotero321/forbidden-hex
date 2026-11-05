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

    @GameTest(template = "brewing_empty")
    public static void field_uses_own_power_ratio(GameTestHelper helper) {
        var result = SpellPlans.compile(new SpellInput(List.of("bei"), List.of("chi"), "chi", "san", 0f, 0));
        helper.assertTrue(result.ok(), "Cannot compile field regression");
        var plan = result.plan();
        Cow caster = new Cow(EntityType.COW, helper.getLevel());
        Cow target = new Cow(EntityType.COW, helper.getLevel());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200d);
        target.setHealth(200f);
        target.getAttribute(com.example.akaishi.forbidden.magic.MagicAttributes.MAGIC_RESISTANCE.get()).setBaseValue(0.25d);
        try {
            var context = new MagicContext(caster, helper.getLevel(), caster.position(), "san", "chi",
                    List.of("chi"), List.of("bei"), 6, SpellPlanAdapter.snapshot(plan, plan.operations().get(0)));
            float base = ExecSupport.power(context) + ExecSupport.primaryDamageBonus();
            float ratio = context.snapshot().magnitude() / 2f;
            float expected = base * (0.75f + Math.min(0.1f * ratio, 0.4f));
            helper.assertTrue(ExecSupport.dealDamage(context, target), "Field did not hit");
            helper.assertTrue(Math.abs(200f - target.getHealth() - expected) < 0.001f,
                    "Field used DAMAGE baseline or reduced the SAN component with magic resistance");
        } finally {
            caster.discard();
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void link_preserves_damage_snapshot(GameTestHelper helper) {
        Cow caster = new Cow(EntityType.COW, helper.getLevel());
        Cow first = new Cow(EntityType.COW, helper.getLevel());
        Cow second = new Cow(EntityType.COW, helper.getLevel());
        for (Cow target : List.of(first, second)) {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200d);
            target.setHealth(200f);
            target.getAttribute(com.example.akaishi.forbidden.magic.MagicAttributes.MAGIC_RESISTANCE.get()).setBaseValue(0.25d);
        }
        int count = com.example.akaishi.forbidden.magic.link.MagicLinks.create(helper.getLevel(),
                List.of(first, second), 10f, 20, caster, null, "yuan", "san", 2f);
        helper.assertTrue(count == 2, "Link not created without a visual payload");
        helper.runAfterDelay(6, () -> {
            try {
                helper.assertTrue(Math.abs(first.getHealth() - 190.5f) < 0.001f
                                && Math.abs(second.getHealth() - 190.5f) < 0.001f,
                        "Link lost main ink / SAN ratio or did not conduct to both targets");
                helper.assertTrue(first.invulnerableTime > 0 && second.invulnerableTime > 0,
                        "Link removed normal invulnerability");
                helper.succeed();
            } finally {
                caster.discard();
                first.discard();
                second.discard();
            }
        });
    }

    @GameTest(template = "brewing_empty")
    public static void san_hit_preserves_boss_damage_gates(GameTestHelper helper) {
        var boss = new com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity(
                com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities.AGAITOLOS.get(), helper.getLevel());
        var player = helper.makeMockPlayer();
        try {
            float before = boss.getHealth();
            helper.assertTrue(!com.example.akaishi.forbidden.magic.SpellDamage.hurt(boss, null, "chi", "san", 100f, 4f),
                    "SAN bypassed boss non-player source immunity");
            helper.assertTrue(com.example.akaishi.forbidden.magic.SpellDamage.hurt(boss, player, "chi", "san", 10f, 4f),
                    "SAN player hit rejected by idle boss");
            helper.assertTrue(Math.abs(before - boss.getHealth() - 5.6f) < 0.001f,
                    "SAN component was swallowed or bypassed boss 60% reduction");
            float after = boss.getHealth();
            helper.assertTrue(!com.example.akaishi.forbidden.magic.SpellDamage.hurt(boss, player, "chi", "san", 100f, 4f)
                    && Math.abs(after - boss.getHealth()) < 0.001f, "SAN bypassed boss 4-tick gate");
            helper.assertTrue(com.example.akaishi.forbidden.magic.MagicDamageTypes.psychic(helper.getLevel(), player)
                    .is(com.example.akaishi.effect.ModDamageTypes.PSYCHIC), "Boss psychic output key changed");
        } finally {
            boss.discard();
            player.discard();
        }
        var capped = new com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity(
                com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities.AGAITOLOS.get(), helper.getLevel());
        var caster = helper.makeMockPlayer();
        try {
            float before = capped.getHealth();
            float cap = Math.max(24f, capped.getMaxHealth() * 0.05f);
            helper.assertTrue(com.example.akaishi.forbidden.magic.SpellDamage.hurt(capped, caster, "chi", "san", 10000f, 4f),
                    "Large player SAN hit rejected");
            helper.assertTrue(Math.abs(before - capped.getHealth() - cap) < 0.001f,
                    "SAN components bypassed the boss single-hit cap");
        } finally {
            capped.discard();
            caster.discard();
        }
        var respawning = new com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity(
                com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities.AGAITOLOS.get(), helper.getLevel());
        var attacker = helper.makeMockPlayer();
        try {
            var nbt = new net.minecraft.nbt.CompoundTag();
            respawning.addAdditionalSaveData(nbt);
            nbt.putInt("AgaitolosRespawnTicks", 20);
            respawning.readAdditionalSaveData(nbt);
            helper.assertTrue(respawning.isRespawning(), "Respawn state not restored for gate regression");
            helper.assertTrue(!com.example.akaishi.forbidden.magic.SpellDamage.hurt(respawning, attacker, "xing", "san", 100f, 4f),
                    "Star psychic damage bypassed boss respawn immunity");
        } finally {
            respawning.discard();
            attacker.discard();
        }
        helper.succeed();
    }
}

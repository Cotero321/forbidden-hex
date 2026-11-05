package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.effect.ModDamageTypes;
import com.example.akaishi.forbidden.api.sanity.SanityServices;
import com.example.akaishi.forbidden.magic.MagicAttributes;
import com.example.akaishi.forbidden.magic.MagicDamageTypes;
import com.example.akaishi.forbidden.magic.SpellDamage;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class SpellDamageGameTests {
    private SpellDamageGameTests() {
    }

    @GameTest(template = "brewing_empty")
    public static void spell_damage_ink_types_and_tags(GameTestHelper helper) {
        helper.assertTrue(MagicDamageTypes.INK_TYPES.size() == 11, "Expected ten ordinary inks and star");
        helper.assertTrue(MagicDamageTypes.forInk("yuan").location().getPath().equals("spell_water"), "Yuan must use water");
        for (var entry : MagicDamageTypes.INK_TYPES.entrySet()) {
            var source = MagicDamageTypes.source(helper.getLevel(), null, entry.getKey(), 0f);
            helper.assertTrue(source.is(entry.getValue()), "Wrong damage type for " + entry.getKey());
            helper.assertTrue(source.is(MagicDamageTypes.SPELL_FAMILY), "Missing spell parent for " + entry.getKey());
            boolean star = entry.getKey().equals("xing");
            helper.assertTrue(source.is(MagicDamageTypes.MAGIC_RESISTIBLE) != star, "Wrong resistance tag for " + entry.getKey());
            helper.assertTrue(source.is(ModDamageTypes.PSYCHIC_TYPES) == star, "Wrong psychic tag for " + entry.getKey());
            helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR)
                    && source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                    && source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS), "Missing vanilla bypass tags for " + entry.getKey());
        }
        var boss = MagicDamageTypes.psychic(helper.getLevel(), null);
        helper.assertTrue(boss.is(ModDamageTypes.PSYCHIC_TYPES) && !boss.is(MagicDamageTypes.SPELL_FAMILY), "Boss psychic leaked into spell family");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spell_damage_resistance_attribute_and_cap(GameTestHelper helper) {
        Cow target = cow(helper);
        Player player = helper.makeMockPlayer();
        try {
            helper.assertTrue(player.getAttribute(MagicAttributes.MAGIC_RESISTANCE.get()) != null, "Player lacks magic resistance");
            near(helper, MagicAttributes.resistance(player), 0f, "Player default resistance");
            near(helper, MagicAttributes.resistance(target), 0f, "Cow default resistance");
            for (double resistance : new double[]{0d, 0.25d, 0.8d, 2d}) {
                target.getAttribute(MagicAttributes.MAGIC_RESISTANCE.get()).setBaseValue(resistance);
                float expected = 10f * (1f - (float) Math.min(resistance, 0.8d));
                hit(helper, target, "chi", "life", 1f, expected);
            }
        } finally {
            target.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spell_damage_ignores_vanilla_defenses_not_physical(GameTestHelper helper) {
        Cow target = cow(helper);
        try {
            target.getAttribute(Attributes.ARMOR).setBaseValue(30d);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20d);
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            helmet.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
            target.setItemSlot(EquipmentSlot.HEAD, helmet);
            for (String ink : MagicDamageTypes.INK_TYPES.keySet()) hit(helper, target, ink, "life", 1f, 10f);
            target.removeAllEffects();
            target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0d);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0d);
            target.getAttribute(MagicAttributes.MAGIC_RESISTANCE.get()).setBaseValue(0.8d);
            reset(target);
            helper.assertTrue(target.hurt(target.damageSources().generic(), 10f), "Physical control failed to hit");
            near(helper, 200f - target.getHealth(), 10f, "Magic resistance reduced physical damage");
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spell_damage_san_components_and_star_cog_once(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        var server = helper.getLevel().getServer();
        var previousDifficulty = helper.getLevel().getDifficulty();
        try {
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200d);
            helper.assertTrue(SanityServiceImpl.state(player) != null, "Mock player has no real sanity capability");
            SanityServices.get().setCog(player, 200f);
            near(helper, SanityServices.get().getCog(player), 200f, "COG write did not reach real state");
            for (double resistance : new double[]{0d, 0.25d, 0.8d}) {
                player.getAttribute(MagicAttributes.MAGIC_RESISTANCE.get()).setBaseValue(resistance);
                for (float power : new float[]{1f, 2f, 4f, 8f}) {
                    float bonus = Math.min(0.1f * power, 0.4f);
                    hit(helper, player, "chi", "san", power, 10f * (1f - (float) resistance) + 10f * bonus * 0.5f);
                    hit(helper, player, "xing", "san", power, 10f * (1f + bonus) * 0.5f);
                }
                hit(helper, player, "xing", "life", 1f, 5f);
            }
        } finally {
            server.setDifficulty(previousDifficulty, true);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spell_damage_preserves_absorption_and_invulnerability(GameTestHelper helper) {
        Cow target = cow(helper);
        try {
            target.setAbsorptionAmount(3f);
            helper.assertTrue(SpellDamage.hurt(target, null, "chi", "san", 10f, 4f), "Absorption hit rejected");
            near(helper, target.getHealth(), 189f, "SAN total must hit absorption once");
            near(helper, target.getAbsorptionAmount(), 0f, "Absorption was bypassed");
            helper.assertTrue(target.invulnerableTime > 0, "Spell removed vanilla invulnerability");
            helper.assertTrue(!SpellDamage.hurt(target, null, "chi", "san", 10f, 4f), "Equal repeat bypassed invulnerability");
            near(helper, target.getHealth(), 189f, "Repeated spell damaged invulnerable target");
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void zero_san_converts_whole_spell_without_double_reduction(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        var server = helper.getLevel().getServer();
        var previousDifficulty = helper.getLevel().getDifficulty();
        boolean sanityEnabled = com.example.akaishi.config.ModConfig.sanityEnabled;
        try {
            com.example.akaishi.config.ModConfig.sanityEnabled = true;
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200d);
            helper.assertTrue(SanityServiceImpl.state(player) != null, "Missing real player sanity state");
            SanityServices.get().setCog(player, 200f);
            SanityServices.get().setSan(player, 0f);
            for (double resistance : new double[]{0d, 0.25d, 0.8d}) {
                player.getAttribute(MagicAttributes.MAGIC_RESISTANCE.get()).setBaseValue(resistance);
                for (String source : new String[]{"life", "san"}) {
                    float incoming = source.equals("san") ? 14.4f : 12f;
                    for (String ink : new String[]{"chi", "xing"}) {
                        hit(helper, player, ink, source, 2f, incoming * 0.5f);
                        reset(player);
                        player.setAbsorptionAmount(3f);
                        helper.assertTrue(SpellDamage.hurt(player, null, ink, source, 10f, 2f),
                                "Zero-SAN absorption hit rejected");
                        float expected = ink.equals("xing") ? incoming * 0.5f - 3f
                                : (incoming - 3f) * 0.5f;
                        near(helper, 200f - player.getHealth(), expected,
                                "Zero-SAN absorption " + ink + "/" + source);
                        near(helper, player.getAbsorptionAmount(), 0f, "Absorption was not consumed once");
                    }
                }
            }
        } finally {
            com.example.akaishi.config.ModConfig.sanityEnabled = sanityEnabled;
            server.setDifficulty(previousDifficulty, true);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spell_damage_brewing_reduction_only_psychic_component(GameTestHelper helper) {
        Cow target = cow(helper);
        try {
            target.getAttribute(MagicAttributes.MAGIC_RESISTANCE.get()).setBaseValue(0.25d);
            target.addEffect(new MobEffectInstance(
                    com.example.akaishi.forbidden.brewing.BrewingStatusEffects.get(
                            com.example.akaishi.forbidden.brewing.BrewingEffectType.DRUNK), 200, 0));
            hit(helper, target, "chi", "san", 2f, 9.2f);
            hit(helper, target, "chi", "life", 2f, 7.5f);
            hit(helper, target, "xing", "san", 2f, 10.2f);
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    private static Cow cow(GameTestHelper helper) {
        Cow cow = new Cow(EntityType.COW, helper.getLevel());
        helper.assertTrue(cow.getAttribute(MagicAttributes.MAGIC_RESISTANCE.get()) != null, "Cow lacks magic resistance");
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200d);
        reset(cow);
        return cow;
    }

    private static void reset(LivingEntity target) {
        target.invulnerableTime = 0;
        target.setAbsorptionAmount(0f);
        target.setHealth(200f);
    }

    private static void hit(GameTestHelper helper, LivingEntity target, String ink, String source, float power, float expected) {
        reset(target);
        helper.assertTrue(SpellDamage.hurt(target, null, ink, source, 10f, power), "Real hurt rejected " + ink + "/" + source);
        near(helper, 200f - target.getHealth(), expected, ink + "/" + source + " S=" + power);
    }

    private static void near(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.001f, message + ": " + actual + " != " + expected);
    }
}

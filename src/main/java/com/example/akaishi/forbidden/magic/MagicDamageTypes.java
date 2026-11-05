package com.example.akaishi.forbidden.magic;

import com.example.akaishi.AkaishiMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * 法术伤害以 {@link #SPELL_FAMILY} 为父级，主墨决定子类型。
 * 常规法术只读取魔法抗性；星墨独立为法术精神类型，保留既有精神防御。
 * 共享的非施法精神伤害键不属于法术父级，避免改变 BOSS 与理智系统。
 */
public final class MagicDamageTypes {

    /** 法术伤害：数据层见 {@code data/forbidden_hex/damage_type/spell.json} */
    public static final ResourceKey<DamageType> SPELL = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "spell"));

    public static final TagKey<DamageType> SPELL_FAMILY = tag("spell");
    public static final TagKey<DamageType> MAGIC_RESISTIBLE = tag("magic_resistible");
    public static final ResourceKey<DamageType> SPELL_PSYCHIC = key("spell_psychic");
    public static final Map<String, ResourceKey<DamageType>> INK_TYPES = Map.ofEntries(
            Map.entry("chi", key("spell_fire")), Map.entry("rang", key("spell_earth")),
            Map.entry("ju", key("spell_storm")), Map.entry("man", key("spell_nature")),
            Map.entry("shi", key("spell_corrosion")), Map.entry("xu", key("spell_order")),
            Map.entry("hun", key("spell_chaos")), Map.entry("yuan", key("spell_water")),
            Map.entry("yao", key("spell_radiance")), Map.entry("an", key("spell_shadow")),
            Map.entry("xing", SPELL_PSYCHIC));

    private MagicDamageTypes() {
    }

    private static ResourceKey<DamageType> key(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, id));
    }

    private static TagKey<DamageType> tag(String id) {
        return TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, id));
    }

    public static ResourceKey<DamageType> forInk(String mainInk) {
        return mainInk == null ? SPELL : INK_TYPES.getOrDefault(mainInk, SPELL);
    }

    /** 非施法来源沿用共享精神伤害键，不套魔法抗性。 */
    public static DamageSource psychic(Level level, Entity caster) {
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(com.example.akaishi.effect.ModDamageTypes.PSYCHIC);
        return caster == null ? new DamageSource(holder) : new DamageSource(holder, caster, caster);
    }

    public static DamageSource source(Level level, Entity caster) {
        return source(level, caster, null, 0f);
    }

    public static DamageSource source(Level level, Entity caster, String mainInk, float psychicFraction) {
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(forInk(mainInk));
        return new SpellSource(holder, caster, psychicFraction);
    }

    /** 一次命中携带两种伤害分量，共用目标的承伤闸、锁伤和无敌帧。 */
    public static final class SpellSource extends DamageSource {
        private final float psychicFraction;

        private SpellSource(Holder<DamageType> type, Entity caster, float psychicFraction) {
            super(type, caster, caster);
            this.psychicFraction = Float.isFinite(psychicFraction)
                    ? Math.max(0f, Math.min(1f, psychicFraction)) : 0f;
        }

        public float psychicFraction() {
            return psychicFraction;
        }
    }
}

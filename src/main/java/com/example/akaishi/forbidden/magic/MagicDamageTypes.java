package com.example.akaishi.forbidden.magic;

import com.example.akaishi.AkaishiMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 魔法侧伤害类型键的<b>唯一真源</b>（设计记忆 §11：「新增 {@code forbidden_hex:spell}」）。
 *
 * <p><b>为什么不直接用原版的 {@code magic}</b>：法术伤害需要与其它魔法效果区分开 ——
 * 击杀归属、死亡消息、以及将来"破绽 / 生命侵蚀"这类状态对法术伤害的识别，
 * 都要有一个专属类型可挂。用原版 {@code magic} 会让"谁打死的"这类信息糊在一起。
 *
 * <p><b>数据层</b>：{@code data/forbidden_hex/damage_type/spell.json}；
 * 双语死亡消息 {@code death.attack.forbidden_hex.spell[.player]}（RULES §0 三维度：注册 / 生效 / 可见）。
 * 刻意<b>不进</b> {@code bypasses_armor} 等绕过标签 —— 法术伤害仍应被护甲与抗性正常削减，
 * 真伤是 BOSS 的专属手段，不是法术的默认行为。
 */
public final class MagicDamageTypes {

    /** 法术伤害：数据层见 {@code data/forbidden_hex/damage_type/spell.json} */
    public static final ResourceKey<DamageType> SPELL = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "spell"));

    private MagicDamageTypes() {
    }

    /**
     * 构造法术伤害源（伤害类型注册于数据包，运行期须从注册表取 holder）。
     *
     * @param caster 施法者；非空时作为攻击者，击杀归属与 {@code .player} 死亡消息才会生效
     */
    public static DamageSource psychic(Level level, Entity caster) {
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(com.example.akaishi.effect.ModDamageTypes.PSYCHIC);
        return caster == null ? new DamageSource(holder) : new DamageSource(holder, caster, caster);
    }

    public static DamageSource source(Level level, Entity caster) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SPELL);
        return caster == null ? new DamageSource(holder) : new DamageSource(holder, caster);
    }
}

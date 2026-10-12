package com.example.akaishi.effect;

import com.example.akaishi.AkaishiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * 中立伤害类型键的<b>唯一真源</b>（既不属于理智系统、也不属于 BOSS 的共享常量）。
 *
 * <p><b>为什么要有这个类</b>：精神伤害（{@code forbidden_hex:psychic}）同时被两套系统消费 ——
 * BOSS 的「天魔＊灾」用它<b>替换</b>自己的伤害类型（造源），理智系统则用它识别"这一段伤害"、
 * 施加 COG 精神减免与 40% 档易伤（读源）。若键仍定义在 BOSS 包（{@code AgaitolosCombat}），
 * 理智侧就必须 import {@code boss.agaitolos}，与"理智系统与 BOSS 必须解耦"的口径自相矛盾。
 * 把<b>键本身</b>挪到中立位置后，双方都只依赖本类，谁都不必 import 对方。
 *
 * <p><b>只搬键、不搬逻辑</b>：这里是纯粹的常量持有处，不含任何造源/结算代码
 * （造源仍在 {@code AgaitolosCombat#psychic}，理智侧的读法仍在各自的处理器里）。
 *
 * <p><b>id 不变</b>：{@link #PSYCHIC} 的注册 id 逐字仍是 {@code forbidden_hex:psychic}，
 * 数据层 {@code data/forbidden_hex/damage_type/psychic.json} 与三条绕过标签（护甲/抗性/附魔）一概未动。
 */
public final class ModDamageTypes {

    /**
     * 精神伤害：BOSS 阶段三「天魔＊灾」的伤害类型，也是理智系统影怪/幻翼/0% 档精神化的承载类型。
     * <p>刻意<b>不</b>进 {@code bypasses_cooldown}：它仍要受目标无敌帧约束
     * （见 {@code AgaitolosCombat#PSYCHIC} 的注释）。
     */
    public static final ResourceKey<DamageType> PSYCHIC = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "psychic"));

    private ModDamageTypes() {
    }
}

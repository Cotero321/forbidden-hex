package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityServices;
import net.minecraft.world.entity.player.Player;

/**
 * 精神伤害减免（内部计算层）：对 {@code forbidden_hex:psychic} 伤害按 COG 系数减伤。
 *
 * <p><b>解耦边界</b>（只做这一件事，且只在这一处）：
 * <ul>
 *   <li>不改动 {@code AgaitolosPsychic} 的 {@code psychic_until} 语义——"谁被改写、何时转永久"与该类无关；</li>
 *   <li>不改 BOSS 的伤害源构造（{@code AgaitolosCombat#psychic}）——本类只对<b>已经施加</b>的伤害量做缩放；</li>
 *   <li>不新增伤害类型、不新增效果、不碰血条。</li>
 * </ul>
 *
 * <p><b>平台挂点</b>：伤害事件属平台 API，common 不可见，故本类只提供纯计算，
 * 由平台侧处理器（forge 的 {@code AkaishiSanityDamageHandler}，挂 {@code LivingHurtEvent}）调用。
 * 平台处理器只负责"是不是精神伤害 + 挨打方是不是玩家"的判定，减免口径全在本类，避免两份系数表。
 *
 * <p><b>与既有伤害管线的叠加顺序</b>：{@code LivingHurtEvent} 在 Forge 1.20.1 的
 * {@code LivingEntity#actuallyHurt} <b>开头</b>触发，早于护甲吸收（{@code getDamageAfterArmorAbsorb}）、
 * 抗性/附魔吸收（{@code getDamageAfterMagicAbsorb}）与伤害吸收（absorption）结算。
 * 之所以在该点缩放是等价的：{@code forbidden_hex:psychic} 已进 {@code bypasses_armor / bypasses_resistance /
 * bypasses_enchantments} 三个标签，上述三步对本类型本就<b>整段跳过</b>，
 * 故"乘在吸收之前"与"乘在吸收之后"数值完全一致；同时该点与项目既有的护甲减伤
 * （{@code AkaishiModForge#onLivingHurt}）同在一个事件里，两条减伤都是<b>乘性缩放</b>当前量，
 * 先后顺序不影响彼此的相对效果（乘法交换）。
 */
public final class SanityDamageGuard {

    private SanityDamageGuard() {
    }

    /**
     * 对玩家受到的 {@code forbidden_hex:psychic} 伤害施加精神减免。
     *
     * <p>无理智数据（系统未就绪 / 无 capability）、非正伤害、无减免档位时<b>原样返回</b>入参，
     * 保证"接不上理智也不影响原伤害"（既不吞伤害也不放大）。
     *
     * @param victim 挨打方（服务端玩家）
     * @param amount 事件当前的伤害量（已是该事件阶段的实际量）
     * @return 减免后的伤害量
     */
    public static float applyPsychicReduction(Player victim, float amount) {
        if (victim == null || !(amount > 0f)) {
            return amount;
        }
        float reduction = SanityCogCurve.psychicDamageReduction(SanityServices.get().getCog(victim));
        if (!(reduction > 0f)) {
            return amount;
        }
        return amount * (1f - reduction);
    }
}

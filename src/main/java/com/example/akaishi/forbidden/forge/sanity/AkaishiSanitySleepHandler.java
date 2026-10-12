package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.effect.ModDamageTypes;
import com.example.akaishi.forbidden.sanity.SanitySleepDeprivation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 睡眠相关机制的<b>平台挂点</b>（forge）：把两个 Forge 事件转成 common 侧的纯逻辑调用。
 *
 * <p><b>为什么挂这两个事件</b>：
 * <ul>
 *   <li>{@link PlayerWakeUpEvent}：由原版 {@code Player#stopSleepInBed} 开头发射
 *       （见 Forge 1.20.1 {@code Player.java.patch}）。{@code wakeImmediately()} 为 false 的路径
 *       就是"睡到天亮"，为 true 的是"被打醒/被强制唤醒"——本类只把前者交给 common 判奖励；</li>
 *   <li>{@link LivingHurtEvent}：识别"幻翼的原版咬击"（源实体是 {@link Phantom}，且伤害类型
 *       <b>不是</b> {@code forbidden_hex:psychic}）后，向 common 记一笔待投递的附加精神伤害。
 *       <b>排除 psychic 是本条与 P4「0% 档俯冲撞击」的去重口径</b>：P4 的撞击本身就走
 *       {@code forbidden_hex:psychic}（{@code SanityPhantomDive#IMPACT_DAMAGE}），若不过滤，
 *       同一次撞击会被判成"幻翼咬击"再加一段，形成重复结算。过滤后两者各自独立、互不放大。</li>
 * </ul>
 *
 * <p><b>为什么不在事件里直接追加伤害</b>：原版 {@code LivingEntity#hurt} 在结算前就把
 * {@code invulnerableTime = 20}，事件内再调 {@code hurt()} 会被无敌帧吞掉（详见
 * {@link SanitySleepDeprivation} 类注释），故改为"记录 → 下一个无敌帧空档投递"。
 */
public final class AkaishiSanitySleepHandler {

    public static final AkaishiSanitySleepHandler INSTANCE = new AkaishiSanitySleepHandler();

    private AkaishiSanitySleepHandler() {
    }

    /** 睡醒：只有"自然睡到天亮"才可能给 +3（周围有怪时 common 侧会跳过奖励） */
    @SubscribeEvent
    public void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.wakeImmediately()) {
            return;
        }
        SanitySleepDeprivation.onWakeUp(player, false);
    }

    /** 幻翼的原版咬击（非 psychic）⇒ 在睡眠剥夺状态下追加一段 psychic 伤害 */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Phantom phantom)) {
            return; // 只认幻翼的出手（含其俯冲链在贴身时结算的原版咬击）
        }
        if (event.getSource().is(ModDamageTypes.PSYCHIC)) {
            return; // 0% 档俯冲撞击（P4）：本身就是 psychic，不重复附加
        }
        if (!SanitySleepDeprivation.isSleepDeprived(player)) {
            return;
        }
        SanitySleepDeprivation.queuePhantomPsychic(player, phantom);
    }
}

package com.example.akaishi.forbidden.effect;

import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 理智恢复（{@code forbidden_hex:sanity_restore}）：由酿造药水施加，<b>逐 tick 把窗口总量分摊补回 SAN</b>。
 *
 * <p><b>为什么用效果承载窗口分摊，而不是在"喝完一口"的事件里一次性补</b>：
 * 一次性补 7/10 点会让药水变成"瞬间回理智的应急包"，也与食补（P1 的窗口分摊）手感割裂；
 * 挂载（{@code com.example.akaishi.forbidden.sanity.content.SanityRestorePotions} 里按 100t = 5s 烘焙进 {@code MobEffectInstance}），
 * 不需要任何按玩家的运行期状态（无状态 ⇒ 无同步、无落盘、无内存泄漏面）。
 *
 * <p><b>总量口径（仅强度决定）</b>：I 级（amplifier 0）共 {@link #TOTAL_LEVEL_1}、II 级共 {@link #TOTAL_LEVEL_2}，
 * 每 tick 施加 {@code 总量 / 窗口时长}，故"效果时长 = 回复窗口"。若外部（指令 / 其它模组）延长了时长，
 * 总量会随时长等比放大 —— 这是"时长即窗口"的必然结果，如实标注。
 * 强度超过 II 级按 II 级处理（防"用指令刷个 100 级"把总量抬到离谱）。
 *
 * <p><b>"红石无效"（不加长时间版本）由配方层保证</b>：{@code PotionBrewing} 的长效版本必须显式
 * 注册 {@code addMix(基础药水, 红石, 长效药水)} 才存在；本模组<b>刻意不注册该条</b>，
 * 故红石粉对本药水无任何配方命中（详见 forge 侧 {@code AkaishiSanityPotionBrewing}）。
 *
 * <p><b>连喝为何不会叠加成倍收益</b>：原版的 {@code LivingEntity#addEffect} 对"同强度且剩余时长更短"的
 * 新实例只做<b>时长刷新</b>、不做总量叠加，因此连喝的第二瓶会把剩余窗口重置为 100t、
 * 却不会把总量翻倍（已付出与待付出的切片仍来自同一总量口径）—— 这正好等价于 P1 的
 * "连续食用收益递减"手感，故本轮不再为药水另做一套链计数（见报告中的差异说明）。
 *
 * <p><b>系数</b>：不乘 COG 食补效力（本效果不经 {@code SanityFoodRegistry} 档位链路），
 * 也<b>不</b>乘自然恢复倍率（{@code SanityCogCurve.naturalRegenMultiplier} 只作用于"自然回复速率"，
 * 药水是道具恢复，两者不该互相放大）。数值均为<b>待调手感值</b>。
 */
public class SanityRestoreEffect extends MobEffect {

    /** 窗口时长（tick）：100 = 5s，与药水烘焙进 {@code MobEffectInstance} 的时长一致（待调手感值） */
    public static final int WINDOW_TICKS = 100;

    /** I 级（amplifier 0）窗口总量：7（待调手感值） */
    public static final float TOTAL_LEVEL_1 = 7f;

    /** II 级（amplifier 1）窗口总量：10（待调手感值） */
    public static final float TOTAL_LEVEL_2 = 10f;

    /** 支持的最高强度下标（超过按最高档处理） */
    private static final int MAX_AMPLIFIER = 1;

    /**
     * 效果主色：{@code 0x4FE3D0}（青蓝）。
     *
     * <p>取值口径：直接取交付的 {@code textures/mob_effect/sanity_restore.png} 的<b>识别色</b>
     * {@code #4FE3D0}（该资产由美术侧按"深轮廓 #123138 + 亮面 #E6FAF7 + 识别色 #4FE3D0"绘制，
     * 见设计记忆 §42.2 第 1/7 条），故粒子颜色与图标一致。
     * 1.20.1 的颜色必须在构造器里定死（图标是贴图、无法反查），图标若改色要同步改这一个常量。
     */
    public static final int COLOR = 0x4FE3D0;

    public SanityRestoreEffect() {
        // 增益类：与原版治疗/再生同族，牛奶桶可清除（同原版口径，不做免疫处理）
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    /** 逐 tick 都参与分摊（窗口总量 / 窗口时长），故恒真 */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    /**
     * 分摊施加：每 tick 补"总量 / 窗口时长"。
     *
     * <p>服务端限定（{@code LivingEntity#tickEffects} 两端都会跑，不判会让客户端也补一份，
     * 与权威值打架）；只对玩家生效（SAN 是玩家属性，生物身上无意义）。
     */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity == null || entity.level() == null || entity.level().isClientSide()
                || !ModConfig.sanityEnabled || !(entity instanceof Player player)) {
            return;
        }
        SanityServiceImpl.instance().addSanInternal(player, sliceOf(amplifier), SanityChangeSource.FOOD);
    }

    /** 单 tick 分摊量 = 总量 / 窗口时长 */
    public static float sliceOf(int amplifier) {
        return totalOf(amplifier) / WINDOW_TICKS;
    }

    /** 该强度的窗口总量（amplifier 0 → 7，≥1 → 10） */
    public static float totalOf(int amplifier) {
        return amplifier >= MAX_AMPLIFIER ? TOTAL_LEVEL_2 : TOTAL_LEVEL_1;
    }
}

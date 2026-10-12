package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.ISanityThresholdHook;
import com.example.akaishi.forbidden.api.sanity.SanityCallbacks;
import com.example.akaishi.forbidden.api.sanity.SanityThresholdRegistry;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 阈值边沿判定与派发：理智百分比跨越 80 / 60 / 40 / 20 / 0 档时通知监听方。
 *
 * <p><b>基准是硬上限</b>（{@code SANC − tempCut}）而不是 SANC：
 * 硬上限才是 SAN 实际被夹取的上界，用 SANC 会让"临时削减上限"期间出现"百分比虚高、
 * 档位与体感不符"（SAN 已经只剩 30%，却因为削减前上限算成 45%）。
 *
 * <p><b>逐档、不合并不跳过</b>：一次变化跨多档时按跨越方向逐档派发——
 * 下坠按 80 → 60 → 40 → 20 → 0（从高档往低档走），回升按 0 → 20 → 40 → 60 → 80。
 * 例：SAN 从 90% 直接掉到 10%，监听方依次收到
 * {@code (80, 进入) → (60, 进入) → (40, 进入) → (20, 进入)}，共 4 次；
 * 反之从 10% 回到 90% 依次收到 {@code (20, 离开) → (40, 离开) → (60, 离开) → (80, 离开)}。
 * 合档会让"由低档产生的持续状态"（例如某档开始播放的低语）在中途一次跳档时被漏掉，
 * 所以宁可多派发也不能合并。
 *
 * <p><b>方向语义</b>：{@code entering = true} 表示跌破该档（进入更差状态），
 * {@code false} 表示回升跨过该档（脱离该状态）；两个方向都会派发，监听方必须都处理。
 *
 * <p>本类只做边沿判定与派发，<b>不含任何档位惩罚内容</b>（那是后续段落的事）。
 */
public final class SanityThresholds {

    private static final Logger LOGGER = LogManager.getLogger("akaishi.sanity");

    /** 档位百分比（相对硬上限），<b>降序</b>：下坠按此序派发（待调手感值） */
    public static final float[] LEVELS = {80f, 60f, 40f, 20f, 0f};

    private SanityThresholds() {
    }

    /** 当前理智百分比（0~100）；硬上限为 0 时恒为 0（此时 SAN 必为 0，档位自然落到最低档） */
    public static float percentOf(float san, float effectiveMax) {
        if (!(effectiveMax > 0f)) {
            return 0f;
        }
        float percent = san / effectiveMax * 100f;
        return Math.max(0f, Math.min(100f, percent));
    }

    /** 当前理智百分比（0~100） */
    public static float percentOf(SanityState state) {
        return percentOf(state.san(), state.effectiveMax());
    }

    /**
     * 边沿判定：给一次变化的前后百分比，把所有被跨越的档位逐档派发出去。
     *
     * <p>只在真正跨档时派发（{@code oldPercent == newPercent} 直接返回），
     * 因此可当作状态机边沿使用。
     */
    public static void evaluate(Player player, float oldPercent, float newPercent) {
        if (player == null || oldPercent == newPercent) {
            return;
        }
        if (newPercent < oldPercent) {
            // 下坠：从高档往低档逐档（LEVELS 已是降序）
            for (float level : LEVELS) {
                if (oldPercent > level && newPercent <= level) {
                    dispatch(player, level, true);
                }
            }
        } else {
            // 回升：从低档往高档逐档
            for (int i = LEVELS.length - 1; i >= 0; i--) {
                float level = LEVELS[i];
                if (oldPercent <= level && newPercent > level) {
                    dispatch(player, level, false);
                }
            }
        }
    }

    /** 派发一次跨档：无 id 的即时监听 + 带 id 的注册钩子（钩子异常隔离，不影响其余钩子与主流程） */
    private static void dispatch(Player player, float level, boolean entering) {
        SanityCallbacks.fireThresholdCrossed(player, level, entering);
        for (ISanityThresholdHook hook : SanityThresholdRegistry.getAll()) {
            try {
                hook.onThreshold(player, level, entering);
            } catch (Throwable t) {
                LOGGER.warn("[akaishi] 理智阈值钩子异常（已隔离）: {}", hook.getClass().getName(), t);
            }
        }
    }
}

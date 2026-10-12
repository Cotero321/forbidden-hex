package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityValues;
import com.example.akaishi.config.ModConfig;

/**
 * 低理智视野表现的<b>客户端强度源</b>（仅客户端读取，服务端从不加载）。
 *
 * <p><b>为什么强度值要在客户端再算一次、而不随包下发</b>：灰化/血丝/黑白是纯表现量，
 * 服务端只需给出权威的 SAN/SANC（已经在 {@link SanitySyncS2C} 里了），
 * 档位换算是一次纯函数 —— 把它放进同步包等于为"已经能算出来的东西"多付带宽。
 *
 * <p><b>档位基准与服务端完全一致</b>：用 {@code SAN/SANC}（不含 tempCut），
 * 与服务端惩罚档位同口径（见 {@link SanityPenalties}），因此"看到的画面"与"吃到的惩罚"永远同步。
 *
 * <p><b>平滑</b>：每秒服务端同步一次数值，客户端按客户端 tick 向目标线性趋近，
 * 避免跨档瞬间画面硬切；趋近系数越大过渡越快。
 */
public final class ClientSanityVision {

    /** 每 tick 向目标趋近的比例（越大过渡越快；待调手感值） */
    private static final float LERP = 0.08f;
    /** 低于此强度视为不可见（用于决定要不要挂后处理链，避免白挂一条全屏 pass） */
    private static final float MIN_VISIBLE = 0.02f;

    /** 各档位（下标同 {@link SanityPenalties}）的 {灰化, 血丝, 黑白} 目标值（待调手感值） */
    private static final float[][] TIER_TARGETS = {
            {0.00f, 0.00f, 0.00f}, // 正常
            {0.15f, 0.00f, 0.00f}, // 80%：微灰
            {0.35f, 0.00f, 0.00f}, // 60%：更灰
            {0.55f, 0.35f, 0.00f}, // 40%：更灰 + 血丝
            {0.75f, 0.50f, 0.80f}, // 20%：黑白（保留少量灰化与血丝）
            {1.00f, 0.70f, 1.00f}  // 0% ：全黑白 + 强血丝
    };

    private static volatile float gray;
    private static volatile float blood;
    private static volatile float mono;

    private ClientSanityVision() {
    }

    /** 客户端 tick 调用：按最新权威快照推进三个强度（渲染线程只读，故字段为 volatile） */
    public static void update() {
        float[] target = targetOf();
        gray = approach(gray, target[0]);
        blood = approach(blood, target[1]);
        mono = approach(mono, target[2]);
    }

    /** 是否有可见表现（后处理是否挂载的判据） */
    public static boolean active() {
        return gray > MIN_VISIBLE || blood > MIN_VISIBLE || mono > MIN_VISIBLE;
    }

    public static float gray() {
        return gray;
    }

    public static float blood() {
        return blood;
    }

    public static float mono() {
        return mono;
    }

    private static float approach(float current, float target) {
        float diff = target - current;
        if (diff * diff < 1e-6f) {
            return target;
        }
        return current + diff * LERP;
    }

    /** 当前档位对应的一组目标强度；总开关关闭 / 尚未收到权威数据时回到全零 */
    private static float[] targetOf() {
        if (!ModConfig.sanityEnabled || !ClientSanityData.hasData()) {
            return TIER_TARGETS[SanityPenalties.TIER_NORMAL];
        }
        SanityValues values = ClientSanityData.snapshot();
        float percent = SanityThresholds.percentOf(values.san(), values.sanc());
        return TIER_TARGETS[SanityPenalties.tierOfPercent(percent)];
    }
}

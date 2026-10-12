package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 理智回调表：<b>纯 common 自建监听</b>，不依赖任何平台事件总线。
 *
 * <p><b>为什么不用平台事件</b>：理智结算发生在 common 的结算层（同一份逻辑跑在 forge 上），
 * 若监听挂在平台事件总线上，common 就得知道各平台的事件类型，等于把公共逻辑绑死在具体平台；
 * 自建表两边一致、且能在 common 内部直接派发。附属也无需区分加载器。
 *
 * <p>四类回调：
 * <ol>
 *   <li>{@link ValueListener} —— 数值变化（谁从多少变到多少）；</li>
 *   <li>{@link ThresholdListener} —— 阈值跨越（百分比 + 方向）；</li>
 *   <li>{@link FirstEncounterListener} —— 首见触发；</li>
 *   <li>{@link EnvironmentListener} —— 环境结算前的<b>否决</b>闸门（可取消）。</li>
 * </ol>
 *
 * <p><b>异常隔离口径</b>（沿用本项目对第三方代码的防御做法，见 {@code ItemTerminalSearch} / {@code ThirdPartyProcesses}）：
 * 每个监听器单独 try/catch <b>Throwable</b>——不是 RuntimeException，
 * 因为附属类缺失时抛的是 {@code NoClassDefFoundError}，只捕 Exception 会让整个结算线程崩掉；
 * 单个监听器抛异常只记 WARN 并继续派发其余监听器与主流程。
 *
 * <p><b>派发方</b>：只有内部结算层可以调用 {@code fireXxx}；附属只注册，不要主动触发，
 * 否则等于自造事件（数值由附属自己宣布变化），会绕过夹取与阈值链。
 */
public final class SanityCallbacks {

    private static final Logger LOGGER = LogManager.getLogger("akaishi.api.sanity");

    /** 拷贝-写入列表：派发期间注册/注销不会抛 ConcurrentModificationException，且不阻塞读 */
    private static final CopyOnWriteArrayList<ValueListener> VALUE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ThresholdListener> THRESHOLD_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<FirstEncounterListener> FIRST_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EnvironmentListener> ENVIRONMENT_LISTENERS = new CopyOnWriteArrayList<>();

    /** 空句柄：监听器为 null 时返回，保证注册方法永远返回非 null */
    private static final Registration NOOP = () -> {
    };

    private SanityCallbacks() {
    }

    // ------------------------------------------------------------------
    // 注册（附属调用）：返回可注销句柄
    // ------------------------------------------------------------------

    /**
     * 注册数值变化监听。
     *
     * <p>同一实例重复注册只保留一份（幂等）；返回的句柄注销时会移除该实例的登记，
     * 因此重复注册拿到的两个句柄效果相同（任一注销即失效）。
     */
    public static Registration registerValueListener(ValueListener listener) {
        return add(VALUE_LISTENERS, listener);
    }

    /** 注册阈值跨越监听；同一实例重复注册幂等 */
    public static Registration registerThresholdListener(ThresholdListener listener) {
        return add(THRESHOLD_LISTENERS, listener);
    }

    /** 注册首见触发监听；同一实例重复注册幂等 */
    public static Registration registerFirstEncounterListener(FirstEncounterListener listener) {
        return add(FIRST_LISTENERS, listener);
    }

    /** 注册环境结算否决监听；同一实例重复注册幂等 */
    public static Registration registerEnvironmentListener(EnvironmentListener listener) {
        return add(ENVIRONMENT_LISTENERS, listener);
    }

    // ------------------------------------------------------------------
    // 派发（仅内部结算层调用）
    // ------------------------------------------------------------------

    /**
     * 派发数值变化。
     *
     * <p>{@code oldValue == newValue} 时不派发（避免"写入相同值"刷回调）。
     */
    public static void fireValueChanged(Player player, SanityStat stat, float oldValue, float newValue,
                                        SanityChangeSource source) {
        if (player == null || oldValue == newValue) {
            return;
        }
        for (ValueListener listener : VALUE_LISTENERS) {
            try {
                listener.onValueChanged(player, stat, oldValue, newValue, source);
            } catch (Throwable t) {
                LOGGER.warn("[akaishi] 理智数值变化监听器异常（已隔离）: {}", listener.getClass().getName(), t);
            }
        }
    }

    /** 派发阈值跨越：{@code percentOfSanc} 为档位百分比（80/60/40/20/0），{@code entering} 为方向 */
    public static void fireThresholdCrossed(Player player, float percentOfSanc, boolean entering) {
        if (player == null) {
            return;
        }
        for (ThresholdListener listener : THRESHOLD_LISTENERS) {
            try {
                listener.onThresholdCrossed(player, percentOfSanc, entering);
            } catch (Throwable t) {
                LOGGER.warn("[akaishi] 理智阈值监听器异常（已隔离）: {}", listener.getClass().getName(), t);
            }
        }
    }

    /** 派发首见触发（在核心判定"确为首次"且已记档之后） */
    public static void fireFirstEncounter(Player player, ResourceLocation encounterId) {
        if (player == null || encounterId == null) {
            return;
        }
        for (FirstEncounterListener listener : FIRST_LISTENERS) {
            try {
                listener.onFirstEncounter(player, encounterId);
            } catch (Throwable t) {
                LOGGER.warn("[akaishi] 理智首见监听器异常（已隔离）: {}", listener.getClass().getName(), t);
            }
        }
    }

    /**
     * 询问是否允许本次环境扣减（<b>可取消</b>）。
     *
     * <p>语义：
     * <ul>
     *   <li>返回 false 即整体否决本次扣减，剩余监听器不再被询问（短路），也不进入数值结算；</li>
     *   <li>监听器抛异常视为"无意见"（放行）——附属自己的 bug 不该变成玩家免疫环境伤害；</li>
     *   <li>只能"放行/否决"，<b>不能改量</b>：多条监听器改量时叠加顺序不可预测。
     *       想改变扣减量请注册 {@link ISanityRule}，让量在规则里显式表达、可被别的附属看见。</li>
     * </ul>
     *
     * @return true = 允许扣减；false = 本次被否决
     */
    public static boolean fireEnvironmentDebit(Player player, ISanityRule rule, SanityContext ctx, float amount) {
        if (player == null || rule == null) {
            return true;
        }
        for (EnvironmentListener listener : ENVIRONMENT_LISTENERS) {
            try {
                if (!listener.allowEnvironmentDebit(player, rule, ctx, amount)) {
                    return false;
                }
            } catch (Throwable t) {
                LOGGER.warn("[akaishi] 理智环境否决监听器异常（视为放行）: {}", listener.getClass().getName(), t);
            }
        }
        return true;
    }

    private static <T> Registration add(CopyOnWriteArrayList<T> list, T listener) {
        if (listener == null) {
            return NOOP;
        }
        if (!list.addIfAbsent(listener)) {
            LOGGER.debug("[akaishi] 理智回调重复注册（幂等复用）: {}", listener.getClass().getName());
        }
        return () -> list.remove(listener);
    }

    /** 注销句柄：注销已失效的登记为无害空操作（可重复调用） */
    public interface Registration {
        void unregister();
    }

    /** 数值变化监听：一次变更派发一次 */
    @FunctionalInterface
    public interface ValueListener {
        /**
         * @param stat     变化的数值种类
         * @param oldValue 变更前值
         * @param newValue 变更后值（已夹取，即玩家实际生效的值）
         * @param source   变更来源；附属经 {@link ISanityService} 写入时为 {@link SanityChangeSource#EXTERNAL}
         */
        void onValueChanged(Player player, SanityStat stat, float oldValue, float newValue, SanityChangeSource source);
    }

    /**
     * 阈值跨越监听：只在真正跨档时派发（进入/离开两个方向都有）。
     *
     * <p>与 {@link ISanityThresholdHook} 的区别：钩子是<b>带 id 的注册条目</b>（可查询、可注销、可被 UI 列出），
     * 本监听是<b>无 id 的即时监听</b>（适合附属内部的表现逻辑，不想被外部看见）。
     */
    @FunctionalInterface
    public interface ThresholdListener {
        void onThresholdCrossed(Player player, float percentOfSanc, boolean entering);
    }

    /** 首见触发监听 */
    @FunctionalInterface
    public interface FirstEncounterListener {
        void onFirstEncounter(Player player, ResourceLocation encounterId);
    }

    /** 环境结算否决监听（返回 false = 否决本次扣减） */
    @FunctionalInterface
    public interface EnvironmentListener {
        boolean allowEnvironmentDebit(Player player, ISanityRule rule, SanityContext ctx, float amount);
    }
}

package com.example.akaishi.forbidden.magic.exec;

import com.example.akaishi.forbidden.magic.exec.deliver.MagicDeliverers;
import com.example.akaishi.forbidden.magic.exec.effect.MagicExecutors;
import com.example.akaishi.forbidden.magic.exec.resolve.MagicResolvers;
import com.example.akaishi.forbidden.magic.link.MagicLinks;
import com.example.akaishi.forbidden.magic.projectile.SpellProjectiles;
import com.example.akaishi.forbidden.magic.table.MagicIds;

import java.util.Set;

/**
 * 执行层<b>内置内容</b>的统一注册入口（设计记忆 §11 / §19.2）。
 *
 * <p><b>为什么要一个入口</b>：执行层的内容分布在解析器 / 投送器 / 执行器三个包，
 * 若各自在 {@code AkaishiForbiddenMod.init} 里自己注册，那个方法会随内容增长而失控；
 * 更重要的是"注册是否完整"这件事没有单一检查点。收成一个入口后，
 * 自检只需问一次"内置内容装了没、还差什么"。
 *
 * <p><b>内置与附属同权</b>：本类只是把内置项塞进 {@code MagicExecRegistry}，
 * 与附属调用的是同一个公开方法 —— 不存在"官方走后门"的第二条路径
 * （与理智系统的 {@code SanityBuiltinRules} 同一口径）。
 *
 * <p><b>幂等</b>：注册表本身可重复写（同 id 覆盖），但 {@link MagicScheduler#register()}
 * 会挂 tick 监听 —— 重复调用会导致任务被执行多次。故用一次性开关守住。
 */
public final class MagicExecBuiltins {

    /**
     * <b>尚未实现的 X</b>：目前<b>为空</b> —— 7 种交付方式已全部就位。
     *
     * <p>保留这张表（而不是删掉判断）是为了让自检继续问出正确的问题：
     * <b>"每个 X 要么已注册、要么在这张表里"</b>。将来若加入第 8 种 X，
     * 忘了实现时自检会立刻变红，而不是静默地"施法时投送器未注册、什么都不发生"。
     */
    public static final Set<String> DEFERRED_DELIVERIES = Set.of();

    /**
     * <b>尚未实现的 E 执行器</b>：每一个都需要<b>世界里的载体</b>或<b>尚未裁定的口径</b>。
     *
     * <p>同样显式列出，让自检能问出"每个 E 要么已注册、要么在这张表里"。
     *
     * <p>为什么这三条不能像其它 16 条那样用原版效果顶上：
     * <ul>
     *   <li>{@code SUMMON} —— 要有可召唤的投影体（§4.3 丘的"低维投影体"），且
     *       "T1 投影基准 50%"具体指哪项属性减半尚未裁定；</li>
     *   <li>{@code VESSEL} —— 要有能存法术的承载物（§4.3 杯），且"存进去之后怎么取出来"未定；</li>
     *   <li>{@code TEMPORAL} —— 口径未定：X6 的投送器已经负责延迟（§21.4 又强制它只配 X6），
     *       执行器再延迟就是双重延迟（见 §40.5）。</li>
     * </ul>
     */
    public static final Set<String> DEFERRED_EFFECTS = Set.of(
            MagicIds.Effect.SUMMON, MagicIds.Effect.VESSEL, MagicIds.Effect.TEMPORAL
    );

    private static volatile boolean installed;

    private MagicExecBuiltins() {
    }

    /** 安装全部内置执行层内容（由 {@code AkaishiForbiddenMod.init} 调用一次） */
    public static synchronized void register() {
        if (installed) {
            return;
        }
        MagicResolvers.register();
        MagicDeliverers.register();
        MagicExecutors.register();
        // 延迟调度：供 X6 回响（以及将来需要"某一刻发生什么"的实现）使用
        MagicScheduler.register();
        // 飞行中弹体的登记项兜底清理（X2 投射；防区块卸载导致的残留）
        SpellProjectiles.register();
        // 链的逐 tick 推进（LINK：闪电链式传导，每秒一跳）
        MagicLinks.register();
        installed = true;
    }

    /** 内置内容是否已安装（自检用） */
    public static boolean installed() {
        return installed;
    }
}

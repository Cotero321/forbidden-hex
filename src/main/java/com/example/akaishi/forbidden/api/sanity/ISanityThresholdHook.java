package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 阈值钩子：理智跌破 / 回升跨过某个百分比档位时被调用。
 *
 * <p><b>阈值的定义</b>：档位是<b>当前 SANC 的百分比</b>（默认 80 / 60 / 40 / 20 / 0），
 * 不是绝对值。这样上限被抬升或削减时，档位会跟着动，附属不必自己换算绝对值，
 * 也不存在"上限变了但阈值没变"导致的档位错位。
 * 百分比换算的基准是<b>硬上限</b>（{@code sanc - tempCut}），与实际 SAN 夹取口径一致。
 *
 * <p><b>两个方向都会调用</b>：{@link #onThreshold} 的 {@code entering} 参数区分方向——
 * 跌破该档（进入更差状态）为 true，回升跨过该档（脱离该状态）为 false。
 * 附属必须两个方向都处理，否则会出现"掉了血条变红，恢复后还是红的"。
 *
 * <p><b>调用约束</b>：
 * <ul>
 *   <li>只在<b>真正跨越</b>时调用（同档位区间内的连续变化不重复调用），因此可作为状态机边沿使用；</li>
 *   <li>一次结算中跨越多个档位（例如直接扣穿 40 与 20）时，按跨越方向<b>逐档</b>调用，不合并、不跳过；</li>
 *   <li>抛异常由结算层隔离（该钩子本次调用丢弃），不得影响其它钩子与主结算流程；</li>
 *   <li>禁止在此回调里再写理智数值（会引发递归跨越），如需调整请用 {@link SanityCallbacks} 或延后一 tick。</li>
 * </ul>
 */
public interface ISanityThresholdHook {

    /** 钩子 id，必须带自己的命名空间 */
    ResourceLocation id();

    /**
     * 跨越阈值回调。
     *
     * @param player        被结算玩家（服务端）
     * @param percentOfSanc 跨越的档位百分比：80 / 60 / 40 / 20 / 0（相对硬上限）
     * @param entering      true = 跌破该档；false = 回升跨过该档
     */
    void onThreshold(Player player, float percentOfSanc, boolean entering);
}

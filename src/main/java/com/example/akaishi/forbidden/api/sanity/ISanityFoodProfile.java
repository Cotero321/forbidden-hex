package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

/**
 * 食补档位：进食某物品后，理智在时间窗口内被"分摊补回"的档位表。
 *
 * <p><b>为什么是窗口总量 + 分摊</b>：一次性把 40 点理智灌进去，会让玩家"打怪打到一半吃口饭瞬间满血"，
 * 也会让食品变成纯粹的应急道具。改成"在 {@link #windowTicks()} 内分摊施加"后，
 * 进食是一个持续过程：饱食度/呕吐/吃第二口的行为都能在窗口内产生交互（例如重复食用刷新窗口）。
 *
 * <p><b>结算语义（内部实现层必须按此实现）</b>：
 * <ul>
 *   <li>{@link #totalSan()} 是<b>窗口内总量</b>，不是瞬时值：实现层按 tick 切片分摊施加，
 *       每 tick 施加量 = 剩余总量 / 剩余 tick 数（切片需累积余数，避免浮点丢失导致"少补"）；</li>
 *   <li>{@link #instantSan()} 是<b>立刻</b>施加的额外量（不参与分摊），用于"解腻/压惊"式即时回补；</li>
 *   <li>两者相加的实际补量都要<b>再乘 COG 的食补效力系数</b>——该系数不在档位里给，
 *       由系统统一施加（与 {@link ISanityRule} 的环境扣除系数同源，保证认知值的影响只在一处调平衡）；</li>
 *   <li>{@link #protection()} 提供临时理智保护，随窗口一起施加；</li>
 *   <li>{@link #decayTiers()} 定义<b>同一物品连续食用时的效力衰减</b>：第 1 次全额、第 2 次按 tier[1]…</li>
 * </ul>
 */
public interface ISanityFoodProfile {

    /** 绑定的物品 id；必须带自己的命名空间，且该物品须真实存在（可经合成/掉落获取）才不产生孤儿条目 */
    ResourceLocation itemId();

    /** 分摊窗口长度（tick）。{@link #totalSan()} &gt; 0 时必须 &gt; 0，否则注册被拒（否则无法分摊＝静默失效） */
    int windowTicks();

    /** 窗口内理智总量（可正可负；负值＝吃错东西缓慢掉理智） */
    float totalSan();

    /** 窗口内附带的临时理智保护总量 */
    float protection();

    /** 立即施加的理智量（不参与分摊），可 0 */
    float instantSan();

    /**
     * 重复食用效力档位：第 1/2/3/4 次生效的百分比（{@code 1.0f = 100%}）。
     *
     * <p>长度不足视为<b>该次数起不再生效</b>（例如只有 2 项，则第 3 次食用无任何效果）。
     * 返回值视为<b>只读</b>：调用方不得修改数组；实现层应在注册/首次读取时复制成内部不可变快照，
     * 避免附属在运行期改动数组导致结算量突变。
     */
    float[] decayTiers();

    /** 衰减计数的重置时间：距上次食用超过多少 tick 后，恢复按第 1 档计算 */
    int refreshTicks();
}

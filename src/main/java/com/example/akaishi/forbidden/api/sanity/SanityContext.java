package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * 只读世界上下文快照：一次环境结算所依据的全部环境事实。
 *
 * <p><b>为什么要有它</b>：环境规则（{@link ISanityRule}）与首见判定（{@link ISanityFirstEncounter}）
 * 都依赖"玩家此刻处在什么环境"。如果让每条规则各自去查群系、光照、结构，就会出现两个问题：
 * <ul>
 *   <li><b>口径漂移</b>：同样是"深层地底"，A 规则判定 Y&lt;0，B 规则判定 Y&lt;10，结果互相矛盾；</li>
 *   <li><b>重复开销</b>：一次结算 N 条规则 × 每次查询 6 项环境 = 6N 次查询，其中绝大多数答案相同。</li>
 * </ul>
 * 由结算层在进入规则循环前构建一次上下文，所有判定共用同一份事实，两个问题同时消失。
 *
 * <p><b>实现者约束</b>（内部实现层请严格遵守）：
 * <ul>
 *   <li>上下文只在<b>本次结算回调存活期内</b>有效；规则不得缓存它到下一 tick；</li>
 *   <li>全部 getter 必须<b>无副作用、可在同一 tick 内重复调用</b>（内部可自行缓存字段，但不得改世界状态）；</li>
 *   <li>仅在<b>服务端</b>构建（{@link #level()} 返回 {@link ServerLevel}），客户端不得持有；</li>
 *   <li>玩家为 null / 玩家已离开世界时不得构建上下文，而是跳过本次结算。</li>
 * </ul>
 */
public interface SanityContext {

    /** 上下文所属的服务端世界（必非 null） */
    ServerLevel level();

    /** 被结算的玩家（必非 null；不等于 {@code level()} 的任意玩家，就是本次对象） */
    Player player();

    /**
     * 采样点：本次环境判定的取点位置。
     *
     * <p>惯例为玩家脚部所在方块；具体取点由实现层固定，附属只需按"这一点"理解全部环境 getter
     * （不要在规则里再用 {@code player.blockPosition()} 自行取点，否则与 {@link #blockLight()} 等口径不一致）。
     */
    BlockPos pos();

    /** 采样点是否位于 Y&lt;0（深层地底判据；与维度、与是否属于某结构无关） */
    boolean isUnderY0();

    /** 采样点方块光 0~15（不含天空光） */
    int blockLight();

    /**
     * 采样点局部综合亮度 0~15。
     *
     * <p>口径：方块光与天空光按"是否见天"取较大者（即 {@code level.getMaxLocalRawBrightness(pos)} 语义），
     * <b>不</b>叠加昼夜、天气、月相修正——那些属于独立判据（见 {@link #isDay()}），
     * 混进来会让"洞穴里白天变亮/晚上变暗"这类错误结论出现。
     */
    int effectiveLight();

    /** 采样点是否直见天空 */
    boolean canSeeSky();

    /** 是否主世界昼间（{@code level.isDay()} 语义）；非主世界恒为 false */
    boolean isDay();

    /** 所在群系是否带 {@code minecraft:is_ocean} 标签（海洋 / 深海族统一判据） */
    boolean isOcean();

    /**
     * 是否处于远古城市结构范围内。
     *
     * <p>口径由核心实现层固定（结构定位 + 判定半径），规则不得自行改用群系或坐标近似。
     */
    boolean inAncientCity();

    /**
     * 是否处于幽匿环境（深层黑暗）。
     *
     * <p><b>为什么不能只判群系</b>：玩家完全可以在普通洞穴里自己铺一圈幽匿方块造出等价环境，
     * 只认 {@code deep_dark} 群系会漏掉这种情况，而它的感官体验与远古城市并无差别。
     * 因此口径定为：<b>群系为 {@code deep_dark}，或（{@link #isUnderY0()} 且 {@link #effectiveLight()} 低于实现层阈值且采样点附近存在幽匿族方块）</b>。
     * 阈值与搜索半径属内部实现细节，附属只依赖本布尔值；这样即使后续调平衡，规则侧也不用改。
     */
    boolean inDeepDark();

    /** 是否下界维度 */
    boolean isNether();

    /** 是否主世界维度 */
    boolean isOverworld();
}

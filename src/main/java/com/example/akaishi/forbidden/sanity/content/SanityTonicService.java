package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.item.SanityTonicItem;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.world.entity.player.Player;

/**
 * 理智剂的"喝下"结算：开窗口分摊 + 落冷却时间戳（<b>本类是全模组唯一写这两样的地方</b>）。
 *
 * <p><b>窗口为什么复用 {@link SanityState.FoodRuntime}（食补窗口）而不是另起一套</b>：
 * 窗口的逐 tick 分摊已经有一条成熟的 tick 循环（{@code SanityFoodSettlement.tickWindows}，1 tick 节拍、
 * 剩余总量 / 剩余 tick 的误差自收敛切法），复用它可以零新增 tick 注册、零新增存档段；
 * 代价只是"食补窗口"这个映射里多住进了两个非食物条目 —— 该映射的键是物品 id 字符串，
 * 语义等价（"某物品的补量窗口"），且窗口字段与连续食用计数字段相互独立，互不污染。
 *
 * <p><b>为什么不进食补衰减链（连续食用衰减 / 15 分钟刷新）</b>：
 * <ol>
 *   <li>冷却（30 / 20 分钟）已经严格限频，且冷却期内再次使用被<b>直接拒绝</b>，
 *       叠加衰减链属于"双重惩罚"，读数上只会让玩家觉得数值对不上；</li>
 *   <li>衰减链的语义是"同一口饭连吃会腻"，而理智剂是炼制的药剂、定量生效，
 *       与 {@link SanityBuiltinRestores}（P2 建立的 SANC 首用表）把它当"固定档位"的口径一致。</li>
 * </ol>
 * 因此本类只调 {@code setWindow}，<b>不</b>调 {@code setChain}（链计数保持 0 ⇒ 窗口走完后条目自动回到空闲、不再落盘）。
 *
 * <p><b>为什么也不乘 COG 食补效力</b>：食补效力是"进食"这条链路的系数（{@code SanityCogCurve.foodEfficiency}），
 * 作用于 {@code SanityFoodRegistry} 的档位；理智剂不经那条链路，补量为设计稿给定的定量
 * （普通 30 / 高级 40），故此处按原量写入窗口，不做任何系数缩放。
 *
 * <p><b>冷却为什么按玩家落盘、不按物品 NBT</b>：物品 NBT 的冷却可以被"多瓶轮换 / 混进潜影盒"绕过
 * （每瓶各自计时），而冷却的语义是"<b>这个玩家</b>刚补过理智，身体还没缓过来"。
 * 故记在 {@link SanityState}（随躯体 capability 落盘，跨死亡、跨维度、跨重登保留）里的
 * {@code 物品 id → 上次使用时刻（gameTime）}，权威在服务端。
 *
 * <p><b>两种理智剂的冷却键互相独立</b>（各自 id 一条），故可以"先喝普通再喝高级"（合计 70 SAN）；
 * 这是刻意的（两种药剂是不同道具），若要合并成一个键，把 {@link SanityTonicItem#restoreId()} 换成同一个常量即可。
 *
 * <p>所有数值均为<b>待调手感值</b>。
 */
public final class SanityTonicService {

    /** 窗口时长（tick）：100 = 5s（与设计稿"5 秒内回复"一致；待调手感值） */
    public static final int WINDOW_TICKS = 100;
    /** 理智回复剂：窗口内共 30 SAN（待调手感值） */
    public static final float TONIC_TOTAL_SAN = 30f;
    /** 理智回复剂：冷却 30 分钟（待调手感值） */
    public static final int TONIC_COOLDOWN_TICKS = 30 * 60 * 20;
    /** 高级理智恢复剂：窗口内共 40 SAN（待调手感值） */
    public static final float GREATER_TONIC_TOTAL_SAN = 40f;
    /**
     * 高级理智恢复剂：冷却 20 分钟。
     *
     * <p><b>待确认（疑似写反）</b>：用户原文给的是"高级款 20 分钟 < 普通款 30 分钟"，
     * 与"高级 = 更强"的直觉相反；已向用户提出疑问但未被纠正，故<b>按原文实现 20 分钟</b>。
     * 若确认写反，只改本常量即可（窗口量 40 不动）。
     */
    public static final int GREATER_TONIC_COOLDOWN_TICKS = 20 * 60 * 20;

    private SanityTonicService() {
    }

    /**
     * 喝下一瓶：开窗口 + 落冷却（仅服务端）。
     *
     * <p><b>总开关关闭时</b>：仍然落冷却（否则"关掉理智系统 → 连喝 → 再打开"就能绕过限频），
     * 但不写窗口（写进去也没人分摊，等于留下一条永不消费的坏数据）。
     */
    public static void drink(Player player, SanityTonicItem item) {
        if (player == null || item == null || player.level() == null || player.level().isClientSide) {
            return;
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state != null) {
            state.setItemUseTick(item.restoreId(), player.level().getGameTime());
            state.markDirty();
            if (ModConfig.sanityEnabled) {
                // 复用餐补窗口的逐 tick 分摊（见类注释）；保护量与链计数一律 0
                state.foodRuntime(item.restoreId()).setWindow(item.windowSan(), item.windowTicks(), 0f);
            }
        }
        // 视觉冷却：服务端设置会经 ClientboundCooldownPacket 下发，客户端物品格出现冷却遮罩；
        // 重登后该视觉丢失（原版冷却表不落盘），但权威时间戳仍在，误用时会被 use() 拦下并提示剩余时间。
        player.getCooldowns().addCooldown(item, item.cooldownTicks());
    }

    /**
     * 冷却剩余（tick，0 = 可用）。
     *
     * <p>已过期或时间戳非法（时钟回退：换存档 / 数据损坏）时顺手清掉条目，避免冷却表在存档里长期留垃圾。
     */
    public static int cooldownRemainingTicks(Player player, SanityTonicItem item) {
        if (player == null || item == null || player.level() == null) {
            return 0;
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return 0;
        }
        long last = state.itemUseTick(item.restoreId());
        if (last <= 0L) {
            return 0;
        }
        long elapsed = player.level().getGameTime() - last;
        int cooldown = item.cooldownTicks();
        if (elapsed < 0L || elapsed >= cooldown) {
            state.setItemUseTick(item.restoreId(), 0L);
            return 0;
        }
        return (int) (cooldown - elapsed);
    }

    /** 冷却剩余秒数（向上取整：剩 1t 也显示 1 秒，避免显示 0 秒却仍不可用） */
    public static int formatSeconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }
}

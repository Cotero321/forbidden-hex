package com.example.akaishi.forbidden.codex;

import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 「已解锁键」的查询与拦截钩子 —— 秘典产出的解锁物，供将来的消费点（配方 / 物品 / 祭坛仪式）询问。
 *
 * <p><b>本轮默认不锁任何现有内容</b>（用户给出的三个口径互相矛盾，取其最安全的一种：
 * 机制做全，但不动既有玩法与老存档）。因此本类的方法<b>当前没有任何生产调用点</b>，
 * 只在 {@code AkaishiAltarRitual#match} 与 {@code ForbiddenSetHooks}（禁断饰品使用点）留了接线说明。
 *
 * <p><b>将来要接线时改两处</b>：
 * <ol>
 *   <li><b>禁断饰品使用点</b>：{@code com.example.akaishi.effect.ForbiddenSetHooks} —— 在
 *       {@code socketsLocked(...)} 的调用方（改造/佩戴入口）前面加一句
 *       {@code if (!CodexUnlocks.requireUnlock(player, 对应的键)) return;}；</li>
 *   <li><b>祭坛配方匹配点</b>：{@code com.example.akaishi.forbidden.life.altar.AkaishiAltarRitual#match} ——
 *       在遍历 {@code AkaishiAltarRecipe.all()} 命中某条配方后，用该配方对应的键
 *       {@code CodexUnlocks.requireUnlock(player, 键)} 决定放行或跳过。</li>
 * </ol>
 * 键的命名约定：{@code forbidden_hex:codex/<节点路径>}（由 {@link CodexTable} 里节点的
 * {@link CodexReward#unlockKey()} 产出）。
 *
 * <p><b>权威性</b>：解锁键记在玩家存档里（{@link SanityState#hasCodexUnlock}）。
 * 客户端调用恒为 false —— 消费点全部在服务端，客户端不需要也不允许据此放行。
 */
public final class CodexUnlocks {

    private CodexUnlocks() {
    }

    /** 该玩家是否解锁了某键（客户端 / 无存档一律 false） */
    public static boolean hasUnlocked(Player player, ResourceLocation key) {
        if (player == null || key == null || player.level() == null || player.level().isClientSide) {
            return false;
        }
        SanityState state = SanityServiceImpl.state(player);
        return state != null && state.hasCodexUnlock(key.toString());
    }

    /**
     * 拦截钩子（带拒绝反馈）：放行返回 true；未解锁则给出提示并返回 false。
     *
     * <p>给消费点用的<b>唯一入口</b>——它把"查询 + 拒绝反馈"合成一次调用，
     * 避免每个消费点各写一遍提示文案（漏了提示的表现就是"点了没反应"）。
     */
    public static boolean requireUnlock(Player player, ResourceLocation key) {
        if (hasUnlocked(player, key)) {
            return true;
        }
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("message.forbidden_hex.codex.unlock_required"), true);
        }
        return false;
    }
}

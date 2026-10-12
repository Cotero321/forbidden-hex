package com.example.akaishi.forbidden.codex;

import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 禁忌秘典的<b>服务端权威结算</b>：推进阶段 / 学完结算 / 可重复仪式。
 *
 * <p><b>为什么所有写入都必须过这里</b>：客户端只发一条"我要研究这个节点"，
 * 门槛（认知 / 首见 / 前置节点 / 物品 / 地点 / 伤害六种）、阶段序号、仪式次数上限
 * <b>全部在服务端重新算一遍</b>。客户端送来的只有节点 id，送不来"我已经够了"；
 * 节点 id 还要过 {@link CodexTable#get} 的注册表白名单，
 * 改包的客户端既伪造不了门槛，也点不到不存在的节点。
 * 物品类条件在通过校验后还会<b>真的从背包扣除</b>（见 {@link #consumeOnComplete}）。
 *
 * <p><b>为什么数值改写走 {@code SanityServiceImpl} 的内部漏斗而不是直接改状态</b>：
 * 理智的五个值互相耦合（上限变化要连带下压 SAN、扣减要先吃临时保护），
 * 绕过漏斗改字段会造出自相矛盾的状态并漏掉回调/同步/阈值判定。这里只用漏斗的两个内部入口：
 * SANC 增益走 {@code addSancInternal}，代价走 {@code debitInternal}（扣减语义，先由临时保护抵扣）。
 *
 * <p>文案在这里组装成 {@link Component}（服务端只给翻译键，客户端按自己的语言渲染）；
 * 界面不解锁任何条件，回执文案是玩家唯一能看到的"为什么不行"。
 */
public final class CodexService {

    private CodexService() {
    }

    /**
     * 执行一次秘典交互：未学 → 推进一阶段（走完即学完并结算奖励）；已学且可重复 → 举行一次仪式。
     *
     * @return 给玩家的回执文案（成功或失败都有）
     */
    public static Component study(Player player, @Nullable ResourceLocation nodeId) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return Component.translatable("message.forbidden_hex.codex.no_state");
        }
        CodexNode node = CodexTable.get(nodeId);
        if (node == null) {
            // 白名单：只认静态表里的节点。客户端送来的 id 一律不可信
            return Component.translatable("message.forbidden_hex.codex.unknown");
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return Component.translatable("message.forbidden_hex.codex.no_state");
        }
        String id = node.id().toString();

        if (!state.hasCodexNode(id)) {
            return advance(serverPlayer, state, node, id);
        }
        if (!node.repeatable()) {
            return Component.translatable("message.forbidden_hex.codex.already");
        }
        return performRitual(serverPlayer, state, node, id);
    }

    /** 推进一个阶段；走完全部阶段即学完并结算奖励 */
    private static Component advance(ServerPlayer player, SanityState state, CodexNode node, String id) {
        int stage = Math.min(state.codexStage(id), node.stageCount());
        if (stage >= node.stageCount()) {
            // 存档自相矛盾（阶段已满却没记"已学完"）：就地自愈，不抛异常也不给二次奖励
            state.markCodexNodeLearned(id);
            return Component.translatable("message.forbidden_hex.codex.already");
        }
        List<CodexCondition> conditions = CodexGates.evaluate(player, state, node, stage);
        if (!CodexGates.allSatisfied(conditions)) {
            return Component.translatable("message.forbidden_hex.codex.locked");
        }
        // 阶段完成时真的扣物：条件里标了 consume 的物品在此从背包扣掉。
        // 放在"校验通过之后、推进之前"：扣不动就整段不推进（存档/背包被外部改动时也不会白送进度）。
        if (!consumeOnComplete(player, node.stages().get(stage))) {
            return Component.translatable("message.forbidden_hex.codex.locked");
        }
        int next = stage + 1;
        state.setCodexStage(id, next);
        if (next < node.stageCount()) {
            return Component.translatable("message.forbidden_hex.codex.stage",
                    Component.translatable(node.nameKey()), next, node.stageCount());
        }
        state.markCodexNodeLearned(id);
        applyReward(player, state, node);
        return Component.translatable("message.forbidden_hex.codex.learned",
                Component.translatable(node.nameKey()));
    }

    /** 可重复仪式：每次扣 SAN、加 SANC，次数用尽即拒绝 */
    private static Component performRitual(ServerPlayer player, SanityState state, CodexNode node, String id) {
        CodexReward reward = node.reward();
        if (reward == null || reward.type() != CodexReward.Type.RITUAL) {
            return Component.translatable("message.forbidden_hex.codex.already");
        }
        int uses = state.codexUses(id);
        if (uses >= reward.ritualMaxUses()) {
            return Component.translatable("message.forbidden_hex.codex.exhausted");
        }
        SanityServiceImpl sanity = SanityServiceImpl.instance();
        // 代价走 debitInternal（扣减语义：先由临时保护抵扣，余量才落到 SAN；不会扣出负数）
        sanity.debitInternal(player, reward.ritualSanCost(), SanityChangeSource.EXTERNAL);
        sanity.addSancInternal(player, reward.sanc(), SanityChangeSource.EXTERNAL);
        state.setCodexUses(id, uses + 1);
        return Component.translatable("message.forbidden_hex.codex.ritual", fmt(reward.sanc()),
                uses + 1, reward.ritualMaxUses());
    }

    /** 学完结算：按奖励类型落地（RITUAL 在学习时不给奖励，奖励是之后可反复举行的仪式） */
    private static void applyReward(ServerPlayer player, SanityState state, CodexNode node) {
        CodexReward reward = node.reward();
        if (reward == null) {
            return;
        }
        SanityServiceImpl sanity = SanityServiceImpl.instance();
        switch (reward.type()) {
            case SANC -> sanity.addSancInternal(player, reward.sanc(), SanityChangeSource.EXTERNAL);
            case GIFT -> {
                if (reward.sanRestore() != 0f) {
                    sanity.addSanInternal(player, reward.sanRestore(), SanityChangeSource.EXTERNAL);
                }
                // 物品延迟求值（静态表构建时物品域可能还没注册完），此处才真正取实例
                Item item = reward.item() == null ? null : reward.item().get();
                if (item != null && reward.itemCount() > 0) {
                    player.getInventory().placeItemBackInInventory(
                            new ItemStack(item, reward.itemCount()));
                }
            }
            case UNLOCK -> {
                if (reward.unlockKey() != null) {
                    state.markCodexUnlock(reward.unlockKey().toString());
                }
            }
            case RITUAL -> {
                // 学习本身不结算：仪式由 {@link #performRitual} 在之后每次点击时结算
            }
        }
    }

    /**
     * 阶段完成时消耗物品（{@link CodexRequirement#consume()} 为真的那几条）。
     *
     * <p><b>为什么"扣"必须独立于"判定"再走一遍</b>：判定只回答"够不够"，
     * 而扣物要真改物品栏。两者若合成一次遍历，就只能在遍历过程中边判边扣，
     * 一旦后面还有一条不够，前面的已经被扣掉了——那是不可回滚的破坏。
     * 因此先判（{@link CodexGates} 的求值 + {@code allSatisfied}）、后扣（本方法），
     * 且本方法仍<b>逐条复核</b>，任何一条扣不动就返回 false 并放弃整个阶段推进。
     *
     * @return true = 全部扣成功（或本阶段无需扣物）
     */
    private static boolean consumeOnComplete(ServerPlayer player, CodexStage stage) {
        for (CodexRequirement requirement : stage.requirements()) {
            if (requirement.kind() != CodexCondition.KIND_ITEM || !requirement.consume()) {
                continue;
            }
            Item item = requirement.item() == null ? null : requirement.item().get();
            if (item == null || !consume(player, item, requirement.count())) {
                return false;
            }
        }
        return true;
    }

    /** 从背包扣除若干某物品；不够则一件不扣并返回 false（含护甲与副手槽，与判定口径同一套遍历） */
    private static boolean consume(ServerPlayer player, Item item, int count) {
        if (count <= 0) {
            return true;
        }
        if (CodexGates.countItems(player, item) < count) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        return remaining == 0;
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }
}

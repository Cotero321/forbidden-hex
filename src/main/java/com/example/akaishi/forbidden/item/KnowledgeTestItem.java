package com.example.akaishi.forbidden.item;

import com.example.akaishi.forbidden.codex.CodexNode;
import com.example.akaishi.forbidden.codex.CodexReward;
import com.example.akaishi.forbidden.codex.CodexTable;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.example.akaishi.forbidden.menu.AkaishiCodexMenu;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 开发测试用品：右键后直接完成当前版本的秘典节点，并把魔法进度提升到上限。
 *
 * <p>只在服务端写入玩家状态；客户端只显示服务端返回的结果。该物品不消耗，便于反复验证界面和存档读写。
 */
public final class KnowledgeTestItem extends Item {

    public KnowledgeTestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.isCreative() && !serverPlayer.hasPermissions(2)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.forbidden_hex.knowledge_test.permission"), true);
                return InteractionResultHolder.fail(stack);
            }
            GrantResult result = grantAll(serverPlayer);
            if (result == null) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.forbidden_hex.knowledge_test.no_state"), true);
            } else {
                if (serverPlayer.containerMenu instanceof AkaishiCodexMenu menu) {
                    menu.markSnapshotDirty();
                }
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.forbidden_hex.knowledge_test.granted",
                        result.learnedNodes(), result.totalNodes(),
                        result.unlockedWords(), result.totalWords(), result.level()), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.forbidden_hex.forbidden_hex_knowledge_test.hint"));
    }

    private static GrantResult grantAll(ServerPlayer player) {
        SanityState sanity = SanityServiceImpl.state(player);
        MagicState magic = MagicStates.state(player);
        if (sanity == null || magic == null) {
            return null;
        }

        int learnedNodes = 0;
        for (CodexNode node : CodexTable.all()) {
            String nodeId = node.id().toString();
            if (sanity.codexStage(nodeId) != node.stageCount()) {
                sanity.setCodexStage(nodeId, node.stageCount());
            }
            if (!sanity.hasCodexNode(nodeId)) {
                sanity.markCodexNodeLearned(nodeId);
            }
            if (node.reward() != null
                    && node.reward().type() == CodexReward.Type.UNLOCK
                    && node.reward().unlockKey() != null) {
                String unlockKey = node.reward().unlockKey().toString();
                if (!sanity.hasCodexUnlock(unlockKey)) {
                    sanity.markCodexUnlock(unlockKey);
                }
            }
            learnedNodes++;
        }

        magic.setLevel(MagicState.LEVEL_MAX);
        for (MagicWord word : MagicWord.values()) {
            magic.unlockWord(word.id());
        }

        return new GrantResult(
                learnedNodes,
                CodexTable.all().size(),
                magic.wordCount(),
                MagicWord.values().length,
                magic.level());
    }

    private record GrantResult(int learnedNodes, int totalNodes, int unlockedWords, int totalWords, int level) {
    }
}

package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.api.magic.IMagicService.CastOutcome;
import com.example.akaishi.forbidden.api.magic.CastResult;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.MagicServiceImpl;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 法杖的<b>正式施法入口</b>（C4，§44.4）：右键 → 读法术槽咒核 → 组装载体调整 → 进施法链。
 *
 * <p><b>链路</b>：{@code WandItem.use}（服务端）→ 本类 →
 * {@link MagicServiceImpl#castWithWand}（源由咒核决定、逐词墨参与判定、额外池先扣）→
 * {@code MagicCastManager.begin}（前摇 / 持续段 / 冷却，与调试入口同一状态机）。
 *
 * <p><b>装卸免费</b>（§44.4）：咒核是储存器不是耗材，施法不耗核不耗墨；
 * 消耗走 §2 支付表（经额外池先扣修正）。
 */
public final class WandCasting {

    private WandCasting() {
    }

    /** 服务端右键入口（{@code WandItem.use} 调用；客户端侧不做任何事） */
    public static void castFromHand(ServerPlayer player, ItemStack wand) {
        ItemStack spell = WandItem.spell(wand);
        if (spell.isEmpty()) {
            reject(player, "no_spell");
            return;
        }
        // 词序列与逐词墨：按核内槽位序（与书写序一致）
        List<String> wordIds = new ArrayList<>();
        List<String> wordInks = new ArrayList<>();

        for (int i = 0; i < MagicCoreItem.SLOTS; i++) {
            String word = MagicCoreItem.word(spell, i);
            if (!word.isEmpty()) {

                wordIds.add(word);
                wordInks.add(MagicCoreItem.ink(spell, i));
            }
        }
        if (wordIds.isEmpty()) {
            reject(player, "no_spell");
            return;
        }
        String mainInk = "";
        for (String ink : wordInks) {
            if (!ink.isEmpty()) {
                mainInk = ink;
                break;
            }
        }
        if (mainInk.isEmpty()) {
            reject(player, "no_ink");
            return;
        }
        MagicSource source = MagicCoreItem.source(spell);
        long now = player.level().getGameTime();
        var compiled = com.example.akaishi.forbidden.api.magic.SpellPlans.fromCore(spell, mainInk, 0f, 0);
        if (!compiled.ok()) {
            reject(player, compiled.diagnostics().stream().anyMatch(d -> d.code().contains("conflict"))
                    ? "conflict" : "not_exists");
            return;
        }
        String spellKey = source.id() + ":" + String.join(",", wordIds) + ":" + String.join(",", wordInks);
        int combo = WandComboTracker.peek(player.getUUID(), spellKey, now);
        WandModifiers adjustments = WandModifiers.resolve(wand, source, wordInks,
                WandItem.extra(wand, source), combo);

        if (com.example.akaishi.forbidden.magic.cast.MagicCastManager.isCasting(player)) {
            reject(player, "busy");
            return;
        }
        WandSessionLinks.link(player.getUUID(), wand, source, adjustments.postCastRefund());
        CastOutcome outcome = MagicServiceImpl.instance().castWithWand(player, wordIds, mainInk, adjustments);
        if (!outcome.ok()) {
            WandSessionLinks.unlink(player.getUUID());
            if (!outcome.detail().isEmpty()) {
                player.displayClientMessage(com.example.akaishi.forbidden.magic.judge.MagicModifierSupport
                        .reason(outcome.detail()).copy().withStyle(ChatFormatting.RED), true);
            } else reject(player, outcome.result().name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        com.example.akaishi.forbidden.magic.cast.MagicCastManager.onFirstDelivered(player,
                () -> WandComboTracker.commit(player.getUUID(), spellKey, player.level().getGameTime()));
    }

    /** 施法反馈（动作栏；拒绝码走统一模板键，内容与 CastResult 一一对应） */
    private static void reject(ServerPlayer player, String key) {
        player.displayClientMessage(
                Component.translatable("gui.forbidden_hex.wand.cast." + key).withStyle(ChatFormatting.RED), true);
    }
}

package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 客户端候选和服务端写入使用同一份替换槽校验。 */
public final class InscriptionSelection {
    private InscriptionSelection() {}
    public static Component reason(List<String> words, List<String> inks, int slot, String word,
                                   String ink, String source, int open, Set<String> unlockedWords,
                                   Set<String> unlockedInks) {
        if (slot < 0 || slot >= open) return key("block_room");
        if (!unlockedWords.contains(word)) return key("fail.words");
        if (MagicInkTable.get(ink) == null) return key("block_no_ink");
        if (!unlockedInks.contains(ink)) return key("fail.ink_locked", ink);
        List<String> candidateWords = new ArrayList<>(words);
        List<String> candidateInks = new ArrayList<>(inks);
        candidateWords.set(slot, word);
        candidateInks.set(slot, ink);
        String main = "";
        for (int i = 0; i < candidateWords.size(); i++) {
            if (candidateWords.get(i).isEmpty()) { candidateInks.set(i, ""); continue; }
            if (!unlockedWords.contains(candidateWords.get(i))) return key("fail.words");
            if (!unlockedInks.contains(candidateInks.get(i))) return key("fail.ink_locked", candidateInks.get(i));
            if (main.isEmpty()) main = candidateInks.get(i);
        }
        var result = SpellPlans.compile(new SpellInput(candidateWords, candidateInks, main, source, 0f, 0));
        for (var diagnostic : result.diagnostics()) {
            // 参数草稿可先写入；冲突、未知词与不支持的机制仍立即阻止。
            if (diagnostic.severity() == CompileDiagnostic.Severity.ERROR && !diagnostic.code().equals("no_effect"))
                return Component.translatable("item.forbidden_hex.magic_core.plan_error." + diagnostic.code());
        }
        return null;
    }
    private static Component key(String suffix, Object... args) {
        return Component.translatable("gui.forbidden_hex.inscription." + suffix, args);
    }
}

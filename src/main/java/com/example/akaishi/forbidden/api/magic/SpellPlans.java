package com.example.akaishi.forbidden.api.magic;

import com.example.akaishi.forbidden.magic.formula.SpellPlanCompiler;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Tooltip 与服务共用的只读入口；直接读取八槽，不读取 PrimarySlot。 */
public final class SpellPlans {
    private SpellPlans() {}

    public static SpellCompileResult compile(SpellInput input) {
        return SpellPlanCompiler.compile(input);
    }

    public static SpellCompileResult fromCore(ItemStack stack, String mainInk, float seconds, int shots) {
        List<String> words = new ArrayList<>(8);
        List<String> inks = new ArrayList<>(8);
        var tag = stack.getTag();
        var wordTags = tag == null ? null : tag.getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING);
        var inkTags = tag == null ? null : tag.getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING);
        // 保留未知原始 id，让编译器明确诊断，避免旧读取器把非法词静默变空。
        int count = Math.min(9, Math.max(8, Math.max(wordTags == null ? 0 : wordTags.size(), inkTags == null ? 0 : inkTags.size())));
        for (int slot = 0; slot < count; slot++) {
            words.add(wordTags != null && slot < wordTags.size() ? wordTags.getString(slot) : "");
            inks.add(inkTags != null && slot < inkTags.size() ? inkTags.getString(slot) : "");
        }
        return compile(new SpellInput(words, inks, mainInk, MagicCoreItem.source(stack).id(), seconds, shots));
    }
}

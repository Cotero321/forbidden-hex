package com.example.akaishi.forbidden.item;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.magic.table.*;
import com.example.akaishi.forbidden.menu.CoreLayout;
import com.example.akaishi.forbidden.magic.formula.SpellPlanTooltips;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** 四源咒核，词和墨始终保留八槽对应关系。 */
public final class MagicCoreItem extends Item {
    public static final int SLOTS = 8;
    public static final String SOURCE_TAG = "Source", LEVEL_TAG = "Level", WORDS_TAG = "Words", WORD_INKS_TAG = "WordInks";
    private final MagicSource source;
    public MagicCoreItem(MagicSource source) { super(new Properties().stacksTo(1)); this.source = source; }
    public MagicSource source() { return source; }

    @Override public Component getName(ItemStack stack) {
        MagicWord first = null, last = null;
        for (int i = 0; i < SLOTS; i++) {
            MagicWord word = MagicWord.byId(word(stack, i));
            if (word == null) continue;
            if (first == null) first = word;
            last = word;
        }
        if (first == null) return super.getName(stack);
        return first == last ? Component.translatable("item.forbidden_hex.magic_core.engraved", first.label(), source(stack).label())
                : Component.translatable("item.forbidden_hex.magic_core.engraved_pair", first.label(), last.label(), source(stack).label());
    }

    public record Summary(SpellPlan result, Component reason) {}
    public static Summary summarize(ItemStack stack) {
        String main = "";
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!word(stack, slot).isEmpty()) { main = ink(stack, slot); break; }
        }
        var compiled = SpellPlans.fromCore(stack, main, 0f, 0);
        if (compiled.ok()) return new Summary(compiled.plan(), null);
        var error = compiled.diagnostics().stream().filter(d -> d.severity() == CompileDiagnostic.Severity.ERROR)
                .findFirst();
        return new Summary(null, error.map(d -> Component.translatable("item.forbidden_hex.magic_core.plan_error." + d.code()))
                .orElse(Component.translatable("item.forbidden_hex.magic_core.sum_reason.none")));
    }

    @Override public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.forbidden_hex.magic_core.tip.source", source(stack).label(), source(stack).id())
                .withStyle(ChatFormatting.AQUA));
        int count = 0;
        for (int i = 0; i < SLOTS; i++) if (!word(stack, i).isEmpty()) count++;
        if (count == 0) {
            tooltip.add(Component.translatable("item.forbidden_hex.magic_core.tip.hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.forbidden_hex.magic_core.tip.level", level(stack), CoreLayout.openSlotCount(level(stack)))
                .withStyle(ChatFormatting.YELLOW));
        boolean expanded = Platform.getEnvironment() == Env.CLIENT && (Screen.hasShiftDown() || Screen.hasControlDown());
        var summary = summarize(stack);
        if (summary.result() == null) tooltip.add(summary.reason().copy().withStyle(ChatFormatting.RED));
        else if (expanded) SpellPlanTooltips.append(tooltip, summary.result(), true, 0f);
        else appendCompactSummary(tooltip, summary.result());
        if (!expanded) {
            tooltip.add(Component.translatable("item.forbidden_hex.magic_core.tip.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            var word = MagicWord.byId(word(stack, i));
            if (word == null) continue;
            var ink = MagicInkTable.get(ink(stack, i));
            tooltip.add(Component.literal((i + 1) + " ").append(MagicGlyphs.leading(word.id(),
                    Component.literal(word.label() + " · " + (ink == null ? "—" : ink.label())))));
        }
    }

    private static void appendCompactSummary(List<Component> tooltip, SpellPlan plan) {
        tooltip.add(Component.translatable("item.forbidden_hex.magic_core.plan.operation_count", plan.operations().size())
                .withStyle(ChatFormatting.GRAY));
        for (var operation : plan.operations()) {
            tooltip.add(Component.translatable("item.forbidden_hex.magic_core.plan.brief",
                    Component.translatable("item.forbidden_hex.magic_core.op." + operation.effectId()),
                    SpellPlanTooltips.briefValue(operation))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.forbidden_hex.magic_core.plan.cost",
                String.format(java.util.Locale.ROOT, "%.2f", plan.cost().total()),
                String.format(java.util.Locale.ROOT, "%.2f", plan.cost().segmentCost()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MagicSource source(ItemStack stack) {
        if (stack.getItem() instanceof MagicCoreItem core) return core.source;
        CompoundTag tag = stack.getTag();
        return tag == null ? MagicSource.DEFAULT : MagicSource.byIdOrDefault(tag.getString(SOURCE_TAG));
    }
    public static int level(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getInt(LEVEL_TAG));
    }
    public static String word(ItemStack stack, int slot) {
        String id = entry(stack, WORDS_TAG, slot);
        return MagicWord.exists(id) ? id : "";
    }
    public static String ink(ItemStack stack, int slot) {
        String id = entry(stack, WORD_INKS_TAG, slot);
        return MagicInkTable.get(id) == null ? "" : id;
    }
    private static String entry(ItemStack stack, String key, int slot) {
        CompoundTag tag = stack.getTag();
        if (slot < 0 || slot >= SLOTS || tag == null || !tag.contains(key, Tag.TAG_LIST)) return "";
        ListTag entries = tag.getList(key, Tag.TAG_STRING);
        return slot < entries.size() ? entries.getString(slot) : "";
    }
    public static ItemStack blank(MagicSource source) {
        ItemStack stack = new ItemStack(AkaishiMagicItems.core(source).get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SOURCE_TAG, source.id());
        tag.putInt(LEVEL_TAG, 0);
        ListTag words = new ListTag(), inks = new ListTag();
        for (int i = 0; i < SLOTS; i++) { words.add(StringTag.valueOf("")); inks.add(StringTag.valueOf("")); }
        tag.put(WORDS_TAG, words);
        tag.put(WORD_INKS_TAG, inks);
        return stack;
    }
}

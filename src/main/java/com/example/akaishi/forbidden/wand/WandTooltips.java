package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 法杖模块的<b>悬浮文本组装</b>：把 {@link WandPartDef} 的词条翻译成人话。
 *
 * <p><b>为什么代码组装而不是逐模块写 lang 长句</b>：68 行模块的效果句是
 * 「词条 × 模板」的组合（"魔法消耗 -15%"），逐模块手写会出现
 * 68 份与表平行的第二真源 —— 表改了文案忘改，就"说的和做的不一样"。
 * 故 lang 里只放<b>模板键</b>（约 25 条通用句式），数值由代码按表填。
 */
public final class WandTooltips {

    private WandTooltips() {
    }

    /**
     * 法杖本体的<b>聚合</b>说明：把三段（端/柄/核）的同词条合并成一行 ——
     * 显示的不再是各段自己的值，而是<b>合成后的总效果</b>（乘算词条相乘、绝对词条相加）。
     *
     * <p>分组键 = 词条 + 作用域三元组（墨 / 源 / 威力域）——
     * 不同作用域的同一词条不合并（"该墨能耗 85%" 不能与全局消耗混为一谈）。
     * 行序 = {@link WandMod} 枚举序（稳定可 diff）。
     */
    public static void appendAggregated(List<Component> tooltip, List<WandPartDef> parts) {
        // ① 分组乘积
        Map<String, Float> values = new LinkedHashMap<>();
        Map<String, WandPartDef> representatives = new LinkedHashMap<>();
        for (WandMod mod : WandMod.values()) {
            for (WandPartDef part : parts) {
                float value = part.mod(mod);
                if (value == mod.defaultValue()) continue;
                String key = mod.name() + '|' + part.inkId() + '|' + part.sourceId() + '|' + part.powerScope();
                float existing = values.getOrDefault(key, mod.defaultValue());
                float merged;
                if (mod == WandMod.EXTRA_REGEN_INTERVAL) {
                    merged = (existing == mod.defaultValue()) ? value : Math.min(existing, value);
                } else if (mod.multiplicative()) {
                    merged = existing * value;
                } else {
                    merged = existing + value;
                }
                values.put(key, merged);
                representatives.put(key, part);
            }
        }
        // ② 逐词条渲染（枚举序 = 渲染序）
        for (WandMod mod : WandMod.values()) {
            for (Map.Entry<String, Float> entry : values.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(mod.name() + '|')) continue;
                WandPartDef scope = representatives.get(key);
                appendMod(tooltip, scope, mod, entry.getValue());
            }
        }
        // ③ 行为旗标：有则各一行
        for (WandFlag flag : WandFlag.values()) {
            boolean any = false;
            for (WandPartDef part : parts) any |= part.has(flag);
            if (any) tooltip.add(line("flag." + flagName(flag)));
        }
    }

    /** 一段模块的完整说明（灰字，逐行追加） */
    public static void appendPart(List<Component> tooltip, WandPartDef def) {
        for (WandMod mod : WandMod.values()) {
            float value = def.mod(mod);
            if (value == mod.defaultValue()) continue;
            appendMod(tooltip, def, mod, value);
        }
        if (def.kind() == WandPartDef.Kind.TIP && !def.powerScope().isEmpty()) {
            tooltip.add(line("source_tip_off_scope"));
        }
        if (def.kind() == WandPartDef.Kind.CORE && !def.powerScope().isEmpty()) {
            tooltip.add(key("source_core_scope", sourceLabel(def.sourceId())));
        }
        for (WandFlag flag : WandFlag.values()) {
            if (def.has(flag)) tooltip.add(line("flag." + flagName(flag)));
        }
    }

    private static void appendMod(List<Component> tooltip, WandPartDef def, WandMod mod, float value) {
        switch (mod) {
            case COST -> tooltip.add(pct("mod.cost", value));
            case COOLDOWN -> tooltip.add(pct("mod.cooldown", value));
            case CAST_TIME -> tooltip.add(pct("mod.cast_time", value));
            case RANGE -> tooltip.add(pct("mod.range", value));
            case RADIUS -> tooltip.add(pct("mod.radius", value));
            case EXTRA_POOL_FLAT -> tooltip.add(num("mod.extra_pool_flat", value));
            case HEAT_DISSIPATION -> tooltip.add(pct("mod.heat_dissipation", value));
            case HEAT_THRESHOLD -> tooltip.add(pct("mod.heat_threshold", value));
            case INK_COST -> tooltip.add(key("mod.ink_cost", inkLabel(def.inkId()), signed(value)));
            case SOURCE_COST -> tooltip.add(pct("mod.source_cost", value));
            case SOURCE_COOLDOWN -> tooltip.add(pct("mod.source_cooldown", value));
            case EFFICACY -> tooltip.add(pct("mod.efficacy", value));
            case POWER -> tooltip.add(key("mod.power", scopeLabel(def.powerScope()), signed(value)));
            case EXTRA_POOL -> tooltip.add(pct("mod.extra_pool", value));
            case REGEN_EFF -> tooltip.add(pct("mod.regen_eff", value));
            case MAIN_REGEN -> tooltip.add(pct("mod.main_regen", value));
            case EXTRA_REGEN_INTERVAL -> tooltip.add(key("mod.extra_regen_interval", num0(value)));
            case POST_CAST_REFUND -> tooltip.add(key("mod.post_cast_refund", num0(value * 100f)));
            case ATTACK_COST -> tooltip.add(pct("mod.attack_cost", value));
            case DEFENSE_COST -> tooltip.add(pct("mod.defense_cost", value));
            case ATTACK_COOLDOWN -> tooltip.add(pct("mod.attack_cooldown", value));
            case DEFENSE_COOLDOWN -> tooltip.add(pct("mod.defense_cooldown", value));
            case THIRD_COOLDOWN -> tooltip.add(pct("mod.third_cooldown", value));
            case THIRD_POWER -> tooltip.add(pct("mod.third_power", value));
        }
    }

    public static void appendPreview(List<Component> tooltip, net.minecraft.world.item.ItemStack wand,
            net.minecraft.world.item.ItemStack spell) {
        var summary = com.example.akaishi.forbidden.item.MagicCoreItem.summarize(spell);
        if (summary.result() == null) {
            tooltip.add(summary.reason().copy().withStyle(ChatFormatting.RED));
            return;
        }
        List<String> inks = new java.util.ArrayList<>();
        for (int i = 0; i < com.example.akaishi.forbidden.item.MagicCoreItem.SLOTS; i++) {
            if (!com.example.akaishi.forbidden.item.MagicCoreItem.word(spell, i).isEmpty())
                inks.add(com.example.akaishi.forbidden.item.MagicCoreItem.ink(spell, i));
        }
        var base = summary.result();
        var source = com.example.akaishi.forbidden.magic.table.MagicSource.byIdOrDefault(base.input().sourceId());
        var result = com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter.adjusted(base,
                WandModifiers.resolve(wand, source, inks, WandItem.extra(wand, source), 1));
        tooltip.add(key("preview.combo"));
        boolean expanded = dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT
                && net.minecraft.client.gui.screens.Screen.hasShiftDown();
        com.example.akaishi.forbidden.magic.formula.SpellPlanTooltips.append(tooltip, result, expanded,
                WandItem.extra(wand, source));
    }

    private static String decimal(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    // ==================== 格式化 ====================

    /** 系数 → 百分比串：0.90 → "-10%"、1.03 → "+3%"、1.00 → "" */
    public static String signed(float mul) {
        int percent = Math.round((mul - 1f) * 100f);
        if (percent == 0) return "";
        return (percent > 0 ? "+" : "") + percent + "%";
    }

    private static Component pct(String suffix, float mul) {
        return key(suffix, signed(mul));
    }

    private static Component num(String suffix, float value) {
        return key(suffix, num0(value));
    }

    private static String num0(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private static Component key(String suffix, Object... args) {
        return Component.translatable("gui.forbidden_hex.wand." + suffix, args).withStyle(ChatFormatting.GRAY);
    }

    private static Component line(String suffix) {
        return key(suffix);
    }

    private static String flagName(WandFlag flag) {
        return switch (flag) {
            case OVERFLOW_TO_EXTRA -> "overflow";
            case MAIN_REGEN_DOUBLE_WHEN_EXTRA_EMPTY -> "main_regen_double";
        };
    }

    private static String inkLabel(String inkId) {
        MagicInkDef ink = MagicInkTable.get(inkId);
        return ink == null ? inkId : ink.label();
    }

    private static String sourceLabel(String sourceId) {
        return com.example.akaishi.forbidden.magic.table.MagicSource.byIdOrDefault(sourceId).label();
    }

    private static String scopeLabel(String scope) {
        return Component.translatable("gui.forbidden_hex.wand.scope." + scope).getString();
    }
}

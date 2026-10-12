package com.example.akaishi.forbidden.magic.judge;

import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import net.minecraft.network.chat.Component;

/** 非主词的实际消费分类；不把表中的设计语义当成已经实现的机制。 */
public final class MagicModifierSupport {
    private MagicModifierSupport() {}

    public static String kind(MagicWord word, MagicWord core) {
        return switch (word) {
            // ★v20：原 8 个 unsupported 词已更换 E/X 并迁入 B 组，现在都有有效效果
            case CHA -> "fork";
            case XI -> MagicIds.Effect.DAMAGE.equals(core.effect()) ? "weakpoint" : "point";
            case ZHEN -> MagicIds.Effect.DAMAGE.equals(core.effect()) ? "piercing_point" : "point";
            case GEN, SI, JI_TRACE, FENG_SEAM -> "point";
            case MEN, ZHE -> "line_of_sight";
            case BIAO -> "hidden";
            case JIAN -> MagicIds.Effect.DAMAGE.equals(core.effect()) ? "piercing" : "numeric";
            case BENG -> MagicIds.Effect.DAMAGE.equals(core.effect()) ? "followup" : "numeric";
            case JING -> MagicIds.Effect.BREAK_TERMINATE.equals(core.effect()) ? "purify" : "numeric";
            // ★v20：迁入 B 组的新词按 B 组词的修饰分类
            case ZHAO_SUMMON, ZHOU -> MagicIds.Effect.DAMAGE.equals(core.effect()) ? "followup" : "numeric";
            case QIU -> "weaken";
            case BEI -> "field";
            case YUAN -> "boost";
            case HEN -> "bind";
            case CENG -> "sense";
            case WEI -> "link";
            default -> "numeric";
        };
    }

    public static Component description(MagicWord word, MagicWord core) {
        return Component.translatable("item.forbidden_hex.magic_core.modifier." + kind(word, core), word.label());
    }

    public static String rejection(ParsedSpell spell) {
        for (MagicWord word : spell.allWords()) {
            if (word != spell.core() && "unsupported".equals(kind(word, spell.core())))
                return "modifier:" + word.id();
        }
        return "";
    }

    public static Component reason(String detail) {
        if (detail.startsWith("modifier:")) {
            MagicWord word = MagicWord.byId(detail.substring("modifier:".length()));
            return Component.translatable("item.forbidden_hex.magic_core.modifier.unsupported",
                    word == null ? detail : word.label());
        }
        return Component.literal(detail);
    }
}

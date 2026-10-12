package com.example.akaishi.forbidden.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次施法的不可变视觉快照。 */
public record SpellVisual(
        String effectId,
        String deliveryId,
        String sourceId,
        String inkId,
        int inkRgb,
        int sourceRgb,
        float power,
        float radius,
        float duration,
        int wordCount,
        List<String> wordIds,
        String glyphs) {

    private static final int MAX_WORDS = 16;
    private static final int MAX_ID_LENGTH = 48;
    private static final int MAX_GLYPHS = 128;

    public SpellVisual {
        effectId = bounded(effectId);
        deliveryId = bounded(deliveryId);
        sourceId = bounded(sourceId);
        inkId = bounded(inkId);
        List<String> safeWords = new ArrayList<>();
        if (wordIds != null) {
            for (String wordId : wordIds) {
                if (safeWords.size() >= MAX_WORDS || wordId == null || wordId.length() > MAX_ID_LENGTH) {
                    break;
                }
                safeWords.add(wordId);
            }
        }
        wordIds = Collections.unmodifiableList(safeWords);
        wordCount = safeWords.size();
        glyphs = glyphs == null ? "" : glyphs.substring(0, Math.min(glyphs.length(), MAX_GLYPHS));
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), MAX_ID_LENGTH));
    }

    public SpellVisualSpecs.Form form() {
        return SpellVisualSpecs.form(effectId);
    }

    public SpellVisualSpecs.Carrier carrierSpec() {
        return SpellVisualSpecs.carrier(deliveryId);
    }

    public SpellCarrierForm carrier() {
        return carrierSpec().form();
    }

    public SpellVisualSpecs.Source body() {
        return SpellVisualSpecs.source(sourceId);
    }

    public SpellVisualSpecs.Modifier modifier() {
        int layers = 0;
        int detail = 0;
        float size = 1f;
        float alpha = 1f;
        float spin = 1f;
        for (String wordId : wordIds) {
            SpellVisualSpecs.Modifier modifier = SpellVisualSpecs.modifier(wordId);
            if (modifier != null) {
                layers += modifier.extraLayers();
                detail += modifier.extraDetail();
                size *= modifier.sizeMul();
                alpha *= modifier.alphaMul();
                spin *= modifier.spinMul();
            }
        }
        return new SpellVisualSpecs.Modifier(layers, detail, size, alpha, spin);
    }

    public static SpellVisual assemble(String coreWordId, String deliveryId, String mainInkId,
            String sourceId, float power, float radius, float duration, List<String> wordIds) {
        MagicWord core = MagicWord.byId(coreWordId);
        String effectId = core == null ? MagicIds.Effect.DAMAGE : core.effect();
        MagicInkDef ink = MagicInkTable.get(mainInkId);
        int inkRgb = ink == null ? 0xFFFFFF : ink.rgb();
        MagicSource source = MagicSource.byIdOrDefault(sourceId);
        List<String> safeWords = wordIds == null ? List.of() : wordIds;
        StringBuilder glyphs = new StringBuilder();
        for (String id : safeWords) {
            MagicWord word = MagicWord.byId(id);
            if (word != null) glyphs.appendCodePoint(word.glyph().codePointAt(0));
        }
        return new SpellVisual(effectId, deliveryId == null ? MagicIds.Delivery.INSTANT : deliveryId,
                source.id(), ink == null ? "" : ink.id(), inkRgb, source.rgb(), power, radius, duration,
                safeWords.size(), safeWords, glyphs.toString());
    }

    public static SpellVisual from(com.example.akaishi.forbidden.api.magic.exec.MagicContext context) {
        if (context == null || context.snapshot() == null) return null;
        var snapshot = context.snapshot();
        return assemble(snapshot.coreWordId(), snapshot.deliveryId(), context.mainInkId(), context.sourceId(),
                snapshot.power(), snapshot.radius(), snapshot.durationSeconds(), context.words());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(effectId, MAX_ID_LENGTH);
        buf.writeUtf(deliveryId, MAX_ID_LENGTH);
        buf.writeUtf(sourceId, MAX_ID_LENGTH);
        buf.writeUtf(inkId, MAX_ID_LENGTH);
        buf.writeInt(inkRgb);
        buf.writeInt(sourceRgb);
        buf.writeFloat(power);
        buf.writeFloat(radius);
        buf.writeFloat(duration);
        buf.writeVarInt(wordIds.size());
        for (String wordId : wordIds) buf.writeUtf(wordId, MAX_ID_LENGTH);
        buf.writeUtf(glyphs, MAX_GLYPHS);
    }

    public static SpellVisual read(FriendlyByteBuf buf) {
        String effectId = buf.readUtf(MAX_ID_LENGTH);
        String deliveryId = buf.readUtf(MAX_ID_LENGTH);
        String sourceId = buf.readUtf(MAX_ID_LENGTH);
        String inkId = buf.readUtf(MAX_ID_LENGTH);
        int inkRgb = buf.readInt();
        int sourceRgb = buf.readInt();
        float power = buf.readFloat();
        float radius = buf.readFloat();
        float duration = buf.readFloat();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_WORDS) throw new IllegalArgumentException("Invalid word count");
        List<String> wordIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) wordIds.add(buf.readUtf(MAX_ID_LENGTH));
        String glyphs = buf.readUtf(MAX_GLYPHS);
        return new SpellVisual(effectId, deliveryId, sourceId, inkId, inkRgb, sourceRgb,
                power, radius, duration, wordIds.size(), wordIds, glyphs);
    }

    public float baseSize() {
        float fromPower = 0.35f + Math.min(1.6f, Math.max(0f, power) * 0.12f);
        float fromRadius = Math.max(0f, radius) * 0.35f;
        return Math.min(3.0f, fromPower + fromRadius);
    }

    public float brightness() {
        return Math.min(1.4f, 0.6f + Math.max(0f, power) * 0.06f);
    }

    public float complexity() {
        return 1f + Math.max(0, wordCount - 1) * 0.12f;
    }
}

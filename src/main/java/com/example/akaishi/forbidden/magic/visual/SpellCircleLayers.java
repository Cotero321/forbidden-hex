package com.example.akaishi.forbidden.magic.visual;

import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 法阵的四层物理槽位快照。
 *
 * <p>这里刻意不使用 {@code MagicParser} 的角色分组：法阵必须保留咒核中的原始位置，
 * 因此槽位 {@code 0..1}、{@code 2..3}、{@code 4..5}、{@code 6..7} 永远对应四层。
 */
public record SpellCircleLayers(List<Layer> layers) {
    public static final int SLOT_COUNT = 8;
    public static final int LAYER_COUNT = 4;
    public static final int SLOTS_PER_LAYER = 2;

    public SpellCircleLayers {
        List<Layer> safe = new ArrayList<>(LAYER_COUNT);
        for (int index = 0; index < LAYER_COUNT; index++) {
            Layer layer = layers != null && index < layers.size() ? layers.get(index) : null;
            safe.add(layer == null ? Layer.empty(index) : layer);
        }
        layers = Collections.unmodifiableList(safe);
    }

    public Layer layer(int index) {
        return index >= 0 && index < layers.size() ? layers.get(index) : Layer.empty(index);
    }

    public static SpellCircleLayers from(MagicContext context, SpellVisual visual) {
        return from(visual, context == null ? null : context.slotWords(), context == null ? null : context.slotInks());
    }

    public static SpellCircleLayers from(SpellVisual visual, List<String> slotWords, List<String> slotInks) {
        List<String> words = normalize(slotWords, visual == null ? null : visual.slotWords());
        List<String> inks = normalize(slotInks, visual == null ? null : visual.slotInks());
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (MagicWord.byId(words.get(slot)) == null) words.set(slot, "");
            if (words.get(slot).isEmpty() || MagicInkTable.get(inks.get(slot)) == null) inks.set(slot, "");
        }
        List<Layer> result = new ArrayList<>(LAYER_COUNT);
        for (int layerIndex = 0; layerIndex < LAYER_COUNT; layerIndex++) {
            int firstSlot = layerIndex * SLOTS_PER_LAYER;
            List<String> layerWords = List.of(words.get(firstSlot), words.get(firstSlot + 1));
            List<String> layerInks = List.of(inks.get(firstSlot), inks.get(firstSlot + 1));
            StringBuilder glyphs = new StringBuilder();
            String inkId = "";
            for (int i = 0; i < SLOTS_PER_LAYER; i++) {
                String wordId = layerWords.get(i);
                if (wordId != null && !wordId.isEmpty()) {
                    MagicWord word = MagicWord.byId(wordId);
                    if (word != null && word.glyph() != null && !word.glyph().isEmpty()) {
                        glyphs.appendCodePoint(word.glyph().codePointAt(0));
                    }
                    if (inkId.isEmpty() && layerInks.get(i) != null && !layerInks.get(i).isEmpty()) {
                        inkId = layerInks.get(i);
                    }
                }
            }
            int fallback = visual == null ? 0xFFFFFF : visual.inkRgb();
            int rgb = inkRgb(inkId, fallback);
            result.add(new Layer(layerIndex, layerWords, layerInks, glyphs.toString(),
                    !glyphs.isEmpty() || layerWords.stream().anyMatch(value -> value != null && !value.isEmpty()),
                    rgb, fallback));
        }
        return new SpellCircleLayers(result);
    }

    private static List<String> normalize(List<String> values, List<String> fallback) {
        List<String> source = values != null && !values.isEmpty() ? values : fallback;
        List<String> normalized = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String value = source != null && i < source.size() ? source.get(i) : "";
            normalized.add(value == null || value.length() > 48 || value.indexOf((char) 1) >= 0 ? "" : value);
        }
        return normalized;
    }

    private static int inkRgb(String inkId, int fallback) {
        if (inkId == null || inkId.isEmpty()) return fallback;
        MagicInkDef ink = MagicInkTable.get(inkId);
        return ink == null ? fallback : ink.rgb();
    }

    public record Slot(String glyph, int inkRgb) {
        public boolean occupied() {
            return glyph != null && !glyph.isEmpty();
        }
    }

    public record Layer(int index, List<String> wordIds, List<String> inkIds,
                        String glyphs, boolean occupied, int inkRgb, List<Slot> slots) {
        public Layer(int index, List<String> wordIds, List<String> inkIds,
                String glyphs, boolean occupied, int inkRgb) {
            this(index, wordIds, inkIds, glyphs, occupied, inkRgb, inkRgb);
        }

        private Layer(int index, List<String> wordIds, List<String> inkIds,
                String glyphs, boolean occupied, int inkRgb, int fallback) {
            this(index, wordIds, inkIds, glyphs, occupied, inkRgb, resolveSlots(wordIds, inkIds, fallback));
        }

        public Layer {
            wordIds = fixedPair(wordIds);
            inkIds = fixedPair(inkIds);
            glyphs = glyphs == null ? "" : glyphs;
            slots = slots == null || slots.size() != SLOTS_PER_LAYER || slots.stream().anyMatch(value -> value == null)
                    ? resolveSlots(wordIds, inkIds, inkRgb) : List.copyOf(slots);
        }

        private static List<Slot> resolveSlots(List<String> words, List<String> inks, int fallback) {
            List<String> safeWords = fixedPair(words), safeInks = fixedPair(inks);
            List<Slot> resolved = new ArrayList<>(SLOTS_PER_LAYER);
            for (int slot = 0; slot < SLOTS_PER_LAYER; slot++) {
                MagicWord word = MagicWord.byId(safeWords.get(slot));
                String glyph = word == null || word.glyph() == null ? "" : word.glyph();
                resolved.add(new Slot(glyph, SpellCircleLayers.inkRgb(safeInks.get(slot), fallback)));
            }
            return List.copyOf(resolved);
        }

        public Slot slot(int index) {
            return slots.get(index);
        }

        private static List<String> fixedPair(List<String> values) {
            List<String> result = new ArrayList<>(SLOTS_PER_LAYER);
            for (int i = 0; i < SLOTS_PER_LAYER; i++) {
                String value = values != null && i < values.size() ? values.get(i) : "";
                result.add(value == null ? "" : value);
            }
            return Collections.unmodifiableList(result);
        }

        public static Layer empty(int index) {
            return new Layer(index, List.of("", ""), List.of("", ""), "", false, 0xFFFFFF);
        }
    }
}

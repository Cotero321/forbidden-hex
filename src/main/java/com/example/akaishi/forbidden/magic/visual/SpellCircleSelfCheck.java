package com.example.akaishi.forbidden.magic.visual;

import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Executable data and wire-format regression checks for fixed-slot spell circles. */
public final class SpellCircleSelfCheck {
    private SpellCircleSelfCheck() {
    }

    public static void main(String[] args) {
        List<String> empty = java.util.Collections.nCopies(8, "");
        SpellVisual emptyVisual = visual(empty, empty);
        require(emptyVisual.circleLayers().layers().size() == 4, "empty core must retain four layers");
        require(emptyVisual.circleLayers().layers().stream().noneMatch(SpellCircleLayers.Layer::occupied),
                "empty layers must not contain glyphs");

        List<String> sparse = List.of("tou", "", "", "", "", "", "", "hu");
        List<String> inks = List.of("chi", "", "", "", "", "", "", "yao");
        SpellVisual sparseVisual = visual(sparse, inks);
        SpellCircleLayers layers = sparseVisual.circleLayers();
        require(layers.layer(0).wordIds().equals(List.of("tou", "")), "first physical pair moved");
        require(!layers.layer(1).occupied() && !layers.layer(2).occupied(), "empty middle layers collapsed");
        require(layers.layer(3).wordIds().equals(List.of("", "hu")), "last physical pair moved");
        require(layers.layer(0).inkRgb() != layers.layer(3).inkRgb(), "layer inks were flattened");

        List<String> full = java.util.Arrays.stream(MagicWord.values()).limit(8).map(MagicWord::id).toList();
        SpellCircleLayers fullLayers = visual(full, java.util.Collections.nCopies(8, "chi")).circleLayers();
        for (int layer = 0; layer < 4; layer++) {
            require(fullLayers.layer(layer).wordIds().equals(full.subList(layer * 2, layer * 2 + 2)),
                    "full core lost physical layer " + layer);
        }

        MagicContext context = new MagicContext(null, null, Vec3.ZERO, "san", "chi", List.of("chi"),
                List.of("tou", "hu"), 1, null, sparse, inks, null, null);
        require(context.withOrigin(new Vec3(2, 3, 4)).slotWords().equals(sparse), "origin copy lost slots");
        require(context.withImpact(null, Vec3.ZERO, new Vec3(0, 0, 1)).slotInks().equals(inks),
                "impact copy lost inks");
        new MagicContext(null, null, Vec3.ZERO, "san", "chi", List.of("chi"),
                List.of("tou"), 1, null, null, null);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            sparseVisual.write(buffer);
            buffer.writeInt(0x51A7C1E);
            SpellVisual decoded = SpellVisual.read(buffer);
            require(decoded.slotWords().equals(sparse), "wire format lost empty slots");
            require(decoded.slotInks().equals(inks), "wire format lost per-slot inks");
            require(decoded.equals(sparseVisual), "wire format changed visual snapshot");
            require(buffer.readInt() == 0x51A7C1E && buffer.readableBytes() == 0,
                    "wire format consumed the next packet field");
        } finally {
            buffer.release();
        }

        List<String> damaged = List.of("unknown_word", "x".repeat(49), "tou", "", "", "", "", "hu");
        SpellVisual damagedVisual = visual(damaged, inks);
        require(!damagedVisual.circleLayers().layer(0).occupied(), "unknown words must become empty slots");
        require(damagedVisual.slotWords().get(1).isEmpty(), "overlong slot must become empty");
        require(damagedVisual.circleLayers().layer(1).wordIds().get(0).equals("tou"),
                "damaged earlier slots moved a later valid word");
        require(damagedVisual.circleLayers().layer(3).wordIds().get(1).equals("hu"),
                "damaged input truncated the last layer");
        System.out.println("SpellCircleSelfCheck PASS: empty, sparse, full, context copies, wire format, damaged slots");
    }

    private static SpellVisual visual(List<String> slots, List<String> inks) {
        return SpellVisual.assemble("tou", "place", "chi", "san", 2f, 2f, 3f,
                slots.stream().filter(word -> word != null && !word.isEmpty()).toList(), slots, inks);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

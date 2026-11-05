package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.magic.MagicDamageTypes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** 法术子类型及父级标签由主墨映射生成，避免注册和数据包名单分叉。 */
public final class SpellDamageDataProvider implements DataProvider {
    private final Path root;

    public SpellDamageDataProvider(PackOutput output) {
        root = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        List<String> ordinary = new ArrayList<>();
        ordinary.add(MagicDamageTypes.SPELL.location().toString());
        MagicDamageTypes.INK_TYPES.values().stream().sorted(java.util.Comparator.comparing(key -> key.location().toString()))
                .forEach(key -> {
                    boolean psychic = key.equals(MagicDamageTypes.SPELL_PSYCHIC);
                    JsonObject type = new JsonObject();
                    type.addProperty("exhaustion", 0f);
                    type.addProperty("message_id", psychic ? "forbidden_hex.psychic" : "forbidden_hex.spell");
                    type.addProperty("scaling", psychic ? "always" : "when_caused_by_living_non_player");
                    writes.add(DataProvider.saveStable(output, type,
                            root.resolve("data/forbidden_hex/damage_type/" + key.location().getPath() + ".json")));
                    if (!psychic) ordinary.add(key.location().toString());
                });
        writes.add(tag(output, "magic_resistible", ordinary));
        writes.add(tag(output, "spell", List.of("#forbidden_hex:magic_resistible", "forbidden_hex:spell_psychic")));
        writes.add(tag(output, "psychic", List.of("forbidden_hex:psychic", "forbidden_hex:spell_psychic")));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> tag(CachedOutput output, String name, List<String> entries) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        JsonArray values = new JsonArray();
        entries.forEach(values::add);
        tag.add("values", values);
        return DataProvider.saveStable(output, tag, root.resolve("data/forbidden_hex/tags/damage_type/" + name + ".json"));
    }

    @Override
    public String getName() {
        return "Forbidden Hex spell damage types";
    }
}

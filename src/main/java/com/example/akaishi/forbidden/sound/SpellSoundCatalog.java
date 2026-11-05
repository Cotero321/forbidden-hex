package com.example.akaishi.forbidden.sound;

import com.example.akaishi.forbidden.magic.table.MagicDeliveryTable;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The same synthesis specification is consumed by the generator and sound registration. */
public final class SpellSoundCatalog {
    private static final JsonObject SPEC = load();
    private static final Set<String> EVENTS = createEvents();

    private SpellSoundCatalog() { }

    private static JsonObject load() {
        var stream = SpellSoundCatalog.class.getResourceAsStream("/assets/forbidden_hex/audio/spell_synth.json");
        if (stream == null) throw new IllegalStateException("Missing spell sound synthesis specification");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid spell sound synthesis specification", exception);
        }
    }

    private static Set<String> createEvents() {
        Set<String> events = new LinkedHashSet<>();
        for (String source : ids("sources")) {
            for (String stage : List.of("charge", "sustain", "release")) {
                events.add("spell.source." + source + "." + stage);
            }
        }
        for (String group : List.of("effects", "inks", "carriers")) {
            String singular = switch (group) { case "effects" -> "effect"; case "inks" -> "ink"; default -> "carrier"; };
            for (String id : ids(group)) events.add("spell." + singular + "." + id);
        }
        for (String ink : ids("inks")) events.add("spell.ink." + ink + ".charge");
        for (String id : List.of("end", "interrupt", "shatter", "pulse")) events.add("spell." + id);
        return java.util.Collections.unmodifiableSet(events);
    }

    public static Set<String> events() { return EVENTS; }
    public static Set<String> ids(String group) { return Set.copyOf(SPEC.getAsJsonObject(group).keySet()); }
    public static String known(String group, String id, String fallback) {
        return id != null && SPEC.getAsJsonObject(group).has(id) ? id : fallback;
    }

    public static float frequency(String effect) {
        String id = known("effects", effect, "damage");
        return SPEC.getAsJsonObject("effects").getAsJsonObject(id).get("frequency").getAsFloat();
    }

    public static List<String> inspect() {
        List<String> failures = new ArrayList<>();
        for (String id : MagicEffectTable.all().keySet()) {
            if (!ids("effects").contains(id)) failures.add("Missing effect sound: " + id);
        }
        for (String id : MagicDeliveryTable.all().keySet()) {
            if (!ids("carriers").contains(id)) failures.add("Missing carrier sound: " + id);
        }
        for (var ink : MagicInkTable.all().values()) {
            if (!ids("inks").contains(ink.id())) failures.add("Missing ink sound: " + ink.id());
        }
        for (MagicSource source : MagicSource.values()) {
            if (!ids("sources").contains(source.id())) failures.add("Missing source sound: " + source.id());
        }
        return failures;
    }
}

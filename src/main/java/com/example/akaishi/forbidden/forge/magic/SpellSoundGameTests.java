package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import com.example.akaishi.forbidden.sound.SpellSoundCatalog;
import com.example.akaishi.forbidden.sound.SpellSoundEvent;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class SpellSoundGameTests {
    private SpellSoundGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void sound_catalog_has_registered_playable_assets(GameTestHelper helper) throws Exception {
        helper.assertTrue(SpellSoundCatalog.inspect().isEmpty(), SpellSoundCatalog.inspect().toString());
        var stream = SpellSoundGameTests.class.getResourceAsStream("/assets/forbidden_hex/sounds.json");
        helper.assertTrue(stream != null, "Missing sounds.json");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            for (String event : SpellSoundCatalog.events()) {
                helper.assertTrue(AkaishiForbiddenSounds.spell(event) != null, "Unregistered sound " + event);
                helper.assertTrue(json.has(event), "Missing sound definition " + event);
                for (var entry : json.getAsJsonObject(event).getAsJsonArray("sounds")) {
                    ResourceLocation id = new ResourceLocation(entry.getAsJsonObject().get("name").getAsString());
                    try (var audio = SpellSoundGameTests.class.getResourceAsStream("/assets/" + id.getNamespace()
                            + "/sounds/" + id.getPath() + ".ogg")) {
                        helper.assertTrue(audio != null, "Missing OGG " + id);
                        helper.assertTrue(audio.read() == 'O' && audio.read() == 'g' && audio.read() == 'g'
                                && audio.read() == 'S', "Invalid OGG container " + id);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void sound_protocol_roundtrip_and_boundaries(GameTestHelper helper) {
        SpellVisual visual = SpellVisual.assemble("tou", "projectile", "chi", "dark", 4f, 2f, 3f,
                List.of("tou", "hu"), List.of("tou", "", "hu"), List.of("chi", "", "yao"));
        for (var phase : SpellSoundEvent.Phase.values()) {
            SpellSoundEvent original = new SpellSoundEvent(helper.getLevel().dimension().location(),
                    UUID.randomUUID(), 108, phase, new Vec3(1.25d, 82d, -4.5d), 12L, 30L, visual);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                original.write(buffer);
                buffer.writeInt(0x35BC21A9);
                helper.assertTrue(original.equals(SpellSoundEvent.read(buffer)), "Sound packet mismatch " + phase);
                helper.assertTrue(buffer.readInt() == 0x35BC21A9 && !buffer.isReadable(), "Sound packet overread");
            } finally { buffer.release(); }
        }
        boolean rejected = false;
        try {
            new SpellSoundEvent(helper.getLevel().dimension().location(), UUID.randomUUID(), -1,
                    SpellSoundEvent.Phase.CHARGE, new Vec3(Double.NaN, 0d, 0d), 0L, 1L, visual);
        } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Non-finite sound endpoint accepted");
        helper.succeed();
    }
}

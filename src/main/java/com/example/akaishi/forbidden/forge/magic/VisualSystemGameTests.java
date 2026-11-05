package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.sanity.ClientDeepSeaLock;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.api.magic.SpellPlans;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;
import com.example.akaishi.forbidden.wand.WandItem;
import com.example.akaishi.forbidden.wand.WandTipTable;
import com.example.akaishi.forbidden.wand.WandHandleTable;
import com.example.akaishi.forbidden.wand.WandCoreTable;
import com.example.akaishi.forbidden.visual.CastVisualTimeline;
import com.example.akaishi.forbidden.visual.ItemTooltipMode;
import com.example.akaishi.forbidden.visual.PostEffectPolicy;
import com.example.akaishi.forbidden.visual.ShaderCompatibility;
import com.example.akaishi.forbidden.visual.VisualColor;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.visual.VisualMaterial;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class VisualSystemGameTests {
    private static final ResourceLocation ENERGY = new ResourceLocation("forbidden_hex", "energy");
    private static final ResourceLocation SANITY = new ResourceLocation("forbidden_hex", "sanity_vision");
    private static final ResourceLocation UNNAMEABLE = new ResourceLocation("forbidden_hex", "unnameable");

    private VisualSystemGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void spellInkMaterialCoverage(GameTestHelper helper) {
        var materials = com.example.akaishi.forbidden.magic.visual.SpellInkMaterial.ALL;
        helper.assertTrue(materials.keySet().equals(com.example.akaishi.forbidden.magic.table.MagicInkTable.all().keySet()),
                "Spell ink material closure differs from actual inks");
        for (var material : materials.values()) {
            helper.assertTrue(Float.isFinite(material.flowSpeed()) && material.flowSpeed() > 0f
                            && material.turbulence() >= 0f && material.turbulence() <= 0.25f
                            && material.trailWidth() > 0f && material.trailWidth() <= 1.25f
                            && material.fadePower() > 0f,
                    "Unbounded spell material parameters");
        }
        helper.assertTrue(materials.get("yuan").rise() < 0f && (materials.get("yuan").edgeRgb() & 255)
                > (materials.get("yuan").edgeRgb() >> 16 & 255), "Water ink must settle and retain blue highlights");
        helper.assertTrue(com.example.akaishi.forbidden.magic.visual.SpellInkMaterial.get(null)
                == com.example.akaishi.forbidden.magic.visual.SpellInkMaterial.get("unknown"), "Unknown ink fallback differs");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spellCarrierEnvelope(GameTestHelper helper) {
        var visual = com.example.akaishi.forbidden.magic.visual.SpellVisual.assemble("yin", "instant",
                "chi", "san", 1f, 0f, 1f, List.of("yin"));
        for (int ticks : new int[]{1, 6, 8, 20, 100}) {
            var event = new com.example.akaishi.forbidden.magic.visual.SpellVisualEvent(visual,
                    net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, -1, ticks);
            helper.assertTrue(event.alphaAt(0f) == 0f && event.alphaAt(ticks) == 0f,
                    "Carrier envelope has a hard lifecycle boundary");
            helper.assertTrue(event.alphaAt(-1f) == 0f && event.alphaAt(Float.NaN) == 0f
                    && event.alphaAt(Float.POSITIVE_INFINITY) == 0f, "Invalid age reached rendering");
            float previous = 0f;
            for (int sample = 0; sample <= ticks * 400; sample++) {
                float age = sample / 400f;
                float alpha = event.alphaAt(age);
                helper.assertTrue(Float.isFinite(alpha) && alpha >= 0f && alpha <= 1f,
                        "Carrier opacity escaped bounds");
                helper.assertTrue(Math.abs(alpha - previous) < 0.04f, "Carrier envelope jumped");
                if (age <= Math.min(2f, ticks * 0.16f)) {
                    helper.assertTrue(alpha >= previous, "Carrier attack is not monotone");
                }
                if (age > ticks - Math.min(8f, ticks * 0.55f)) {
                    helper.assertTrue(alpha <= previous + 0.0001f, "Carrier release is not monotone");
                }
                previous = alpha;
            }
            helper.assertTrue(event.alphaAt(ticks * 0.4f) > 0.95f, "Carrier lost its readable hold");
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void spellCameraImpulseBoundaries(GameTestHelper helper) {
        helper.assertTrue(com.example.akaishi.forbidden.magic.visual.SpellFeedback.contractCheck(),
                "Spell impulse exceeded duration, strength or cooldown");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void materialParameterBoundaries(GameTestHelper helper) {
        VisualMaterial parsed = VisualMaterial.parse(JsonParser.parseString(
                "{\"uniforms\":{\"FlowSpeed\":-4,\"PulseStrength\":1,\"EdgeSoftness\":0}}").getAsJsonObject());
        helper.assertTrue(parsed.uniforms().get("FlowSpeed") == -4f, "Reverse flow was rejected");
        reject(helper, () -> material(Map.of("FlowSpeed", 4.01f)), "Out-of-range flow was accepted");
        reject(helper, () -> material(Map.of("GlowStrength", Float.NaN)), "NaN reached a shader uniform");
        reject(helper, () -> material(Map.of("PulseSpeed", Float.POSITIVE_INFINITY)), "Infinity reached a shader uniform");
        reject(helper, () -> material(Map.of("ProjMat", 1f)), "Reserved engine uniform was accepted");
        reject(helper, () -> material(Map.of("invalid name", 1f)), "Invalid scalar name was accepted");
        reject(helper, () -> VisualMaterial.parse(JsonParser.parseString(
                "{\"uniforms\":{\"FlowSpeed\":\"fast\"}}").getAsJsonObject()), "Non-numeric scalar was accepted");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void immutableMaterialState(GameTestHelper helper) {
        Map<String, Float> parameters = new HashMap<>(Map.of("GlowStrength", 1f));
        VisualMaterial material = material(parameters);
        parameters.put("GlowStrength", 2f);
        helper.assertTrue(material.uniforms().get("GlowStrength") == 1f, "Caller changed cached material state");
        boolean immutable = false;
        try {
            material.uniforms().put("GlowStrength", 3f);
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Material parameters remained mutable");
        helper.assertTrue(material.equals(material(Map.of("GlowStrength", 1f))), "Equivalent materials have different cache keys");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void postEffectPrioritiesAndFailure(GameTestHelper helper) {
        List<PostEffectPolicy.Request> requests = List.of(
                new PostEffectPolicy.Request(SANITY, 10, 0.8f),
                new PostEffectPolicy.Request(UNNAMEABLE, 100, 0.1f));
        helper.assertTrue(UNNAMEABLE.equals(PostEffectPolicy.select(requests, Set.of())), "Higher-priority effect did not win");
        helper.assertTrue(SANITY.equals(PostEffectPolicy.select(requests, Set.of(UNNAMEABLE))), "Failed effect did not yield to fallback");
        helper.assertTrue(PostEffectPolicy.select(requests, Set.of(SANITY, UNNAMEABLE)) == null, "Failed effects were selected");
        helper.assertTrue(PostEffectPolicy.select(List.of(
                new PostEffectPolicy.Request(SANITY, 10, Float.NaN),
                new PostEffectPolicy.Request(UNNAMEABLE, 100, 0f)), Set.of()) == null, "Invalid or invisible effect was selected");
        List<PostEffectPolicy.Request> tied = List.of(new PostEffectPolicy.Request(UNNAMEABLE, 10, 1f), requests.get(0));
        helper.assertTrue(SANITY.equals(PostEffectPolicy.select(tied, Set.of())), "Equal-priority selection is not deterministic");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void shaderPackFallbackAndPostOwnership(GameTestHelper helper) {
        for (ShaderCompatibility.State state : ShaderCompatibility.State.values()) {
            boolean allowed = ShaderCompatibility.allowsCustomPrograms(state, false);
            boolean vanilla = state == ShaderCompatibility.State.ABSENT || state == ShaderCompatibility.State.INACTIVE;
            helper.assertTrue(allowed == vanilla, "Active or unknown shader state allowed local programs");
            helper.assertTrue(!ShaderCompatibility.allowsCustomPrograms(state, true), "Forced fallback was bypassed");
            ResourceLocation requested = allowed ? SANITY : null;
            helper.assertTrue(PostEffectPolicy.decide(false, SANITY, requested)
                    == (allowed ? PostEffectPolicy.Action.KEEP : PostEffectPolicy.Action.RELEASE),
                    "Shader state did not release only the owned post chain");
            helper.assertTrue(PostEffectPolicy.decide(true, SANITY, requested) == PostEffectPolicy.Action.YIELD,
                    "Shader transition would shut down an external post chain");
        }
        helper.assertTrue(PostEffectPolicy.decide(false, null,
                ShaderCompatibility.allowsCustomPrograms(ShaderCompatibility.State.INACTIVE, false) ? SANITY : null)
                == PostEffectPolicy.Action.LOAD, "Disabled shader pack prevented post-effect recovery");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void externalPostChainOwnership(GameTestHelper helper) {
        helper.assertTrue(PostEffectPolicy.decide(true, null, SANITY) == PostEffectPolicy.Action.YIELD,
                "Local effect would overwrite an external chain");
        helper.assertTrue(PostEffectPolicy.decide(true, SANITY, null) == PostEffectPolicy.Action.YIELD,
                "Release would shut down an external chain");
        helper.assertTrue(PostEffectPolicy.decide(false, SANITY, SANITY) == PostEffectPolicy.Action.KEEP,
                "Unchanged effect would rebuild its render targets");
        helper.assertTrue(PostEffectPolicy.decide(false, SANITY, UNNAMEABLE) == PostEffectPolicy.Action.LOAD,
                "Higher-priority chain could not replace the owned chain");
        helper.assertTrue(PostEffectPolicy.decide(false, SANITY, null) == PostEffectPolicy.Action.RELEASE,
                "Owned effect would remain active after removal");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void visualColorBounds(GameTestHelper helper) {
        helper.assertTrue(VisualColor.withAlpha(0x123456, 2f) == 0xFF123456, "Opacity exceeded one");
        helper.assertTrue(VisualColor.withAlpha(0xFF123456, Float.NaN) == 0x00123456, "NaN opacity was not cleared");
        helper.assertTrue(VisualColor.mix(0, 0xFFFFFF, 0.5f) == 0x808080, "Color interpolation changed channel rounding");
        helper.assertTrue(VisualColor.brighten(0x123456, 0f) == 0x123456, "Zero brightening changed the color");
        helper.assertTrue(VisualColor.brighten(0x123456, 1f) == 0xFFFFFF, "Full brightening was not white");
        helper.assertTrue(VisualColor.red(0xFF0000) == 1f && VisualColor.blue(0xFF0000) == 0f, "RGB channels are swapped");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void wandVisualTimelineAndTooltipModes(GameTestHelper helper) {
        CastVisualTimeline timeline = new CastVisualTimeline();
        timeline.tick(MagicCastState.CHARGING);
        helper.assertTrue(timeline.chargeProgress(20f, 0f) == 0f, "Charge began before the first tick");
        timeline.tick(MagicCastState.CHARGING);
        helper.assertTrue(timeline.chargeProgress(20f, 0.5f) > 0f, "Charge timeline did not advance");
        float age = timeline.age(0.5f);
        timeline.chargeProgress(20f, 0.5f);
        timeline.transition(0.5f);
        timeline.segmentPhase(5f, 0.5f);
        helper.assertTrue(timeline.age(0.5f) == age, "Rendering advanced the timeline");
        for (int i = 0; i < 40; i++) timeline.tick(MagicCastState.CHARGING);
        helper.assertTrue(timeline.chargeProgress(20f, 0f) < 1f, "Local timing confirmed server completion");
        helper.assertTrue(timeline.segmentPhase(5f, 0.5f) >= 0f && timeline.segmentPhase(5f, 0.5f) < 1f,
                "Segment phase escaped its unit interval");
        helper.assertTrue(timeline.segmentPhase(Float.NaN, 0f) == 0f, "Invalid interval poisoned the phase");
        timeline.tick(MagicCastState.CHANNELING);
        helper.assertTrue(timeline.age(0f) == 0f, "State transition retained old age");
        timeline.reset();
        helper.assertTrue(timeline.state() == MagicCastState.IDLE && timeline.age(0f) == 0f,
                "Timeline reset retained state");
        helper.assertTrue(ItemTooltipMode.select(false, false) == ItemTooltipMode.SUMMARY,
                "Default tooltip mode changed");
        helper.assertTrue(ItemTooltipMode.select(true, false) == ItemTooltipMode.USAGE,
                "Shift tooltip mode is not usage");
        helper.assertTrue(ItemTooltipMode.select(false, true) == ItemTooltipMode.TECHNICAL,
                "Ctrl tooltip mode is not technical");
        helper.assertTrue(ItemTooltipMode.select(true, true) == ItemTooltipMode.TECHNICAL,
                "Ctrl did not take priority over Shift");
        helper.assertTrue(timeline.chargeProgress(Float.NaN, 0f) == 0f,
                "Non-finite charge duration was accepted");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void releaseKeepsNonChannelVisuals(GameTestHelper helper) {
        CastVisualTimeline timeline = new CastVisualTimeline();
        timeline.tick(MagicCastState.CHARGING, false, true);
        timeline.tick(MagicCastState.CHARGING, false, false);
        helper.assertTrue(timeline.state() == MagicCastState.CHARGING && timeline.age(0f) == 1f,
                "Non-channel charge disappeared on release");
        timeline.tick(MagicCastState.VOLLEYING, false, false);
        timeline.tick(MagicCastState.VOLLEYING, false, false);
        helper.assertTrue(timeline.state() == MagicCastState.VOLLEYING && timeline.age(0f) == 1f,
                "Volley stopped advancing on release");
        timeline.tick(MagicCastState.IDLE, false, false);
        helper.assertTrue(timeline.state() == MagicCastState.IDLE, "Server idle did not stop volley visuals");
        for (MagicCastState state : List.of(MagicCastState.CHARGING, MagicCastState.CHANNELING)) {
            timeline.tick(state, true, true);
            helper.assertTrue(timeline.state() == state, "Held channel lost server state");
            timeline.tick(state, true, false);
            helper.assertTrue(timeline.state() == MagicCastState.IDLE, "Channel visuals survived release");
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void itemDetailViewsAndDamagedSlots(GameTestHelper helper) {
        ItemStack core = MagicCoreItem.blank(MagicSource.LIFE);
        core.getOrCreateTag().getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("beng"));
        core.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("chi"));
        core.getOrCreateTag().putInt(MagicCoreItem.LEVEL_TAG, 1);
        helper.assertTrue(MagicCoreItem.summarize(core).result() != null, "Tooltip fixture is not a valid spell");
        ItemStack wand = AkaishiWandItems.createWand(WandTipTable.all().keySet().iterator().next(),
                WandHandleTable.all().keySet().iterator().next(), WandCoreTable.all().keySet().iterator().next());
        WandItem.setSpell(wand, core);
        ItemStack originalCore = core.copy();
        ItemStack originalWand = wand.copy();
        for (ItemTooltipMode mode : ItemTooltipMode.values()) {
            List<Component> lines = new ArrayList<>();
            MagicCoreItem.appendDetails(core, lines, mode);
            helper.assertTrue(hasKey(lines, "item.forbidden_hex.magic_core.tip.socket") == (mode == ItemTooltipMode.USAGE),
                    "Core usage leaked into another view");
            helper.assertTrue(hasKey(lines, "item.forbidden_hex.magic_core.tip.slot") == (mode == ItemTooltipMode.TECHNICAL),
                    "Core slot diagnostics leaked into another view");
            helper.assertTrue(hasKey(lines, "item.forbidden_hex.magic_core.plan.payment") == (mode == ItemTooltipMode.TECHNICAL),
                    "Core payment preview appeared outside the technical view");
            lines.clear();
            WandItem.appendDetails(wand, lines, mode);
            helper.assertTrue(hasKey(lines, "gui.forbidden_hex.wand.staff.use") == (mode == ItemTooltipMode.USAGE),
                    "Wand handling leaked into another view");
            helper.assertTrue(hasKey(lines, "gui.forbidden_hex.wand.staff.part_identity") == (mode == ItemTooltipMode.TECHNICAL),
                    "Wand component identities appeared outside the technical view");
            helper.assertTrue(hasKey(lines, "item.forbidden_hex.magic_core.plan.quote_only") == (mode == ItemTooltipMode.TECHNICAL),
                    "Wand quote disclaimer appeared outside the technical view");
        }
        helper.assertTrue(ItemStack.matches(core, originalCore) && ItemStack.matches(wand, originalWand),
                "Reading tooltip details changed item data");
        ItemStack damaged = core.copy();
        damaged.getOrCreateTag().getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("unknown_word"));
        damaged.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("unknown_ink"));
        List<Component> diagnostics = new ArrayList<>();
        MagicCoreItem.appendDetails(damaged, diagnostics, ItemTooltipMode.TECHNICAL);
        helper.assertTrue(hasKey(diagnostics, "item.forbidden_hex.magic_core.tip.slot")
                && diagnostics.stream().anyMatch(line -> line.getString().contains("unknown_word")),
                "Unknown slot identities disappeared from technical diagnostics");
        for (ItemTooltipMode mode : ItemTooltipMode.values()) {
            MagicCoreItem.appendDetails(MagicCoreItem.blank(MagicSource.LIFE), new ArrayList<>(), mode);
            WandItem.appendDetails(new ItemStack(AkaishiWandItems.wand.get()), new ArrayList<>(), mode);
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void legacyInkFallbackAndChannelUsage(GameTestHelper helper) {
        ItemStack legacy = MagicCoreItem.blank(MagicSource.LIFE);
        legacy.getOrCreateTag().putInt(MagicCoreItem.LEVEL_TAG, 2);
        legacy.getOrCreateTag().getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("tou"));
        legacy.getOrCreateTag().getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING).set(1, StringTag.valueOf("jian"));
        legacy.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(1, StringTag.valueOf("chi"));
        ItemStack original = legacy.copy();
        var actual = MagicCoreItem.summarize(legacy).result();
        var expected = SpellPlans.fromCore(legacy, "chi", 0f, 0);
        helper.assertTrue(actual != null && expected.ok() && actual.equals(expected.plan()),
                "Legacy quote does not match the casting compiler's main-ink fallback");
        helper.assertTrue(actual.cost().words().get(0).inkId().equals("chi"), "Missing slot ink was not inherited");
        List<Component> lines = new ArrayList<>();
        MagicCoreItem.appendDetails(legacy, lines, ItemTooltipMode.TECHNICAL);
        helper.assertTrue(hasKey(lines, "item.forbidden_hex.magic_core.plan.payment")
                && hasKey(lines, "item.forbidden_hex.magic_core.tip.inherit_ink")
                && !hasKey(lines, "item.forbidden_hex.magic_core.tip.unknown_ink"),
                "Legacy core lost its quote or mislabeled inherited ink");
        ItemStack wand = AkaishiWandItems.createWand(WandTipTable.all().keySet().iterator().next(),
                WandHandleTable.all().keySet().iterator().next(), WandCoreTable.all().keySet().iterator().next());
        WandItem.setSpell(wand, legacy);
        ItemStack originalWand = wand.copy();
        lines.clear();
        WandItem.appendDetails(wand, lines, ItemTooltipMode.TECHNICAL);
        helper.assertTrue(hasKey(lines, "item.forbidden_hex.magic_core.plan.payment")
                && hasKey(lines, "item.forbidden_hex.magic_core.tip.inherit_ink"), "Legacy wand quote disappeared");
        helper.assertTrue(ItemStack.matches(legacy, original) && ItemStack.matches(wand, originalWand),
                "Legacy preview mutated item data");
        ItemStack orphanInk = legacy.copy();
        orphanInk.getOrCreateTag().getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf(""));
        orphanInk.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("ju"));
        helper.assertTrue(MagicCoreItem.mainInkId(orphanInk).equals("chi") && WandItem.mainInkId(orphanInk).equals("chi"),
                "Ink in an empty word slot became the main ink");
        ItemStack missing = legacy.copy();
        missing.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(1, StringTag.valueOf(""));
        lines.clear();
        MagicCoreItem.appendDetails(missing, lines, ItemTooltipMode.TECHNICAL);
        helper.assertTrue(MagicCoreItem.summarize(missing).result() == null
                && hasKey(lines, "item.forbidden_hex.magic_core.tip.missing_ink"), "All-missing ink was accepted");
        ItemStack unknown = legacy.copy();
        unknown.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("unknown_ink"));
        lines.clear();
        MagicCoreItem.appendDetails(unknown, lines, ItemTooltipMode.TECHNICAL);
        helper.assertTrue(MagicCoreItem.summarize(unknown).result() == null
                && hasKey(lines, "item.forbidden_hex.magic_core.tip.unknown_ink")
                && SpellPlans.fromCore(unknown, "chi", 0f, 0).diagnostics().stream().anyMatch(d -> d.code().equals("unknown_ink")),
                "Unknown nonempty ink was silently inherited");
        unknown.getOrCreateTag().getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("unknown_word"));
        unknown.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf(""));
        helper.assertTrue(MagicCoreItem.summarize(unknown).result() == null
                && SpellPlans.fromCore(unknown, "chi", 0f, 0).diagnostics().stream().anyMatch(d -> d.code().equals("unknown_word")),
                "Unknown word disappeared during main-ink selection");
        for (String word : List.of("tou", "beng", "liu")) {
            ItemStack core = MagicCoreItem.blank(MagicSource.LIFE);
            core.getOrCreateTag().getList(MagicCoreItem.WORDS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf(word));
            core.getOrCreateTag().getList(MagicCoreItem.WORD_INKS_TAG, Tag.TAG_STRING).set(0, StringTag.valueOf("chi"));
            lines.clear();
            MagicCoreItem.appendDetails(core, lines, ItemTooltipMode.USAGE);
            helper.assertTrue(MagicCoreItem.summarize(core).result() != null
                    && hasKey(lines, "item.forbidden_hex.magic_core.plan.sustain") == word.equals("liu"),
                    "Release-to-stop hint did not match the actual channel delivery");
        }
        helper.succeed();
    }

    private static boolean hasKey(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> containsKey(line, key));
    }

    private static boolean containsKey(Component line, String key) {
        if (line.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals(key)) return true;
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component component && containsKey(component, key)) return true;
            }
        }
        return line.getSiblings().stream().anyMatch(sibling -> containsKey(sibling, key));
    }

    @GameTest(template = "brewing_empty")
    public static void intensityReadsAreStable(GameTestHelper helper) {
        ClientDeepSeaLock.reset();
        ClientDeepSeaLock.setTarget(1f);
        ClientDeepSeaLock.update();
        float first = ClientDeepSeaLock.current();
        helper.assertTrue(first > 0f && first < 1f, "Intensity did not approach its target");
        helper.assertTrue(first == ClientDeepSeaLock.current(), "Reading intensity advanced its transition");
        ClientDeepSeaLock.setTarget(Float.NaN);
        ClientDeepSeaLock.update();
        helper.assertTrue(Float.isFinite(ClientDeepSeaLock.current()), "Non-finite target poisoned intensity state");
        ClientDeepSeaLock.reset();
        helper.assertTrue(ClientDeepSeaLock.current() == 0f, "Session reset retained old intensity");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void shaderResourceContracts(GameTestHelper helper) throws IOException {
        JsonObject shader = JsonParser.parseString(resource("shaders/core/energy.json")).getAsJsonObject();
        Set<String> names = new HashSet<>();
        shader.getAsJsonArray("uniforms").forEach(element -> names.add(element.getAsJsonObject().get("name").getAsString()));
        helper.assertTrue(names.contains("VisualTime"), "Core shader has no shared animation clock");
        String source = resource("shaders/core/energy.fsh");
        for (String file : List.of("glow", "flow")) {
            VisualMaterial material = VisualMaterial.parse(JsonParser.parseString(
                    resource("visual/materials/" + file + ".json")).getAsJsonObject());
            helper.assertTrue(material.shader().equals(ENERGY), "Built-in material targets a missing shader");
            for (String parameter : material.uniforms().keySet()) {
                helper.assertTrue(names.contains(parameter) && source.contains("uniform float " + parameter + ";"),
                        "Material scalar is not declared by JSON and GLSL: " + parameter);
            }
        }
        String post = resource("shaders/program/unnameable.json");
        helper.assertTrue(post.contains("AkaishiIntensity") && resource("shaders/program/unnameable.fsh").contains("uniform float AkaishiIntensity;"),
                "Post-effect intensity declaration is incomplete");
        helper.succeed();
    }

    private static VisualMaterial material(Map<String, Float> uniforms) {
        return new VisualMaterial(VisualMaterial.Pipeline.ENERGY_ADDITIVE, ENERGY, uniforms);
    }

    private static void reject(GameTestHelper helper, Runnable operation, String failure) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, failure);
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = VisualSystemGameTests.class.getClassLoader().getResourceAsStream("assets/forbidden_hex/" + path)) {
            if (stream == null) {
                throw new IOException("Missing visual resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.example.akaishi.forbidden.forge.magic.visual.SpellImpactVfx;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

/** 仅显式隔离场景运行的四源×十一墨对照，不能作为普通客户端入口。 */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSpellVfxGallery {
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(32768));
    private static Path output;
    private static int sample;
    private static int frames;
    private static int pending;
    private static boolean drawn;
    private static Vec3 eye;
    private static Vec3 forward;
    private static Vec3 side;

    private ClientSpellVfxGallery() { }

    public static void start(Path directory) throws java.io.IOException {
        var player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("Gallery requires the isolated world player");
        java.nio.file.Files.createDirectories(directory.resolve("screenshots"));
        sample = 0;
        frames = 0;
        pending = 0;
        drawn = false;
        output = directory;
        eye = player.getEyePosition();
        forward = player.getLookAngle().normalize();
        side = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.5d) side = new Vec3(1, 0, 0);
    }

    public static boolean active() {
        return output != null && (sample < MagicSource.values().length * 6 || pending > 0);
    }

    public static void verifyOutput() throws java.io.IOException {
        if (sample != MagicSource.values().length * 6 || pending != 0) {
            throw new java.io.IOException("Spell gallery did not complete all sources, forms and detail modes");
        }
        for (MagicSource source : MagicSource.values()) {
            for (String detail : new String[]{"-all", "-minimal"}) {
                for (String form : new String[]{"-tou", "-wo", "-hu"}) {
                    if (!java.nio.file.Files.isRegularFile(output.resolve("screenshots")
                            .resolve("source-" + source.id() + detail + form + ".png"))) {
                        throw new java.io.IOException("Missing spell gallery image: " + source.id() + detail + form);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!ClientVisualSceneTest.enabled() || !active() || sample >= MagicSource.values().length * 6
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.screen != null || minecraft.getOverlay() != null) return;
        MagicSource source = MagicSource.values()[sample % MagicSource.values().length];
        float detail = (sample / MagicSource.values().length) % 2 == 0 ? 1f : 0.5f;
        minecraft.options.particles().set(detail == 1f ? ParticleStatus.ALL : ParticleStatus.MINIMAL);
        float time = (minecraft.level.getGameTime() % 1200L + event.getPartialTick()) / 20f;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        for (int i = 0; i < MagicInkTable.order().size(); i++) {
            String ink = MagicInkTable.order().get(i);
            String word = switch (sample / (MagicSource.values().length * 2)) {
                case 1 -> "wo";
                case 2 -> "hu";
                default -> "tou";
            };
            SpellVisual visual = SpellVisual.assemble(word, MagicIds.Delivery.PROJECTILE,
                    ink, source.id(), 1f, 0f, 1f, java.util.List.of(word, "si", "ji_base"));
            Vec3 center = eye.add(forward.scale(10d)).add(side.scale((i % 6 - 2.5d) * 1.6d))
                    .add(0, i < 6 ? 1.0d : -1.1d, 0);
            pose.pushPose();
            pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            pose.mulPose(event.getCamera().rotation());
            SpellVisualPainter.paint(pose, BUFFERS, visual, time, 0.88f, detail);
            if (detail > 0.72f) {
                SpellVisualPainter.paintWordRing(pose, BUFFERS, visual.slotWords(), visual.slotInks(),
                        visual.inkRgb(), 0.34f, 0.07f, time * 9f, 0.65f, 1f);
            }
            pose.pushPose();
            pose.translate(-0.27f, -0.72f, 0f);
            pose.scale(0.46f, 0.46f, 0.46f);
            SpellVisual beam = SpellVisual.assemble("si", MagicIds.Delivery.CHANNEL,
                    ink, source.id(), 1f, 0f, 1f, java.util.List.of("si", "liu"));
            SpellVisualPainter.paintCarrier(pose, BUFFERS, beam, new Vec3(-0.8d, 1.1d, 0d), time,
                    0.85f, new org.joml.Quaternionf(), detail);
            pose.popPose();
            pose.translate(0.28f, -0.72f, 0f);
            pose.scale(0.48f, 0.48f, 0.48f);
            SpellImpactVfx.paint(pose, BUFFERS, visual, 0.18f, new org.joml.Quaternionf(), detail);
            pose.popPose();
        }
        BUFFERS.endBatch();
        drawn = true;
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!ClientVisualSceneTest.enabled() || !active() || !drawn || pending > 0
                || event.phase != TickEvent.Phase.END || ++frames < 60) return;
        frames = 0;
        drawn = false;
        MagicSource source = MagicSource.values()[sample % MagicSource.values().length];
        int form = sample / (MagicSource.values().length * 2);
        boolean minimal = (sample / MagicSource.values().length) % 2 == 1;
        String name = "source-" + source.id() + (minimal ? "-minimal" : "-all")
                + (form == 0 ? "-tou" : form == 1 ? "-wo" : "-hu") + ".png";
        pending++;
        Minecraft minecraft = Minecraft.getInstance();
        Screenshot.grab(output.toFile(), name, minecraft.getMainRenderTarget(), message -> minecraft.execute(() -> {
            if (!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents)
                    || !"screenshot.success".equals(contents.getKey()))
                throw new IllegalStateException("Source gallery screenshot failed: " + message.getString());
            LogUtils.getLogger().info("SPELL_SOURCE_GALLERY: {} inks={} scope=live_painter,word_glyphs,link_beam,impact; no_gameplay_or_charge_verdict", name, MagicInkTable.order());
            sample++;
            pending--;
        }));
    }
}

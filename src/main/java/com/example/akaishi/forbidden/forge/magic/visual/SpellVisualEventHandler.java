package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.forge.magic.particle.SpellParticleBursts;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.example.akaishi.forbidden.magic.visual.SpellVisualEvent;
import com.example.akaishi.forbidden.magic.visual.SpellVisualEvents;
import com.example.akaishi.forbidden.magic.visual.LinkVisualEvent;
import com.example.akaishi.forbidden.magic.visual.LinkVisualEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 法术表现的三条主线程有界队列（仅客户端）：
 * <ol>
 *   <li>{@link SpellChargeVfx} —— <b>起手</b>：施法期间在瞄准点的蓄力表现（由状态机状态驱动，不占队列）；</li>
 *   <li>{@code active} —— <b>载体主体</b>：一条束 / 一枚核心 / 一个印记，存活 = 事件的 {@code lifeTicks}；</li>
 *   <li>{@code impacts} —— <b>命中冲击 + 余韵</b>：<b>比事件活得久</b>（1.2s vs 0.4s），
 *       这样才有"余韵"；若与事件同寿，一击打完世界立刻恢复原状，玩家会觉得没打着。</li>
 * </ol>
 * 三条队列都在"换世界 / 退出"时清空，各自有上限，避免刷屏与泄漏。
 */
public final class SpellVisualEventHandler {
    public static final SpellVisualEventHandler INSTANCE = new SpellVisualEventHandler();

    /** 单条队列上限（防刷屏 / 防泄漏） */
    private static final int MAX_ENTRIES = 128;
    /** 命中冲击（含余韵）存活刻数：与 {@code SpellImpactVfx.LINGER_SECONDS} 对齐 */
    private static final int IMPACT_LIFE_TICKS = Math.round(SpellImpactVfx.LINGER_SECONDS * 20f);

    private record Active(SpellVisualEvent event, long started) { }
    private record Impact(SpellVisualEvent event, long started) { }
    private record LinkPulse(LinkVisualEvent event, long started) { }

    private final List<Active> active = new ArrayList<>();
    private final List<Impact> impacts = new ArrayList<>();
    private final List<LinkPulse> links = new ArrayList<>();
    private final SpellChargeVfx charge = new SpellChargeVfx();
    private ClientLevel world;
    private final MultiBufferSource.BufferSource visualBuffers = MultiBufferSource.immediate(new BufferBuilder(8192));

    private SpellVisualEventHandler() { }

    public void register() {
        LinkVisualEvents.registerClient(this::acceptLink);
        SpellVisualEvents.registerClient(event -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            if (world != level) { active.clear(); impacts.clear(); links.clear(); world = level; }
            long now = level.getGameTime();
            addBounded(active, new Active(event, now));
            addBounded(impacts, new Impact(event, now));
            // 命中即刻的粒子爆发（形取墨 = "是什么魔法打上来的"）
            SpellParticleBursts.onEvent(level, event);
        });
    }

    private void acceptLink(LinkVisualEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (world != level) { active.clear(); impacts.clear(); links.clear(); world = level; }
        addBounded(links, new LinkPulse(event, level.getGameTime()));
    }

    private static <T> void addBounded(List<T> list, T value) {
        if (list.size() >= MAX_ENTRIES) {
            list.remove(0);
        }
        list.add(value);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { active.clear(); impacts.clear(); links.clear(); world = level; }
        if (level == null || Minecraft.getInstance().isPaused()) return;
        charge.tick(level);
        long now = level.getGameTime();
        active.removeIf(value -> now - value.started() >= value.event().lifeTicks());
        // 持续型载体（束 / 场 / 印记）在寿命内稀疏续发（形取载体 = "怎么在维持"）
        for (Active value : active) {
            SpellParticleBursts.onTick(level, value.event(), (int) (now - value.started()));
        }
        impacts.removeIf(value -> now - value.started() >= IMPACT_LIFE_TICKS);
        links.removeIf(link -> now - link.started() >= LinkVisualEvent.LIFE_TICKS);
    }

    @SubscribeEvent
    public void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || world == null) return;
        var pose = event.getPoseStack();
        var buffers = visualBuffers;
        Vec3 camera = event.getCamera().getPosition();
        var rotation = event.getCamera().rotation();

        // ① 起手：世界里总得发生点什么 —— 否则"按了右键却毫无动静"会被当成没按上
        charge.paint(pose, buffers, world, camera, event.getPartialTick());

        // ② 载体主体
        for (Active value : active) {
            SpellVisualEvent spell = value.event();
            Vec3 to = anchor(world, spell);
            if (camera.distanceToSqr(to) > 128d * 128d) continue;
            float age = world.getGameTime() - value.started() + event.getPartialTick();
            float alpha = Math.max(0, 1 - age / spell.lifeTicks());
            pose.pushPose();
            pose.translate(to.x - camera.x, to.y - camera.y, to.z - camera.z);
            SpellVisualPainter.paintCarrier(pose, buffers, spell.visual(), spell.from().subtract(to),
                    age / 20f, alpha, rotation);
            pose.popPose();
        }

        for (LinkPulse link : links) {
            float age = world.getGameTime() - link.started() + event.getPartialTick();
            if (age >= LinkVisualEvent.LIFE_TICKS) continue;
            paintLink(pose, buffers, world, camera, link.event(), event.getPartialTick(), age);
        }

        // ③ 命中冲击 + 余韵（自有寿命，比事件活得久）
        for (Impact value : impacts) {
            SpellVisualEvent spell = value.event();
            Vec3 to = anchor(world, spell);
            if (camera.distanceToSqr(to) > 128d * 128d) continue;
            float age = world.getGameTime() - value.started() + event.getPartialTick();
            pose.pushPose();
            pose.translate(to.x - camera.x, to.y - camera.y, to.z - camera.z);
            SpellImpactVfx.paint(pose, buffers, spell.visual(), age / 20f, rotation);
            pose.popPose();
        }

        // 专用 buffer 只含本阶段法术绘制，提交几何和最后一批纹理，不触碰原版队列。
        buffers.endBatch();
    }

    private static void paintLink(com.mojang.blaze3d.vertex.PoseStack pose, MultiBufferSource buffers,
            ClientLevel level, Vec3 camera, LinkVisualEvent link, float partialTick, float age) {
        Vec3 b = linkAnchor(level, link.toId(), link.to(), partialTick);
        Vec3 a = link.fromId() < 0 ? b : linkAnchor(level, link.fromId(), link.from(), partialTick);
        Vec3 d = b.subtract(a);
        Vec3 closest = d.lengthSqr() < 1.0e-6d ? b
                : a.add(d.scale(Math.max(0d, Math.min(1d, camera.subtract(a).dot(d) / d.lengthSqr()))));
        if (camera.distanceToSqr(closest) > 128d * 128d) return;
        float alpha = Math.max(0f, 1f - age / LinkVisualEvent.LIFE_TICKS);
        if (link.fromId() < 0 || d.lengthSqr() < 1.0e-6d) {
            pose.pushPose();
            pose.translate(b.x - camera.x, b.y - camera.y, b.z - camera.z);
            var rotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.SPARK, rotation,
                    0.24f, alpha * 0.22f, link.visual().sourceRgb());
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.SPARK, rotation,
                    0.12f, alpha, AkaishiMagicParticles.photonRgb(link.visual().inkRgb()));
            pose.popPose();
            return;
        }
        Vec3 axis = d.normalize();
        Vec3 side = axis.cross(Math.abs(axis.y) > 0.9d ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = axis.cross(side).normalize();
        int steps = Math.max(4, Math.min(16, (int) Math.ceil(d.length() * 2d)));
        double spread = Math.min(0.45d, d.length() * 0.09d);
        var random = new java.util.Random(31L * link.fromId() + link.toId() * 17L + (int) age);
        Vec3 last = a;
        for (int i = 1; i <= steps; i++) {
            Vec3 next = i == steps ? b : a.add(d.scale((double) i / steps))
                    .add(side.scale((random.nextDouble() - 0.5d) * spread))
                    .add(up.scale((random.nextDouble() - 0.5d) * spread));
            paintBoltSegment(pose, buffers, camera, last, next, link, alpha);
            last = next;
        }
    }

    private static Vec3 linkAnchor(ClientLevel level, int id, Vec3 fallback, float partialTick) {
        var entity = level.getEntity(id);
        // 致死命中仍要闪；实体已移除时保留服务端发送的真实位置。
        return entity == null ? fallback : entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.5d, 0);
    }

    private static void paintBoltSegment(com.mojang.blaze3d.vertex.PoseStack pose, MultiBufferSource buffers,
            Vec3 camera, Vec3 from, Vec3 to, LinkVisualEvent link, float alpha) {
        pose.pushPose();
        pose.translate(from.x - camera.x, from.y - camera.y, from.z - camera.z);
        Vec3 line = to.subtract(from);
        SpellVisualPainter.ribbon(buffers, pose, line, 0.055f, alpha * 0.18f,
                link.visual().sourceRgb(), SpellGeometry.WHITE, 0f);
        SpellVisualPainter.ribbon(buffers, pose, line, 0.028f, alpha * 0.30f,
                link.visual().inkRgb(), SpellGeometry.WHITE, 0f);
        SpellVisualPainter.ribbon(buffers, pose, line, 0.009f, alpha,
                AkaishiMagicParticles.photonRgb(link.visual().inkRgb()), SpellGeometry.WHITE, 0f);
        pose.popPose();
    }

    /**
     * 表现锚点：附着类（X4）挂在目标身上，其余落在真实命中点 ——
     * 与粒子层的锚点保持<b>同一口径</b>，避免"图形贴在目标身上、粒子却掉在地上"。
     */
    private static Vec3 anchor(ClientLevel level, SpellVisualEvent event) {
        if (MagicIds.Delivery.ATTACH.equals(event.visual().deliveryId()) && event.targetId() > 0) {
            var target = level.getEntity(event.targetId());
            if (target != null) {
                return target.position().add(0, target.getBbHeight() * 0.5, 0);
            }
        }
        return event.to();
    }
}

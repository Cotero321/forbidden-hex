package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.ClientCastState;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellCarrierForm;
import com.example.akaishi.forbidden.wand.WandItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * <b>起手蓄力</b>（仅客户端）：施法期间的"瞄准点"表现。
 *
 * <p><b>为什么必须有它</b>：按右键到法术生效之间隔着前摇（<b>0.5~1.5 秒</b>）。
 * 这段时间原本世界里<b>什么都没发生</b> —— 玩家会以为没按上、然后连点。
 * HUD 的准星光环只说明"我在施法"，说不出<b>打到哪儿</b>；本类补的就是这句话。
 *
 * <h3>两种节拍，一眼分得清"在蓄"还是"在放"</h3>
 * <ul>
 *   <li><b>前摇 CHARGING</b>：环<b>由大收紧</b>地脉冲一次（0.55s 一轮）——
 *       读作"正在把力量压进这一点"；</li>
 *   <li><b>持续段 CHANNELING / VOLLEYING</b>：换成<b>稳定呼吸</b>的断环，不再收紧 ——
 *       读作"已经成形，正在维持"。</li>
 * </ul>
 *
 * <p><b>为什么会汇聚微光</b>：粒子从玩家四周收向瞄准点，方向性本身就是"在吸取"的暗示；
 * 颜色取自<b>杖内核的源与主墨</b>（表现层手上只有物品栈，读法与施法链同口径）。
 *
 * <p><b>为什么不用新网络包</b>：是否施法已由 {@code forbidden_hex:cast_state} 即时包同步
 * （见 {@link ClientCastState}），颜色/源直接读手里的法杖 —— 零额外流量。
 */
public final class SpellChargeVfx {

    /** 起手表现的位置：视线前方多少格（够近才有"在手里成型"的感觉，又不能糊在脸上） */
    private static final float AIM_DISTANCE = 1.25f;
    /** 前摇脉冲周期（秒）：环"由大收紧一次"的节拍 */
    private static final float PULSE_SECONDS = 0.55f;
    /** 汇聚微光的生成间隔（刻） */
    private static final int MOTE_INTERVAL_TICKS = 2;

    private int moteAge;

    /**
     * 画一次起手表现（非施法状态直接返回并复位节拍）。
     *
     * @param camera 相机世界坐标（本类在"相机已平移"的坐标系里作画）
     */
    public void paint(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level,
            Vec3 camera, float partialTick) {
        MagicCastState state = ClientCastState.state();
        if (!state.isActive()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack wand = WandItem.heldWand(player);
        if (wand.isEmpty()) {
            return;
        }
        MagicSource source = WandItem.socketedSource(wand);
        if (source == null) {
            return;
        }
        int ink = WandItem.mainInkRgb(WandItem.spell(wand));
        int aura = source.rgb();

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getLookAngle();
        double aimX = eye.x + look.x * AIM_DISTANCE;
        double aimY = eye.y + look.y * AIM_DISTANCE;
        double aimZ = eye.z + look.z * AIM_DISTANCE;
        float time = level.getGameTime() + partialTick;
        Quaternionf billboard = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();

        poseStack.pushPose();
        poseStack.translate(aimX - camera.x, aimY - camera.y, aimZ - camera.z);
        if (state == MagicCastState.CHARGING) {
            // 前摇：环由大收紧 + 亮度递增 —— "正在把力量压进这一点"
            float phase = (time % PULSE_SECONDS) / PULSE_SECONDS;
            float tighten = 1f - phase;
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING, billboard,
                    0.14f + 0.30f * tighten, 0.20f + 0.55f * phase, ink);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.HALO, billboard,
                    0.10f + 0.12f * phase, 0.20f + 0.30f * phase, aura);
        } else {
            // 持续段：稳定呼吸的断环 —— 与前摇在观感上分开（"已经成形，正在维持"）
            float breath = 0.5f + 0.5f * (float) Math.sin(time * 4.0d);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING_PULSE, billboard,
                    0.26f + 0.05f * breath, 0.45f + 0.25f * breath, ink);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.HALO, billboard,
                    0.16f + 0.06f * breath, 0.35f + 0.20f * breath, aura);
        }
        poseStack.popPose();

    }

    public void tick(ClientLevel level) {
        if (!ClientCastState.state().isActive()) { moteAge = 0; return; }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack wand = WandItem.heldWand(player);
        if (wand.isEmpty()) return;
        MagicSource source = WandItem.socketedSource(wand);
        if (source == null || moteAge++ % MOTE_INTERVAL_TICKS != 0) return;
        ItemStack spell = WandItem.spell(wand);
        Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(AIM_DISTANCE));
        spawnMotes(level, player, source, WandItem.mainInkId(spell), WandItem.mainInkRgb(spell),
                aim.x, aim.y, aim.z);
    }

    /** 从玩家眼睛周围一圈生成一颗微光，初速指向瞄准点（"在吸取"的方向性） */
    private static void spawnMotes(ClientLevel level, LocalPlayer player, MagicSource source, String inkId,
            int ink, double aimX, double aimY, double aimZ) {
        double angle = level.random.nextDouble() * Math.PI * 2d;
        double radius = 0.70d + level.random.nextDouble() * 0.40d;
        Vec3 eye = player.getEyePosition();
        double x = eye.x + Math.cos(angle) * radius;
        double y = eye.y + (level.random.nextDouble() - 0.35d) * 0.80d;
        double z = eye.z + Math.sin(angle) * radius;
        // 起手尚未定载体，取最中性的「核心」形 —— 微光仍带着该墨的形状与个性
        AkaishiMagicParticles.converge(level, source, inkId, SpellCarrierForm.ORB, ink,
                x, y, z, aimX, aimY, aimZ, 0.085d);
    }
}

package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * 阿盖托洛丝的<b>自定义铭牌式血条</b>（仅客户端 HUD），替换原版 {@code ServerBossEvent} 血条。
 * <p>
 * <b>为什么整套搬到客户端</b>：原版血条是「服务端下发事件 → 客户端用固定 182×5 的 bars.png 画」，
 * 形状、配色、阶段表现都改不了。这里改成纯客户端 overlay：<b>不占一条服务端 BossEvent，
 * 但按需求的铭牌贴图自绘</b>，并且按阶段换图集 —— 原版事件做不到「同一条血条换皮」。
 * <p>
 * <b>为什么不需要服务端参与</b>：客户端世界里的实体是服务端按 {@code clientTrackingRange}
 * 同步过来的，玩家身边没有该实体时，客户端根本没有这个对象 —— 于是「扫到了实体」与
 * 「玩家看得见它」天然等价，不需要再写一套参与者对账（早期服务端 BossEvent 那一套已整体移除）。
 * <p>
 * <b>数据来源</b>：阶段（{@code DATA_PHASE}）与复活（{@code DATA_RESPAWNING}）是同步数据，
 * 客户端可直接读；血量/最大血量走原版同步，也直接读。本类<b>只读不写</b>，不产生任何包。
 * <p>
 * <b>叠放契约（三张图集几何同源，故只有这一套常量）</b>：坐标基准 {@code (px, py)} = 铭牌左上角。
 * <pre>
 *   顺序 区域        源矩形(图集内)                绘制到(相对 px,py)
 *   1    plate      (4,0,200,40)                  (px,        py)
 *   2    fill       (0,64,W,16) W=⌊192×血量比⌋     (px+4,      py+24)
 *   3    rim 主体    (0,80,189,16)                 (px+4,      py+24)   ← 最上层压边
 *   4    rim 右端帽  (237,80,3,16)                 (px+193,    py+24)
 * </pre>
 * 内嵌轨道可用区在 plate 内为 x 8..247（宽 240）、y 24..39（高 16）；plate 内 y 5..21 是留给
 * 代码写名字的净区，贴图在那里没有元素，故文字可以直接叠在 plate 上。
 * <p>
 * <b>只画这么少的依据（本轮「降占屏比」的裁剪取证）</b>：图集 256×256 的 plate 区里，真正构成
 * 「面板」的只有 (0,0)-(255,63) 这一块；冠饰(v96,u64..127)、坠饰(v96,u128..191)、左右端件
 * (v96,u0..63)、四角件(v128) 全是<b>额外装饰</b>，画出来只贡献占屏比，故本轮不再绘制
 * （原来 8 次 blit → 现在 4 次，其中 3 次是底板/填充/压边，1 次是压边的右端帽，见下）。
 * <p>
 * <b>底板子区 (4,0,200,40) 的实机读像素取证</b>（phase1/2/3 布局一致，逐像素 alpha 探针）：
 * <ul>
 *   <li><b>左边界为什么取 4 而不是 0</b>：源 x0..3 在 y29..34 是<b>透明缺口</b>（原本给左端件留的插槽），
 *       裁进来会在面板左缘留一个 4×6 的洞 → 破图；x=4 起所有 y4..39 全不透明，正好避开。</li>
 *   <li><b>右边界为什么取 203 而不是 251</b>：源 x252..255 在 y29..34 同样是透明缺口（右端件插槽），
 *       x=203 落在 y0..39 内 alpha 全 255 的纯实心列上。</li>
 *   <li><b>逐行实心校验</b>：子区内 y4..39 每一行 x4..203 都是 200/200 不透明；仅 y0..1 有 40px 透明，
 *       那是底板自带的<b>冠齿装饰</b>（间隔齿状，属贴图原设计），不是裁剪破洞。</li>
 *   <li><b>底边为什么取 y=39</b>：y39 是全宽纯色 (56,56,60)，天然收边；再往下 y40..58 是底板下半段
 *       装饰带、y57 才是底板自身的金色收边线 —— 裁掉下段可省 24px 高，整条从 120 高降到 40 高。</li>
 *   <li><b>轨道宽度为什么取 192</b>：rim 的竖直分段刻度固定每 24px 一枚（源 x 24/48/…/216），
 *       192 = 8×24 正好整 8 段，刻度落点整齐；剩余横宽让底板左右各留 4px 内边距（4+192+4=200）。</li>
 * </ul>
 * <p>
 * <b>占屏比</b>：底板 200×40，与原版 Boss 血条（宽 182、y=12 起）同量级。
 * 相比上一版 320×120（含冠饰/坠饰/端件/四角件）：宽 62.5%、高 33.3%、面积 20.8%。
 */
public final class AgaitolosBossBarOverlay implements IGuiOverlay {

    /** 图集尺寸（三张都是 256×256），作为 blit 的 textureWidth/textureHeight */
    private static final int TEX_SIZE = 256;

    /**
     * 阶段 → 图集。下标 = {@code combatOrdinal - 1}（1/2/3 分别对应一/二/三阶段）。
     * 阶段是同步数据，客户端直接读，不需要任何额外包。
     */
    private static final ResourceLocation[] PHASE_TEXTURES = {
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/gui/agaitolos_bar_phase1.png"),
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/gui/agaitolos_bar_phase2.png"),
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/gui/agaitolos_bar_phase3.png"),
    };

    // ---------------------------------------------------------------- 图集子区（源）
    /** 底板子区左上角：源 x=4 起（避开 x0..3 的端件插槽缺口），y=0 起（保留底板自带的冠齿顶饰） */
    private static final int PLATE_U = 4;
    private static final int PLATE_V = 0;
    /** 底板子区尺寸：宽 200（=轨道 192 + 左右各 4 内边距），高 40（顶饰 4 + 名称净区 + 轨道 16） */
    private static final int PLATE_W = 200;
    private static final int PLATE_H = 40;

    /** 内嵌轨道在 plate 内的源起点（也是 fill/rim 的源基准） */
    private static final int TRACK_SRC_U = 8;
    private static final int TRACK_SRC_V = 24;
    /** 轨道宽 = 8×24 段；高 16（图集里 fill/rim 都是 16 高） */
    private static final int TRACK_W = 192;
    private static final int TRACK_H = 16;

    /** 轨道左上角相对底板左上角的偏移 = 源偏移 - 底板源偏移（底板从 x=4 起，故横向只剩 +4） */
    private static final int TRACK_X = TRACK_SRC_U - PLATE_U;
    private static final int TRACK_Y = TRACK_SRC_V - PLATE_V;

    private static final int FILL_V = 64;
    private static final int RIM_V = 80;
    /**
     * rim 右端帽（源 x237..239，3px 宽）：rim 的左右端帽在源里相距 240px，任何 &lt;240 的子区都
     * 不可能同时含两端 —— 所以右端帽单独再 blit 一次，把轨道右端"封口"。
     * 不封口的话，满血时填充会以一条没有描边的硬边结束在底板暗部，看着像画断了。
     */
    private static final int RIM_CAP_U = 237;
    private static final int RIM_CAP_W = 3;
    /** rim 主体宽度：轨道减去右端帽的宽度（左端帽 + 上下面框 + 每 24px 的竖直刻度都在这一段里） */
    private static final int RIM_W = TRACK_W - RIM_CAP_W;
    /** rim 右端帽相对 px 的目标 x */
    private static final int RIM_CAP_X = TRACK_X + TRACK_W - RIM_CAP_W;

    // ---------------------------------------------------------------- 布局（相对 px, py）
    /** 铭牌整体占位 = 底板子区本身（不再有跳出底板的装饰件） */
    private static final int BAR_WIDTH = PLATE_W;
    private static final int BAR_HEIGHT = PLATE_H;
    /** 把铭牌水平居中于屏幕需要减掉的偏移（底板自身即占位，故就是半宽） */
    private static final int BAR_CENTER = PLATE_W / 2;

    /**
     * 铭牌左上角的屏幕 y（逻辑值，未缩放）。
     * <p>取值依据：与原版 Boss 血条同区（原版 {@code BOSS_EVENT_PROGRESS} 是 y=12 起），
     * 既不贴屏幕顶边挡 F3 首行，也不会和顶部其它 HUD 打架；铭牌本身高 40，占 y 12..51。
     */
    private static final int PLATE_Y = 12;

    /** 名称净区（plate 内 y 5..21）在屏幕上的文字基线 y（相对 py）：(17 - 9) / 2 = 4 ⇒ py+5+4 */
    private static final int NAME_OFFSET_Y = 9;

    /**
     * 名称净区可用宽度（逻辑像素），超过就退回只画名称。
     * <p>推算依据：名称居中于底板中线（px+100），百分比右对齐到轨道右端内侧（px+195），
     * 百分比最宽（{@code "100%"}）时左缘落在 px+173，故名称右半可用 173-100-3 = 70、两侧共 140，
     * 再留 4px 余量取 136。宁可少画一段副标题，也不让两段文字互相压字。
     */
    private static final int NAME_ZONE_WIDTH = 136;

    /** 名称与副标题的分隔符 */
    private static final String NAME_SEPARATOR = " · ";

    // ---------------------------------------------------------------- 兜底缩放
    /** 兜底缩放的左右/上下留白（逻辑像素）：缩到刚好不贴边 */
    private static final int EDGE_PADDING = 6;
    /** 最大占屏比上限（待调手感值）：常态不让铭牌超过 GUI 宽度的 40% */
    private static final float MAX_SCREEN_WIDTH_FRACTION = 0.40F;
    /** 最大占屏比上限（待调手感值）：常态不让铭牌超过 GUI 高度的 12% */
    private static final float MAX_SCREEN_HEIGHT_FRACTION = 0.12F;

    // ---------------------------------------------------------------- 目标选取
    /**
     * 客户端扫描半径（格）。取 96 = 6 区块，<b>依据</b>：
     * <ul>
     *   <li>本实体 {@code clientTrackingRange(12)} = 12 区块 = 192 格，即客户端最多能在 192 格内看到它；
     *       扫 96 格只会"漏掉 96~192 格之间那只"——那个距离上 BOSS 已是一个被雾遮住的小点，
     *       给它挂一条屏幕血条反而是误导（也与原版血条"隔半张地图还挂着一条"的观感一致地收敛）。</li>
     *   <li>反过来，任何被扫到的实体一定已在客户端世界里 = 已被服务端追踪，不会出现
     *       "玩家看不见 BOSS 却还挂着血条"。</li>
     *   <li>性能：96 格 = 12×12 个区块段，比按 192 扫少 4 倍的遍历量。逐帧一次，无缓存。</li>
     * </ul>
     */
    private static final double SCAN_RADIUS = 96.0D;

    // ---------------------------------------------------------------- 复活脉动（待调手感值）
    /** 复活/无敌期间的基础不透明度 */
    private static final float RESPAWN_ALPHA = 0.45F;
    /** 复活/无敌期间的正弦脉动幅度（±） */
    private static final float RESPAWN_PULSE_AMPLITUDE = 0.15F;
    /** 复活/无敌期间一个完整脉动周期的毫秒数 */
    private static final float RESPAWN_PULSE_PERIOD_MS = 1200.0F;

    /** 名称与百分比字色（仅 RGB，浅色、暗底可读；alpha 由复活脉动在绘制时按位或入高 8 位） */
    private static final int NAME_RGB = 0x00FFFFFF;
    private static final int PERCENT_RGB = 0x00E6F7FF;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // F1 隐藏 HUD 时一并隐藏（与原版 BOSS_EVENT_PROGRESS overlay 的 hideGui 门控保持一致）
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        AgaitolosEntity boss = findNearest(player);
        // 血量归零 = 正在播死亡演出：此刻不该再挂一条血条（否则"血条满格却打不动"的误导，与旧实现同理）
        if (boss == null || boss.getHealth() <= 0.0F) {
            return;
        }

        // 阶段 → 图集。combatOrdinal 是 1/2/3，落 0（异常存档里的 RESPAWN）时钳到一阶段图，不越界不崩
        int textureIndex = Mth.clamp(boss.getPhase().combatOrdinal() - 1, 0, PHASE_TEXTURES.length - 1);
        ResourceLocation texture = PHASE_TEXTURES[textureIndex];

        float healthRatio = Mth.clamp(boss.getHealth() / Math.max(1.0F, boss.getMaxHealth()), 0.0F, 1.0F);
        // 比例 > 0 时至少 1px：否则剩最后几滴血（192 × 0.5% < 1）时轨道看起来像"已经空了"
        int fillWidth = healthRatio > 0.0F ? Math.max(1, (int) (TRACK_W * healthRatio)) : 0;

        // 逻辑坐标（未缩放）：把铭牌中心对齐屏幕中线
        int px = screenWidth / 2 - BAR_CENTER;
        int py = PLATE_Y;

        // 兜底缩放：既不超出屏幕（fit），也不超过最大占屏比（cap）。四者取最小，且上限 1.0。
        // 不把系数揉进坐标常量：坐标保持可读的逻辑值，缩放只影响矩阵，
        // 于是 fill 宽度、文字居中、端帽偏移全都仍是同一套换算，不会因缩放而错位。
        float fitWidth = (screenWidth - 2.0F * EDGE_PADDING) / (float) BAR_WIDTH;
        float fitHeight = (screenHeight - 2.0F * EDGE_PADDING) / (float) (PLATE_Y + BAR_HEIGHT);
        float capWidth = screenWidth * MAX_SCREEN_WIDTH_FRACTION / (float) BAR_WIDTH;
        float capHeight = screenHeight * MAX_SCREEN_HEIGHT_FRACTION / (float) BAR_HEIGHT;
        float scale = Math.min(1.0F, Math.min(Math.min(fitWidth, fitHeight), Math.min(capWidth, capHeight)));

        // 复活/无敌阶段：降低不透明度 + 正弦脉动，表达"打不动"
        float alpha = 1.0F;
        if (boss.isRespawning()) {
            float progress = (Util.getMillis() % (long) RESPAWN_PULSE_PERIOD_MS) / RESPAWN_PULSE_PERIOD_MS;
            float pulse = Mth.sin(progress * Mth.TWO_PI);
            alpha = Mth.clamp(RESPAWN_ALPHA + RESPAWN_PULSE_AMPLITUDE * pulse, 0.05F, 1.0F);
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (scale != 1.0F) {
            // 以「屏幕中竖线 + 屏幕顶边」为不动点缩放：水平仍居中，垂直只向下收，不会缩出屏幕
            pose.translate(screenWidth / 2.0F, 0.0F, 0.0F);
            pose.scale(scale, scale, 1.0F);
            pose.translate(-screenWidth / 2.0F, 0.0F, 0.0F);
        }

        // 贴图统一降透明度：GuiGraphics#setColor 走 RenderSystem.setShaderColor，
        // 而 GuiGraphics 的 blit 是"设 shader → BufferUploader.drawWithShader"立即绘制，
        // 因此这里设的 ColorModulator 对本段每一张图立即生效（不像文字那样缓入 bufferSource 延后 flush）。
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        drawBar(graphics, texture, px, py, fillWidth);
        // 立刻恢复全局 shader 颜色：文字是缓入 bufferSource、在 GUI 帧末才 flush 的，
        // 若把低透明度留到那时，会被叠加进文字的 ColorModulator（双重变暗）并污染后续 HUD。
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        drawText(minecraft.font, graphics, px, py, healthRatio, alpha);

        pose.popPose();
    }

    /** 单只最近目标：本帧找不到（或没有）就整条不画 */
    private static AgaitolosEntity findNearest(LocalPlayer player) {
        Level level = player.level();
        List<AgaitolosEntity> candidates = level.getEntitiesOfClass(AgaitolosEntity.class,
                player.getBoundingBox().inflate(SCAN_RADIUS));
        AgaitolosEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (AgaitolosEntity candidate : candidates) {
            double distanceSqr = candidate.distanceToSqr(player);
            if (distanceSqr < nearestSqr) {
                nearestSqr = distanceSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * 严格按契约表叠放：底板 → 填充 → rim 主体 → rim 右端帽，共 4 次 blit。
     * <p>rim 必须压在 fill 之上（fill 只是轨道内的进度色块，rim 是它的描边/压边）。
     * 端帽放最后，保证它压住填充的右端硬边。
     */
    private static void drawBar(GuiGraphics graphics, ResourceLocation texture, int px, int py, int fillWidth) {
        // 1) 底板（200×40 子区，1:1 不拉伸）
        graphics.blit(texture, px, py, (float) PLATE_U, (float) PLATE_V, PLATE_W, PLATE_H, TEX_SIZE, TEX_SIZE);
        // 2) 进度填充：源 u 从 0 起、宽 = fillWidth，绘制宽度同为 fillWidth（1:1 不拉伸）
        if (fillWidth > 0) {
            graphics.blit(texture, px + TRACK_X, py + TRACK_Y,
                    0.0F, (float) FILL_V, fillWidth, TRACK_H, TEX_SIZE, TEX_SIZE);
        }
        // 3) 压边主体（含左端帽、上下面框与每 24px 的竖直刻度），压在最上层
        graphics.blit(texture, px + TRACK_X, py + TRACK_Y,
                0.0F, (float) RIM_V, RIM_W, TRACK_H, TEX_SIZE, TEX_SIZE);
        // 4) 压边右端帽：把轨道右端封口（rim 源里两端帽相距 240px，只能另起一次 blit）
        graphics.blit(texture, px + RIM_CAP_X, py + TRACK_Y,
                (float) RIM_CAP_U, (float) RIM_V, RIM_CAP_W, TRACK_H, TEX_SIZE, TEX_SIZE);
    }

    /**
     * 名称与血量百分比（用原版字体，不依赖贴图）。
     * <p>透明度<b>写进颜色 int 的 alpha 通道</b>而不是靠 shader 颜色：文字是缓入 bufferSource
     * 的，flush 时机在 GUI 帧末，那时 shader 颜色已被恢复成 1,1,1,1；而文字渲染类型带
     * TRANSLUCENT_TRANSPARENCY，顶点色 alpha 一定生效，是可靠做法。
     */
    private static void drawText(Font font, GuiGraphics graphics,
                                 int px, int py, float healthRatio, float alpha) {
        int alphaBits = Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24;
        // 名称：居中于底板中线（px+100），落在净区 py+5..py+21 的中线上。
        // 副标题键 boss.forbidden_hex.agaitolos.subtitle 是既有双语键、此前全项目无人引用（孤儿键），
        // 这里用「名称 · 副标题」单行形式消费掉；超宽则退回只画名称（贴图净区不动）
        Component name = Component.translatable("entity.forbidden_hex.agaitolos");
        Component fullTitle = name.copy()
                .append(NAME_SEPARATOR)
                .append(Component.translatable("boss.forbidden_hex.agaitolos.subtitle"));
        Component title = font.width(fullTitle) <= NAME_ZONE_WIDTH ? fullTitle : name;
        graphics.drawCenteredString(font, title, px + BAR_CENTER, py + NAME_OFFSET_Y, alphaBits | NAME_RGB);
        // 百分比：右对齐到轨道右端内侧（px+4+192-1），用 ceil 保证"活着就不会显示 0%"
        String percent = Mth.ceil(healthRatio * 100.0F) + "%";
        int percentX = px + TRACK_X + TRACK_W - 1 - font.width(percent);
        graphics.drawString(font, percent, percentX, py + NAME_OFFSET_Y, alphaBits | PERCENT_RGB);
    }
}

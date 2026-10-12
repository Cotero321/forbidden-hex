package com.example.akaishi.forbidden.forge.magic.particle;

import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.particle.SpellMoteOptions;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.magic.visual.SpellVisualEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 把一次施法表现（{@link SpellVisualEvent}）翻译成粒子。
 *
 * <p><b>为什么挂在这里而不是每个执行器里各写一份</b>：执行器只管"打成没打中"，
 * 而 {@code SpellVisualEvent} 已经是<b>所有交付方式命中后的统一广播点</b>
 * （§43 建立：携带真实端点、目标、源与词序列）。在它上面接一层粒子，
 * 就等于一次覆盖 X1/X3/X4/X6/X7，且不需要新增任何网络包。
 *
 * <p><b>形取「墨 × 载体」、色取墨、量取「墨个性 × 载体修正」</b>：
 * 墨回答"这是什么魔法"、载体回答"怎么送过来的"，两者都取自 {@link SpellVisual} 已同步的字段；
 * 数量与散布再按 {@link AkaishiMagicParticles#emitMul}/{@code spreadMul} 缩放 ——
 * 于是"炽墨的直射"与"星墨的布场"不只是颜色不同，密度和散开程度也不同。
 *
 * <p><b>载体还决定铺法</b>：束沿 from→to 铺、场在落点绕圈、印记贴目标、齐射多点小爆。
 */
public final class SpellParticleBursts {

    /** 持续型载体的续发间隔（tick）：太密会糊成一片 */
    private static final int CONTINUOUS_INTERVAL = 3;
    /** 基础爆发量：再按亮度（威力）与墨/载体的数量倍率加成 */
    private static final int BASE_COUNT = 6;
    /** 单颗粒子的随机初速幅度 */
    private static final double JITTER = 0.02;
    /** 数量倍率的夹取区间（防"混墨 × 连发"这类叠乘出刷屏） */
    private static final float EMIT_MIN = 0.5f;
    private static final float EMIT_MAX = 2.5f;
    /** 散布倍率的夹取区间 */
    private static final float SPREAD_MIN = 0.4f;
    private static final float SPREAD_MAX = 2.0f;

    private SpellParticleBursts() {
    }

    /** 事件到达时的一次性爆发 */
    public static void onEvent(ClientLevel level, SpellVisualEvent event) {
        SpellVisual visual = event.visual();
        SpellMoteOptions options = optionsOf(visual);
        Vec3 at = anchor(level, event);
        int heavy = Math.min(32, Math.max(1, Math.round((BASE_COUNT + Mth.ceil(visual.brightness() * 8f)) * emitOf(visual))));
        for (int i = 0; i < 4; i++) {
            AkaishiMagicParticles.photon(level, visual.inkRgb(), at.x, at.y, at.z,
                    jitter(level, 0.06d), jitter(level, 0.06d), jitter(level, 0.06d), 1f, 1f);
        }
        switch (visual.carrier()) {
            case BEAM -> {
                along(level, options, event.from(), at, 5, Math.max(1, heavy / 3));
                burst(level, options, at, heavy, spreadOf(visual, 0.35));
            }
            case ORB -> {
                along(level, options, event.from(), at, 7, 2);      // 飞行残迹
                burst(level, options, at, heavy, spreadOf(visual, 0.40));
            }
            case SALVO -> {
                along(level, options, event.from(), at, 3, Math.max(1, heavy / 4));
                burst(level, options, at, Math.max(1, heavy / 2), spreadOf(visual, 0.50));
            }
            case MARK -> burst(level, options, at, Math.max(1, heavy / 2), spreadOf(visual, 0.25));
            case FIELD -> ring(level, options, at, Math.max(1f, visual.radius()), heavy);
            case AFTERIMAGE -> burst(level, options, at, heavy, spreadOf(visual, 0.50));
        }
    }

    /** 持续型载体（束 / 场 / 印记）在寿命内稀疏续发 */
    public static void onTick(ClientLevel level, SpellVisualEvent event, int age) {
        if (age <= 0 || age % CONTINUOUS_INTERVAL != 0) return;
        SpellVisual visual = event.visual();
        SpellMoteOptions options = optionsOf(visual);
        Vec3 at = anchor(level, event);
        switch (visual.carrier()) {
            case BEAM -> {
                along(level, options, event.from(), at, 2, 1);
                Vec3 line = at.subtract(event.from());
                Vec3 point = event.from().add(line.scale((age * 0.13d) % 1d));
                Vec3 velocity = line.normalize().scale(0.05d);
                AkaishiMagicParticles.photon(level, visual.inkRgb(), point.x, point.y, point.z,
                        velocity.x, velocity.y, velocity.z, 0.8f, 1f);
            }
            case FIELD -> ring(level, options, at, Math.max(1f, visual.radius()), 3);
            case MARK -> burst(level, options, at, 1, spreadOf(visual, 0.20));
            default -> {
                // ORB / AFTERIMAGE / SALVO 只亮一次，不需要续发
            }
        }
    }

    /** 粒子参数：形 = (墨 × 载体)、色 = 墨；缺墨或缺载体时回退源形 */
    private static SpellMoteOptions optionsOf(SpellVisual visual) {
        return AkaishiMagicParticles.options(MagicSource.byIdOrDefault(visual.sourceId()),
                visual.inkId(), visual.carrier(), visual.inkRgb());
    }

    /** 数量倍率（墨 × 载体），夹取后使用 */
    private static float emitOf(SpellVisual visual) {
        return Mth.clamp(AkaishiMagicParticles.emitMul(visual.inkId(), visual.carrier()), EMIT_MIN, EMIT_MAX);
    }

    /** 散布倍率（墨 × 载体），夹取后乘到基准散布上 */
    private static double spreadOf(SpellVisual visual, double base) {
        float mul = Mth.clamp(AkaishiMagicParticles.spreadMul(visual.inkId(), visual.carrier()),
                SPREAD_MIN, SPREAD_MAX);
        return base * mul;
    }

    /**
     * 粒子锚点：附着类（X4）挂在目标身上，其余落在真实命中点 ——
     * 与 {@code SpellVisualEventHandler} 的渲染锚点保持同一口径，避免"图形贴在目标身上、粒子却掉在地上"。
     */
    private static Vec3 anchor(ClientLevel level, SpellVisualEvent event) {
        if (MagicIds.Delivery.ATTACH.equals(event.visual().deliveryId()) && event.targetId() > 0) {
            Entity target = level.getEntity(event.targetId());
            if (target != null) return target.position().add(0, target.getBbHeight() * 0.5, 0);
        }
        return event.to();
    }

    private static void burst(ClientLevel level, SpellMoteOptions options, Vec3 at, int count, double spread) {
        for (int i = 0; i < count; i++) {
            level.addParticle(options, at.x, at.y, at.z,
                    jitter(level, spread), jitter(level, spread), jitter(level, spread));
        }
    }

    /** 沿 from→to 均匀铺 count 个点，每点发 perPoint 颗 —— 束 / 飞行残迹用。 */
    private static void along(ClientLevel level, SpellMoteOptions options, Vec3 from, Vec3 to, int count, int perPoint) {
        if (perPoint <= 0) return;
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            double x = Mth.lerp(t, from.x, to.x);
            double y = Mth.lerp(t, from.y, to.y);
            double z = Mth.lerp(t, from.z, to.z);
            for (int k = 0; k < perPoint; k++) {
                level.addParticle(options, x, y, z,
                        jitter(level, JITTER), jitter(level, JITTER), jitter(level, JITTER));
            }
        }
    }

    /** 在落点外圈撒一圈（半径取法术半径）—— 场地类用，让范围可见。 */
    private static void ring(ClientLevel level, SpellMoteOptions options, Vec3 at, float radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = radius * (0.55 + level.random.nextDouble() * 0.45);
            level.addParticle(options, at.x + Math.cos(angle) * r, at.y + 0.05, at.z + Math.sin(angle) * r,
                    0d, 0.01d, 0d);
        }
    }

    private static double jitter(ClientLevel level, double scale) {
        return (level.random.nextDouble() - 0.5d) * 2d * scale;
    }
}

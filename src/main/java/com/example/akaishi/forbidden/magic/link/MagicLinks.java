package com.example.akaishi.forbidden.magic.link;

import com.example.akaishi.forbidden.magic.MagicDamageTypes;
import com.example.akaishi.forbidden.magic.exec.MagicScheduler;
import com.example.akaishi.forbidden.magic.visual.LinkVisualEvents;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;

import dev.architectury.event.events.common.TickEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 「链」的连接表（设计记忆 §4.2 丝 / 根；口径 = <b>闪电链</b>）。
 *
 * <p><b>机制</b>：把一次施法解析出的全部目标按解析顺序（近距离优先）串成一条链；
 * 链存在期间<b>每秒</b>从链首开始，把伤害沿链<b>一环一环跳</b>过去（每环间隔
 * {@link #RING_DELAY_TICKS} 刻），并在相邻两环之间拉一串电火花 ——
 * 视觉与手感就是"闪电顺着链跳"。
 *
 * <p><b>为什么必须独立于执行器存在</b>：执行器只在"该生效的那一刻"被调用一次，
 * 而链要活 {@code durationSeconds} 秒。这段存活期既不属于施法状态机
 * （它只管前摇与逐段结算），也不属于任何实体 —— 故单独一张表 + 逐 tick 推进。
 *
 * <h3>生命周期与防泄漏</h3>
 * <ul>
 *   <li>链有<b>明确死期</b>（创建时刻 + 持续时长）；到期即解散；</li>
 *   <li>成员用 {@link WeakReference} 持有 —— 实体被移除 / 区块卸载后<b>不会</b>被链拖着不放；</li>
 *   <li>逐跳时重新解析成员存活情况；存活不足 {@link #MIN_RINGS} 环即解散；</li>
 *   <li>同时存在的链有上限 {@link #MAX_LINKS}，超出时丢弃最旧的一条；</li>
 *   <li>过期清理<b>全局</b>扫描（{@code getGameTime()} 是服务端共享基准），
 *       这样维度卸载后也不会有链永远赖着。</li>
 * </ul>
 */
public final class MagicLinks {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagicLinks.class);

    /** 同时存在的链上限（超出丢最旧） */
    public static final int MAX_LINKS = 8;

    /** 每条链的环数上限（与 §36.1 的多目标硬上限同量级） */
    public static final int MAX_RINGS = 8;

    /** 「连接两方」—— 少于两环就没有链可言（§4.2 丝） */
    public static final int MIN_RINGS = 2;

    /** 每秒一跳 */
    public static final int PULSE_INTERVAL_TICKS = 20;

    /** 相邻两环之间的传导间隔：2 刻 = 0.1s —— 这个"延迟"才是"链"的观感来源 */
    public static final int RING_DELAY_TICKS = 2;

    /** 一条链 */
    private static final class Link {

        final ServerLevel level;
        final List<WeakReference<Entity>> rings;
        /** 施法者（击杀归属 / 死亡消息用；可为 null —— 无主施法） */
        final WeakReference<Entity> caster;
        /** 每跳对每一环造成的伤害（HP） */
        final float damagePerPulse;
        long nextPulseTick;
        final long expireTick;
        final SpellVisual visual;

        Link(ServerLevel level, List<WeakReference<Entity>> rings, Entity caster,
                float damagePerPulse, long nextPulseTick, long expireTick, SpellVisual visual) {
            this.level = level;
            this.rings = rings;
            this.caster = new WeakReference<>(caster);
            this.damagePerPulse = damagePerPulse;
            this.nextPulseTick = nextPulseTick;
            this.expireTick = expireTick;
            this.visual = visual;
        }
    }

    private static final List<Link> LINKS = new CopyOnWriteArrayList<>();
    private static volatile boolean registered;

    private MagicLinks() {
    }

    /** 注册逐 tick 推进（由 {@code MagicExecBuiltins} 调用） */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TickEvent.SERVER_LEVEL_POST.register(MagicLinks::serverTick);
    }

    /**
     * 建立一条链。
     *
     * @param rings         链的成员，<b>按顺序</b>（索引 0 = 链首）
     * @param damagePerPulse 每跳对每一环造成的伤害（HP）
     * @param durationTicks 持续刻数
     * @param caster        施法者（可为 null）
     */
    public static int create(ServerLevel level, List<Entity> rings, float damagePerPulse,
            int durationTicks, Entity caster, SpellVisual visual) {
        if (level == null || rings == null || rings.size() < MIN_RINGS || !Float.isFinite(damagePerPulse) || damagePerPulse <= 0f) {
            return 0;
        }
        List<WeakReference<Entity>> refs = new ArrayList<>(Math.min(rings.size(), MAX_RINGS));
        for (Entity entity : rings) {
            if (entity instanceof LivingEntity living && living.isAlive()
                    && living.level() == level && refs.size() < MAX_RINGS
                    && refs.stream().noneMatch(ref -> ref.get() == entity)) {
                refs.add(new WeakReference<>(entity));
            }
        }
        if (refs.size() < MIN_RINGS) {
            return 0;
        }
        trimIfFull();
        long now = level.getGameTime();
        // 首跳紧随施法（+1 刻）：否则按下技能后要干等整整一秒才有任何反应
        Link link = new Link(level, refs, caster, damagePerPulse,
                now + 1L, now + Math.max(PULSE_INTERVAL_TICKS, durationTicks), visual);
        LINKS.add(link);
        return refs.size();
    }

    /** 每维度每 tick 调用 */
    public static void serverTick(ServerLevel level) {
        if (LINKS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Link link : LINKS) {
            if (link.level == level && now > link.expireTick) {
                LINKS.remove(link);
                continue;
            }
            if (link.level != level || now < link.nextPulseTick) {
                continue;
            }
            link.nextPulseTick = now + PULSE_INTERVAL_TICKS;
            try {
                pulse(link, now);
            } catch (RuntimeException e) {
                LOGGER.error("[AkaishiMagic] 链传导异常", e);
            }
        }
    }

    /** 一次脉冲：从链首开始逐环安排传导（每环延后 {@link #RING_DELAY_TICKS} 刻） */
    private static void pulse(Link link, long now) {
        List<Entity> alive = new ArrayList<>();
        for (WeakReference<Entity> ref : link.rings) {
            Entity entity = ref.get();
            if (entity instanceof LivingEntity living && living.isAlive() && living.level() == link.level) {
                alive.add(living);
            }
        }
        if (alive.size() < MIN_RINGS) {
            LINKS.remove(link);
            return;
        }
        Entity caster = link.caster.get();
        for (int i = 0; i < alive.size(); i++) {
            Entity victim = alive.get(i);
            Entity previous = i == 0 ? null : alive.get(i - 1);
            long delay = (long) i * RING_DELAY_TICKS;
            if (delay == 0L) {
                strike(link.level, caster, victim, previous, link.damagePerPulse, link.visual);
            } else {
                MagicScheduler.schedule(link.level, (int) delay,
                        () -> {
                            if (LINKS.contains(link)) {
                                strike(link.level, caster, victim, previous, link.damagePerPulse, link.visual);
                            }
                        });
            }
        }
    }

    /** 只有实际受伤成功才闪电跳跃，不干预后续环的结算。 */
    private static void strike(ServerLevel level, Entity caster, Entity victim, Entity previous,
            float damage, SpellVisual visual) {
        if (!(victim instanceof LivingEntity living) || !living.isAlive() || living.level() != level
                || com.example.akaishi.forbidden.magic.exec.resolve.MagicResolvers.allied(caster, victim)
                || (caster instanceof net.minecraft.world.entity.player.Player
                    && victim instanceof net.minecraft.world.entity.player.Player && !level.getServer().isPvpAllowed())) {
            return;
        }
        boolean piercing = visual != null && ("san".equals(visual.sourceId())
                || visual.wordIds().contains("jian") || visual.wordIds().contains("zhen"));
        var source = visual != null && "san".equals(visual.sourceId()) ? MagicDamageTypes.psychic(level, caster)
                : piercing ? level.damageSources().indirectMagic(caster, caster) : MagicDamageTypes.source(level, caster);
        if (living.hurt(source, damage)) {
            LinkVisualEvents.emit(level, visual, previous, living);
        }
    }

    private static void trimIfFull() {
        while (LINKS.size() >= MAX_LINKS) {
            if (LINKS.isEmpty()) {
                return;
            }
            LINKS.remove(0);
        }
    }

    /** 当前链数（自检 / 调试用） */
    public static int count() {
        return LINKS.size();
    }

    /** 清空（世界重置时用；正常流程不调） */
    public static void clear() {
        LINKS.clear();
    }
}

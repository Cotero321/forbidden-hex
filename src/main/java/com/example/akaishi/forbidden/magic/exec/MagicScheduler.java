package com.example.akaishi.forbidden.magic.exec;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 服务端<b>延迟任务队列</b>（按 tick 计）。
 *
 * <p><b>为什么需要它</b>：X6 回响要"延迟 1~3s 才复现"（§19.3），而那时的施法会话早已结束、
 * 状态机也已不再持有它 —— 这件事不属于"进行中的施法"，只属于"未来的某一刻要发生什么"。
 * 用一个极简的 tick 队列承载，比为了回响去扩状态机要干净得多。
 *
 * <p><b>为什么不用实体承载</b>：回响没有可视载体（§36.2 里它只是"延迟亮一下法阵"），
 * 造一个只有计时器作用的实体是纯粹的开销。等 P5-B 的投射物 / 场地实体落地后，
 * 它们会各自持有自己的生命周期，也不需要走这里。
 *
 * <h3>并发与内存</h3>
 * <ul>
 *   <li>用 {@link CopyOnWriteArrayList} 遍历（任务由施法线程在同一 tick 内新增，
 *       而遍历可能正在另一个维度上发生）；</li>
 *   <li><b>容量上限</b>：超过 {@link #MAX_PENDING} 时丢弃<b>最旧</b>的任务 ——
 *       宁可少一次回响，也不能让队列无限增长；</li>
 *   <li><b>过期丢弃</b>：到点已过 {@link #STALE_TICKS} 刻仍未执行（说明那个维度当时没在 tick），
 *       直接丢弃，防止残留任务在维度卸载后永远赖着。</li>
 * </ul>
 */
public final class MagicScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagicScheduler.class);

    /** 待执行任务上限（超出则丢最旧的） */
    public static final int MAX_PENDING = 512;

    /** 过期阈值：到点后超过这么多刻还没执行就丢弃（20 秒） */
    public static final int STALE_TICKS = 400;

    /** 一条待执行任务 */
    private record Task(ResourceKey<Level> dimension, long dueTick, long createdNanos, Runnable action) {
    }

    private static final List<Task> TASKS = new CopyOnWriteArrayList<>();
    private static volatile boolean registered;

    private MagicScheduler() {
    }

    /** 注册到服务端 tick（由 {@code MagicExecBuiltins} 调用） */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TickEvent.SERVER_LEVEL_POST.register(MagicScheduler::serverTick);
    }

    /**
     * 安排一个延迟任务。
     *
     * @param level      任务所属维度（只在那个维度 tick 时执行）
     * @param delayTicks 延迟刻数（≤0 表示下一 tick 执行）
     * @param action     到点要做的事；抛出的异常只记录、不影响其余任务
     */
    public static void schedule(ServerLevel level, int delayTicks, Runnable action) {
        if (level == null || action == null) {
            return;
        }
        trimIfFull();
        long due = level.getGameTime() + Math.max(1, delayTicks);
        TASKS.add(new Task(level.dimension(), due, System.nanoTime(), action));
    }

    /** 每维度每 tick 调用：执行到点且属于本维度的任务 */
    public static void serverTick(ServerLevel level) {
        if (TASKS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ResourceKey<Level> here = level.dimension();
        long staleNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(STALE_TICKS / 20L);
        long currentNanos = System.nanoTime();
        for (Task task : TASKS) {
            if (currentNanos - task.createdNanos() > staleNanos) {
                TASKS.remove(task);
                continue;
            }
            if (!here.equals(task.dimension())) {
                continue;
            }
            // 先移除再执行：任务内部再次 schedule 不应影响本次遍历，也避免失败任务被反复重试
            if (now >= task.dueTick()) {
                if (TASKS.remove(task)) {
                    runQuietly(task);
                }
            }
        }
    }

    private static void runQuietly(Task task) {
        try {
            task.action().run();
        } catch (RuntimeException e) {
            LOGGER.error("[AkaishiMagic] 延迟任务异常", e);
        }
    }

    private static void trimIfFull() {
        while (TASKS.size() >= MAX_PENDING) {
            if (TASKS.isEmpty()) {
                return;
            }
            TASKS.remove(0);
        }
    }

    /** 待执行任务数（自检 / 调试） */
    public static int pending() {
        return TASKS.size();
    }

    /** 清空（玩家 / 世界重置时用；正常流程不调） */
    public static void clear() {
        TASKS.clear();
    }
}

package com.example.akaishi.forbidden.magic.projectile;

import com.example.akaishi.forbidden.api.magic.exec.MagicDeliveryAction;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞行中弹体的「<b>落地后要做什么</b>」登记表（设计记忆 §19.3 X2）。
 *
 * <p><b>为什么需要一张表</b>：弹体是实体，命中的那一刻在世界线程里回调；
 * 而"这一发法术打谁、打多狠"是 {@link MagicDeliveryAction}（一个闭包），
 * <b>无法序列化进实体</b>。把闭包按弹体 UUID 存在服务端一侧、命中时取用，
 * 是最短的一条路 —— 比把整份 {@code MagicContext}（十几项数值 + 词表 + 墨表 + 来源）
 * 序列化进实体 NBT 再在命中时重建要可靠得多。
 *
 * <h3>生命周期与防泄漏</h3>
 * <ul>
 *   <li>登记发生在<b>发射瞬间</b>；正常命中或被打落时<b>立刻移除</b>；</li>
 *   <li>{@link #TTL_TICKS} 秒兜底过期 —— 弹体若因区块卸载 / 世界关闭等原因
 *       从未回调，登记项也不会永久留着（§19.3 的飞行时长上限是 2s，5 秒足够宽裕）；</li>
 *   <li>用 {@link ConcurrentHashMap}：登记/取用都在服务端主线程，但兜底扫描可能与
 *       "另一个维度正在发射"同刻发生，并发容器把这类交叉变成本地的一次 map 操作。</li>
 * </ul>
 */
public final class SpellProjectiles {

    /** 登记项的兜底存活时长（tick）：5 秒 —— 远宽于 §19.3 的"飞行 0.5~2s" */
    public static final int TTL_TICKS = 100;

    /** 一条待结算的弹体 */
    private record Pending(MagicDeliveryAction action, long expireTick) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private SpellProjectiles() {
    }

    /** 注册兜底清理（由 {@code MagicExecBuiltins} 调用） */
    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(SpellProjectiles::serverTick);
    }

    /** 发射时登记：把"命中后要执行的闭包"与弹体绑起来 */
    public static void bind(Entity projectile, ServerLevel level, MagicDeliveryAction action) {
        if (projectile == null || level == null || action == null) {
            return;
        }
        PENDING.put(projectile.getUUID(), new Pending(action, level.getGameTime() + TTL_TICKS));
    }

    /** 命中：取出闭包并触发一次（位置 = 命中点） */
    public static void fire(Entity projectile, Vec3 impact) {
        fire(projectile, null, impact, projectile == null ? Vec3.ZERO : projectile.getDeltaMovement());
    }

    public static void fire(Entity projectile, Entity hit, Vec3 impact, Vec3 direction) {
        Pending pending = projectile == null ? null : PENDING.remove(projectile.getUUID());
        if (pending == null) {
            return;
        }
        pending.action().fire(hit, impact, direction);
    }

    /** 被打落 / 中途消失：丢弃登记，不结算 */
    public static void drop(Entity projectile) {
        if (projectile != null) {
            PENDING.remove(projectile.getUUID());
        }
    }

    /**
     * 兜底清理：过期项一律丢弃（弹体早已不在，结算也无从谈起）。
     *
     * <p><b>不按维度过滤</b>：{@code getGameTime()} 取自服务端共享的世界数据（跨维度同一基准），
     * 故任一维度 tick 时都可以全局清理。若按"本次 tick 的维度"过滤，
     * 那些所在维度已卸载的登记项就永远没人清 —— 那才是真泄漏。
     */
    public static void serverTick(ServerLevel level) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        PENDING.entrySet().removeIf(entry -> now > entry.getValue().expireTick());
    }

    /** 当前待结算数（自检 / 调试用） */
    public static int pending() {
        return PENDING.size();
    }

    /** 清空（世界重置时用；正常流程不调） */
    public static void clear() {
        PENDING.clear();
    }
}

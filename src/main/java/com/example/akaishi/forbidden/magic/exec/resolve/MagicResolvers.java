package com.example.akaishi.forbidden.magic.exec.resolve;

import com.example.akaishi.forbidden.api.magic.exec.ITargetResolver;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 形状解析器的<b>注册入口与共用过滤</b>（设计记忆 §19.2 阶段① / §36.1 五条细则）。
 *
 * <p><b>为什么把过滤单独收在这里</b>：§36.1 的五条细则（友伤 / PVP / 上限 / 视线 / 隐蔽）
 * 对<b>所有七个形状</b>都成立 —— 若各解析器各写一遍，"某天只改了 RAY 的隐蔽判定"就会变成
 * 一个只有特定法术才复现的 bug。过滤只写一份，各解析器只负责"怎么圈"。
 *
 * <p>解析器共 6 个实现类（{@link SphereResolver} 同时供给 SPHERE 与 AREA —— 两者算法相同，
 * 差别在<b>原点从哪来</b>，而那由投送器交给管线的 {@code at} 决定，见该类的类注释）。
 */
public final class MagicResolvers {

    // ==================== 注册 ====================

    /** 注册全部内置解析器（由 {@code MagicExecBuiltins} 调用） */
    public static void register() {
        MagicExecRegistry.register(new SelfResolver());
        MagicExecRegistry.register(new SingleResolver());
        MagicExecRegistry.register(new RayResolver());
        MagicExecRegistry.register(new ConeResolver());
        MagicExecRegistry.register(new SphereResolver(MagicShape.SPHERE));
        MagicExecRegistry.register(new SphereResolver(MagicShape.AREA));
        MagicExecRegistry.register(new ChainResolver());
    }

    private MagicResolvers() {
    }

    // ==================== 共用过滤 ====================

    /**
     * 圈出以 {@code center} 为心、半径 {@code radius} 内的全部合法实体，<b>按距离升序</b>。
     *
     * <p>升序是刻意的：§36.1 细则 3 要求"超出上限时按<b>距离近者优先</b>截断"，
     * 排序放在过滤里，截断就只是取前 N 个。
     *
     * @param allowCaster 是否允许把施法者自己算作目标（自身向 / 以己为中心的范围效果要传 true）
     */
    public static List<Entity> around(Level level, Vec3 center, double radius,
            Entity caster, MagicTargetQuery query, boolean allowCaster) {
        if (level == null || center == null) {
            return List.of();
        }
        double r = Math.max(0.5d, radius);
        AABB box = new AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r);
        double rSqr = r * r;
        List<Entity> found = level.getEntitiesOfClass(Entity.class, box,
                e -> valid(caster, e, query, allowCaster) && e.position().distanceToSqr(center) <= rSqr);
        List<Entity> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
        return sorted;
    }

    /**
     * 圈出"以 {@code eye} 为顶、沿 {@code dir} 张开 {@code cosHalfAngle} 的锥"内的全部合法实体，
     * <b>按距离升序</b>。
     *
     * <p>单体 / 锥形 / 连锁的<b>起点</b>都是这件事，只是容许的半角不同 ——
     * 抽到这里之后，它们的差别就只剩一个余弦常量（各 20° / 45° / 20°）。
     */
    public static List<Entity> aimed(Level level, Vec3 eye, Vec3 dir, double range,
            double cosHalfAngle, Entity caster, MagicTargetQuery query, boolean allowCaster) {
        if (level == null || dir == null) {
            return List.of();
        }
        List<Entity> found = around(level, eye, range, caster, query, allowCaster);
        List<Entity> aimed = new ArrayList<>(found.size());
        for (Entity target : found) {
            if (aimCos(dir, target.getEyePosition().subtract(eye)) >= cosHalfAngle) {
                aimed.add(target);
            }
        }
        return aimed;
    }

    /**
     * §36.1 的五条细则（细则 1 的友伤极性由 {@code allowCaster} 与执行器共同承担 ——
     * 解析器不做"伤害还是增益"的判断，那是 E 的语义）。
     */
    public static boolean valid(Entity caster, Entity target, MagicTargetQuery query, boolean allowCaster) {
        if (target == null || target.isRemoved() || !target.isAlive()) {
            return false;
        }
        if (query.alliesOnly() && !allied(caster, target)) return false;
        if (query.enemiesOnly() && allied(caster, target)) return false;
        // 旁观者不该被任何法术选中（原版也如此）
        if (target.isSpectator()) {
            return false;
        }
        if (!allowCaster && target == caster) {
            return false;
        }
        // 细则 5：隐匿者默认不可选；「迹」/「标」豁免（由 query 带入）
        if (!query.includeHidden() && hidden(target)) {
            return false;
        }
        // 细则 2：PVP 关闭时，玩家不能以"别的玩家"为目标（与 length 无关，整类跳过）
        if (!query.allowPvp() && caster instanceof Player && target instanceof Player && target != caster) {
            return false;
        }
        // 细则 4：默认要求视线；「门」/「折」豁免（由 query 带入）
        // 视线判定在 LivingEntity 上（Entity 基类没有）—— 非生物施法者无从判视线，按"不要求"放行
        if (query.requireLineOfSight() && caster instanceof LivingEntity watcher
                && !watcher.hasLineOfSight(target)) {
            return false;
        }
        return true;
    }

    public static boolean allied(Entity caster, Entity target) {
        if (caster == null || target == null) return false;
        if (caster == target || caster.isAlliedTo(target) || target.isAlliedTo(caster)) return true;
        Entity ownerA = caster instanceof net.minecraft.world.entity.TamableAnimal pet ? pet.getOwner() : caster;
        Entity ownerB = target instanceof net.minecraft.world.entity.TamableAnimal pet ? pet.getOwner() : target;
        return ownerA != null && ownerB != null && (ownerA == ownerB || ownerA.isAlliedTo(ownerB));
    }

    /** 是否处于隐匿：隐形（原版隐身 / 「角」）或潜行 */
    public static boolean hidden(Entity entity) {
        return entity.isInvisible() || (entity instanceof LivingEntity living && living.isCrouching());
    }

    /** 截断到上限（调用方须已按距离升序） */
    public static List<Entity> capped(List<Entity> sorted, int limit) {
        int n = Math.max(1, Math.min(limit, MagicTargetQuery.DEFAULT_LIMIT));
        return sorted.size() <= n ? sorted : List.copyOf(sorted.subList(0, n));
    }

    /** 施法者眼睛位置；无施法者时退回原点 */
    public static Vec3 eyeOf(Entity caster, Vec3 fallback) {
        return caster == null ? fallback : caster.getEyePosition();
    }

    /** 施法者视线方向；无施法者时返回 {@code null}（方向类形状此时无法解析） */
    public static Vec3 lookOf(Entity caster) {
        return caster == null ? null : caster.getLookAngle();
    }

    /** 本次的射程（格）；未算出时退回 1，避免零长度射线 */
    public static double rangeOf(MagicContext context) {
        return Math.max(1d, context.snapshot().range());
    }

    /** 与给定方向的夹角余弦（用于锥形 / 单体瞄准）；{@code dir} 与 {@code to} 都已归一化 */
    public static double aimCos(Vec3 dir, Vec3 to) {
        double len = to.length();
        if (len < 1e-6d) {
            return -1d;
        }
        return dir.dot(to.scale(1d / len));
    }
}

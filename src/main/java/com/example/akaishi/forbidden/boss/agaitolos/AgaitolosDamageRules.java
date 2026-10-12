package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosGuardSkill;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * 阿盖托洛丝受击管线的唯一入口（设计文档 §3.1）。
 * <p>
 * 刻意做成<b>无状态纯函数</b>：所有状态（上次受击时间等）由实体持有并传入。
 * 理由：技能里严禁再写第二套减伤/锁伤口径，否则必然出现某招漏套；统一收口后
 * 各技能只负责"如何产生伤害"，不管"伤害如何被 BOSS 吃下"。
 */
public final class AgaitolosDamageRules {

    /** 减伤 60% ⇒ 实际承伤系数 0.4（规格："生命 1444，减伤 60%"） */
    public static final float DAMAGE_MULTIPLIER = 0.4F;

    /** 受击冷却 4 tick = 0.2s（规格："每 0.2s 仅受到一次伤害"） */
    public static final int HURT_COOLDOWN_TICKS = 4;

    /** 锁伤下限 24（规格："伤害限制 24"） */
    public static final float DAMAGE_CAP_MIN = 24.0F;

    /** 锁伤上限比例：最大生命的 5%（§6.4 第 2 条定案；1444 血时上限 = 72.2） */
    public static final float DAMAGE_CAP_HEALTH_RATIO = 0.05F;

    private AgaitolosDamageRules() {
    }

    /**
     * 结算一次受击。<b>所有</b>进入 BOSS 的伤害都必须经此函数。
     *
     * @param gameTime         当前世界游戏刻
     * @param lastHurtGameTime 上次成功受击的游戏刻（未受过击请传远小于 gameTime 的值）
     * @param maxHealth        当前最大生命（多玩家加成抬高后锁伤上限同步抬高）
     * @param respawning       是否处于复活阶段（无敌）；由实体从同步数据读出后传入
     * @param selfDamage       是否为「被玩家反弹回来的凋零头」造成的自伤（规格：无视减伤与锁伤）
     * @param guarding         承伤方是否正处于格挡架势；仅作廉价前置短路，<b>真正的判定</b>仍是
     *                         {@link AgaitolosGuardSkill#isBlocking(LivingEntity, DamageSource)}（§3.3 唯一判定器）
     * @param projectileImmune 是否处于「免疫远程」阶段（阶段三，见 {@link AgaitolosEntity#isProjectileImmune()}）；
     *                         仅对弹射物生效，且排在反弹自伤通道<b>之后</b>（理由见方法内 ⑥）
     * @param blocker          承伤方实体（判定朝向/近战必须拿到实体，故由调用方把自身传入）
     * @param source           伤害来源
     * @param amount           原始伤害量
     * @return 实际生效的伤害量；返回 0 表示本次免伤
     */
    public static float resolve(long gameTime, long lastHurtGameTime, float maxHealth,
                                boolean respawning, boolean selfDamage, boolean guarding, boolean projectileImmune,
                                LivingEntity blocker, DamageSource source, float amount) {
        // ① 非玩家来源免伤（规格："不会受到非玩家的伤害"）。
        // 判据用 getEntity()：玩家射出的箭其 getEntity() 仍是玩家，因此算玩家伤害；陷阱/摔落/其它生物则免伤。
        if (!(source.getEntity() instanceof Player)) {
            return 0.0F;
        }
        // ② 复活阶段免伤（规格："复活阶段 BOSS 不会受到伤害"）。无敌只在这一条口径里判，
        //    严禁另开 isInvulnerableTo（设计文档 §3.1 明令禁止第二套口径）。
        if (respawning) {
            return 0.0F;
        }
        // ③ 0.2s 受击冷却：冷却内伤害记 0 而不是取消事件，保持击退等判定口径统一
        if (gameTime - lastHurtGameTime < HURT_COOLDOWN_TICKS) {
            return 0.0F;
        }
        // ④ 格挡闸：状态闸，不是数值口径，故不参与 ⑦⑧（归零就是归零，不乘减伤、不受锁伤影响）。
        //    放在 ③ 之后是刻意的：冷却期内的攻击已被 ③ 归零，不该再由它触发格挡（否则会白送一次反击）。
        //    判定唯一走 AgaitolosGuardSkill.isBlocking：BOSS 架势与玩家「格挡成功」是同一件事的两面（§3.3）。
        //    注：BOSS 侧的格挡判据本身要求"近战"（{@code directEntity} 是生物）⇒ 弹射物永远不会走到这里
        //    （见 AgaitolosGuardSkill#isMelee 的注释），故本闸与 ⑥ 的远程免疫正交、不存在谁先谁后的问题。
        if (guarding && AgaitolosGuardSkill.isBlocking(blocker, source)) {
            return 0.0F;
        }
        // ⑤ 反弹自伤通道：凋零头被玩家打回 BOSS 时（selfDamage=true）跳过 ⑦⑧，
        //    直接返回原始伤害（规格："BOSS 承受此伤害并且无视自身减伤和锁伤"）。
        //    ③ 的 0.2s 冷却在其之前仍生效：规格只免减伤与锁伤，未免受击冷却。
        //    ⚠ 必须排在 ⑥ 之前：被玩家打回来的凋零头<b>本身就是弹射物</b>，若先判远程免疫，
        //      规格里这条"反弹就能伤到它"的唯一手段会被自己的免疫整条吃掉（设计记忆 §7 已列为风险项）。
        if (selfDamage) {
            return amount;
        }
        // ⑥ 阶段三免疫远程（规格："BOSS 免疫远程攻击"）：弹射物伤害一律归零（完全免伤，不是减伤）。
        //    排在这里的好处：① 已保证本闸只会看到"玩家来源"的伤害，故不存在"非玩家免伤 + 远程免疫"
        //    两套规则打架或写出死分支；② 自伤通道（⑤）已经先行返回，天然成为免疫的显式例外。
        if (projectileImmune && isProjectileDamage(source)) {
            return 0.0F;
        }
        // ⑦ 减伤 60%
        amount *= DAMAGE_MULTIPLIER;
        // ⑧ 锁伤：上限 = max(24, 最大生命 5%)
        amount = Math.min(amount, Math.max(DAMAGE_CAP_MIN, maxHealth * DAMAGE_CAP_HEALTH_RATIO));
        return amount;
    }

    /**
     * 这次伤害是否算「弹射物伤害」（阶段三免疫远程的唯一判据）。
     * <p>
     * <b>双判据，缺一不可</b>：
     * <ol>
     *   <li>{@code source.is(DamageTypeTags.IS_PROJECTILE)} —— 主判据。原版把所有弹射物的伤害类型
     *       都登记进了 {@code #minecraft:is_projectile}（箭、雪球、三叉戟、火球、凋零头、投掷药水…），
     *       按<b>类型</b>判比按实体判更贴规格（"免疫远程攻击"说的是伤害的性质，不是某个实体的类）；</li>
     *   <li>{@code source.getDirectEntity() instanceof Projectile} —— 兜底判据。第三方模组的弹射物若用了
     *       自定义伤害类型且没登记进标签，只靠第 ① 条会漏；按直接伤害实体判能兜住
     *       （本 BOSS 自己的 {@code AgaitolosWitherSkull} 正是 {@code Projectile} 子类）。
     *       它不是死分支：{@code /damage} 一类指令与部分模组弹体都可能走到这条。</li>
     * </ol>
     * 只判"是不是弹射物"、不判"来自谁"：来源过滤是 ① 的职责（非玩家来源在第 ① 步已经免伤），
     * 本方法只管伤害性质 ⇒ 两处口径不重叠、也不会互相抵消。
     */
    public static boolean isProjectileDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
    }
}

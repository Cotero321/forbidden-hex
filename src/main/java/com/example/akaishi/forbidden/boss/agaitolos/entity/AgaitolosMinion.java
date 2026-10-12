package com.example.akaishi.forbidden.boss.agaitolos.entity;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosActionFx;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.level.Level;

/**
 * 「恶怨倒转」的召唤物：<b>原版凋零骷髅 + 一条"主人没了就自我消散"的看门狗</b>。
 * <p>
 * <b>为什么需要这个子类（它补的是 BOSS 侧够不到的洞）</b>：召唤物由 BOSS 在技能收尾时统一回收
 * （{@code AgaitolosMinionSkill#dismissAll}，四个出口），但有一类情形 BOSS 自己<b>无法</b>处理 ——
 * 主人<b>被卸载</b>时它不 tick，自然没人执行清场。实测 1.20.1 字节码，这条路径是
 * {@code PersistentEntitySectionManager#unloadEntity(EntityAccess)} 直接调
 * {@code EntityAccess.setRemoved(UNLOADED_TO_CHUNK)}：既不经过 {@code Entity#remove}（可覆写），
 * 而 {@code Entity#setRemoved} 本身是 {@code final}（覆写会编译失败）。
 * ⇒ <b>"区块卸载后不留孤儿怪"只能由召唤物自己边跑边查</b>。
 * <p>
 * <b>为什么用子类、而不是给原版实例挂一个 Goal</b>：Goal 方案要先拿到 {@code Mob#goalSelector}，
 * 而它是 {@code protected}（跨包/跨类不可及，实测编译报"goalSelector 在 Mob 中是 protected 访问控制"）；
 * 子类则天然拥有这一权限，且可以直接在 {@link #aiStep()} 里做判定 —— 比"注册一个永远真条件、
 * 只在 start 里干活"的 Goal 更直白（也少一个类）。
 * <p>
 * <b>实体类型仍是原版 {@code WITHER_SKELETON}</b>（构造时传入该类型）：白拿原版 AI / 掉落 / 渲染 / 音效
 * 与 {@code EntityType.WITHER_SKELETON} 的一切登记关系，只额外挂一条本类逻辑。
 * <p>
 * <b>已知边界（诚实记录）</b>：本类不是注册实体类型，故区块重载后原版工厂会按
 * {@code WITHER_SKELETON} 重建出<b>普通</b>凋零骷髅，这条看门狗随之丢失（归属标记与队伍成员身份都还在，
 * 后者让"不内斗"依旧成立）。此时兜底换人：BOSS 的蓄力状态是落盘的，重载后蓄力照常推进并在 ≤12s 内
 * 收尾清场。两条机制合起来保证孤儿怪要么当场消散、要么在下一次蓄力收尾时被收走。
 */
public class AgaitolosMinion extends WitherSkeleton {

    /**
     * 主人（召唤者 BOSS）的 UUID；由 {@code AgaitolosMinionSkill#summonRing} 在入世界之前写入。
     * <p>刻意不与实体标签（{@code AgaitolosMinionOwner:<uuid>}）重复存两处：标签是"结算侧的归属判据"，
     * 本字段只是本类做一次 O(1) 查询的入参，二者同源同值。
     * <p>不落盘：本类实例本身就不会在重载后重建（见类注释），存了也用不上。
     */
    private UUID ownerId;

    public AgaitolosMinion(EntityType<? extends WitherSkeleton> type, Level level) {
        super(type, level);
    }

    /** 记下主人。必须在 {@code addFreshEntity} 之前调用，否则首 tick 会被看门狗判成"主人为空"而立刻消散 */
    public void setOwner(UUID ownerId) {
        this.ownerId = ownerId;
    }

    /**
     * 每 tick 的看门狗判定（服务端）。
     * <p>
     * 放在 {@link #aiStep()} 而不是自己下一个 Goal：{@code Mob#aiStep} 本身就是"每 tick 一次"的钩子，
     * 且原版也在这里做"该不该消失"（{@code Mob#checkDespawn} 就是在这个位置 {@code discard()}），
     * 故在 aiStep 里 discard 是原版认可的时机，不存在与移动/目标系统的时序冲突。
     * <p>
     * 判定本身是一次 O(1) 的 UUID 查询（{@code EntityLookup} 的 {@code byUuid} 哈希表，
     * 只含<b>已加载</b>实体，被卸载的主人查不到 ⇒ 恰好就是我们要的信号）。
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.ownerId != null && this.isOwnerGone()) {
            // 消散表现与 BOSS 侧整队回收同款（青蓝魂焰 + discard，不产战利品、不播死亡音效）
            AgaitolosActionFx.minionDissolve(this);
            this.discard();
        }
    }

    /**
     * 主人是否已经"不在了"。任一成立即为真：
     * <ul>
     *   <li>查不到主人（区块卸载 / 已从世界消失）—— 召唤物已无归属；</li>
     *   <li>主人类型不符（理论上不会，防将来复用）或已死亡；</li>
     *   <li>主人活着但<b>不在蓄力</b>：正常流程下这条永远不成立（技能一结束就会整队回收），
     *       它兜的是"清场漏掉 / 旧存档残留"的坏数据 —— 顺手把老存档里的孤儿小怪也收干净。</li>
     * </ul>
     */
    private boolean isOwnerGone() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity owner = level.getEntity(this.ownerId);
        if (!(owner instanceof AgaitolosEntity boss)) {
            return true;
        }
        return !boss.isAlive() || !boss.isCharging();
    }
}

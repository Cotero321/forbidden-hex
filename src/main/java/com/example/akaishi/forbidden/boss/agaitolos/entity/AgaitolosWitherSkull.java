package com.example.akaishi.forbidden.boss.agaitolos.entity;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosCombat;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosDoom;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPhaseThreeFx;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPhaseThreeSounds;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPsychic;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosMinionSkill;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的远程弹体：凋零头颅（设计文档 §0「远程攻击」/§1 第 20 条）。
 * <p>
 * 继承原版 {@link WitherSkull} 复用其飞行、渲染、与方块/实体碰撞管线，<b>只替换命中结算</b>：
 * 原版是 8 点凋零/魔法伤害 + 凋零 II 10s，与本 BOSS 规格（5 点爆炸 + 凋零 III 2s + 2 点真实伤害）不符。
 * <p>
 * 伤害数值常量放在本类（消费端）而非发射器，避免弹体与 skill 互相引用。
 * <p>
 * <b>四处对原版的刻意偏离</b>（均由 1.20.1 字节码实测支撑，见各自方法注释）：
 * <ol>
 *   <li><b>可被近战打回</b>：原版 {@code WitherSkull#isPickable()} 与 {@code WitherSkull#hurt(...)}
 *       都无条件返回 false，弹体永远不会被玩家命中；而带偏转逻辑的
 *       {@code AbstractHurtingProjectile#hurt} 被 {@code WitherSkull#hurt} 整段覆盖，
 *       子类也无法经 {@code super} 调到。若不打开这两个口子，规格的
 *       「如反弹至 BOSS 则 BOSS 承受此伤害」永远不可达，故本类覆写二者还原偏转语义。
 *       偏转时 {@code setOwner(偏转者)} 会把 owner 从 BOSS 换成玩家；此后本弹体的伤害源
 *       （{@code directEntity=本弹体 / causingEntity=owner}）恰好满足
 *       {@code AgaitolosEntity#hurt} 内的判据
 *       {@code source.getDirectEntity() instanceof AgaitolosWitherSkull && source.getEntity() instanceof Player}，
 *       于是命中 {@code AgaitolosDamageRules#resolve} 的 ⑤ 自伤通道（跳过 ⑦ 减伤与 ⑧ 锁伤）。
 *       <b>阶段三追加</b>：该通道还承担"绕开免疫远程"的职责 —— 自伤判定排在 ⑥ 远程免疫之前，
 *       否则阶段三"反弹就能伤到它"会被它自己的远程免疫吃掉（见 {@code AgaitolosDamageRules} 的 ⑤⑥）。</li>
 *   <li><b>不破坏地形</b>：原版 {@code WitherSkull#onHit} 无条件调用
 *       {@code level().explode(this, x, y, z, 1.0F, false, Level.ExplosionInteraction.MOB)}；
 *       注意原版 {@code setDangerous(false)} <b>并不能阻止爆炸破坏地形</b>
 *       （它只改 {@code getInertia()} 与火方块上的阻爆），故本类整个覆写 {@code onHit} 不生成爆炸。</li>
 *   <li><b>不打自家召唤物</b>：原版 {@code Projectile#canHitEntity} 不含队伍/友军判定，
 *       本类按归属标记把 owner 名下的召唤物排除出可命中目标（见 {@link #canHitEntity(Entity)}）。</li>
 *   <li><b>命中后必定消失 + 寿命兜底</b>（2026-09-24 修复）：原版 {@code WitherSkull#onHit} 命中即
 *       {@code discard}，但本类为"不破坏地形"整段覆写后，一度让"命中 owner"分支无条件早退
 *       ⇒ 弹体一旦停在发射者体内就永不移除。现在该分支<b>有界</b>（连续命中 owner 超过
 *       {@link #OWNER_HIT_GRACE_TICKS} 即强制移除），另加 {@link #MAX_LIFETIME_TICKS} 寿命上限兜底
 *       "打空后一直存在"。见 {@link #tick()} 与 {@link #onHit(HitResult)}。</li>
 * </ol>
 */
public class AgaitolosWitherSkull extends WitherSkull {

    /** 命中爆炸伤害 5 点。P8 转配置项 */
    public static final float BURST_DAMAGE = 5.0F;

    /** 命中附加的真实伤害 2 点（无视护甲/抗性/附魔）。P8 转配置项 */
    public static final float TRUE_DAMAGE = 2.0F;

    /** 凋零持续 40 tick = 2s。P8 转配置项 */
    public static final int WITHER_DURATION_TICKS = 40;

    /** 凋零等级放大器 2 = 凋零 III（amplifier 从 0 起算）。P8 转配置项 */
    public static final int WITHER_AMPLIFIER = 2;

    /**
     * 破防比例（<b>默认 1.0 = 不破防</b>）：1.0 = 原有行为（远程攻击 / 饱和轰炸，逐位不变）；
     * 0.7 = 无视 30% 防御（阶段三「三重投掷」，规格「此攻击无视 30% 的防御」）。
     * <p>消费点在 {@link #onHitEntity}：&lt; 1.0 时改走
     * {@link AgaitolosCombat#damageAfterPartialArmorBypass} 预折算 + {@code forbidden_hex:triple_throw}
     * 伤害源，与俯冲镰扫（0.7）/高速踢击（0.6）<b>共用同一条"按比例削甲"口径</b>，不复制第二份实现。
     * <p>做成弹体字段而不是另立一个弹体类：三重投掷要的只是"同一个凋零头，多破一点甲"，
     * 另立类意味着实体注册 + 渲染 + 掉落 + 语言键一整条落地项，收益为零。
     */
    private float armorKeptRatio = 1.0F;

    /**
     * 「高空投弹」标记（<b>默认 false</b>）：true 时本弹体命中那一刻额外补一圈落地冲击环
     * （{@code AgaitolosPhaseThreeFx#bombardImpact}）。
     * <p>
     * <b>为什么放在弹体而不是状态机</b>：投弹与落地之间隔着约 1s 的飞行（6 格高空 + 十格以上距离），
     * 状态机那一拍不知道弹体何时、在哪里落地；弹体命中处才是唯一真源。
     * <p><b>为什么做成字段而不是另立弹体类</b>：与 {@link #armorKeptRatio} 同一取舍 ——
     * 另立类意味着实体注册 + 渲染 + 掉落 + 语言键一整条落地项，收益为零；默认 false ⇒
     * 远程攻击 / 三重投掷 / 被反弹的弹体行为<b>逐位不变</b>。
     * <p><b>不落盘</b>（如实记录取舍）：这是纯表现开关，读档重建的弹体丢标记的最坏后果是
     * "跨存档的那一发落地少一圈粒子"，结算与伤害完全不受影响；为它加 NBT 反而多一条需要维护的口径。
     */
    private boolean bombard;

    /**
     * 命中发射者本人的<b>宽限 tick 数</b>（<b>待调手感值</b>）：4。
     * <p>贴脸保护必须<b>有界</b>：命中 owner 只豁免"这一下"，连续命中超过本值即判定"卡在发射者体内"并强制移除。
     * 原实现的 {@code return} 是无条件早退 ⇒ 一旦弹体停在 owner 体内就永不移除（本次修复的根因）。
     */
    private static final int OWNER_HIT_GRACE_TICKS = 4;

    /**
     * 弹体寿命上限（tick）：100 = 5s（<b>待调手感值</b>）。
     * <p>兜底"打空后一直存在"：{@code dangerous=false} ⇒ 惯性取父类 0.95，弹体总射程约
     * {@code 0.8 / (1 - 0.95) = 16} 格（<b>小于</b> 24 格射程），飞不到目标就会自己停住；
     * 没有寿命上限时它会停在半空永不消失。
     */
    private static final int MAX_LIFETIME_TICKS = 100;

    /** 已存在 tick 数（仅服务端自增；<b>不落盘</b> —— 读档重建的弹体从头计时，更保守） */
    private int ageTicks;

    /** 连续命中 owner 的计数（仅服务端维护，见 {@link #OWNER_HIT_GRACE_TICKS}） */
    private int ownerHitStreak;

    /** 上次命中 owner 时的 {@link #ageTicks}（用于判定"连续"） */
    private int lastOwnerHitAge;

    public AgaitolosWitherSkull(EntityType<? extends WitherSkull> type, Level level) {
        super(type, level);
    }

    /** 设定破防比例（只由 {@code AgaitolosSkullSkill#fireSpread} 调用；0~1 之间钳制） */
    public void setArmorKeptRatio(float armorKeptRatio) {
        this.armorKeptRatio = Mth.clamp(armorKeptRatio, 0.0F, 1.0F);
    }

    /** 标记本发为「高空投弹」（只由 {@code AgaitolosBombardSkill#fire} 调用；纯表现，不参与任何结算） */
    public void markBombard() {
        this.bombard = true;
    }

    /**
     * 服务端两条兜底（客户端不自行判断移除，完全听服务端移除包）：
     * <ol>
     *   <li><b>寿命上限</b>：存在超过 {@link #MAX_LIFETIME_TICKS} 即自动移除 —— 兜住"打空 / 被弹开后再无命中"
     *       的全部情形（含 {@code dangerous=false} 惯性 0.95 导致"飞不到射程就停住"）；</li>
     *   <li><b>owner 连续命中计数的复位</b>：与上次命中 owner 不相邻的 tick 即清零，
     *       保证只有"真的卡在发射者体内"才会触发 {@link #onHit} 的强制移除，正常飞行的弹体不受影响。</li>
     * </ol>
     */
    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            if (++this.ageTicks >= MAX_LIFETIME_TICKS) {
                this.discard();
                return;
            }
            if (this.ownerHitStreak > 0 && this.ageTicks - this.lastOwnerHitAge > 1) {
                this.ownerHitStreak = 0;
            }
        }
        super.tick();
    }

    /**
     * 成为射线可命中的目标。原版 {@code WitherSkull#isPickable()} 字节码是 {@code iconst_0; ireturn}（恒 false），
     * 而玩家近战的取靶（实体射线）与 {@code Player#attack} → {@code Entity#hurt} 这条链都以可命中为目标前提，
     * 不开则玩家永远打不到这一发头，规格的反弹玩法不可达。
     * <p>副作用：{@code Entity#canBeHitByProjectile()} = {@code isAlive() && isPickable()}，
     * 因此本弹体自此也会被其他弹体判定为可命中目标，这是可偏转设计的必然代价。</p>
     */
    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * <b>不打自家召唤物</b>（2026-09-21 补）。
     * <p>
     * 原版 {@code Projectile#canHitEntity} 只判 {@code canBeHitByProjectile()} 与"是否与 owner 同乘载具"
     * （实测 1.20.1 字节码），<b>没有任何队伍/友军判定</b> ⇒ 弹体飞过自家召唤物时照样结算伤害。
     * 伤害本身现在不会引发内斗（同队已被 {@code HurtByTargetGoal} 排除，见
     * {@code AgaitolosMinionSkill} 的类注释），但"BOSS 朝玩家吐头、把自己挡在弹道上的小怪打成筛子"
     * 仍是明确的错误行为，故按<b>归属标记</b>（唯一的"谁是我的人"判据）在这里挡掉。
     * <p>
     * 只对 {@code owner} 是本 BOSS 的情况生效：被玩家反弹后 owner 变成玩家
     * （见 {@link #hurt}），此时不再过滤 —— 反弹回来的头打中谁就是谁，与原版一致。
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (AgaitolosMinionSkill.isMinionOf(target, this.getOwner())) {
            return false;
        }
        return super.canHitEntity(target);
    }

    /**
     * 近战打回。原版 {@code WitherSkull#hurt(...)} 恒 {@code return false}，把 {@code AbstractHurtingProjectile#hurt}
     * 的偏转逻辑整段吃掉，故本方法按该父类<b>字节码语义</b>重写（实测指令顺序：
     * {@code isInvulnerableTo → markHurt → source.getEntity() → 非客户端则 getLookAngle → setDeltaMovement →
     * xPower/yPower/zPower = look * 0.1D（三个字段均为 public double，可直接写）→ setOwner(偏转者)}，
     * 有实体来源返回 true，否则 false）：
     * <p>与原版仅两点差异：① 客户端直接 {@code return false}，状态改动只在服务端做，避免两端分叉；
     * ② 偏转者必须是<b>本弹体可命中的生物</b>。1.20.1 实测不存在 {@code Entity#canAttack(Entity)}
     * （{@code Entity} 上只有 {@code isAttackable/skipAttackInteraction}），
     * {@code LivingEntity#canAttack(LivingEntity)} 只接受生物、{@code canAttackType} 恒返回 true，
     * 对非生物的弹体都不可用，故用 {@code Projectile#canHitEntity}
     * （{@code target.canBeHitByProjectile() && 不与 owner 同乘载具}）做等价筛除，挡掉旁观者/已死亡目标。
     * <p>{@code setOwner(偏转者)} 是整条反弹链的关键：owner 由 BOSS 换成玩家后，
     * {@link #onHitEntity} 构造的伤害源 {@code causingEntity} 才变成玩家，
     * {@code AgaitolosEntity} 侧才会把它识别为「被反弹」并放行进 ⑤ 自伤通道。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide()) {
            return false;
        }
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (!(source.getEntity() instanceof LivingEntity deflector) || !this.canHitEntity(deflector)) {
            return false;
        }
        this.markHurt();
        Vec3 look = deflector.getLookAngle();
        this.setDeltaMovement(look);
        this.xPower = look.x * 0.1D;
        this.yPower = look.y * 0.1D;
        this.zPower = look.z * 0.1D;
        this.setOwner(deflector);
        return true;
    }

    /**
     * 命中分派（<b>刻意不调用 super</b>）。原版 {@code WitherSkull#onHit} 字节码为
     * 「{@code super.onHit(result)}（继承链上落到 {@code Projectile#onHit} 的 ENTITY/BLOCK 分派）
     * → {@code if (!level().isClientSide) { level().explode(this, getX(), getY(), getZ(), 1.0F, false,
     * Level.ExplosionInteraction.MOB); discard(); } }」——即<b>爆炸是 WitherSkull 自己在 onHit 里无条件生成的</b>，
     * {@code setDangerous(false)} 挡不住；调 super 就等于保留破坏地形的爆炸。规格要求「不可破坏地形」，
     * 故此处自建分派：实体命中交给 {@link #onHitEntity}，方块命中不做任何事（不爆炸、不做方块交互），
     * 最后服务端 discard。
     * <p>特例：加 {@code isPickable()=true} 后，刚生成的头可能与发射者（owner = BOSS 自己）重叠并被判定命中自身，
     * 此时<b>不结算</b>也不立即 discard，让弹体正常飞出，避免贴脸自毁；但该豁免<b>有界</b> ——
     * 连续命中 owner 超过 {@link #OWNER_HIT_GRACE_TICKS} 次即强制移除
     * （原实现是无条件 {@code return}，弹体一旦停在发射者体内就永不移除，即"命中后留在原地"的根因）。
     * <p>非 owner 命中一律在同一 tick 内 {@code discard()} ⇒ "命中后必定消失"。
     */
    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) result;
            if (entityHit.getEntity() == this.getOwner()) {
                // 贴脸保护：只豁免这一下。连续命中超过宽限 ⇒ 判定"卡在发射者体内"，强制移除（有界早退）
                this.lastOwnerHitAge = this.ageTicks;
                if (!this.level().isClientSide() && ++this.ownerHitStreak > OWNER_HIT_GRACE_TICKS) {
                    this.discard();
                }
                return;
            }
            this.onHitEntity(entityHit);
        }
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        // 每一段伤害的伤害源都必须同时带 directEntity = 本弹体、causingEntity = owner，两个理由缺一不可：
        //   ① BOSS 侧靠 source.getDirectEntity() instanceof AgaitolosWitherSkull 识别「这发头是被反弹回来的」；
        //   ② 真实伤害段若不带 causingEntity，source.getEntity() 为 null，会被 BOSS 受击管线第 ① 步
        //      （非玩家来源免伤）整段挡掉，规格要求的「反弹至 BOSS 则 BOSS 承受此伤害」就落不了地。
        // 故不能走 level.damageSources().explosion(null, owner)：explosion 的 directEntity 会退化为 null，理由①失效。
        Holder<DamageType> explosionHolder = living.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.EXPLOSION);
        DamageSource explosionSource = new DamageSource(explosionHolder, this, owner);

        // 随在场玩家数增强（§0 第 71 行）：爆炸 5 与真实 2 都是<b>固定数值</b>，故乘 BOSS 的人数伤害系数。
        // owner 必须是本 BOSS 才乘 —— 被玩家打回后 owner 已换成玩家，这一发已经是"玩家的弹药"，
        // 再吃 BOSS 的加成会让"反弹"凭空变强（判据与下面凋零/凋亡分流用的 owner 判据同一取舍）。
        float damageScale = owner instanceof AgaitolosEntity skullOwner ? (float) skullOwner.getDamageScale() : 1.0F;

        // ① 5 点爆炸伤害（只借爆炸伤害类型，不生成真正的爆炸 —— 不破坏地形）。
        //    破防变体（三重投掷）：改走"按比例削甲"口径 —— 先按「护甲 × armorKeptRatio、韧性 × armorKeptRatio」
        //    预折算，再用已进 bypasses_armor 的 forbidden_hex:triple_throw 施加（否则会被原版护甲步骤二次减免）。
        //    默认比例 1.0 ⇒ 与改动前逐位相同（等额护甲/韧性算一遍，再走 bypasses_armor 跳过原版那一步）。
        if (armorKeptRatio < 1.0F) {
            float piercing = AgaitolosCombat.damageAfterPartialArmorBypass(living, BURST_DAMAGE * damageScale, armorKeptRatio);
            // 精神污染换壳（天魔＊灾之后）：受击方是被改写的玩家时，爆炸段也一并改判精神伤害
            living.hurt(AgaitolosPsychic.forVictim(
                    AgaitolosCombat.tripleThrow(living.level(), this, owner), living, living.level().getGameTime()), piercing);
        } else {
            living.hurt(AgaitolosPsychic.forVictim(explosionSource, living, living.level().getGameTime()),
                    BURST_DAMAGE * damageScale);
        }
        // ② 凋零 III 2s；施加者取 owner 保证击杀归属，owner 非生物（理论上不会）时用弹体自身兜底。
        //    1.20.1 常量名是 MobEffects.WITHER。目标若是本 BOSS，canBeAffected=false 会自动挡掉，无需特判。
        //    阶段三：owner 是本 BOSS 时走 AgaitolosDoom 分流成"凋亡 III"（被玩家打回来的头 owner 已换成玩家，
        //    阶段判定自然不成立 ⇒ 仍走原版凋零，与本弹体"被偏转后不再是 BOSS 的弹药"这一定义一致）
        if (owner instanceof AgaitolosEntity boss) {
            AgaitolosDoom.applyWitherOrDoom(boss, living, WITHER_DURATION_TICKS, WITHER_AMPLIFIER);
        } else {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION_TICKS, WITHER_AMPLIFIER),
                    owner instanceof LivingEntity livingOwner ? livingOwner : this);
        }
        // ③ 2 点真实伤害（同样携带 direct + causing，被反弹回 BOSS 时才会被自伤通道吃下）。
        //    同样乘人数系数（固定数值），理由与爆炸段一致
        living.hurt(AgaitolosPsychic.forVictim(
                AgaitolosCombat.trueDamage(living.level(), this, owner), living, living.level().getGameTime()),
                TRUE_DAMAGE * damageScale);
        // ④ 落地冲击（纯表现，仅「高空投弹」这一族）：本弹体刻意不生成爆炸（见 #onHit），
        //    原版那套"爆炸声 + 爆炸粒子"在这里并不存在，故落地的可见/可闻表现只能自己给。
        //    位置取弹体自身坐标 = 命中点，不取受击者位置（擦边命中时才对得上"砸在哪里"）
        if (this.bombard && this.level() instanceof ServerLevel serverLevel) {
            AgaitolosPhaseThreeFx.bombardImpact(serverLevel, this.position());
            AgaitolosPhaseThreeSounds.bombardImpact(serverLevel, this.position());
        }
    }
}

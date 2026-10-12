package com.example.akaishi.forbidden.sanity.shadow;

import com.example.akaishi.forbidden.sanity.SanityKillReward;
import com.example.akaishi.forbidden.sanity.SanityPenalties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Collections;
import java.util.UUID;

/**
 * 影怪 {@code forbidden_hex:shadow} —— 低理智（SAN 低于 40%）时在玩家周围浮现的幻影（P4）。
 *
 * <p><b>用户拍板的行为</b>（逐条落地位置）：
 * <ol>
 *   <li>任意环境 + 低 SAN 才可能出现（不限亮度），档位门控与生成节拍在 {@link ShadowSpawner}；</li>
 *   <li><b>被攻击累计 4 次即消散</b>（{@link #HITS_TO_VANISH}），<b>与伤害量无关</b> ——
 *       见 {@link #hurt}：本类刻意不调 {@code super.hurt}，因此影怪"不会被伤害打死"，只被"打散"；</li>
 *   <li>每次被击中先播 {@code vanish}、播完再落到玩家周围另一个位置（{@link ShadowBlink}）；</li>
 *   <li>攻击（近战扑击 / 20% 档远程精神弹）在 {@link ShadowCombat}；</li>
 *   <li>消散时按击杀口径结算奖励：{@link SanityKillReward#apply}（击杀者 = 第 4 次伤害的责任玩家）。</li>
 * </ol>
 *
 * <p><b>为什么继承 {@code LivingEntity} 而不是 {@code Monster}/{@code Mob}</b>（关键设计取舍）：
 * <ul>
 *   <li>项目里 {@code DecayZoneManager#convertEntities} 只遍历 {@code Mob}，
 *       {@code AkaishiDecaySpawnBlocker} 的两个拦截口也都以 {@code instanceof Mob} 为门 ——
 *       不继承 {@code Mob} 就<b>天然</b>绕开"衰竭区转化"与"死寂禁刷"两条干扰，
 *       不必去改任何衰减区代码（用户要求"影怪不受这两者干扰"且"不动 DecayZone"）；</li>
 *   <li>影怪的行为（漂移、贴身、被击中瞬移、命中计数消散）全部自持，不需要原版寻路 / 目标选择器，
 *       继承 {@code Mob} 只会白白把 mobcap、难度判定、自然消失等一整套语义引进来；</li>
 *   <li>代价（如实记录）：不吃 {@code Mob#isInvertedHealAndHarm} 的亡灵豁免 ⇒ 在衰竭区内仍会吃到
 *       区域每秒 1 点魔法伤害（区域对一切非亡灵生物的效果，无法在不改衰减区代码的前提下豁免）。
 *       生成端已拒绝衰竭区落点（见 {@link ShadowSpawner}），漂进去的影怪会在数秒内自然消散，
 *       这与"影怪不该存在于死寂地带"的直觉一致。</li>
 * </ul>
 *
 * <p><b>不落盘</b>：覆写 {@code save(CompoundTag)} 返回 {@code false} —— 原版 {@code EntityStorage} 正是
 * 依据该返回值决定是否把实体写进区块存档（1.20.1 字节码：{@code if (entity.save(tag)) list.add(tag)}）。
 * 因此玩家离开 / 切档 / 重启后，影怪一律不复存在，不会在世界里堆积孤儿实体；
 * 配合 {@link #MAX_LIFETIME_TICKS} 的寿命上限，即使玩家原地挂机也不会越攒越多。
 *
 * <p>数值均为<b>待调手感值</b>。
 */
public class ShadowEntity extends LivingEntity implements GeoEntity {

    // ===== 手感常量（待调手感值）=====

    /** 命中多少次即消散（用户拍板：4 次，与伤害量无关） */
    public static final int HITS_TO_VANISH = 4;

    /**
     * 命中计数节流（tick）：同一瞬间的多来源伤害（火焰 + 近战、衰变 + 弹体等）合并成一次计数。
     * <p>取 3（0.15s）：刚好吃掉"同一次结算里叠了多段伤害"的重复，又远小于任何常规连击间隔
     * （即便高攻速武器也不会被吞），不去改变玩家"挥几次就是几次"的体感。
     */
    public static final int HIT_GATE_TICKS = 3;

    /** 存活上限（tick）：1200 = 60s。到点自毁（不给奖励），兜住"玩家原地挂机刷出一堆影怪" */
    public static final int MAX_LIFETIME_TICKS = 1200;

    /** 与目标玩家的最大距离（格）：超出即自毁（跟丢了就别留在世界里） */
    public static final double DESPAWN_DISTANCE = 32.0D;

    /** 漂移速度（格/tick）：0.3 ≈ 6 格/s，比玩家步行略慢，贴脸前给得出反应时间 */
    public static final double FLY_SPEED = 0.30D;

    /** 贴身距离（格）：进到这个距离就停止漂移、原地悬停准备出手 */
    public static final double HOVER_DISTANCE = 1.4D;

    /** 近战判定距离（格） */
    public static final double MELEE_RANGE = 2.0D;

    /** 出现后的"愣神"时长（tick）：20 = 1s 内不出手，避免刚浮现就贴脸打一下 */
    private static final int SPAWN_GRACE_TICKS = 20;

    // ===== 同步数据 =====

    /**
     * 是否处于动作窗口（扑击 / 消散在播）。
     * <p>走同步数据而不是只靠触发：MAIN 控制器<b>两端都读它</b>才能在动作期间停播待机
     * （服务端触发动作、客户端各自轮询同一份真源，与 agaitolos 的 guard/charge 同一手法）。
     */
    private static final EntityDataAccessor<Boolean> DATA_ACTING =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ===== 运行期状态（全部不落盘）=====

    /** 锁定的目标玩家（生成时绑定；不落盘 ⇒ 读档重建后为空 ⇒ 实体自行消散） */
    private UUID targetId;
    /** 已被命中的次数 */
    private int hitCount;
    /** 存活 tick 数 */
    private int lifeTicks;
    /** 动作窗口剩余 tick（>0 时不出手、不再次瞬移） */
    private int actionTicks;
    /** 上次计入命中计数的时刻（节流用） */
    private long lastHitTick = -HIT_GATE_TICKS;
    private int meleeCooldown = SPAWN_GRACE_TICKS;
    private int rangedCooldown = SPAWN_GRACE_TICKS + 20;
    /** 正在消散（播完 vanish 即移除） */
    private boolean dissolving;
    /** 本次动作窗口结束时是否要落位（受击瞬移） */
    private boolean blinkPending;
    /** 消散奖励的击杀者（仅第 4 次命中时非空；超时自毁 / 目标离开时为空 ⇒ 不给奖励） */
    private ServerPlayer rewardKiller;

    public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        // 影怪是"虚影"：不受重力、不与方块碰撞（不卡墙、不卡地形，也不需要寻路）。
        // 瞬移落点仍然做校验（见 ShadowBlink）—— 不是因为它会卡住，而是"从墙里/地里冒出来"观感不对。
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** 基础属性；影怪"没有血条"，血量只是占位（伤害一律不落地，见 {@link #hurt}） */
    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // ------------------------------------------------------------------ 绑定 / 查询

    /** 生成时绑定目标玩家（只由 {@link ShadowSpawner} 调用一次） */
    public void bindTo(ServerPlayer player) {
        this.targetId = player.getUUID();
        this.hitCount = 0;
        this.lifeTicks = 0;
        ShadowFx.appear(this);
    }

    /** 是否归属于该玩家（生成与清扫都按它配对，避免影怪互相"串门"） */
    public boolean isBoundTo(Player player) {
        return player != null && player.getUUID().equals(this.targetId);
    }

    /** 是否处于动作窗口（待机控制器据此让出骨骼） */
    public boolean isActing() {
        return this.entityData.get(DATA_ACTING);
    }

    /** 动作窗口开合（只有本类的落点校验与动作播放入口会写） */
    private void setActing(boolean value) {
        this.entityData.set(DATA_ACTING, value);
    }

    // ------------------------------------------------------------------ 同步数据 / 存档

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTING, Boolean.FALSE);
    }

    /**
     * <b>不落盘</b>：返回 false 让原版区块保存跳过本实体（1.20.1 {@code EntityStorage} 依据返回值决定是否写入）。
     * 影怪是一次性演出实体，"重启后从存档里爬出来"没有任何意义，还会变成孤儿。
     */
    @Override
    public boolean save(CompoundTag tag) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        // 不落盘：无自有字段（save 已被覆写为 false，这里只为满足抽象方法）。
        // 注意访问权限：Forge 1.20.1 的 LivingEntity 把这两个方法提升为 public，覆写不得收窄。
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // 不落盘：无自有字段。若被第三方以其它途径写进存档，重建后目标为空 ⇒ 下一 tick 自行消散
    }

    // ------------------------------------------------------------------ 占位实现（非 Mob 的 LivingEntity 必备）

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // 无装备槽
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true; // 灵体不呼吸：否则在水里会被"淹死"（4 次溺水伤害 = 消散）
    }

    @Override
    public boolean fireImmune() {
        return true; // 灵体不着火：影怪可能在任意环境（含岩浆旁）生成
    }

    @Override
    public boolean isPushable() {
        return false; // 虚影不该把玩家推来推去
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    // ------------------------------------------------------------------ 主循环

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            this.serverStep();
        }
        // 先决策再交给原版：LivingEntity#aiStep 内部会 travel（重力已被 noGravity 关掉，只剩摩擦与位移）
        super.aiStep();
    }

    /** 服务端权威每 tick：消散收尾 → 目标校验 → 动作窗口推进 → 漂移与出手 */
    private void serverStep() {
        if (this.dissolving) {
            if (--this.actionTicks <= 0) {
                this.settleThenRemove();
            }
            return;
        }
        ServerPlayer target = this.resolveTarget();
        if (target == null || ++this.lifeTicks > MAX_LIFETIME_TICKS
                || this.distanceTo(target) > DESPAWN_DISTANCE) {
            this.dissipate(); // 目标离线/换维度/跑远，或活得太久 ⇒ 自然消散（不给奖励）
            return;
        }
        // 动作窗口结束那一 tick 才落位：观感是"先消失、再从别处冒出"（见 ShadowBlink）
        if (this.actionTicks > 0 && --this.actionTicks == 0) {
            this.setActing(false);
            if (this.blinkPending) {
                this.blinkPending = false;
                ShadowBlink.perform(this, target);
            }
        }
        if (this.meleeCooldown > 0) {
            this.meleeCooldown--;
        }
        if (this.rangedCooldown > 0) {
            this.rangedCooldown--;
        }
        this.faceTarget(target);
        this.driftTowards(target);
        if (this.actionTicks > 0) {
            return; // 动作期间不叠加第二个动作
        }
        int tier = SanityPenalties.tierOf(target);
        if (this.distanceTo(target) <= MELEE_RANGE) {
            if (this.meleeCooldown <= 0) {
                ShadowCombat.melee(this, target, tier);
            }
        } else if (this.distanceTo(target) <= ShadowCombat.RANGED_RANGE && this.rangedCooldown <= 0) {
            // 远程的档位门（只有 20% 档才放）在 ShadowCombat.fireBolt 内部，近战优先
            ShadowCombat.fireBolt(this, target, tier);
        }
    }

    /** 向目标漂移；贴身后原地悬停 */
    private void driftTowards(LivingEntity target) {
        double distance = this.distanceTo(target);
        if (distance <= HOVER_DISTANCE) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 toTarget = target.getEyePosition().subtract(this.getEyePosition());
        if (toTarget.lengthSqr() < 1.0E-6D) {
            return;
        }
        this.setDeltaMovement(toTarget.normalize().scale(FLY_SPEED));
    }

    /**
     * 水平对准目标、俯仰对准其眼睛高度。
     * <p>三个旋转都要显式写：{@code yRot} 驱动躯体朝向、{@code yBodyRot} 驱动渲染用的身体朝向、
     * {@code yHeadRot} 驱动头部（不写会出现"身子转了、头还在看别处"）。非 Mob 没有 LookControl，
     * 无法走原版插值，因此逐个赋值。
     */
    void faceTarget(LivingEntity target) {
        double deltaX = target.getX() - this.getX();
        double deltaZ = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double deltaY = target.getEyeY() - this.getEyeY();
        float pitch = (float) (-(Mth.atan2(deltaY, horizontal) * (180.0D / Math.PI)));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    /** 解析目标玩家：离线 / 死亡 / 创造 / 旁观一律视为"没有目标"（影怪随即消散） */
    private ServerPlayer resolveTarget() {
        if (this.targetId == null) {
            return null;
        }
        if (!(this.level().getPlayerByUUID(this.targetId) instanceof ServerPlayer player)) {
            return null;
        }
        if (player.isDeadOrDying() || player.isCreative() || player.isSpectator()) {
            return null;
        }
        return player;
    }

    // ------------------------------------------------------------------ 受击：计数 + 瞬移 / 消散

    /**
     * <b>影怪没有血条</b>：受击只计数、不掉血，第 {@link #HITS_TO_VANISH} 次即消散。
     *
     * <p>因此这里<b>刻意不调用 {@code super.hurt}</b>：
     * <ol>
     *   <li>{@code super.hurt} 会把伤害真正落地（可能被打死 ⇒ 走死亡掉落/死亡动画，与"消散"语义不符）；</li>
     *   <li>{@code super.hurt} 会写 {@code invulnerableTime = 20}，导致同一目标的后续攻击在 0.5s 内
     *       整段被无敌帧吞掉 —— 而"4 次命中"要的是<b>每一次挥手都算数</b>；</li>
     *   <li>不落地伤害 ⇒ 不需要处理"护甲/附魔/吸收"等与幻影无关的结算。</li>
     * </ol>
     * 受击反馈（红闪 + 音效 + 粒子）自行补齐：红闪走原版伤害事件
     * （{@code Level#broadcastDamageEvent} ⇒ 客户端 {@code handleDamageEvent} 设 hurtTime）。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || this.dissolving) {
            return false;
        }
        long now = this.level().getGameTime();
        if (now - this.lastHitTick < HIT_GATE_TICKS) {
            return false; // 同一瞬间的多来源合并成一次计数
        }
        this.lastHitTick = now;
        this.hitCount++;
        this.hurtTime = this.hurtDuration = 10;
        this.level().broadcastDamageEvent(this, source);
        this.playHurtSound(source);
        ShadowFx.hit(this);
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (this.hitCount >= HITS_TO_VANISH) {
            this.startDissolve(killer); // 第 4 次：消散并结算奖励
        } else {
            this.startBlink(); // 1~3 次：先消失，播完再落位
        }
        return true;
    }

    /** 受击瞬移：播 vanish，动作窗口结束后落位（落点校验见 {@link ShadowBlink}） */
    private void startBlink() {
        this.blinkPending = true;
        this.playAction(ShadowAnimations.TRIGGER_VANISH, ShadowAnimations.VANISH_TICKS);
        ShadowFx.vanish(this);
    }

    /** 第 4 次命中：播 vanish → 移除，移除前按击杀口径结算奖励 */
    private void startDissolve(ServerPlayer killer) {
        this.dissolving = true;
        this.rewardKiller = killer;
        this.blinkPending = false;
        this.playAction(ShadowAnimations.TRIGGER_VANISH, ShadowAnimations.VANISH_TICKS);
        ShadowFx.vanish(this);
    }

    /** 自然消散（超时 / 跟丢 / SAN 回升 / 被清扫）：同一条演出，但<b>不给奖励</b> */
    public void dissipate() {
        if (this.dissolving) {
            return;
        }
        this.startDissolve(null);
    }

    /**
     * 消散收尾：先结算奖励再移除。
     * <p><b>为什么不走 {@code die()}</b>：影怪不落盘、无掉落、无死亡动画，走 {@code die()} 会把它拖进
     * 死亡管线（掉落 / 经验 / 死亡音效）并让 {@code LivingDeathEvent} 再结算一次。这里直接复用
     * {@link SanityKillReward#apply}——<b>数值口径只有一处</b>（SAN +5 与账本 {@code relieve(5)} 都在那里），
     * 与 forge 侧 {@code AkaishiSanityKillHandler} 走的是同一个入口。
     */
    private void settleThenRemove() {
        if (this.rewardKiller != null) {
            SanityKillReward.apply(this.rewardKiller, this);
            this.rewardKiller = null;
        }
        this.discard();
    }

    // ------------------------------------------------------------------ 动作窗口（给 ShadowCombat / ShadowBlink 用）

    /** 播一次性动作并锁住动作窗口（tick 与 clip 等长） */
    public void playAction(String trigger, int ticks) {
        this.actionTicks = ticks;
        this.setActing(true);
        this.triggerAnim(ShadowAnimations.CONTROLLER_ACTION, trigger);
    }

    public void setMeleeCooldown(int ticks) {
        this.meleeCooldown = Math.max(0, ticks);
    }

    public void setRangedCooldown(int ticks) {
        this.rangedCooldown = Math.max(0, ticks);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        ShadowAnimations.registerControllers(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}

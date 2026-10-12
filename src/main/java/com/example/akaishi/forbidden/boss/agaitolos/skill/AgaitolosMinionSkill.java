package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosActionFx;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosScaling;
import com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosMinion;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * 一阶段技能「恶怨倒转」的<b>召唤面</b>（设计文档 §0 一阶段 / §1 第 22 条）。
 * <p>
 * 规格：生成凋零骷髅分队（5 近战 5 远程，拥有增强过的属性和装备）；BOSS 在此期间为蓄力模式。
 * <p>
 * 本类负责"把分队铺出来"、"把它们认回来"、"技能结束时整队回收"三件事：
 * <ul>
 *   <li>{@link #summon(AgaitolosEntity)} —— 生成整支分队；</li>
 *   <li>{@link #findLivingMinions(AgaitolosEntity, double)} —— 按归属标记筛出<b>本 BOSS 召唤的</b>存活成员
 *       （吸血结算只吸自己召唤的、不吸自然生成的凋零骷髅；分队全灭判定也用它）；</li>
 *   <li>{@link #dismissAll(AgaitolosEntity)} —— 一次性回收本 BOSS 名下全部存活召唤物（见下方"清场口径"）。</li>
 * </ul>
 * 蓄力状态、时长与取消条件归实体自己持有（见 {@code AgaitolosEntity#tickChargeState}），本类不碰状态计时。
 * <p>
 * <b>友军归属用原版 Team（2026-09-21 补，修"召唤物内斗"）</b>：
 * 召唤物是原版 {@code WitherSkeleton}，它的目标选择器里有 {@code HurtByTargetGoal}（报复）—— 挨了谁的打，
 * 就把谁设成目标。而"挨打"的判据是 {@code LivingEntity#hurt} 写的 {@code lastHurtByMob}，<b>不筛敌我</b>：
 * <ol>
 *   <li><b>弹体友伤是真实存在的</b>：实测 Forge 1.20.1 字节码，
 *       {@code Projectile#canHitEntity} 只判 {@code canBeHitByProjectile()} 与"是否与 owner 同乘载具"，
 *       <b>没有任何队伍/友军判定</b> ⇒ 远程那 5 只的弓箭会命中挡在弹道上的近战同类，
 *       BOSS 自己的 {@code AgaitolosWitherSkull} 同理（原 {@code onHitEntity} 对"任何 LivingEntity"结算）。</li>
 *   <li><b>报复链没有闸</b>：实测 {@code Entity#isAlliedTo} 是"{@code getTeam() != null &&
 *       getTeam().isAlliedTo(other)}"——<b>无队伍 ⇒ 永远不是友军</b>；而
 *       {@code HurtByTargetGoal#canUse} 走的 {@code TargetingConditions#test}（forCombat 分支）里
 *       恰恰有一条"{@code attacker.isAlliedTo(target)} ⇒ 返回 false"。
 *       ⇒ 只要双方同队，报复就不会成立；而当时双方都没有队伍，于是"被擦一下 → 互相锁目标 → 互殴"，
 *       若被擦的是 BOSS 自己的头，召唤物还会反过来打 BOSS。</li>
 * </ol>
 * 修法即"给这一支分队（连 BOSS 一起）建一支原版队伍、关闭友军伤害"—— 只借原版既有的友军判定，
 * 不另造补丁。这样 {@code HurtByTargetGoal}、{@code NearestAttackableTargetGoal} 两条路径同时被覆盖
 * （两者都经 {@code TargetingConditions}），且队伍信息随 {@code scoreboard.dat} 落盘，
 * 区块卸载/读档后依然有效（召唤物的归属标记也一样会落盘，见 {@link #OWNER_TAG_PREFIX}）。
 * <p>
 * <b>为什么不污染玩家队伍与其它模组</b>：队伍名恒为 {@link #TEAM_NAME_PREFIX} + 召唤者 UUID 前 8 位
 * （模组专属前缀 + 唯一后缀，最长 12 字符，未越过原版 16 字符上限）；本类只写"自己按该名字找到/新建的"
 * 那一支队伍，且只对自己加入的成员做增删 —— 玩家队伍、其它模组的队伍既不加入也不改属性，
 * 属性只在新队建立那一刻设一次（已存在同名队时原样复用，不覆盖别人的设置）。整支队伍在分队回收时一并解散
 * （见 {@link #dismissAll(AgaitolosEntity)}），不在存档里留长期对象。
 * <p>
 * <b>残余项（诚实记录）</b>：原版 {@code Projectile#canHitEntity} 不查队伍，故<b>友军之间的弹体伤害依然会发生</b>；
 * 只是它再也不会引发报复/互殴（那正是"内斗"的可见形态）。BOSS 自己那发头已在
 * {@code AgaitolosWitherSkull#canHitEntity} 里按归属标记排除了召唤物（可自选，故顺手做掉），
 * 原版弓箭不受我们控制，若要彻底免伤需给召唤物换自定义箭矢（本轮不做）。
 * <b>⚠ 将来若给 BOSS 加"误伤召唤物"的招（环形 AoE、牵引、自爆等），必须回到这里加闸</b>。
 * <p>
 * <b>清场口径（2026-09-21 补，修"技能结束召唤物不消失"）</b>：归还给 {@link #dismissAll(AgaitolosEntity)}，
 * 由 {@code AgaitolosEntity} 在四个出口调用（技能自然结束 / 蓄力被打断 / 复活或阶段推进 / 死亡或实体移除），
 * 具体口径与理由见该方法 javadoc。分队不存在"技能结束后继续留在场上"的形态。
 */
public final class AgaitolosMinionSkill {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /** 近战召唤数：5（规格明确）。待调手感值 / P8 转配置项 */
    public static final int MELEE_COUNT = 5;

    /** 远程召唤数：5（规格明确）。待调手感值 / P8 转配置项 */
    public static final int RANGED_COUNT = 5;

    /**
     * 近战召唤环半径（格）：4.0。
     * <p>取值依据：① 与 BOSS 自身横扫半径（{@code AgaitolosDiveSweepSkill.SWEEP_RADIUS} = 4.0）同量级，
     * 召唤物不会穿进 BOSS 模型里；② 5 只均分圆周时相邻弧长 = 2π×4/5 ≈ 5.0 格，
     * 远大于凋零骷髅 0.7 的碰撞宽度，落位不互相挤压。待调手感值 / P8 转配置项
     */
    public static final double RING_RADIUS = 4.0D;

    /**
     * 远程召唤环半径（格）：6.0 —— 与近战环径向差 2 格，形成前后两层（"前后错开"）。
     * <p>外环在 {@link #summon(AgaitolosEntity)} 里再额外转半个角步，故 10 只中任意两只都不会叠在同一落点。
     * 待调手感值 / P8 转配置项
     */
    public static final double RANGED_RING_RADIUS = 6.0D;

    /**
     * 召唤点相对 BOSS 脚部的竖直偏移（格）：0，即直接取 BOSS 当前高度。
     * <p>不做贴地探测的理由：凋零骷髅是地面生物，而本 BOSS 常态悬停高度只有
     * {@code AgaitolosMoveControl.HOVER_HEIGHT}（2 格），低于原版 3 格摔落伤害阈值，
     * 落体自然着地且不吃摔落伤害。待调手感值 / P8 转配置项
     */
    public static final double SPAWN_VERTICAL_OFFSET = 0.0D;

    /** 最大生命加成<b>基准</b>（n = 1 时）：+20（原版凋零骷髅 20 ⇒ 40）。随在场玩家数按 {@link AgaitolosScaling} 放大。待调手感值 / P8 转配置项 */
    public static final double BONUS_MAX_HEALTH = 20.0D;

    /** 攻击力加成<b>基准</b>（n = 1 时）：+4（原版凋零骷髅 4.0 ⇒ 8.0，约等于铁剑一击）。随在场玩家数同比例放大。待调手感值 / P8 转配置项 */
    public static final double BONUS_ATTACK_DAMAGE = 4.0D;

    /** 移动速度加成：+0.05（原版 0.25 ⇒ 0.30，追击速度 +20%）。<b>不</b>随人数放大（规格只要求血量与伤害）。待调手感值 / P8 转配置项 */
    public static final double BONUS_MOVEMENT_SPEED = 0.05D;

    /**
     * 「召唤物是否全部阵亡」的搜索半径（格）：64。
     * <p>刻意<b>不</b>用 20（下一轮吸血结算的规格半径）：召唤物会追着玩家跑出很远，
     * 若沿用 20 格，追出去的召唤物会被误判成"已阵亡"、蓄力被提前打断。
     * 64 也 ≥ 召唤物的实际追击上限（其 FOLLOW_RANGE 远小于此），不会漏判。待调手感值 / P8 转配置项
     */
    public static final double ALIVE_CHECK_RADIUS = 64.0D;

    /**
     * 归属标记的前缀（完整标记 = {@code "AgaitolosMinionOwner:<召唤者UUID>"}），写在<b>凋零骷髅自己的
     * 原版实体标签</b>上（{@code Entity#addTag(String)} / {@code Entity#getTags()}）。
     * <p><b>为什么不用 Forge 的 {@code Entity#getPersistentData()}</b>：那是 Forge 补丁进 {@code Entity} 的
     * API，实测只在 forge 侧 jar 里存在；本类位于 {@code common}（Architectury 的 common 编译面 = 原版 +
     * Architectury API），引用它会直接编译失败。原版实体标签是两端都有的等价物，且同样会落盘
     * （{@code Entity#saveWithoutId} 写 {@code "Tags"}、{@code Entity#load} 读回），
     * 故区块卸载/读档后归属不丢。
     * <p><b>为什么用标记而不是新建 EntityType</b>：新建一整套自定义实体意味着实体注册 + 属性 + 渲染器 +
     * 模型贴图 + 语言键等一长串落地项；而本招要的只是"给原版凋零骷髅换个归属"，直接用原版
     * {@link WitherSkeleton} 就能白拿它的 AI / 掉落 / 渲染 / 音效，再打一个标记即可满足
     * "吸血只吸自己召唤的、不吸自然生成的凋零骷髅"。
     * <p>⚠ 已知副作用：实体标签是原版 {@code /tag} 命令的操作对象，有 OP 权限的玩家理论上能给自然生成的
     * 凋零骷髅补一条同样的标记，把它伪装成召唤物。本轮只影响"是否算本 BOSS 分队"，不产生额外收益，
     * 故不额外设防。
     */
    public static final String OWNER_TAG_PREFIX = "AgaitolosMinionOwner";

    /**
     * 分队队伍的<b>名字前缀</b>（完整队名 = 前缀 + 召唤者 UUID 前 8 位十六进制，例如 {@code agt_1a2b3c4d}）。
     * <p>
     * 为什么不把完整 UUID 写进队名：原版队伍名上限 16 字符（{@code Scoreboard} 的合法名字集合），
     * 而 {@code "agt_" + 32 位 UUID} = 36 字符。取前 8 位已足够唯一（碰撞概率 1/4.3e9），
     * 且前缀 {@code agt_} 明确标出"这是本模组建的队"，人类看 {@code /team list} 也一眼能分辨。
     * <p>取名刻意不带 {@code akaishi} 全名：12 字符的队名比 20 字符更容易在日志/命令里读。
     */
    public static final String TEAM_NAME_PREFIX = "agt_";

    /**
     * 原版凋零骷髅的攻击力基础值：4.0。
     * <p>该值由 {@code WitherSkeleton#finalizeSpawn} 补设，而本招用 {@code new WitherSkeleton(...)}
     * 直接生成、<b>不走</b> finalizeSpawn，故必须自己把基础值补上，否则召唤物的基础攻击力只有 2.0
     * （{@code Monster.createMonsterAttributes} 的默认值），比自然生成的同类还弱。
     */
    private static final double VANILLA_ATTACK_DAMAGE = 4.0D;

    // 属性修饰符 UUID：固定值即可——transient 修饰符挂在"每个实体自己的 AttributeInstance"上，
    // 不同召唤物之间不共享实例，故不存在 UUID 碰撞。（同一实例上重复添加同 UUID 才会抛异常）
    private static final UUID UUID_MAX_HEALTH = UUID.fromString("5a7e6c1e-1f3b-4d2a-9c60-7b1a2f4d8e01");
    private static final UUID UUID_ATTACK_DAMAGE = UUID.fromString("5a7e6c1e-1f3b-4d2a-9c60-7b1a2f4d8e02");
    private static final UUID UUID_MOVEMENT_SPEED = UUID.fromString("5a7e6c1e-1f3b-4d2a-9c60-7b1a2f4d8e03");

    private AgaitolosMinionSkill() {
    }

    // ---------------------------------------------------------------- 召唤

    /**
     * 铺开整支凋零骷髅分队（仅服务端；由 {@code AgaitolosEntity#startCharge} 起手时调用）。
     * <p>
     * 阵型：近战占内环、远程占外环，外环再错开半个角步，使 10 只形成"两层交错"的包围圈
     * （即规格要的"前后错开，避免叠在一起"）。
     * <p>所有召唤物共享 BOSS 当前目标，避免小怪到场后站在原地发愣。
     * <p>另有三件附带事：① 起手前先 {@link #dismissAll(AgaitolosEntity)} 清掉上一轮遗留（保证场上只有一支分队）；
     * ② 每只都打归属标记、并加入本 BOSS 的分队队伍（友军保护的唯一实现，见类注释）；
     * ③ 装备/属性/标记/入队全部就位后<b>才</b> {@code addFreshEntity}，客户端首帧看到的就是成品。
     */
    public static void summon(AgaitolosEntity boss) {
        if (boss.level().isClientSide()) {
            // 仅服务端生成：两端各自生成会刷出双份，且客户端实体随即被服务端同步覆盖
            return;
        }
        Level level = boss.level();
        String ownerId = boss.getUUID().toString();
        LivingEntity sharedTarget = boss.getTarget();
        // 起手前先清掉上一轮的遗留：正常路径下技能结束时就已整队回收（见 AgaitolosEntity 的
        // endChargeCompleted/endChargeInterrupted/enterRespawn/setRemoved），这里只兜"旧存档或异常路径
        // 留下的孤儿"—— 保证场上永远只有一支分队，这是"召唤物越打越多"的根治点
        dismissAll(boss);
        // 随在场玩家数增强（§0 第 71 行 / §1 第 11 条）：人数取 BOSS 侧已定案的 n（召唤时 + 每次进阶段
        // 各重算一次，见 AgaitolosEntity#applyPlayerCountScaling）——本招<b>不</b>自己再数一遍人，
        // 否则同一场战斗里"血量按 3 人、小怪按 2 人"这种分叉必然出现。
        // 数量按 n 提升（每多 1 人各 +1，硬上限见 AgaitolosScaling），属性按 n 同比例放大（+30%/人）。
        // n = 1 时数量与属性<b>逐位等于</b>改动前（5 / 5 与 +20 血 / +4 攻）。
        int players = boss.getScaledPlayerCount();
        double attributeScale = AgaitolosScaling.minionAttributeScale(players);
        int meleeCount = AgaitolosScaling.scaledMinionCount(MELEE_COUNT,
                AgaitolosScaling.MINION_EXTRA_MELEE_PER_PLAYER, AgaitolosScaling.MINION_MAX_MELEE, players);
        int rangedCount = AgaitolosScaling.scaledMinionCount(RANGED_COUNT,
                AgaitolosScaling.MINION_EXTRA_RANGED_PER_PLAYER, AgaitolosScaling.MINION_MAX_RANGED, players);
        summonRing(boss, level, ownerId, sharedTarget, meleeCount, RING_RADIUS, 0.0D, false, attributeScale);
        // 外环错开半个角步：角步必须按<b>本轮的</b>远程数算，否则人数变化后外环会与内环对齐（"前后错开"失效）
        summonRing(boss, level, ownerId, sharedTarget, rangedCount, RANGED_RING_RADIUS,
                Math.PI / rangedCount, true, attributeScale);
    }

    /**
     * 在一条环上均分生成 {@code count} 只凋零骷髅。
     *
     * @param angleOffset    起始角（弧度）：外环传半个角步，即可与内环交叉排布
     * @param ranged         true = 主手给弓（自动切远程 Goal），false = 主手给剑
     * @param attributeScale 属性加成倍数（按在场玩家数；n = 1 时为 1.0）
     */
    private static void summonRing(AgaitolosEntity boss, Level level, String ownerId, LivingEntity sharedTarget,
                                   int count, double radius, double angleOffset, boolean ranged, double attributeScale) {
        for (int i = 0; i < count; ++i) {
            double angle = angleOffset + Math.PI * 2.0D * i / count;
            double x = boss.getX() + Math.cos(angle) * radius;
            double z = boss.getZ() + Math.sin(angle) * radius;
            // 朝向圆心（BOSS）：新构造的实体 yaw 默认为 0（全部朝南），不设的话 10 只整齐朝同一方向很出戏
            float yaw = (float) Math.toDegrees(Mth.atan2(-Math.sin(angle), -Math.cos(angle))) - 90.0F;

            // 自定义子类只是给原版凋零骷髅挂一条"主人没了我自散"的看门狗（见 AgaitolosMinion 的类注释）；
            // 实体类型仍是原版 WITHER_SKELETON，故 AI / 掉落 / 渲染 / 音效一律照旧
            AgaitolosMinion minion = new AgaitolosMinion(EntityType.WITHER_SKELETON, level);
            minion.moveTo(x, boss.getY() + SPAWN_VERTICAL_OFFSET, z, yaw, 0.0F);
            minion.setYHeadRot(yaw);
            equip(minion, ranged);
            buff(minion, attributeScale);
            markOwner(minion, ownerId);
            // 入队必须在 addFreshEntity 之前：目标选择器从实体进入世界的第一 tick 就会跑，
            // 迟一 tick 入队就存在"两只召唤物已经在互锁目标"的窗口（友军保护的意义就没了）
            joinSquad(boss, minion);
            // 主人 UUID 同理要在入世界之前写好：看门狗在首 tick 就会查（见 AgaitolosMinion#aiStep）
            minion.setOwner(boss.getUUID());
            // 召唤物不得自然清除：否则分队会在玩家视线外悄悄消失，蓄力变成"招完就散"
            minion.setPersistenceRequired();
            if (sharedTarget != null && sharedTarget.isAlive()) {
                minion.setTarget(sharedTarget);
            }
            // 最后才入世界：装备/属性/标记全部就位后再生成，客户端首帧看到的就是成品
            level.addFreshEntity(minion);
        }
    }

    /**
     * 给召唤物上装备。
     * <p><b>远程那 5 只"只要主手给弓"就够了</b> —— 这是 1.20.1 源码实测出的原版链路，无需手动补 Goal：
     * <pre>
     *   AbstractSkeleton#setItemSlot(slot, stack):
     *       super.setItemSlot(slot, stack);
     *       if (!this.level().isClientSide) { this.reassessWeaponGoal(); }   // ← 触发点在这里
     *
     *   AbstractSkeleton#reassessWeaponGoal():
     *       goalSelector.removeGoal(meleeGoal);
     *       goalSelector.removeGoal(bowGoal);
     *       itemstack = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, it -> it instanceof BowItem));
     *       if (itemstack.is(Items.BOW)) { bowGoal.setMinAttackInterval(...); addGoal(4, bowGoal); }
     *       else                        { addGoal(4, meleeGoal); }
     * </pre>
     * 即：<b>给骷髅主手放弓 → {@code setItemSlot} 覆写 → {@code reassessWeaponGoal} → 换上
     * {@code RangedBowAttackGoal}</b>。弓是无限箭（{@code AbstractSkeleton#performRangedAttack} 走
     * {@code getProjectile} → 缺箭时补 {@code Items.ARROW}），故不需要给弹药。
     * 近战那只同样必须走 {@code setItemSlot}：主手不是弓 ⇒ 落到 {@code meleeGoal} 分支，正好是近战。
     * <p>护甲（头盔 + 胸甲）的两个理由：① 规格要求"装备增强过"；
     * ② {@code AbstractSkeleton#aiStep} 白天会给自己点火，而"头盔格非空"即可免灼烧 ——
     * 本 BOSS 按设计在下界作战，但顺手把这条原版坑堵掉，免得日后场地改到主世界时召唤物集体自燃。
     * <p>近战选 {@code IRON_SWORD} 的理由：原版凋零骷髅默认石剑
     * （{@code WitherSkeleton#populateDefaultEquipmentSlots}），铁剑刚好高一级，是"增强过"的最低成本表达；
     * 不用钻石/下界合金，避免 5 只小怪的输出盖过 BOSS 本体。
     */
    private static void equip(WitherSkeleton minion, boolean ranged) {
        minion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ranged ? Items.BOW : Items.IRON_SWORD));
        minion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        minion.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
    }

    /**
     * 属性增强。三处都用 {@code Operation.ADDITION}，故上面三个 BONUS 常量的语义就是
     * "<b>相对原版凋零骷髅的加成量</b>"；血量与攻击再乘 {@code attributeScale}（在场玩家数缩放）。
     * <p>
     * <b>为什么用 {@code addPermanentModifier}</b>（2026-09-24 修正）：{@code AttributeInstance#save()}
     * <b>只</b>序列化 {@code permanentModifiers}，而这里原先用的是 {@code addTransientModifier} ——
     * 后果是区块一卸载重载（或服务器重启）后，召唤物退回"原版基础值 + 我们的装备"：
     * 血量掉回 20、攻击掉回 4，玩家会看到"同一批小怪，走远再回来就变弱了"。换成永久修饰符后加成随
     * 实体属性块落盘（原版 {@code Mob#finalizeSpawn} 的刷怪加成正是这么做的），跨区块重载/存档保留。
     *
     * @param attributeScale 属性倍数（按在场玩家数，n = 1 时为 1.0）；<b>不</b>作用于移动速度
     */
    private static void buff(WitherSkeleton minion, double attributeScale) {
        AttributeInstance attack = minion.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(VANILLA_ATTACK_DAMAGE);
        }
        applyBonus(minion, Attributes.MAX_HEALTH, UUID_MAX_HEALTH, "Agaitolos minion health",
                BONUS_MAX_HEALTH * attributeScale);
        applyBonus(minion, Attributes.ATTACK_DAMAGE, UUID_ATTACK_DAMAGE, "Agaitolos minion damage",
                BONUS_ATTACK_DAMAGE * attributeScale);
        applyBonus(minion, Attributes.MOVEMENT_SPEED, UUID_MOVEMENT_SPEED, "Agaitolos minion speed",
                BONUS_MOVEMENT_SPEED);
        // 抬过 MAX_HEALTH 之后必须补满，否则召唤物是"上限 40 却只有 20 血"的半血出场
        minion.setHealth(minion.getMaxHealth());
    }

    /** 单个属性的加成；属性缺失时静默跳过，不让一条缺失拖垮整次召唤 */
    private static void applyBonus(WitherSkeleton minion, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = minion.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        // 永久修饰符：会被 AttributeInstance#save() 序列化 ⇒ 跨区块重载/存档保留（见 buff 的说明）
        instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }

    /** 打归属标记：完整标记 = 前缀 + ":" + 召唤者 BOSS 的 UUID，见 {@link #OWNER_TAG_PREFIX} */
    private static void markOwner(WitherSkeleton minion, String ownerId) {
        minion.addTag(ownerTag(ownerId));
    }

    /** 归属标记的完整字符串。用 UUID 区分召唤者：场上若同时存在多个 BOSS，各自的结算只认自己的分队 */
    private static String ownerTag(String ownerId) {
        return OWNER_TAG_PREFIX + ":" + ownerId;
    }

    // ---------------------------------------------------------------- 归属查询

    /**
     * 筛出<b>本 BOSS 召唤的、且存活</b>的分队成员（仅服务端会返回结果）。
     * <p>
     * 两个调用点，都是"只认自己召唤的"语义：
     * <ol>
     *   <li>{@code AgaitolosEntity#tickChargeState} —— 判"小怪全部阵亡即取消蓄力"（用 {@link #ALIVE_CHECK_RADIUS}）；</li>
     *   <li>{@code AgaitolosReversalSkill#drainMinions} —— 吸血结算（规格："吸取周围小怪剩余血量"，用 20 格）；
     *       归属标记正是为了让这次结算只吸自己召唤的、不吸自然生成的凋零骷髅。</li>
     * </ol>
     * 回收（{@link #dismissAll(AgaitolosEntity)}）也以本方法的结果为准。
     * <p>筛选口径：包围盒粗筛 → 球半径二次筛 → 归属标记相等。BOSS 自身被显式排除。
     *
     * @param radius 搜索半径（格）；判"是否全灭"与整队回收请用 {@link #ALIVE_CHECK_RADIUS}，
     *               吸血结算按规格用 20
     */
    public static List<LivingEntity> findLivingMinions(AgaitolosEntity boss, double radius) {
        List<LivingEntity> minions = new ArrayList<>();
        if (boss.level().isClientSide()) {
            return minions;
        }
        String ownerTag = ownerTag(boss.getUUID().toString());
        double radiusSqr = radius * radius;
        for (LivingEntity candidate : boss.level().getEntitiesOfClass(LivingEntity.class,
                boss.getBoundingBox().inflate(radius))) {
            // 包围盒是方盒，按球半径再筛一遍；BOSS 自己不是召唤物
            if (candidate == boss || !candidate.isAlive() || candidate.distanceToSqr(boss) > radiusSqr) {
                continue;
            }
            // 自然生成的凋零骷髅没有这条标记，自然落选
            if (candidate.getTags().contains(ownerTag)) {
                minions.add(candidate);
            }
        }
        return minions;
    }

    /**
     * 只读判据：{@code candidate} 是否是 {@code owner}（本 BOSS）召唤的成员。
     * <p>
     * 供 {@code AgaitolosWitherSkull#canHitEntity} 使用：BOSS 自己那发凋零头不该打到自家召唤物
     * （原版 {@code Projectile#canHitEntity} 不查队伍，弹体会友伤，见类注释的"残余项"）。
     * 判据与 {@link #findLivingMinions} 同源（同一个归属标记），不另立第二套"谁是我的人"。
     *
     * @return owner 不是 {@link AgaitolosEntity}、或 candidate 无本 BOSS 的归属标记时为 false
     */
    public static boolean isMinionOf(Entity candidate, Entity owner) {
        return owner instanceof AgaitolosEntity boss && candidate != boss
                && candidate.getTags().contains(ownerTag(boss.getUUID().toString()));
    }

    // ---------------------------------------------------------------- 友军队伍（原版 Team）

    /**
     * 分队队名：{@link #TEAM_NAME_PREFIX} + 召唤者 UUID 前 8 位十六进制（长度 12 &le; 原版 16 字符上限）。
     * <p>用 UUID 而不是常量名：场上可能存在多个 BOSS，各自的分队必须彼此独立（A 队的召唤物不该是 B 队的友军）。
     */
    private static String teamName(AgaitolosEntity boss) {
        return TEAM_NAME_PREFIX + boss.getUUID().toString().substring(0, 8);
    }

    /**
     * 取得（必要时新建）本 BOSS 的分队队伍，属性只在新队建立时设一次。
     * <p>
     * <b>为什么关闭友军伤害</b>：与原版 {@code /team modify <队名> friendlyFire false} 同义，
     * 表达"这支队就是一支不会互相伤害的队伍"。其原版消费点是 {@code Player#canHarmPlayer(Player)}
     * （实测 1.20.1 字节码：两边都有队伍且同队时返回 {@code friendlyFire}），即它主要管玩家之间 ——
     * 对 mob，真正挡住"内斗"的是<b>队伍带来的"不可被选为目标"</b>（见类注释的字节码证据），
     * 这里设 false 是把意图写进数据、并覆盖任何将来走 {@code canHarmPlayer} 的路径。
     * <p><b>为什么已存在同名队时不再改属性</b>：不改别人的设置是纪律 —— 只有在"本类新建"的分支里
     * 才设置选项；同名队被复用（理论上不可能，见 {@link #TEAM_NAME_PREFIX}）时原样使用。
     * <p>不设置颜色（保持默认 {@code ChatFormatting.RESET}）：{@code ChatFormatting.RESET} 的颜色值为
     * {@code null}，而 {@code Entity#getTeamColor()} 对"无队伍"与"队伍颜色为 null"都返回 0xFFFFFF
     * （实测字节码）⇒ BOSS 蓄力时的发光描边仍是原版白色，不会因为入队而悄悄变色。
     *
     * @return 分队队伍；同名队已存在且创建失败时返回 null，调用方跳过入队
     */
    private static PlayerTeam squadTeam(AgaitolosEntity boss) {
        Scoreboard scoreboard = boss.level().getScoreboard();
        PlayerTeam existing = scoreboard.getPlayerTeam(teamName(boss));
        if (existing != null) {
            return existing;
        }
        PlayerTeam created = scoreboard.addPlayerTeam(teamName(boss));
        if (created != null) {
            created.setAllowFriendlyFire(false);
        }
        return created;
    }

    /**
     * 让 BOSS 与召唤物同处一队 —— <b>"召唤物不互选、也不打 BOSS"的唯一实现</b>。
     * <p>
     * <b>为什么要连 BOSS 一起入队</b>：保护是<b>双向</b>的。若只给召唤物建队，召唤物之间不互斗，
     * 但召唤物若因弹体友伤被 BOSS 的头擦中，BOSS 侧那条 {@code HurtByTargetGoal}（{@code registerGoals}
     * 里优先级 0）会反过来把它锁成目标、抬刀砍自己的分队。同队之后两边都进不了对方的候选目标。
     * <p>BOSS 被加入队伍<b>不影响</b>它对玩家的索敌：玩家不在本队里（本队只加这两个 UUID），
     * 而 {@code NearestAttackableTargetGoal<Player>} / {@code HurtByTargetGoal} 对玩家的判定与队伍无关。
     */
    private static void joinSquad(AgaitolosEntity boss, WitherSkeleton minion) {
        PlayerTeam team = squadTeam(boss);
        if (team == null) {
            // 队名被占且创建失败：宁可少一层友军保护也不抛异常（内斗的最坏后果是观感差，不是崩服）
            return;
        }
        Scoreboard scoreboard = boss.level().getScoreboard();
        // 非玩家实体的记分板名 = UUID 字符串（Entity#getScoreboardName），故队员名单里存的是 UUID
        scoreboard.addPlayerToTeam(boss.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(minion.getScoreboardName(), team);
    }

    // ---------------------------------------------------------------- 整队回收（清场）

    /**
     * <b>一次性回收</b>本 BOSS 名下全部存活召唤物，并解散分队队伍（仅服务端）。
     * <p>
     * <b>清场口径（三个出口，全部由 {@code AgaitolosEntity} 调用）</b>：
     * <ol>
     *   <li><b>技能结束</b>（蓄力期满或被死亡/演出/承伤阈值/全灭打断）：立刻整队回收。
     *       期满时先跑吸血结算（{@code drainMinions} 会杀掉 20 格内的），本方法负责收尾
     *       —— 散落在 20 格外的、以及结算期间新追近的，一并清掉，不留"吸完还剩半支队伍"的形态。
     *       被打断时同样回收：玩家打断蓄力换来的应该是"这一招白放"，而不是"留下一整队小怪继续追我"。</li>
     *   <li><b>阶段推进 / 复活</b>：{@code enterRespawn} 里直接调一次。复活是"无敌 + 回满血 + 4s 演出"的
     *       场景边界，这段窗口里留在场上的小怪只会单方面打玩家（BOSS 无敌又不还手也不合理），
     *       且阶段推进必然先把蓄力打断（见 {@code tickChargeState} 的 ① 闸），这里是同一口径的显式化。</li>
     *   <li><b>死亡 / 实体移除</b>（{@code die} 与 {@code remove}）：BOSS 一死（或 {@code /kill}、
     *       换维度、被 {@code discard}）就整队回收，绝不在世界里留下"没有主人的小怪"。
     *       <b>区块卸载</b>那条路径 BOSS 侧挂不上钩子（不经过 {@code remove}，而 {@code setRemoved} 是 final），
     *       由召唤物自身的 {@code AgaitolosMinion}（"主人没了我自散"的看门狗）兜底。</li>
     * </ol>
     * <p>
     * <b>为什么用 {@code discard()} 而不是 {@code kill()}</b>：{@code kill()} 走完整死亡流程
     * （掉落表 / 经验 / 死亡音效），会把"技能的产物被技能收走"变成"玩家白捡一批战利品与经验"
     * —— 召唤物本来就是靠 BOSS 存在的一次性单位，回收不该产出战利品（这一点与
     * {@code AgaitolosReversalSkill#drainMinions} 刻意不同：那是规格写明的"吸取血量致死"，走 kill）。
     * <p><b>表现</b>：消散时在每只召唤物躯干处放一簇青蓝魂焰（{@link AgaitolosActionFx#minionDissolve}），
     * 复用既有粒子选型与识别色，不新增 {@code ParticleType}，也不需要制作新动画。
     * <p><b>幂等</b>：没有存活召唤物时只做一次"解散空队"（{@code getPlayerTeam} 为 null 时直接返回），
     * 故可以被多个出口重复调用而不产生副作用。
     */
    public static void dismissAll(AgaitolosEntity boss) {
        if (boss.level().isClientSide()) {
            // 仅服务端：客户端既不是召唤的发起方，也不该由它改世界（与 summon 同一分工）
            return;
        }
        for (LivingEntity minion : findLivingMinions(boss, ALIVE_CHECK_RADIUS)) {
            AgaitolosActionFx.minionDissolve(minion);
            minion.discard();
        }
        // 队伍随分队一起解散：队员名单里存的是 UUID 字符串，实体消失后这些条目不会自动清掉，
        // 留着只会让 scoreboard.dat 越积越多（每轮 11 条）。下次召唤会按同一名字重建。
        PlayerTeam team = boss.level().getScoreboard().getPlayerTeam(teamName(boss));
        if (team != null) {
            boss.level().getScoreboard().removePlayerTeam(team);
        }
    }
}

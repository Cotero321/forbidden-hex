package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的<b>技能音效表现层</b>（阶段三五招）：三重投掷三拍 / 高空投弹 / 镰扫出刀 / 劈击命中 / 天魔＊灾呓语。
 * <p>
 * <b>为什么与粒子分成两个类</b>（SRP）：两者的调参理由完全不同 —— 音效只调"选哪个事件、多大音量、多高音高"，
 * 粒子调"数量 / 扩散 / 形状"。且本类是 BOSS 侧<b>唯一的 {@code playSound} 出口</b>：
 * "哪一招在什么时候响、响多大范围"grep 本类一次看全。
 * <p>
 * <b>为什么只用原版事件</b>（唯一例外见 {@link #calamityApplied}）：新增自定义音效要配套
 * {@code .ogg} 素材 + {@code sounds.json} 条目 + 语言键一整套资源落地项（RULES §0 三维度），
 * 而原版 {@code SoundEvents} 里已有语义足够的同族音 —— 零资源义务。
 * <p>
 * <b>音量在本类读作"战斗半径"</b>：{@code ServerLevel#playSound(null, x, y, z, …)} 的下发半径是
 * {@code 16 × volume} 格（{@code volume ≤ 1} 时恒为 16）。故取值与招式射程对齐
 * （三重投掷 / 天魔＊灾 射程 24 格 ⇒ 1.5；投技①② 是贴身技 ⇒ 1.4 / 1.5），
 * 且<b>一律不超过 2.0</b>（32 格）—— 再往上就是"全图可闻"，会把"它在对你出招"变成"到处都在响"。
 * <p>只在服务端播放（与粒子同一口径）：客户端 {@code playSound} 只有本机听得到，多人局里是"主机独享音效"。
 * <p>所有数值均为<b>待调手感值</b>（P8 转配置项）。
 */
public final class AgaitolosPhaseThreeSounds {

    /** 音源类别：五招都是敌对生物的攻击音（玩家调"敌对生物"音量即可整体增减）。 */
    private static final SoundSource SOURCE = SoundSource.HOSTILE;

    // ---------------------------------------------------------------- 三重投掷（6 / 12 / 18t 三拍）

    /**
     * 三拍的音高：1.0 / 1.15 / 1.3（每拍升一档）。
     * <p>三发用的是<b>同一个</b>事件（同一个凋零头），靠音高递增把它们"分成三拍"听出来；
     * 若三拍同高会糊成一声长响，"三连"就听不出来了。待调手感值
     */
    private static final float[] TRIPLE_THROW_PITCHES = {1.0F, 1.15F, 1.3F};

    /** 三重投掷音量：1.5 ⇒ 24 格（与射程同值：射得到的地方就听得到）。待调手感值 */
    private static final float TRIPLE_THROW_VOLUME = 1.5F;

    // ---------------------------------------------------------------- 饱和轰炸（10t 首弹，之后每 20t）

    /** 饱和轰炸音量：2.0 ⇒ 32 格（比其它招更远一档：高空轰击要让"没被瞄准的人"也知道它在投弹）。待调手感值 */
    private static final float BOMBARD_VOLUME = 2.0F;

    /** 饱和轰炸音高：0.8（压暗 = 更闷更重，与三重投掷的清亮三拍区分开）。待调手感值 */
    private static final float BOMBARD_PITCH = 0.8F;

    /** 饱和轰炸落地音量：1.2 ⇒ 19 格（比离手音近一档：只有近处才该觉得"砸在身边"）。待调手感值 */
    private static final float BOMBARD_IMPACT_VOLUME = 1.2F;

    /** 饱和轰炸落地音高：1.0（原速）。待调手感值 */
    private static final float BOMBARD_IMPACT_PITCH = 1.0F;

    // ---------------------------------------------------------------- 投技①（出刀在第 10t）

    /** 投技①音量：1.4 ⇒ 22 格。待调手感值 */
    private static final float GRAB_SWEEP_VOLUME = 1.4F;

    /** 投技①音高：0.85（比玩家自己的横扫更低更长：这是镰刀，不是剑）。待调手感值 */
    private static final float GRAB_SWEEP_PITCH = 0.85F;

    // ---------------------------------------------------------------- 投技②（劈击在第 12t）

    /** 投技②音量：1.5 ⇒ 24 格。待调手感值 */
    private static final float GRAB_SMASH_VOLUME = 1.5F;

    /** 投技②音高：0.8（铁砧再压低一档 = 巨物砸地）。待调手感值 */
    private static final float GRAB_SMASH_PITCH = 0.8F;

    // ---------------------------------------------------------------- 天魔＊灾（改写落地在第 10t）

    /** 天魔＊灾音量：1.2 ⇒ 19 格（呓语是给被污染者的，不必传太远）。待调手感值 */
    private static final float CALAMITY_VOLUME = 1.2F;

    /** 天魔＊灾音高：1.0（素材原速，不做变调）。待调手感值 */
    private static final float CALAMITY_PITCH = 1.0F;

    /** 音源相对脚部的抬升（格）：1.0（取躯干/口部附近，而不是脚下）。待调手感值 */
    private static final double SOURCE_HEIGHT = 1.0D;

    private AgaitolosPhaseThreeSounds() {
    }

    // ---------------------------------------------------------------- 语义入口（五招）

    /**
     * 三重投掷的一拍：原版凋零射击音（{@code WITHER_SHOOT}），音高随拍递增。
     * <p><b>为什么选它</b>：投出去的就是凋零头，原版这个事件正是"凋零头离膛"的唯一同义音；
     * 与弹体的 {@code shoot} 同刻（起手后 6 / 12 / 18t），玩家听到三声就知道来了三发。
     * <p>挂在 {@code AgaitolosPhaseThreeState#tickTripleThrow} 的出手分支。
     *
     * @param beatIndex 第几拍（0/1/2）；越界时按第 0 拍取值，绝不静默不响
     */
    public static void tripleThrowBeat(AgaitolosEntity boss, int beatIndex) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        float pitch = TRIPLE_THROW_PITCHES[Math.max(0, Math.min(TRIPLE_THROW_PITCHES.length - 1, beatIndex))];
        play(level, boss, SoundEvents.WITHER_SHOOT, TRIPLE_THROW_VOLUME, pitch);
    }

    /**
     * 饱和轰炸的一发：原版恶魂射击音（{@code GHAST_SHOOT}），压暗到 0.8。
     * <p><b>为什么不用三重投掷那一声</b>：两招投的是同一种弹体，若用同一事件同一音高就分不出"三连"与"轰炸"；
     * 恶魂射击是原版最"重"的远程发射音（低频尖啸 + 拖尾），配 32 格半径正是"从天而降的炮击"。
     * <p>挂在 {@code AgaitolosPhaseThreeState#tickBombard} 的投弹分支。
     */
    public static void bombardShot(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        play(level, boss, SoundEvents.GHAST_SHOOT, BOMBARD_VOLUME, BOMBARD_PITCH);
    }

    /**
     * 饱和轰炸的<b>落地冲击</b>：原版爆炸音（{@code GENERIC_EXPLODE}），音量压到 1.2。
     * <p><b>为什么这里必须有音</b>：本 BOSS 的凋零头刻意不生成爆炸（不破坏地形），
     * 原版"炸到东西"那一套爆炸声/粒子在本项目里根本不存在 —— 落地若不自己给音，弹体砸中人是静音的。
     * <p><b>为什么选爆炸音而不是别的</b>：这一发的伤害类型本来就是
     * {@code DamageTypes.EXPLOSION}（见 {@code AgaitolosWitherSkull#onHitEntity}），
     * 音画与结算口径同源；音量 1.2（19 格）刻意小于离手音（32 格）——
     * 飞过头顶与砸在脚边应该是两种听感。
     * <p>由弹体在命中那一刻调用（{@code AgaitolosWitherSkull#onHitEntity} 的 ④ 段，与落地粒子同刻）。
     *
     * @param at 命中点（弹体自身坐标）
     */
    public static void bombardImpact(ServerLevel level, Vec3 at) {
        if (level == null || at == null) {
            return;
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SOURCE,
                BOMBARD_IMPACT_VOLUME, BOMBARD_IMPACT_PITCH);
    }

    /**
     * 投技①的出刀：原版横扫攻击音（{@code PLAYER_ATTACK_SWEEP}），压低到 0.85。
     * <p><b>为什么选它</b>：它是原版唯一的"横向挥扫"音（横扫之刃同款），与"镰刀低扫把玩家扫倒"
     * 这一动作逐字同义；压低音高是为了让这一刀听起来比玩家的剑更长、更沉。
     * <p><b>与命中无关</b>：被盾挡下 / 目标恰好躲开时同样播（刀已经挥出去了，与"被格挡也算挥出去"同一取舍）；
     * 是否出冲击环才由 {@code AgaitolosPhaseThreeFx#grabSweepStrike} 的 {@code struck} 决定
     * —— 声音交给耳朵判"挥没挥"，粒子交给眼睛判"打没打到"。
     * <p>挂在 {@code AgaitolosPhaseThreeState#tickGrab} 的出刀分支。
     */
    public static void grabSweepStrike(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        play(level, boss, SoundEvents.PLAYER_ATTACK_SWEEP, GRAB_SWEEP_VOLUME, GRAB_SWEEP_PITCH);
    }

    /**
     * 投技②的劈击命中：原版铁砧落地音（{@code ANVIL_LAND}），压低到 0.8。
     * <p><b>为什么选它</b>：这一记是"20% 最大生命的真实伤害"重击，需要原版最像"巨物砸地"的一击
     * （铁砧落地 = 沉闷巨响 + 尾音）；爆炸音（{@code GENERIC_EXPLODE}）会让人误以为有溅射，
     * 而本招是单体真伤。
     * <p>只在<b>真的打中</b>时播（{@code smashStrike} 返回 true）：空挥/目标跑掉时没有砸地的对象。
     * <p>挂在 {@code tickSmash} 的结算分支。
     *
     * @param target 被劈中的目标（取它的位置当音源 —— 声音从"被打的人身上"发出，距离感才对）
     */
    public static void grabSmashStrike(AgaitolosEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        play(level, target != null ? target : boss, SoundEvents.ANVIL_LAND, GRAB_SMASH_VOLUME, GRAB_SMASH_PITCH);
    }

    /**
     * 天魔＊灾的改写落地：<b>复用项目既有的</b> {@code unnameable_whisper}（{@code VOID_*}/{@code UNNAMEABLE_*}}
     * 家族的耳中呓语，8s 一次性低语）。
     * <p><b>为什么它是本类唯一的非原版事件</b>：规格要求的是"施加不可名状"，
     * 而项目里已有语义逐字对应的音效事件（"不可名状"减益本身的耳中呓语），
     * <b>直接复用不新增任何资源</b>（{@code .ogg} 与 {@code sounds.json} 条目都在），
     * 比硬找原版音更贴切；音源取被污染者本人 —— 呓语是"在他耳边响"，不是从天上来的。
     * <p>只在真的施放成功时播（{@code apply} 返回 true）：目标中途跑掉时不响，避免"没中也有音"。
     * <p>挂在 {@code tickCalamity} 的改写分支。
     */
    public static void calamityApplied(AgaitolosEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity source = target != null ? target : boss;
        play(level, source, AkaishiForbiddenSounds.UNNAMEABLE_WHISPER.get(), CALAMITY_VOLUME, CALAMITY_PITCH);
    }

    // ---------------------------------------------------------------- 共用实现

    /**
     * 在实体躯干高度播放一次音效（{@code except = null} ⇒ 向半径内的所有玩家广播）。
     * <p>取躯干而不是脚部：音源贴地会让人误判方向（原版音效的下发位置只影响"谁听得到"与客户端音像定位，
     * 但抬高一点距离感更自然，与粒子一律取躯干的口径也一致）。
     */
    private static void play(ServerLevel level, LivingEntity at, SoundEvent event, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY() + SOURCE_HEIGHT, at.getZ(), event, SOURCE, volume, pitch);
    }
}

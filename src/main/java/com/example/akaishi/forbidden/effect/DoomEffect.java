package com.example.akaishi.forbidden.effect;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosCombat;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 凋亡：阿盖托洛丝【下界本源】在<b>阶段三</b>替换凋零施加的上位减益
 * （设计文档 §0 阶段三「BOSS 施加的凋零 buff 更改为凋亡」/ §6 已拍板第 3 条）。
 * <p>
 * <b>语义三件（设计记忆的口径）</b>：
 * <ol>
 *   <li><b>掉血高于凋零 III</b>：每 {@link #damageInterval(int)} tick 造成 {@link #DAMAGE_PER_HIT} 点凋亡伤害。
 *       放大器 2（= 游戏内显示的「凋亡 III」）时 8 tick 一跳 = <b>2.5 点/秒</b>；
 *       原版凋零 III 是 {@code 40 >> 2 = 10} tick 一跳 = 2.0 点/秒 ⇒ 每一档放大器都快 25%。</li>
 *   <li><b>无视抗性 / 免疫</b>：伤害走 {@code forbidden_hex:doom} 伤害类型（数据层已登记进
 *       {@code bypasses_armor} / {@code bypasses_resistance} / {@code bypasses_enchantments}）
 *       ⇒ 护甲点数、抗性提升、保护类附魔一律不减伤；且本效果是独立注册的自定义效果、
 *       <b>与 {@code minecraft:wither} 无关</b> ⇒ 目标哪怕"免疫凋零"（亡灵、凋零骷髅、
 *       带凋零免疫的第三方生物）也照常掉血。</li>
 *   <li><b>持续期间降低治疗</b>：治疗量乘 {@link #HEAL_MULTIPLIER}（= 0.4，即降低 60%）。
 *       common 层没有任何"治疗"钩子（原版 {@code LivingEntity#heal} 不派发平台无关事件，
 *       Architectury 的 {@code EntityEvent} 也没有对应的治疗事件），故本类只提供系数，
 *       真正的消费在 forge 侧 {@code AgaitolosDoomHandler}（Forge 的 {@code LivingHealEvent}）。</li>
 * </ol>
 * <p>
 * <b>为什么不复用 {@code forbidden_hex:true_damage}（它已经有同样的三条"无视"标签）</b>：
 * 因为该类型还额外进了 <b>{@code bypasses_cooldown}</b>。凋亡是<b>周期性</b>掉血（8 tick 一跳），
 * 若每跳都从无敌帧里穿过去，就会每 8 tick 把目标的 {@code invulnerableTime} 刷成 20 ——
 * 目标会永久停留在「受击无敌」状态，此后 BOSS 的普攻/技能全部落进原版
 * {@code LivingEntity#hurt} 的"只结算增量伤害"分支：伤害被减去上一跳的 1 点、<b>且不再播受击动画</b>
 * （{@code flag = false}）。这正是 {@code AgaitolosEntity#hurt} 里"每次落地都要 {@code applied == true}"
 * 所依赖的东西（受击动作、蓄力承伤累计都挂在它上面）。故另立 {@code forbidden_hex:doom}：
 * 三条"无视"照旧，<b>刻意不进 {@code bypasses_cooldown}</b>，让凋亡掉血与原版凋零一样走完整的受击管线
 * （无敌帧内被吞、无敌帧外正常落地并给一次受击闪烁）—— 这是与原版凋零一致的取舍，不是缺口。
 * <p>
 * <b>可否被牛奶解除</b>：可以。本类不改任何"可清除项"逻辑，沿用原版 {@link MobEffectCategory#HARMFUL}
 * 效果的默认语义（牛奶桶 / {@code /effect clear} 均可解除），与原版凋零一致；
 * 若将来要做成"不可驱散"，改动点只有施加处（{@code MobEffectInstance#setCurativeItems}），不在本类。
 * <p>
 * 1.20.1 效果图标无需代码绑定：客户端按注册 ID 自动加载 {@code assets/forbidden_hex/textures/mob_effect/doom.png}
 * （18×18，与 decay / unnameable 同规格）。
 */
public class DoomEffect extends MobEffect {

    /**
     * 掉血节奏的<b>基准间隔</b>（tick）：32。
     * <p>取 2 的幂是为了 amplifiers 走位移；32 = 原版凋零的 40 × 0.8 ⇒ 每一档放大器都比凋零快 25%，
     * 即"伤害高于凋零 III"这条规格在<b>所有等级</b>上同时成立，不是只在 III 级成立的特例。
     */
    private static final int DAMAGE_INTERVAL_BASE_TICKS = 32;

    /** 放大器上限：超过它一律当该值处理（防"用指令刷个 1000 级"把间隔位移成 0） */
    private static final int MAX_AMPLIFIER = 8;

    /** 单次凋亡伤害：1 点（与原版凋零同级"每次 1 点"，强度靠更密的节奏拉开）。待调手感值 / P8 转配置项 */
    private static final float DAMAGE_PER_HIT = 1.0F;

    /**
     * 治疗系数：0.4（<b>降低 60%</b>）。<b>待调手感值 / P8 转配置项</b>
     * <p>取值依据：原版凋零的语义就是"治疗减半"（×0.5），凋亡按"它的上位替代"再降一档 ⇒ ×0.4；
     * 且 60% 与 BOSS 自身的减伤同数（{@code AgaitolosDamageRules.DAMAGE_MULTIPLIER}），
     * 形成"它只吃你 40% 的伤害，你也只能回 40% 的血"的对位读数。
     * 若要回调：0.5 = 与凋零同强度，0.25 = 明显更狠。
     */
    public static final float HEAL_MULTIPLIER = 0.4F;

    public DoomEffect() {
        // 0x533577：与交付的凋亡图标（紫色系）主色一致 —— 仅用于效果粒子着色。
        // 2026-09-21 由 0x1F5A55（青灰绿）改为紫：原值是 BOSS 识别色的暗化版，与图标观感脱节
        // （图标已是紫色系，粒子却发暗青），实机表现为"图标一个色、粒子另一个色"。
        // 与衰变（0x6C4696）同属紫区但更暗更沉，仍能区分。
        super(MobEffectCategory.HARMFUL, 0x533577);
    }

    /**
     * 掉血间隔（tick）：{@code 32 >> 等级}，下限 1。
     * <p>下限 1 是<b>必须</b>的：{@code duration % 0} 会抛 {@link ArithmeticException}；
     * 负等级（指令可以给）也会因 Java 位移按 31 取模而算出古怪的小值，故一并夹到 {@code [0, MAX_AMPLIFIER]}。
     * <table border="1">
     *   <caption>节拍对照（原版凋零 = {@code 40 >> 等级}）</caption>
     *   <tr><th>放大器</th><th>凋亡间隔</th><th>凋亡每秒伤害</th><th>凋零间隔</th><th>凋零每秒伤害</th></tr>
     *   <tr><td>0</td><td>32t</td><td>0.63</td><td>40t</td><td>0.50</td></tr>
     *   <tr><td>1</td><td>16t</td><td>1.25</td><td>20t</td><td>1.00</td></tr>
     *   <tr><td>2（BOSS 施加的档位）</td><td><b>8t</b></td><td><b>2.50</b></td><td>10t</td><td>2.00</td></tr>
     *   <tr><td>3</td><td>4t</td><td>5.00</td><td>5t</td><td>4.00</td></tr>
     * </table>
     */
    static int damageInterval(int amplifier) {
        int clamped = Math.min(Math.max(amplifier, 0), MAX_AMPLIFIER);
        return Math.max(1, DAMAGE_INTERVAL_BASE_TICKS >> clamped);
    }

    /** 周期逻辑：每 {@link #damageInterval(int)} tick 判一次（与凋零同款，只是基准更短） */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % damageInterval(amplifier) == 0;
    }

    /**
     * 凋亡掉血：只做伤害，不做别的（治疗抑制在平台层，见类注释）。
     * <p>服务端限定：{@code tickEffects} 两端都会跑，不判会让客户端也多掉一份血（本地预测与权威值打架）。
     * <p>伤害源不带 causer：1.20.1 的 {@code MobEffectInstance} <b>不保存施加者</b>
     * （没有 {@code getCaster()}），效果自己跳血时拿不到归属实体 ⇒ 死亡消息走无攻击者那条键
     * {@code death.attack.forbidden_hex.doom}（需要归属时得由招式侧结算，不在本轮）。
     */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return;
        }
        entity.hurt(AgaitolosCombat.doom(entity.level()), DAMAGE_PER_HIT);
    }
}

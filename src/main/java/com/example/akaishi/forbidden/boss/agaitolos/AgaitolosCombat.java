package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.effect.ModDamageTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 阿盖托洛丝【下界本源】的<b>输出</b>伤害源工厂（BOSS 打人）。
 * <p>
 * 与 {@link AgaitolosDamageRules} 方向相反，故刻意分成两个类：
 * 后者管 BOSS「挨打」的承伤口径（冷却 / 免伤 / 复活无敌），本类只负责 BOSS「打人」时
 * 构造自定义伤害源。两者混在一起会让"谁是攻方、谁是守方"彻底不可读，也违反单一职责。
 */
public final class AgaitolosCombat {

    /**
     * 自定义伤害类型键：真实伤害（数据层见 {@code data/forbidden_hex/damage_type/true_damage.json}）。
     * <p>
     * 标签口径（{@code data/minecraft/tags/damage_type/}）：
     * <ul>
     *   <li>{@code bypasses_armor} —— 无视护甲</li>
     *   <li>{@code bypasses_resistance} —— 无视抗性提升</li>
     *   <li>{@code bypasses_enchantments} —— 无视保护类附魔</li>
     *   <li>{@code bypasses_cooldown} —— <b>必须</b>。普攻是"物理一击 + 紧接的真实伤害"两段，
     *       物理那一下会把目标的 {@code invulnerableTime} 置为 20；原版 {@code LivingEntity#hurt}
     *       在 {@code invulnerableTime > 10} 且伤害源不在该标签内时走"只结算增量伤害"分支
     *       （{@code amount <= lastHurt} 直接返回 0），导致 4 点真实伤害整段被吞掉。
     *       打上该标签后才会走完整结算分支。</li>
     * </ul>
     */
    public static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "true_damage"));

    /**
     * 自定义伤害类型键：俯冲镰扫（数据层见 {@code data/forbidden_hex/damage_type/scythe_sweep.json}）。
     * <p>
     * 标签口径（{@code data/minecraft/tags/damage_type/}）：
     * <ul>
     *   <li>{@code bypasses_armor} —— <b>必须</b>。本技能的护甲已在
     *       {@code AgaitolosDiveSweepSkill} 内按「护甲 × 0.7、韧性 × 0.7」<b>预折算过一次</b>，
     *       若不跳过原版护甲步骤，{@code LivingEntity#getDamageAfterArmorAbsorb} 会用满额护甲再减一遍（二次减免）。</li>
     *   <li>刻意<b>不</b>加入 {@code bypasses_resistance} / {@code bypasses_enchantments} —— 规格只要求无视 30% 护甲与韧性，
     *       抗性提升与保护类附魔须照常生效（原版在跳过护甲后仍会走 {@code getDamageAfterMagicAbsorb}）。</li>
     * </ul>
     * 注意（见 {@link #scytheSweep} 的说明）：原版 {@code bypasses_shield} 标签内直接引用了
     * {@code #minecraft:bypasses_armor}，故本类型会传递性地也被视为「无视盾牌」。
     */
    public static final ResourceKey<DamageType> SCYTHE_SWEEP = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "scythe_sweep"));

    /**
     * 自定义伤害类型键：重击（高速踢击等贴身技；数据层见 {@code data/forbidden_hex/damage_type/heavy_strike.json}）。
     * <p>
     * 标签口径（{@code data/minecraft/tags/damage_type/}）与 {@link #SCYTHE_SWEEP} 完全一致：
     * <ul>
     *   <li>{@code bypasses_armor} —— <b>必须</b>。护甲已在调用方按「护甲 × 生效比例」预折算过一次
     *       （见 {@link #damageAfterPartialArmorBypass}），不进该标签会被原版护甲步骤二次减免。</li>
     *   <li>刻意<b>不</b>加入 {@code bypasses_resistance} / {@code bypasses_enchantments} ——
     *       规格只要求"无视 40% 护甲"，抗性提升与保护类附魔须照常生效。</li>
     * </ul>
     * 同一条连带效应（见 {@link #heavyStrike} 的说明）：{@code #minecraft:bypasses_shield} 直接引用了
     * {@code #minecraft:bypasses_armor}，故本类型会传递性地也被视为「无视盾牌」，
     * 格挡判定因此必须换成等价探针源。
     */
    public static final ResourceKey<DamageType> HEAVY_STRIKE = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "heavy_strike"));

    /**
     * 自定义伤害类型键：凋亡（持续伤害；数据层见 {@code data/forbidden_hex/damage_type/doom.json}）。
     * <p>
     * 标签口径（{@code data/minecraft/tags/damage_type/}）：
     * <ul>
     *   <li>{@code bypasses_armor} / {@code bypasses_resistance} / {@code bypasses_enchantments} ——
     *       规格「无视抗性 / 免疫」的落点：护甲点数、抗性提升、保护类附魔都不减伤；</li>
     *   <li>刻意<b>不</b>进 {@code bypasses_cooldown}（与 {@link #TRUE_DAMAGE} 的关键差别）：
     *       凋亡是周期性掉血，若每跳都穿过无敌帧就会把目标的 {@code invulnerableTime} 每 8 tick 刷回 20，
     *       此后 BOSS 自己的普攻/技能会整段落进"只结算增量伤害"分支（少掉血 + 不播受击动画）。
     *       详见 {@code DoomEffect} 的类注释。</li>
     * </ul>
     */
    public static final ResourceKey<DamageType> DOOM = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "doom"));

    /**
     * 自定义伤害类型键：三重投掷（阶段三空中三连弹体；数据层见
     * {@code data/forbidden_hex/damage_type/triple_throw.json}）。
     * <p>标签口径与 {@link #SCYTHE_SWEEP} 完全一致：只进 {@code bypasses_armor}，
     * 因为规格「此攻击无视 30% 的防御」在调用方已按 {@code damageAfterPartialArmorBypass} 预折算过一次
     * （生效护甲比例 0.7），不进该标签会被原版护甲步骤二次减免；抗性提升与保护类附魔照常生效。
     * <p>刻意<b>不复用</b> {@code scythe_sweep}：伤害类型同时承担死亡消息
     * （{@code death.attack.forbidden_hex.*}），拿"俯冲镰扫"的消息去播"被三连投掷砸死"会读成另一个技能。
     */
    public static final ResourceKey<DamageType> TRIPLE_THROW = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "triple_throw"));

    /**
     * 自定义伤害类型键：精神伤害（阶段三「天魔＊灾」改写的伤害；数据层见
     * {@code data/forbidden_hex/damage_type/psychic.json}）。
     * <p>标签口径（{@code data/minecraft/tags/damage_type/}）：{@code bypasses_armor} +
     * {@code bypasses_resistance} + {@code bypasses_enchantments} 三件齐备 ——
     * 这是"精神攻击不吃物理防护"这条规格的落点：护甲点数、抗性提升、保护类附魔一律不减伤。
     * <p>刻意<b>不</b>进 {@code bypasses_cooldown}：精神伤害仍要受目标无敌帧约束
     * （与 {@link #DOOM} 同款取舍，理由见该常量的注释）。
     * <p>它<b>不是</b>某一招的专属类型，而是「天魔＊灾」之后<b>替换</b> BOSS 其它伤害类型的容器：
     * 替换口径唯一收在 {@code AgaitolosPsychic}（谁被污染、何时转永久），本类只负责造源。
     * <p><b>键的真源在中立类</b> {@link ModDamageTypes#PSYCHIC}：理智系统同样要读这个键，
     * 若键留在这里，理智侧就得 import {@code boss.agaitolos}（违反"理智与 BOSS 解耦"）。
     * 本常量只是<b>转发别名</b>，id 与数据层标签一概未动，既有 {@code AgaitolosCombat.PSYCHIC} 引用点无需改动。
     */
    public static final ResourceKey<DamageType> PSYCHIC = ModDamageTypes.PSYCHIC;

    private AgaitolosCombat() {
    }

    /**
     * 构造凋亡的持续伤害源（供 {@code DoomEffect} 每跳调用）。
     * <p>不带 causer：1.20.1 的 {@code MobEffectInstance} 不保存施加者，效果自己跳血时没有归属实体，
     * 死亡消息因此走无攻击者那条键。客户端 / 非 ServerLevel 环境返回 {@code generic()} 兜底，避免 NPE。
     */
    public static DamageSource doom(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            return new DamageSource(registry.getHolderOrThrow(DOOM));
        }
        return level.damageSources().generic();
    }

    /**
     * 构造真实伤害源（伤害类型注册于数据包，须运行期从注册表取 holder）。
     * <p>客户端 / 非 ServerLevel 环境拿不到注册表，返回 {@code generic()} 兜底，避免 NPE。
     */
    public static DamageSource trueDamage(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            return new DamageSource(registry.getHolderOrThrow(TRUE_DAMAGE));
        }
        return level.damageSources().generic();
    }

    /**
     * 构造<b>携带因果实体</b>的真实伤害源（弹体施加的真实伤害段走这条）。
     * <p>
     * 两个理由必须带 {@code causingEntity}：
     * <ol>
     *   <li>击杀归属与 {@code death.attack.forbidden_hex.true_damage.player} 死亡消息都取自
     *       {@code source.getEntity()}，不带则永远只走无攻击者那条消息键；</li>
     *   <li>BOSS 受击管线第 ① 步按 {@code getEntity()} 判「非玩家来源免伤」，
     *       反弹回来的真实伤害段若不带 causer，会被整段挡掉。</li>
     * </ol>
     *
     * @param directEntity  直接造成伤害的实体（弹体；普攻等无弹体场景传 {@code null}）
     * @param causingEntity 伤害归属实体（施加者）
     */
    public static DamageSource trueDamage(Level level, Entity directEntity, Entity causingEntity) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            return new DamageSource(registry.getHolderOrThrow(TRUE_DAMAGE), directEntity, causingEntity);
        }
        return level.damageSources().generic();
    }

    /**
     * 构造俯冲镰扫的伤害源（一阶段技能「俯冲镰扫」的伤害面）。
     * <p>
     * 与 {@link #trueDamage(Level, Entity, Entity)} 同构，仅伤害类型换成 {@link #SCYTHE_SWEEP}，
     * 外加一处<b>必要的</b>来源位置补偿：
     * <p>
     * 玩家侧格挡判定走的是原版 {@code LivingEntity#isDamageSourceBlocked}，它要求
     * {@code source.getSourcePosition() != null} 才判朝向；而三参构造器在 {@code directEntity == null} 时
     * 该方法恒返回 null（{@code damageSourcePosition} 与 {@code directEntity} 都没有）。
     * 横扫本身<b>无独立弹体</b>（directEntity 传 null），故这里补上施加者（BOSS）的位置作为来源位置，
     * 否则「加害者是谁」有了、判朝向的依据却没有，格挡判定会整条失效。
     * <p>
     * <b>⚠ 为什么不用能同时带 (directEntity, causingEntity, position) 的四参构造器</b>：
     * {@code DamageSource(Holder, Entity, Entity, Vec3)} 在本项目编译期类路径上<b>不可访问</b>
     * （javac 报 private 访问控制，与 javap 在 named jar 上看到的 public 不一致），故需要
     * "来源位置"时只能用两参重载 {@code DamageSource(Holder, Entity)} —— 其内部即
     * {@code this(type, entity, entity)}，把 BOSS 同时当作直接实体与归属实体。横扫本就由 BOSS
     * 本人挥出、没有独立弹体，这个等价替换不损失任何语义，却保住了朝向判定。
     * <p>
     * 击杀归属与 {@code death.attack.forbidden_hex.scythe_sweep.player} 死亡消息取自 {@code getEntity()}，
     * 由 {@code causingEntity} 保证。
     */
    public static DamageSource scytheSweep(Level level, Entity directEntity, Entity causingEntity) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            Holder<DamageType> holder = registry.getHolderOrThrow(SCYTHE_SWEEP);
            if (directEntity == null && causingEntity != null) {
                return new DamageSource(holder, causingEntity);
            }
            return new DamageSource(holder, directEntity, causingEntity);
        }
        return level.damageSources().generic();
    }

    /**
     * 构造重击的伤害源（二阶段技能「高速踢击」的伤害面）。
     * <p>
     * 与 {@link #scytheSweep} 逐行同构，只有伤害类型换成 {@link #HEAVY_STRIKE}：
     * 踢击同样<b>没有独立弹体</b>（directEntity 传 null），若走三参构造器则
     * {@code getSourcePosition()} 为 null，玩家侧盾牌朝向判定会整条失效；
     * 故在 directEntity 为 null 时改用两参重载 {@code DamageSource(Holder, Entity)}
     * （内部即 {@code this(type, entity, entity)}），把 BOSS 同时当作直接实体与归属实体，
     * 换来源位置的同时保住击杀归属与 {@code death.attack.forbidden_hex.heavy_strike.player} 消息键。
     */
    public static DamageSource heavyStrike(Level level, Entity directEntity, Entity causingEntity) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            Holder<DamageType> holder = registry.getHolderOrThrow(HEAVY_STRIKE);
            if (directEntity == null && causingEntity != null) {
                return new DamageSource(holder, causingEntity);
            }
            return new DamageSource(holder, directEntity, causingEntity);
        }
        return level.damageSources().generic();
    }

    /**
     * 构造三重投掷的伤害源（阶段三技能「三重投掷」的伤害面）。
     * <p>
     * 与 {@link #heavyStrike} 逐行同构，只有伤害类型换成 {@link #TRIPLE_THROW}：
     * 三者共用同一条"按比例削甲"的调用契约 —— 调用方（{@code AgaitolosWitherSkull} 的破防变体）
     * 先用 {@link #damageAfterPartialArmorBypass} 把「护甲 × 0.7、韧性 × 0.7」折算一次，
     * 再由本类型（已进 {@code bypasses_armor}）施加，使原版护甲步骤整段跳过、不二次减免。
     * <p>
     * 保留 {@code directEntity}（三重投掷时是本 BOSS 打出的凋零头）：
     * 它既是"这发头是自己射的"的识别依据，也是被玩家打回后走
     * {@code AgaitolosDamageRules} ⑤ 自伤通道的判据来源（与远程攻击同一口径）。
     */
    public static DamageSource tripleThrow(Level level, Entity directEntity, Entity causingEntity) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            Holder<DamageType> holder = registry.getHolderOrThrow(TRIPLE_THROW);
            if (directEntity == null && causingEntity != null) {
                return new DamageSource(holder, causingEntity);
            }
            return new DamageSource(holder, directEntity, causingEntity);
        }
        return level.damageSources().generic();
    }

    /**
     * 构建/替换成精神伤害源（阶段三「天魔＊灾」改写 BOSS 伤害类型的落点）。
     * <p>
     * 与 {@link #heavyStrike} 同构，只有伤害类型换成 {@link #PSYCHIC}；唯一的差别在<b>用法</b>：
     * 它不是"某一招专属"，而是把<b>已经构造好</b>的那一发换壳 —— 调用方（{@code AgaitolosPsychic}）
     * 把原源的 {@code directEntity / causingEntity} 原样传进来，故：
     * <ul>
     *   <li><b>击杀归属不变</b>：{@code getEntity()} 仍是 BOSS，死亡消息走
     *       {@code death.attack.forbidden_hex.psychic.player}；</li>
     *   <li><b>反弹自伤判定不变</b>：被玩家打回的凋零头 {@code directEntity} 仍是那个弹体，
     *       {@code AgaitolosEntity#isReflectedSkull} 照旧成立（换壳不会把反弹通道弄丢）。</li>
     * </ul>
     */
    public static DamageSource psychic(Level level, Entity directEntity, Entity causingEntity) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            Holder<DamageType> holder = registry.getHolderOrThrow(PSYCHIC);
            if (directEntity == null && causingEntity != null) {
                return new DamageSource(holder, causingEntity);
            }
            return new DamageSource(holder, directEntity, causingEntity);
        }
        return level.damageSources().generic();
    }

    /**
     * 「按比例削甲」的<b>唯一</b>结算口径 —— 俯冲镰扫（无视 30% ⇒ 生效 0.7）与高速踢击（无视 40% ⇒ 生效 0.6）共用。
     * <p>
     * <b>为什么必须抽成一段公用代码</b>：两招的差异只有一个比例参数，算法完全同源；历史上本项目反复出现
     * "同一套口径写两份、改一份忘一份"的事故（设计文档 §3.1）。抽成方法后，例外的只有比例常量，
     * 想改算法（例如原版护甲公式变化）只需改这里一处。
     * <p>
     * <b>算法（不是"把伤害乘 0.7"，而是"把护甲收益打七折"）</b>：
     * 用「护甲 × 生效比例、韧性 × 生效比例」自己跑一遍原版 {@link CombatRules#getDamageAfterAbsorb}
     * （参数顺序实测为 <b>(伤害, 护甲, 韧性)</b>），算出应受伤害，再由调用方用带 {@code bypasses_armor}
     * 标签的伤害源施加 —— 原版 {@code LivingEntity#getDamageAfterArmorAbsorb} 会因该标签整段跳过护甲步骤
     * （不会二次减免），而 {@code getDamageAfterMagicAbsorb}（抗性提升 + 保护附魔）照常执行。
     * <p>
     * 护甲取 {@link LivingEntity#getArmorValue()}（= {@code floor(getAttributeValue(Attributes.ARMOR))}，
     * 与原版管线 {@code LivingEntity#getDamageAfterArmorAbsorb} 里的取法逐字一致），
     * 韧性取 {@code getAttributeValue(Attributes.ARMOR_TOUGHNESS)}（double，需显式窄化）。
     *
     * @param target         挨打方（护甲/韧性从这里读）
     * @param baseDamage     折算前的伤害（通常是"目标最大生命 × 比例"）
     * @param armorKeptRatio 生效护甲比例：0.7 = 无视 30%、0.6 = 无视 40%
     * @return 折算后的伤害值（未落地，仍需调用方经破甲伤害源施加）
     */
    public static float damageAfterPartialArmorBypass(LivingEntity target, float baseDamage, float armorKeptRatio) {
        return CombatRules.getDamageAfterAbsorb(baseDamage,
                target.getArmorValue() * armorKeptRatio,
                (float) (target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * armorKeptRatio));
    }
}

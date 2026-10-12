package com.example.akaishi.forbidden.entity;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosWitherSkull;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.projectile.SpellProjectile;
import com.example.akaishi.forbidden.sanity.shadow.ShadowBolt;
import com.example.akaishi.forbidden.sanity.shadow.ShadowEntity;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.function.Supplier;

/**
 * 禁忌模块·实体类型注册（P3b 随理智系统、P3c 随 BOSS 阿盖托洛丝从本体迁入，注册 id 与迁前逐字一致）。
 *
 * <p>影怪 / 精神弹属理智系统，阿盖托洛丝 / 凋零头颅属 BOSS，故均归本模块；未安装本模块时它们根本不注册。
 * 侵入式强转仍收敛在 {@link #entity(String, Supplier)} 一处（与本体 ModEntities 同范式）。
 */
public final class AkaishiForbiddenEntities {

    /**
     * 影怪（低理智幻影）。
     * <p><b>MobCategory 取 MISC 的理由</b>：影怪由理智系统自建生成器投放，<b>不参与原版刷怪体系</b> ——
     * MISC 的数量上限是 -1（不占 mobcap）、不参与自然刷怪与难度判定；而 MONSTER 会把它登记成"怪物"，
     * 让 {@code /kill @e[type=monster]} 之类的原版选择器与刷怪统计把它算进去。
     * 真正的隔离来自"它不继承 {@code Mob}"（见 ShadowEntity 类注释）：衰减区的转化与死寂禁刷都以
     * {@code instanceof Mob} 为门，因此两条都不作用到影怪身上。
     */
    public static RegistrySupplier<EntityType<ShadowEntity>> SHADOW;

    /** 影怪的远程弹体：精神弹（继承原版 WitherSkull，只结算一发精神伤害；渲染复用原版凋零头渲染器） */
    public static RegistrySupplier<EntityType<ShadowBolt>> SHADOW_BOLT;

    /** 阿盖托洛丝【下界本源】BOSS 实体；碰撞箱 0.9×2.2（P3c 自本体迁入，id 不变） */
    public static RegistrySupplier<EntityType<AgaitolosEntity>> AGAITOLOS;

    /** 阿盖托洛丝的远程弹体：凋零头颅（继承原版 WitherSkull，只替换命中结算） */
    public static RegistrySupplier<EntityType<AgaitolosWitherSkull>> AGAITOLOS_WITHER_SKULL;

    /** 法术弹体（设计记忆 §19.3 的 X2 投射）：继承原版 WitherSkull，渲染复用原版凋零头渲染器 */
    public static RegistrySupplier<EntityType<SpellProjectile>> SPELL_BOLT;

    /** 法阵（设计记忆 §36.2 / X5 布场）：贴地印记，渲染由原版粒子承担，实体本身不渲染 */
    public static RegistrySupplier<EntityType<SpellCircleEntity>> SPELL_CIRCLE;

    private AkaishiForbiddenEntities() {
    }

    public static void register() {
        SHADOW = entity("shadow",
                () -> EntityType.Builder
                        .<ShadowEntity>of(ShadowEntity::new, MobCategory.MISC)
                        // 碰撞箱按模型稿实测值定（geo 单位 → 格，1u = 1/16 格）：
                        //   躯干/头/双臂的极值为 x∈[-5.65, 5.65]、y∈[0.8, 28.8]、z∈[-2.1, 2.1]，
                        //   即 0.71 宽 × 1.75 高 × 0.26 深，脚底离地 0.05 格（是一张"薄片人影"）。
                        //   取 0.7 × 1.8：与实测同量级并留少量余量；深度不参与原版碰撞箱（宽/高二值）。
                        //   模型若有改动，这里与 ShadowEntity 的近战距离常量要一起看。
                        .sized(0.7F, 1.8F)
                        // 影怪会瞬移：追踪范围略大、同步间隔取 2，避免瞬移后位姿抖动
                        .clientTrackingRange(10)
                        .updateInterval(2)
                        .build("shadow"));

        SHADOW_BOLT = entity("shadow_bolt",
                () -> EntityType.Builder
                        .<ShadowBolt>of(ShadowBolt::new, MobCategory.MISC)
                        // 与凋零头同尺寸，直接复用原版 WitherSkullRenderer 的模型与贴图
                        .sized(0.3125F, 0.3125F)
                        .clientTrackingRange(8)
                        .updateInterval(2)
                        .build("shadow_bolt"));

        AGAITOLOS = entity("agaitolos",
                () -> EntityType.Builder
                        .<AgaitolosEntity>of(AgaitolosEntity::new, MobCategory.MONSTER)
                        // 碰撞箱按当前一阶段模型实测值定：
                        //   实测躯干核心（躯干+腿+裙甲+双臂）宽 0.78、最高点（冠饰）y=2.07、脚底 y=0.14。
                        //   取 0.9 宽：覆盖整个本体并留少量余量；两侧浮空恶魔头（x±0.90）与翅膀（翼展 2.54）
                        //   是悬浮装饰件，不算本体，故意留在盒外，避免玩家在身侧空气里打中它。
                        //   取 2.2 高：覆盖脚底到冠饰顶并留余量；镰刀举起时到 2.35，但手持武器不参与碰撞箱。
                        .sized(0.9F, 2.2F)
                        // 大型 BOSS：加大追踪半径、降低位置同步间隔，避免高机动时画面抖动
                        .clientTrackingRange(12)
                        .updateInterval(3)
                        .build("agaitolos"));

        AGAITOLOS_WITHER_SKULL = entity("agaitolos_wither_skull",
                () -> EntityType.Builder
                        .<AgaitolosWitherSkull>of(AgaitolosWitherSkull::new, MobCategory.MISC)
                        // 尺寸与追踪参数逐项对齐原版 EntityType.WITHER_SKULL（5/16 格、范围 4、间隔 10）
                        .sized(0.3125F, 0.3125F)
                        .clientTrackingRange(4)
                        .updateInterval(10)
                        .build("agaitolos_wither_skull"));

        // 法术弹体：尺寸与渲染器均对齐原版凋零头（零新增贴图，见 SpellProjectile 类注释）。
        // 追踪范围取 8：弹体飞行仅 0.5~2s、射程约 12 格，8 已覆盖可见范围，无需更大。
        SPELL_BOLT = entity("spell_bolt",
                () -> EntityType.Builder
                        .<SpellProjectile>of(SpellProjectile::new, MobCategory.MISC)
                        .sized(0.3125F, 0.3125F)
                        .clientTrackingRange(8)
                        .updateInterval(2)
                        .build("spell_bolt"));

        // 法阵：碰撞箱只是"占位"（它不可被选取、不可被攻击，见 SpellCircleEntity#isPickable）；
        // 尺寸取 0.5×0.5 而不是 0 —— 零尺寸实体会让部分原版逻辑（包围盒求交、区块实体计数）出怪值。
        // 追踪范围 10：法阵半径可达 8 格，玩家在圈外也要看得见它。
        SPELL_CIRCLE = entity("spell_circle",
                () -> EntityType.Builder
                        .<SpellCircleEntity>of(SpellCircleEntity::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .fireImmune()
                        .clientTrackingRange(10)
                        .updateInterval(10)
                        .build("spell_circle"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> RegistrySupplier<EntityType<T>> entity(String id, Supplier<EntityType<T>> factory) {
        return (RegistrySupplier<EntityType<T>>) (Object) RegistrarManager
                .get(AkaishiMod.MOD_ID).get(Registries.ENTITY_TYPE)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, id), factory);
    }
}

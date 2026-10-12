package com.example.akaishi.forbidden.magic.projectile;

import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 法术弹体（设计记忆 §19.3 的 <b>X2 投射</b>）：「抛射一枚飞行物，飞行段<b>可被躲 / 可被击落</b>」。
 *
 * <p><b>为什么必须是真的实体</b>：X2 的三种性质 —— 可被躲（要有一段时间的飞行）、
 * 可被击落（要有可被攻击的碰撞体）、落点可预期（要有可见轨迹）——
 * 全都是"世界里的一个东西"才有的性质。用"延迟一下再触发"是装不出来的：
 * 那既躲不掉，也打不掉。
 *
 * <p><b>为什么还继承 {@link WitherSkull}</b>：只为拿它的<b>直线飞行与碰撞检测</b>。
 * 外观已不再复用凋零头模型 —— 它由客户端 {@code SpellProjectileRenderer} 按
 * {@link #visual()} 程序化绘制（形态 / 配色 / 符号都随法术而变）。
 * <b>不调 {@code super.onHit}</b>：原版那一支会 {@code level().explode(...)} 炸地形。
 *
 * <p><b>同步的字段比法阵少</b>：弹体又小又快，符号与词数在它身上看不出差别，
 * 故只同步"形态 + 两个颜色 + 威力"这一最小集。
 */
public class SpellProjectile extends WitherSkull {

    /** 初速（格/tick）；配合射程 12 格约 0.5s 命中，落在 §19.3 的"飞行 0.5~2s"区间 */
    public static final double SPEED = 1.2d;
    private Vec3 launchOrigin;
    private double maxRange = 32d;

    public void setMaxRange(Vec3 origin, double range) {
        launchOrigin = origin;
        maxRange = Math.max(1d, Math.min(128d, range));
    }

    /** 核心 E 的 id → 客户端据此查形态规格 */
    private static final EntityDataAccessor<String> DATA_EFFECT =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
    /** 主墨识别色 */
    private static final EntityDataAccessor<Integer> DATA_INK_RGB =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.INT);
    /** 主墨 id（粒子按墨选形态用；未知时回退源形态） */
    private static final EntityDataAccessor<String> DATA_INK_ID =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
    /** 源基调色 */
    private static final EntityDataAccessor<Integer> DATA_SOURCE_RGB =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SOURCE_ID =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_WORD_IDS =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
    /** 威力轴 → 体积与亮度 */
    private static final EntityDataAccessor<Float> DATA_POWER =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_GLYPHS =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);

    public SpellProjectile(EntityType<? extends WitherSkull> type, Level level) {
        super(type, level);
        // 关掉"危险"标记：原版据此播放爆炸前兆并调爆炸；真正的"不炸"靠下面不调 super.onHit
        this.setDangerous(false);
    }

    /** 便捷构造（投送器用；避免它去 import 实体注册表与同步字段） */
    public static SpellProjectile create(Level level, Entity owner, Vec3 from, Vec3 direction,
            SpellVisual visual) {
        SpellProjectile bolt = new SpellProjectile(AkaishiForbiddenEntities.SPELL_BOLT.get(), level);
        bolt.setOwner(owner);
        bolt.setPos(from.x + direction.x, from.y + direction.y, from.z + direction.z);
        bolt.setDeltaMovement(direction.normalize().scale(SPEED));
        bolt.arm(visual);
        return bolt;
    }

    /** 写入视觉描述（服务端在发射时调一次；随后由原版同步机制带给客户端） */
    public void arm(SpellVisual visual) {
        if (visual == null) {
            return;
        }
        this.entityData.set(DATA_EFFECT, visual.effectId());
        this.entityData.set(DATA_INK_RGB, visual.inkRgb());
        this.entityData.set(DATA_INK_ID, visual.inkId());
        this.entityData.set(DATA_SOURCE_RGB, visual.sourceRgb());
        this.entityData.set(DATA_SOURCE_ID, visual.sourceId());
        this.entityData.set(DATA_WORD_IDS, String.join("\u0001", visual.wordIds()));
        this.entityData.set(DATA_GLYPHS, visual.glyphs());
        this.entityData.set(DATA_POWER, visual.power());
    }

    /** 客户端的视觉描述（字段不全时用默认值兜底，绝不返回 null） */
    public SpellVisual visual() {
        String words = this.entityData.get(DATA_WORD_IDS);
        return new SpellVisual(
                this.entityData.get(DATA_EFFECT),
                com.example.akaishi.forbidden.magic.table.MagicIds.Delivery.PROJECTILE,
                this.entityData.get(DATA_SOURCE_ID),
                this.entityData.get(DATA_INK_ID),
                this.entityData.get(DATA_INK_RGB),
                this.entityData.get(DATA_SOURCE_RGB),
                this.entityData.get(DATA_POWER), 0f, 0f, 0,
                words.isEmpty() ? java.util.List.of() : java.util.Arrays.asList(words.split("\\u0001", -1)),
                this.entityData.get(DATA_GLYPHS));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_EFFECT, "");
        this.entityData.define(DATA_INK_RGB, 0xFFFFFF);
        this.entityData.define(DATA_INK_ID, "");
        this.entityData.define(DATA_SOURCE_RGB, 0xFFFFFF);
        this.entityData.define(DATA_SOURCE_ID, "san");
        this.entityData.define(DATA_WORD_IDS, "");
        this.entityData.define(DATA_GLYPHS, "");
        this.entityData.define(DATA_POWER, 0f);
    }

    /** 飞行尾迹：客户端本地生成（逐 tick 广播纯属浪费；服务端 addParticle 本就是空实现）。 */
    @Override
    public void tick() {
        if (!level().isClientSide && (tickCount >= 100 || launchOrigin == null
                || position().distanceToSqr(launchOrigin) >= maxRange * maxRange)) {
            SpellProjectiles.drop(this);
            discard();
            return;
        }
        super.tick();
        if (this.level().isClientSide() && !this.isRemoved() && !this.entityData.get(DATA_EFFECT).isEmpty()) {
            // 尾迹：形取「墨 × 投射物载体」、色取墨、数量按墨个性（炽密、渊稀）缩放
            String inkId = this.entityData.get(DATA_INK_ID);
            com.example.akaishi.forbidden.magic.visual.SpellCarrierForm carrier =
                    com.example.akaishi.forbidden.magic.visual.SpellCarrierForm.ORB;
            int count = Math.max(1, Math.round(2f * AkaishiMagicParticles.emitMul(inkId, carrier)));
            AkaishiMagicParticles.trail(this.level(),
                    MagicSource.byIdOrDefault(this.entityData.get(DATA_SOURCE_ID)),
                    inkId, carrier, this.entityData.get(DATA_INK_RGB),
                    this.getX(), this.getY() + this.getBbHeight() * 0.5d, this.getZ(), count, 0.05);
            if (this.tickCount % 2 == 0) {
                Vec3 motion = this.getDeltaMovement().scale(-0.04d);
                AkaishiMagicParticles.photon(this.level(), this.entityData.get(DATA_INK_RGB),
                        this.getX(), this.getY() + this.getBbHeight() * 0.5d, this.getZ(),
                        motion.x, motion.y, motion.z, 0.8f, 1f);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (this.level().isClientSide()) {
            return;
        }
        // 刚生成时弹体可能还在施法者碰撞箱里 —— 那种重叠不算命中，继续飞
        if (result.getType() == HitResult.Type.ENTITY
                && ((EntityHitResult) result).getEntity() == this.getOwner()) {
            return;
        }
        Vec3 impact = result.getLocation();
        Entity hit = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        Vec3 direction = this.getDeltaMovement().normalize();
        this.discard();
        // 命中即结算：由投送器登记的回调决定"打谁、打多狠"
        SpellProjectiles.fire(this, hit, impact, direction);
    }

    /**
     * 可被击落（§19.3「可被击落」）。
     *
     * <p>原版 {@code WitherSkull} 恒返回 {@code false}（不可交互），本类改成 {@code true}
     * 才能被箭 / 近战打到。被打中后<b>直接消散且不结算</b> —— 这正是"击落"的含义：
     * 飞行段的风险由玩家承担。
     */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || source.getEntity() == this.getOwner()) {
            return false;
        }
        SpellProjectiles.drop(this);
        this.discard();
        return true;
    }
}

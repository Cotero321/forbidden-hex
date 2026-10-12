package com.example.akaishi.forbidden.sanity.shadow;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 影怪的远程弹体：<b>精神弹</b>（仅 20% 档出现）。
 *
 * <p><b>为什么新建一个轻量弹体、而不是直接复用 {@code AgaitolosWitherSkull}</b>：
 * 那个弹体承载的是 BOSS 的规格（5 点爆炸 + 凋零 III + 2 点真实伤害 + 可被近战打回 + 反弹自伤通道 + 凋亡分流），
 * 复用它会把这些语义整包带进理智系统（玩家会被挂凋零、弹体会走 BOSS 的自伤判定链），
 * 而理智系统与 BOSS 必须解耦。本类只继承原版 {@link WitherSkull} 拿它的飞行与客户端渲染，
 * <b>命中只结算一发精神伤害</b>，其余一概不做。
 *
 * <p><b>三处刻意</b>：
 * <ol>
 *   <li><b>不调 {@code super.onHit}</b>：原版 {@code WitherSkull#onHit} 无条件调用 {@code level().explode(...)}
 *       （会破坏地形）。这里只做实体命中结算 + {@code discard}，命中即消失；</li>
 *   <li><b>不打发射者</b>：刚生成时可能与影怪自身重叠（同 {@code AgaitolosWitherSkull} 的坑）；</li>
 *   <li><b>弹体不可被打回</b>：原版 {@code WitherSkull#isPickable()} 恒 false，本类沿用不改 ——
 *       理智系统不需要"反弹"这条玩法，少一个口子就少一条要维护的语义。</li>
 * </ol>
 *
 * <p>渲染直接复用原版 {@code WitherSkullRenderer}（在 forge 侧注册），因此<b>不需要任何新增贴图</b>，
 * 不存在"缺图/死链"；将来若建模侧给出专属弹体材质，只需替换渲染器，本类不受影响。
 */
public class ShadowBolt extends WitherSkull {

    /** 命中伤害（由 {@link ShadowCombat} 按档位写入；默认值与近战常量同源） */
    private float damage = ShadowCombat.BOLT_DAMAGE;

    public ShadowBolt(EntityType<? extends WitherSkull> type, Level level) {
        super(type, level);
        this.setDangerous(false); // 不生成爆炸、降低飞行惯性（真正的"不炸"靠下面不调 super.onHit）
    }

    /** 设定本发命中伤害（只由 {@link ShadowCombat} 调用） */
    public void setDamage(float damage) {
        this.damage = Math.max(0.0F, damage);
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) result;
            if (entityHit.getEntity() != this.getOwner()) {
                this.onHitEntity(entityHit);
            }
        }
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity living)) {
            return;
        }
        // 伤害源：directEntity = 本弹体、causingEntity = 发射者（影怪），击杀归属与死亡消息都取自后者
        living.hurt(ShadowCombat.psychic(living.level(), this, this.getOwner()), this.damage);
    }
}

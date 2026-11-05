package com.example.akaishi.forbidden.boss.agaitolos.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 独立注册的巫咒法球，复用旧弹体的飞行、反弹与服务端命中结算。
 * 客户端由专用法球渲染器表现，不再使用凋零头模型或烟雾尾迹。
 */
public final class AgaitolosHexOrb extends AgaitolosWitherSkull {
    private static final byte IMPACT_EVENT = 61;
    private static Consumer<AgaitolosHexOrb> clientImpactHandler = orb -> {};

    /** 仅由客户端初始化注入；公共实体不加载渲染器或客户端单例。 */
    public static void setClientImpactHandler(Consumer<AgaitolosHexOrb> handler) {
        clientImpactHandler = Objects.requireNonNull(handler);
    }

    @Override
    protected void onHit(HitResult result) {
        boolean ownerHit = result instanceof EntityHitResult entityHit
                && entityHit.getEntity() == this.getOwner();
        if (!this.level().isClientSide() && result.getType() != HitResult.Type.MISS
                && !ownerHit) {
            // 必须在父类移除前发包；寿命结束与发射者贴脸宽限不产生命中特效。
            this.level().broadcastEntityEvent(this, IMPACT_EVENT);
        }
        super.onHit(result);
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == IMPACT_EVENT) {
            if (this.level().isClientSide()) {
                clientImpactHandler.accept(this);
            }
            return;
        }
        super.handleEntityEvent(event);
    }

    public AgaitolosHexOrb(EntityType<? extends AgaitolosHexOrb> type, Level level) {
        super(type, level);
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.ENCHANT;
    }
}

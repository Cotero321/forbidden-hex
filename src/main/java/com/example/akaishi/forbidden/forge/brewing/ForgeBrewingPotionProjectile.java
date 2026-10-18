package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.BrewingPotionProjectile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class ForgeBrewingPotionProjectile extends BrewingPotionProjectile {
    public ForgeBrewingPotionProjectile(EntityType<? extends BrewingPotionProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == SHATTER_EVENT) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.akaishi.forbidden.forge.magic.visual.ProjectileTrailEffects.INSTANCE.shatter(this));
        } else {
            super.handleEntityEvent(event);
        }
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

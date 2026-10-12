package com.example.akaishi.forbidden.forge.magic.particle;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 粒子 provider 注册（<b>仅客户端</b>）。
 *
 * <p>Forge 的 {@code RegisterParticleProvidersEvent} 与粒子 provider 都是客户端专有类，
 * 故本类带 {@code Dist.CLIENT} —— 专用服务端不会加载它，避免 NoClassDefFoundError。
 * 类型本身的注册在 common（{@link AkaishiMagicParticles}），两侧各管各的那一半。
 *
 * <p>只注册<b>一个</b>类型：形态（四源贴图）由 {@code SpellMoteOptions.variant} 在同一张
 * 贴图列表里选下标，颜色由 {@code SpellMoteOptions.rgb} 逐实例染色。
 */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AkaishiMagicParticleProviders {

    private AkaishiMagicParticleProviders() {
    }

    @SubscribeEvent
    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AkaishiMagicParticles.mote().get(), SpellMoteParticle.Provider::new);
        event.registerSpriteSet(AkaishiMagicParticles.photon().get(), PhotonParticle.Provider::new);
    }
}

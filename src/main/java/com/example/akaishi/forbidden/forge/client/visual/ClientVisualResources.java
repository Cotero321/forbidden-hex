package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.menu.AkaishiCodexScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientVisualResources {
    private ClientVisualResources() { }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(VisualMaterials.INSTANCE);
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            ClientPostEffects.INSTANCE.onResourceReload();
            ClientShaderCompatibility.onResourceReload();
            AkaishiCodexScreen.onResourceReload();
        });
    }
}

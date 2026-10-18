package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.forge.client.visual.ClientPostEffects;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies uniforms only to programs belonging to the locally owned post chain. */
@Mixin(PostPass.class)
public class PostPassMixin {
    @Inject(method = "process", at = @At("HEAD"))
    private void akaishi$injectToneTime(float partialTicks, CallbackInfo ci) {
        ClientPostEffects.INSTANCE.applyUniforms((PostPass) (Object) this);
    }
}

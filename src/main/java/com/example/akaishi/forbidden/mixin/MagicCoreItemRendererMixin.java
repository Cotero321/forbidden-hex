package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.forge.magic.core.MagicCoreRenderer;
import com.example.akaishi.forbidden.forge.magic.core.WandRenderer;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.wand.WandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** 在禁忌模块自身挂载核心 BEWLR，避免本体 Forge 反向依赖禁忌模块。 */
@Mixin(Item.class)
public class MagicCoreItemRendererMixin {
    @Inject(method = "initializeClient", at = @At("HEAD"), remap = false, cancellable = true)
    private void akaishi$initializeMagicCore(Consumer<IClientItemExtensions> consumer, CallbackInfo ci) {
        if ((Object) this instanceof MagicCoreItem) {
            consumer.accept(MagicCoreRenderer.INSTANCE);
            ci.cancel();
        } else if ((Object) this instanceof WandItem) {
            consumer.accept(WandRenderer.INSTANCE);
            ci.cancel();
        }
    }
}

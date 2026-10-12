package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.sanity.ClientSanityVision;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为自定义后处理着色器注入"外部无法自行写入"的 uniform。
 * <p>原版 {@code PostPass.process} 每帧都用 {@code safeGetUniform("Time").set(...)} 覆盖 Time，
 * 且该值只是 1 秒锯齿波（PostChain 内部 time 累加到 20 后回绕、再除以 20），
 * 外部无论如何写入都会被下一帧覆盖，也无法表达数秒级周期。
 * <p>因此这里在每帧入口写入：
 * <ul>
 *   <li>{@code AkaishiTime}（客户端真实秒数），供 unnameable.fsh 做 3 秒台阶式色调切换；</li>
 *   <li>{@code AkaishiGray / AkaishiBlood / AkaishiMono}（低理智视野的三个强度），
 *       供 sanity_vision.fsh 按档位表现灰化 / 血丝 / 黑白 —— 同样必须每帧写，
 *       否则会被 JSON 里的初值（0）永久压住。</li>
 * </ul>
 * 这些 uniform 自身不会被原版逻辑触碰，故能稳定保留；
 * 仅当着色器自己声明了该 uniform 时才写入（未声明者 getUniform 返回 null），其余后处理 pass 不受影响。
 */
@Mixin(PostPass.class)
public class PostPassMixin {

    @Inject(method = "process", at = @At("HEAD"))
    private void akaishi$injectToneTime(float partialTicks, CallbackInfo ci) {
        EffectInstance effect = ((PostPass) (Object) this).getEffect();
        // 秒数取模回绕：限制浮点精度损失，对台阶哈希取值无影响
        setIfPresent(effect, "AkaishiTime", (float) (System.nanoTime() / 1.0E9D % 300.0D));
        // 低理智视野强度（未声明这些 uniform 的其它后处理链完全不进入以下分支）
        setIfPresent(effect, "AkaishiGray", ClientSanityVision.gray());
        setIfPresent(effect, "AkaishiBlood", ClientSanityVision.blood());
        setIfPresent(effect, "AkaishiMono", ClientSanityVision.mono());
    }

    private static void setIfPresent(EffectInstance effect, String name, float value) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }
}

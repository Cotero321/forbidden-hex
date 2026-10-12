package com.example.akaishi.forbidden.forge.sanity.shadow;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.sanity.shadow.ShadowEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 影怪的 Geo 模型（几何 / 贴图 / 动画 / 发光遮罩四张资源的定位）。
 *
 * <p>与 {@code AgaitolosModel} 的差别只有一处：影怪只有一套资产（无阶段切换），
 * 因此四张资源都是常量、没有下标表，{@link #glowTexture} 也只需返回同一个值。
 *
 * <p>资源路径与建模侧冻结的契约逐字对齐（写错只会在实机渲染时抛 GeckoLibException，编译查不出来）：
 * <ul>
 *   <li>{@code assets/forbidden_hex/geo/entity/shadow.geo.json}（identifier {@code geometry.akaishi.shadow}）</li>
 *   <li>{@code assets/forbidden_hex/textures/entity/shadow.png}</li>
 *   <li>{@code assets/forbidden_hex/textures/entity/shadow_glow.png}（只含眼睛的自发光遮罩）</li>
 *   <li>{@code assets/forbidden_hex/animations/entity/shadow.animation.json}
 *       （clip：{@code animation.shadow.idle} / {@code .attack} / {@code .vanish}）</li>
 * </ul>
 *
 * <p>GeckoLib 的客户端渲染类只能放 forge 模块：common 是纯 Architectury 编译面，
 * 只通过 modCompileOnly 取接口 API，不承担渲染实现（同 {@code AgaitolosModel} 的边界）。
 */
public class ShadowModel extends GeoModel<ShadowEntity> {

    private static final ResourceLocation MODEL =
            new ResourceLocation(AkaishiMod.MOD_ID, "geo/entity/shadow.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/shadow.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(AkaishiMod.MOD_ID, "animations/entity/shadow.animation.json");
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/shadow_glow.png");

    @Override
    public ResourceLocation getModelResource(ShadowEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShadowEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShadowEntity animatable) {
        return ANIMATION;
    }

    /**
     * 发光遮罩（供 {@link ShadowGlowLayer} 使用）。
     * <p>定为 {@code static}：调用方是渲染图层，它只拿得到 {@code GeoModel} 接口引用；
     * 留在本类而不让图层自带一份，是为了让"本体贴图与遮罩同源"这件事只有一个落点。
     */
    public static ResourceLocation glowTexture() {
        return GLOW_TEXTURE;
    }
}

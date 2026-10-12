package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 阿盖托洛丝阶段化 Geo 模型（几何 / 贴图 / 动画三资源定位）。
 * <p>
 * 三套资产按阶段索引取用，索引统一由 {@link #phaseIndex} 算出：战斗阶段一 / 二 / 三各一套 geo 与贴图。
 * 三套 geo 的骨骼名完全一致（40 根同名骨骼，含 {@code head_r} / {@code scythe} / 翼三节），
 * 故三阶段共用同一份骨架动画 {@code agaitolos_stage1.animation.json}——差异只在几何与贴图；
 * 将来若新增 stage2/3 专属动画，改 {@link #ANIMATIONS} 对应下标即可。
 * <p>
 * GeckoLib 的客户端渲染类只能放 forge 模块：common 是纯 Architectury 编译面，
 * 只通过 modCompileOnly 取接口 API，不承担渲染实现。
 */
public class AgaitolosModel extends GeoModel<AgaitolosEntity> {

    /** 阶段一资产的下标：复活阶段与任何越界序号都回退到它 */
    private static final int STAGE_1_INDEX = 0;

    /** 按阶段索引的几何资源：下标 0 / 1 / 2 = 阶段一 / 二 / 三 */
    private static final ResourceLocation[] MODELS = {
            new ResourceLocation(AkaishiMod.MOD_ID, "geo/entity/agaitolos_stage1.geo.json"),
            new ResourceLocation(AkaishiMod.MOD_ID, "geo/entity/agaitolos_stage2.geo.json"),
            new ResourceLocation(AkaishiMod.MOD_ID, "geo/entity/agaitolos_stage3.geo.json")
    };

    /** 按阶段索引的贴图资源 */
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/agaitolos_stage1.png"),
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/agaitolos_stage2.png"),
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/agaitolos_stage3.png")
    };

    /**
     * 按阶段索引的<b>发光遮罩</b>（emissive）：与本体贴图同尺寸、同 UV 布局，
     * 只保留眼睛／浮颅眼窝与牙列／镰刀刃口与魂宝石／胸前亮块与描边／翼膜破洞边缘等自发光区域，其余像素全透明。
     * <p>由 {@code AgaitolosGlowLayer} 以全亮混合方式覆在本体之上消费（见其类注释的 API 依据）。
     */
    private static final ResourceLocation[] GLOW_TEXTURES = {
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/agaitolos_stage1_glow.png"),
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/agaitolos_stage2_glow.png"),
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/entity/agaitolos_stage3_glow.png")
    };

    /** 骨架动画：现阶段只有这一份，三套 geo 骨骼同构故可共用 */
    private static final ResourceLocation SHARED_ANIMATION =
            new ResourceLocation(AkaishiMod.MOD_ID, "animations/entity/agaitolos_stage1.animation.json");

    /** 按阶段索引的动画资源：三阶段共用同一骨架动画文件 */
    private static final ResourceLocation[] ANIMATIONS = {SHARED_ANIMATION, SHARED_ANIMATION, SHARED_ANIMATION};

    @Override
    public ResourceLocation getModelResource(AgaitolosEntity animatable) {
        return MODELS[phaseIndex(animatable)];
    }

    @Override
    public ResourceLocation getTextureResource(AgaitolosEntity animatable) {
        return TEXTURES[phaseIndex(animatable)];
    }

    @Override
    public ResourceLocation getAnimationResource(AgaitolosEntity animatable) {
        return ANIMATIONS[phaseIndex(animatable)];
    }

    /**
     * 阶段 → 资产下标（0~2）。
     * <p>
     * 用已同步的 {@code DATA_PHASE}：{@code combatOrdinal()} 为 RESPAWN = 0、PHASE_1/2/3 = 1/2/3。
     * 复活阶段（0）与任何越界序号都钳到阶段一：存档缺键时 {@code getInt} 返回 0 会解析成 RESPAWN，
     * 不钳位就会拿到 -1 越界崩客户端。
     */
    private static int phaseIndex(AgaitolosEntity animatable) {
        int index = animatable.getPhase().combatOrdinal() - 1;
        return index < 0 || index >= MODELS.length ? STAGE_1_INDEX : index;
    }

    /**
     * 阶段对应的发光遮罩资源（供 {@code AgaitolosGlowLayer} 使用）。
     * <p>
     * 刻意把这张表与取值一起留在本类、而不是让图层自带一份：下标口径只有 {@link #phaseIndex}
     * 一处，本体贴图（{@link #getTextureResource}）与遮罩必然同步换档；
     * 若日后新增阶段四，只需扩 {@link #MODELS} / {@link #TEXTURES} / {@link #GLOW_TEXTURES} 三张表。
     * <p>
     * 定为 {@code static}：调用方是渲染图层，它只拿得到 {@code GeoModel} 接口引用，
     * 为避免向下转型而直接以类名调用。
     */
    public static ResourceLocation glowTexture(AgaitolosEntity animatable) {
        return GLOW_TEXTURES[phaseIndex(animatable)];
    }
}

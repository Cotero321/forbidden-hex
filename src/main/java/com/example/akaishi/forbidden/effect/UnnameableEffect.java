package com.example.akaishi.forbidden.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 不可名状：黑山羊母神仪式进行中、以及完成瞬间施加的减益。
 * <p>
 * 本效果自身不结算伤害、不做任何服务端逻辑，仅作为"客户端表现开关"：
 * 客户端检测到该效果后叠加视野扭曲、边缘粗线 + 噪点、随机闪烁的低语文字，并播放专属呓语。
 * <p>
 * 1.20.1 效果图标无需代码绑定：客户端按注册 ID 自动加载 textures/mob_effect/unnameable.png（18×18）。
 */
public class UnnameableEffect extends MobEffect {

    public UnnameableEffect() {
        // 0x8C1C2B：与图标（羊头红眼）呼应的暗血红，用于效果粒子与名称着色
        super(MobEffectCategory.HARMFUL, 0x8C1C2B);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false; // 无周期逻辑，全部表现交由客户端渲染层驱动
    }
}

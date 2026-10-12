package com.example.akaishi.forbidden.effect;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

/**
 * 禁忌模块·状态效果注册（P3b 随理智系统、P3c 随 BOSS 从本体 {@code ModEffects} 迁入）。
 *
 * <p>「理智恢复」是理智回复药水的载体效果，随理智系统归本模块；
 * 「凋亡」是 BOSS 阿盖托洛丝阶段三的上位减益，随 BOSS 归本模块；
 * 「不可名状」是母神祭坛仪式减益，随母神祭坛归本模块（P3d 迁入）；
 * 注册 id 与迁前逐字一致：{@code forbidden_hex:sanity_restore} / {@code forbidden_hex:doom} / {@code forbidden_hex:unnameable}
 * （命名空间仍为三模块共用的 {@code forbidden_hex:}）。
 * 其余效果（衰变）留在本体 {@code ModEffects}。
 */
public final class AkaishiForbiddenEffects {

    /**
     * 理智恢复：酿造药水的载体效果（I 级共回 7 SAN / II 级共回 10 SAN，在 5s 窗口内逐 tick 分摊）。
     * <p>施加侧唯一入口是原版酿造链路（药水 id 见 {@code SanityRestorePotions}），
     * 数值与分摊口径全在 {@link SanityRestoreEffect}。
     */
    public static RegistrySupplier<MobEffect> SANITY_RESTORE;

    /**
     * 凋亡：阿盖托洛丝阶段三的减益（周期掉血 + 降低治疗）。
     * <p>施加侧唯一入口见 {@code com.example.akaishi.forbidden.boss.agaitolos.AgaitolosDoom}
     * （阶段一/二仍施加原版凋零，阶段三改施加本效果）；治疗降低的平台消费在 {@code AgaitolosDoomHandler}。
     */
    public static RegistrySupplier<MobEffect> DOOM;

    /**
     * 不可名状：黑山羊母神仪式进行中与完成瞬间施加，仅驱动客户端画面/音效表现（P3d 自本体 {@code ModEffects} 迁入）。
     * <p>施加侧入口见母神祭坛（{@code AkaishiMotherAltarBlockEntity} / {@code AkaishiAltarRitual}）与禁断饰品。
     */
    public static RegistrySupplier<MobEffect> UNNAMEABLE;

    private AkaishiForbiddenEffects() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用（注册表冻结前） */
    public static void register() {
        Registrar<MobEffect> registrar = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.MOB_EFFECT);
        SANITY_RESTORE = registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, "sanity_restore"),
                SanityRestoreEffect::new);
        DOOM = registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, "doom"), DoomEffect::new);
        UNNAMEABLE = registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, "unnameable"), UnnameableEffect::new);
    }
}

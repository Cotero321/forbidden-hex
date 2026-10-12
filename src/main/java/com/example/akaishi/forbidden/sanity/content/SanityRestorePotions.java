package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.effect.SanityRestoreEffect;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

/**
 * 理智回复药水（原版酿造链路）：{@code forbidden_hex:sanity_restore}（I 级）/ {@code forbidden_hex:strong_sanity_restore}（II 级）。
 *
 * <p><b>只注册 Potion，不注册物品</b>：药水直接骑在原版 {@code minecraft:potion} 上
 * （{@code PotionUtils.setPotion}），因此酿造台的产出、喷溅/滞留/药箭的自动衍生、
 * 物品栏图标与 tooltip 全部沿用原版实现，本模组零新增物品/贴图。
 *
 * <p><b>时长写死在效果实例里（100t = 5s）</b>：窗口长度就是效果时长，
 * 故"5 秒内回复"这条规格由 {@link SanityRestoreEffect#WINDOW_TICKS} 一处决定。
 * 药水的"长效版本"必须显式注册酿造配方才会存在，本模组不注册红石条目 ⇒ <b>红石对本药水无效</b>。
 *
 * <p><b>名称 lang 键</b>：原版 {@code PotionItem#getDescriptionId} = {@code "item.minecraft.potion" + ".effect." + 名称}
 * （见 1.20.1 {@code Potion#getName}），而 {@code Potion#getName} 在名称为 null 时取<b>注册表路径</b>。
 * 本类显式传入与注册路径同名的名称，故客户端实际查找的键是
 * {@code item.minecraft.potion.effect.sanity_restore} —— 键名前缀属原版命名空间，但 lang 键可以定义在
 * 本模组的 {@code assets/forbidden_hex/lang} 里（各命名空间 lang 合并成一张表），故双语文件里一并给出
 * 药水 / 喷溅 / 滞留 / 药箭四种键，避免"裸 key"。
 */
public final class SanityRestorePotions {

    /** I 级药水注册路径（同时作为显示名，见类注释） */
    public static final String SANITY_RESTORE_ID = "sanity_restore";
    /** II 级药水注册路径 */
    public static final String STRONG_SANITY_RESTORE_ID = "strong_sanity_restore";

    /** I 级：5s 内共回 7 SAN */
    public static RegistrySupplier<Potion> SANITY_RESTORE;
    /** II 级：5s 内共回 10 SAN */
    public static RegistrySupplier<Potion> STRONG_SANITY_RESTORE;

    /** 重复注册保护（init 被重复调用时只注册一次） */
    private static boolean registered;

    private SanityRestorePotions() {
    }

    /**
     * 注册两支药水（由 {@code AkaishiForbiddenMod.init} 调用，须排在 {@link AkaishiForbiddenEffects#register()} 之后：
     * 药水实例创建时要读已注册的 {@code forbidden_hex:sanity_restore} 效果；注册表事件顺序上
     * {@code MOB_EFFECT} 早于 {@code POTION}，故跨注册表引用在此处是安全的）。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registrar<Potion> registrar = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.POTION);
        SANITY_RESTORE = registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, SANITY_RESTORE_ID),
                () -> new Potion(SANITY_RESTORE_ID,
                        new MobEffectInstance(AkaishiForbiddenEffects.SANITY_RESTORE.get(),
                                SanityRestoreEffect.WINDOW_TICKS, 0)));
        STRONG_SANITY_RESTORE = registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, STRONG_SANITY_RESTORE_ID),
                () -> new Potion(STRONG_SANITY_RESTORE_ID,
                        new MobEffectInstance(AkaishiForbiddenEffects.SANITY_RESTORE.get(),
                                SanityRestoreEffect.WINDOW_TICKS, 1)));
    }
}

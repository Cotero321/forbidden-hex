package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.effect.ModDamageTypes;
import com.example.akaishi.forbidden.sanity.SanityDamageGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 精神伤害减免的平台挂点（forge）：识别 {@code forbidden_hex:psychic} 伤害并交给 common 的
 * {@link SanityDamageGuard} 缩量。
 *
 * <p><b>为什么单独一个类、不并进既有处理器</b>：理智系统与躯体/机械/禁忌体系没有耦合，
 * 并进去会让"关掉理智系统"变成要改既有类的条件分支；独立类只需不注册即整体失效（解耦）。
 *
 * <p><b>事件选择与叠加顺序</b>：挂 {@link LivingHurtEvent}（{@code LivingEntity#actuallyHurt} 开头触发，
 * 早于护甲/抗性/吸收）。精神伤害类型本身带 {@code bypasses_armor / bypasses_resistance /
 * bypasses_enchantments}，那三步对它整段跳过，因此在该点缩放与在其后缩放等价；
 * 与本项目既有的护甲减伤（同为乘性缩放当前量）叠加顺序不影响结果。
 *
 * <p><b>只读伤害类型</b>：伤害类型键取自中立类 {@link ModDamageTypes#PSYCHIC}（唯一真源，
 * BOSS 侧同样引用它），本类不重复写 id 字符串，也不 import BOSS 包 —— 否则键换名/换位置时理智侧会静默失效。
 */
public final class AkaishiSanityDamageHandler {

    public static final AkaishiSanityDamageHandler INSTANCE = new AkaishiSanityDamageHandler();

    private AkaishiSanityDamageHandler() {
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!event.getSource().is(ModDamageTypes.PSYCHIC)) {
            return; // 只处理精神伤害：其余伤害类型与理智无关
        }
        event.setAmount(SanityDamageGuard.applyPsychicReduction(player, event.getAmount()));
    }
}

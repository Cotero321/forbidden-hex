package com.example.akaishi.forbidden.magic.exec.deliver;

import com.example.akaishi.forbidden.api.magic.exec.IDeliverer;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicDeliveryAction;
import com.example.akaishi.forbidden.magic.exec.resolve.MagicResolvers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 「<b>现在，在这里，生效一次</b>」型投送（设计记忆 §19.3）。
 *
 * <h3>为什么一个类服务四种 X</h3>
 * <b>X1 直射 / X3 引导 / X4 附着 / X7 连发的投送动作完全相同</b> —— 都是"立即触发一次本段效果"。
 * 它们真正的差别在<b>别处</b>：
 * <ul>
 *   <li><b>是否逐段</b>：X3 每秒、X7 每 0.1s 由<b>施法状态机</b>反复调用本投送器（§21.1），
 *       X1 / X4 只调一次；</li>
 *   <li><b>消耗 / 冷却 / 释放 / 距离</b>：由 X 表的四个修正系数承担（§21.2）；</li>
 *   <li><b>结算节奏</b>：由 E 的语义与执行器承担（X4 的"慢热"是执行器上的持续效果）。</li>
 * </ul>
 * 把这四份"看起来不同的 X"写成四个等价类，只会让"改了 X1 忘了 X7"成为必然。
 * 差异既然全在数据与状态机里，投送动作就该只有一份。
 *
 * <p>X2 投射与 X5 布场不在此列：它们需要<b>世界里的载体</b>（飞行物 / 落点场），
 * 属 P5-B 的实体部分。
 */
public final class ImmediateDeliverer implements IDeliverer {

    private final String deliveryId;

    /**
     * @param deliveryId 本实例负责的 X id（注册表的键）
     */
    public ImmediateDeliverer(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    @Override
    public String deliveryId() {
        return deliveryId;
    }

    @Override
    public boolean deliver(MagicContext context, MagicDeliveryAction action) {
        if (!context.usable()) {
            return false;
        }
        action.fire(originOf(context));
        return true;
    }

    /**
     * 本次触发的原点：<b>施法者的眼睛位置</b>。
     *
     * <p>为什么不是脚下的原点：瞄准类形状的射线必须从眼睛出发，否则"看着怪打怪"会从胸口射出、
     * 近距离时穿过地面或卡在方块里。无施法者（自主法阵）退回上下文里的 origin。
     */
    static Vec3 originOf(MagicContext context) {
        Entity caster = context.caster();
        return MagicResolvers.eyeOf(caster, context.origin());
    }
}

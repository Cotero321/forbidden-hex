package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicSource;

import java.util.List;

/**
 * 载体（法杖）对一次施法的<b>数值调整与额外池记账</b>接口 —— 施法链的扩展点。
 *
 * <p><b>为什么放在 magic.cast 而不是让施法层认识法杖</b>：依赖倒置 ——
 * 施法链只依赖本抽象；法杖侧（{@code wand} 包）提供实现并注入。
 * 没有载体时它为 {@code null}，施法行为与接入前完全一致（调试入口零影响）。
 *
 * <p><b>实现方约定</b>：实现须是<b>一次施法一份</b>的临时对象（含可变的额外池记账字段），
 * 不得跨玩家共享（{@code recordExtraPaid/extraPaid} 是 decide 与调用方之间的回传通道）。
 */
public interface CastAdjustments {

    /** 本次施法的源（咒核决定 —— 定案 2；非空，施法链不再读玩家状态里的旧 source 键） */
    MagicSource source();

    /** 逐词墨（词序，与词一一对应；空串 = 该词无墨）—— 墨偏向杖端的逐词折扣依据 */
    List<String> wordInks();

    /** 本句涉及的墨集合（主墨 + 逐词墨去重）—— 墨轴 / 协同判定的判定域 */
    List<String> involvedInks();

    /** 消耗系数：全局 × 源限定 × 逐词墨加权（§10.2 先乘后夹） */
    float costMul(MagicSource source, int wordCount);

    /** 冷却系数：全局 × 源限定 × 攻防类别限定 × 连击第三发 */
    float cooldownMul(MagicSource source, MagicEffectDef.Category category, boolean thirdCast);

    /** 施法时间系数 */
    float castTimeMul();

    /** 施法距离系数 */
    float rangeMul();

    /** 法术半径系数 */
    float radiusMul();

    /** 效果威力系数：源限定专精作用域与配件修正 × 连击第三发（限防御类） */
    float powerMul(String effectId, MagicEffectDef.Category category, boolean thirdCast);

    /** 本次施法可用<b>额外池</b>余额（该源；法杖 NBT） */
    float extraAvailable(MagicSource source);

    /** decide 记账：本次首段从额外池实付的量（调用方据此扣法杖 NBT） */
    void recordExtraPaid(float amount);

    /** decide 记账的读取端 */
    float extraPaid();

    /** 是否处于连击第 3 发（30s 窗口内同一法术 —— 定案 3.4） */
    boolean thirdCast();
}

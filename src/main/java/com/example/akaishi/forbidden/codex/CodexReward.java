package com.example.akaishi.forbidden.codex;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * 学会一个秘典节点后给出的奖励（四种类型，示例节点各演示一种以上）。
 *
 * <p><b>为什么用一个 record 装四种奖励的字段</b>：奖励是"节点学会"这一个动作的结算，
 * 四种类型互斥且有各自专属参数；拆成四个子类会引入一层无收益的类型判断，
 * 而用一个 {@link Type} 标签 + 各类型只读自己那几个字段，读端一看 {@code type()} 就知道该看哪儿。
 * 未使用的字段一律由静态工厂填中性值（0 / null），禁止直接 new。
 *
 * <p><b>字段—类型对照</b>：
 * <ul>
 *   <li>{@link Type#SANC} —— 只用 {@link #sanc()}（例："学会安全的知识 +3"）；</li>
 *   <li>{@link Type#GIFT} —— 用 {@link #sanRestore()}（回理智）与 {@link #item()} / {@link #itemCount()}（给物品），两者可只用其一；</li>
 *   <li>{@link Type#UNLOCK} —— 只用 {@link #unlockKey()}：产出一枚"已解锁键"，供将来绑定配方/物品；</li>
 *   <li>{@link Type#RITUAL} —— 用 {@link #sanc()}（每次 +）与 {@link #ritualSanCost()}（每次扣）/ {@link #ritualMaxUses()}（次数上限），
 *       学习本身不给奖励，"奖励"是可反复使用的仪式。</li>
 * </ul>
 *
 * <p><b>物品以 Supplier 持有</b>：静态表在类初始化期构建，此时物品域可能尚未注册完，
 * 直接取 {@code ModItems.xxx.get()} 会读到 null；lambda 延迟到真正发放时求值（与
 * {@code AkaishiAltarRecipe} 同一套规避注册顺序的做法）。
 */
public record CodexReward(Type type, float sanc, float sanRestore, Supplier<Item> item, int itemCount,
                          ResourceLocation unlockKey, float ritualSanCost, int ritualMaxUses) {

    /** 奖励类型 */
    public enum Type {
        /** 学会即提升理智上限（SANC） */
        SANC,
        /** 给玩家专属奖励：回理智与/或给物品 */
        GIFT,
        /** 产出一枚"已解锁键"，供消费点查询 */
        UNLOCK,
        /** 学习后成为可重复使用的仪式（有次数上限，每次扣 SAN、加 SANC） */
        RITUAL
    }

    /** 学会即 +SANC */
    public static CodexReward sanc(float sanc) {
        return new CodexReward(Type.SANC, sanc, 0f, null, 0, null, 0f, 0);
    }

    /** 专属奖励：只回理智 */
    public static CodexReward giftSan(float sanRestore) {
        return new CodexReward(Type.GIFT, 0f, sanRestore, null, 0, null, 0f, 0);
    }

    /** 专属奖励：只给物品 */
    public static CodexReward giftItem(Supplier<Item> item, int count) {
        return new CodexReward(Type.GIFT, 0f, 0f, item, Math.max(1, count), null, 0f, 0);
    }

    /** 专属奖励：回理智 + 给物品 */
    public static CodexReward gift(float sanRestore, Supplier<Item> item, int count) {
        return new CodexReward(Type.GIFT, 0f, sanRestore, item, Math.max(1, count), null, 0f, 0);
    }

    /** 产出解锁键 */
    public static CodexReward unlock(ResourceLocation key) {
        return new CodexReward(Type.UNLOCK, 0f, 0f, null, 0, key, 0f, 0);
    }

    /**
     * 可重复仪式。
     *
     * @param sancGain  每次使用提升的理智上限
     * @param sanCost   每次使用扣除的当前理智（<b>待确认</b>：用户原话只给了 +5 SANC 与 5 次上限，
     *                  未指定代价；此处用节点自带常量，见 {@code CodexTable#RITUAL_SAN_COST} 的标注）
     * @param maxUses   使用次数上限
     */
    public static CodexReward ritual(float sancGain, float sanCost, int maxUses) {
        return new CodexReward(Type.RITUAL, sancGain, 0f, null, 0, null,
                Math.max(0f, sanCost), Math.max(0, maxUses));
    }
}

package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * SANC（理智上限）恢复来源：玩家首次使用某类物品时，永久抬升理智上限。
 *
 * <p><b>"首用类"是什么意思</b>：它和 {@link ISanityFirstEncounter} 的区别是——
 * 首见认的是<b>环境/接触</b>（见过、到过），首用认的是<b>行为</b>（用掉了某物）。
 * 二者都靠"一次性"来避免刷取，但首用的判定必须看到具体的物品栈（消耗品？哪个变体？），
 * 因此单独给一条 {@link #matches(Player, ItemStack)} 通道，而不是复用环境上下文。
 *
 * <p><b>一次性语义</b>：{@link #onceOnly()} 为真时，同一 id 对同一玩家只结算一次（由核心按 id 记档，
 * 与首见同一套存档容错口径：未知 id 原样保留、不清档）。
 * 为假时每次 {@code matches} 都结算，用于"可重复使用的上限提升物"（此时 {@link #sancAmount()} 通常很小）。
 *
 * <p><b>结算时机</b>：由内部实现层在玩家使用物品（右键 / 进食 / 完成对应动作）时调用并判定消耗；
 * 本接口只声明"什么算、给多少"，不负责扣物品——扣物品由实现层在结算成功后执行，避免附属在匹配阶段就改玩家状态。
 */
public interface ISanityRestoreSource {

    /** 来源 id，必须带自己的命名空间；{@link #onceOnly()} 为真时该 id 会进玩家存档（同首见的容错承诺） */
    ResourceLocation id();

    /**
     * 判定本次使用的物品是否命中本来源。
     *
     * <p>只读判定，禁止在此消耗物品或写玩家状态（结算成功后由实现层统一扣除）；
     * 抛异常由结算层隔离（本次视为未命中）。
     */
    boolean matches(Player player, ItemStack stack);

    /** 命中后对理智上限的增量（正=抬升；负值请勿使用，削减上限请走规则 / 临时削减机制） */
    float sancAmount();

    /** 是否一次性：true = 同 id 对同玩家只结算一次（记档）；false = 每次命中都结算 */
    boolean onceOnly();
}

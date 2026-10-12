package com.example.akaishi.forbidden.boss.agaitolos.summon;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.arena.ArenaGeometry;
import com.example.akaishi.forbidden.boss.agaitolos.arena.NetherPrisonArena;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * 阿盖托洛丝【下界本源】的<b>召唤仪式</b>（本轮的最小可用版本；多方块结构后续再补，见 {@link #structureValid}）。
 * <p>
 * <b>仪式口径</b>：在<b>下界</b>，对<b>哭泣黑曜石</b>（仪式锚点）手持<b>供奉物</b>右键。
 * 成功即消耗 1 件供奉物、在锚点正上方生成 BOSS（出场演出走既有 {@code animation.agaitolos.intro}，本类不复刻）；
 * 失败一律<b>给出双语回执且不消耗材料</b>。
 * <p>
 * <b>为什么没有复用母神祭坛（{@code AkaishiAltarRitual} / {@code AkaishiMotherAltarBlock}）</b> ——
 * 逐条核对过它的供奉流程，四条冲突使它做不了召唤入口（细节见设计记忆 §39）：
 * <ol>
 *   <li><b>供奉不是触发口，而是配方输入槽</b>：未成型祭坛右键只是把物品摆上去（可空手取回）；
 *       真正结算要"黑山羊三级祭坛成型 + 发射器注入生命能量 → 进度满 → 消耗 8 座外圈祭品 + 主祭品产出物品"。
 *       把召唤塞进那张配方表，等于把召唤门槛绑死在"三级多方块结构 + 能量物流"上，
 *       与用户"本轮先做仪式、多方块结构后续再补"直接冲突。</li>
 *   <li><b>没有玩家上下文</b>：{@code AkaishiAltarRitual#match} 是纯只读判定（其 javadoc 明写"拿不到是谁在供奉"），
 *       而"仅限下界召唤"要求"非下界给出回执、且不消耗材料"，回执必须有人可发。</li>
 *   <li><b>语义冲突</b>：那张表的产物恒为一个 {@code Item}（禁断饰品族），召唤既不产出物品、也不属于生命融合仪式。</li>
 *   <li><b>回归风险</b>：该族是生命线与 JEI 目录共用的类，为 BOSS 单点特判会同时改到"供奉物可自由取回"的既有语义。</li>
 * </ol>
 * <p>
 * <b>锚点为什么选哭泣黑曜石</b>：① 它在规格里就是下界牢狱的主材（"{@code 哭泣黑曜石掺杂下界合金块和灵魂沙}"），
 * 入口与场地同族；② 它是原版方块 ⇒ 本轮零新增方块/贴图/模型/掉落表/标签，不存在"缺图/死链"；
 * ③ 原版对哭泣黑曜石右键没有任何行为 ⇒ 拦截零冲突；④ 将来补多方块结构时，这块哭泣黑曜石天然就是结构锚点。
 */
public final class AgaitolosSummonRitual {

    /** 仪式锚点方块：哭泣黑曜石（原版既有方块，见类注释的选型理由） */
    public static final Block ANCHOR_BLOCK = Blocks.CRYING_OBSIDIAN;

    /**
     * 供奉物：<b>赤石凋零护符</b>（{@code forbidden_hex:forbidden_hex_wither_charm}，既有物品，不新增内容）。
     * <p>选它的理由：① 语义命中"凋零 + 下界"（配方 = 煤粉 + 灵魂沙，灵魂沙正是下界材料），
     * 与 BOSS 的凋零语汇、牢狱的灵魂沙/灵魂火纹饰同族；② 是可重复合成的护符而非唯一剧情道具，
     * 消耗 1 件作为召唤代价既不破坏别的系统，也不至于像"下界合金锭"那样昂贵到劝退。
     * <p><b>待用户确认</b>：换供奉物只需改本行（用 {@code Supplier} 延迟取值，避免类初始化早于 {@code ForbiddenMaterialItems.register()}）；
     * 例如改成下界合金锭就是 {@code () -> net.minecraft.world.item.Items.NETHERITE_INGOT}。
     */
    private static final Supplier<Item> OFFERING = () -> ForbiddenMaterialItems.witherCharm.get();

    /** 防重复召唤半径（格）：同一半径内已有该 BOSS 即拒绝。待调手感值 */
    public static final double DUPLICATE_RADIUS = 64.0D;

    /** 成功/拒绝回执的广播半径（格）。待调手感值 */
    private static final double ANNOUNCE_RANGE = 64.0D;

    private AgaitolosSummonRitual() {
    }

    /** 手持物是否是本仪式的供奉物（平台事件层用它决定"要不要接管这次右键"） */
    public static boolean isOffering(ItemStack stack) {
        if (stack.isEmpty() || ForbiddenMaterialItems.witherCharm == null) {
            return false;
        }
        return stack.is(OFFERING.get());
    }

    /**
     * 尝试召唤（仅服务端调用）。
     *
     * @return {@code true} = 本模组已接管这次交互（无论成功还是拒绝）；{@code false} = 不是本仪式的交互
     */
    public static boolean trySummon(ServerLevel level, BlockPos anchor, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isOffering(held)) {
            return false;
        }
        // ① 仅限地狱召唤：非下界一律拒绝，且不消耗材料、不召唤（规格 §0 第一行 / 定案表第 1 条）
        if (level.dimension() != Level.NETHER) {
            reply(level, anchor, player, "wrong_dimension");
            return true;
        }
        // ② 多方块结构闸口（本轮恒真，见 structureValid）
        if (!structureValid(level, anchor)) {
            reply(level, anchor, player, "structure_invalid");
            return true;
        }
        // ③ 体积试算：超过快照硬上限不硬干（设计 §4.2.1）
        if (ArenaGeometry.volume() > NetherPrisonArena.MAX_SNAPSHOT_ENTRIES) {
            reply(level, anchor, player, "too_large");
            return true;
        }
        // ④ 两道闸一起判（用户拍板：「只允许一座牢狱出现，且在牢狱消失之前 BOSS 无法再次被召唤」）：
        //   ④a 同一位置半径内已有 BOSS —— 只覆盖"已加载的实体"，故必须有 ④b 兜住远处/未加载的情形；
        //   ④b 该维度尚有一座未结束的牢狱（BUILD/ACTIVE/RESTORE 任一状态）—— 判据读 ArenaSnapshotStore
        //       （SavedData，落盘可见）⇒ 服务端重启后这道闸依然有效，不会出现"重启绕过限制、再召唤一座"。
        if (hasBossNear(level, anchor)) {
            reply(level, anchor, player, "boss_present");
            return true;
        }
        if (NetherPrisonArena.hasUnfinishedArena(level)) {
            reply(level, anchor, player, "already_present");
            return true;
        }
        // ⑤ 生成：位置 = 锚点正上方（出场演出会把它抬到悬停高度上方再降临，故无需在此摆位）
        AgaitolosEntity boss = AkaishiForbiddenEntities.AGAITOLOS.get().create(level);
        if (boss == null) {
            reply(level, anchor, player, "failed");
            return true;
        }
        boss.moveTo(anchor.getX() + 0.5D, anchor.getY() + 1.0D, anchor.getZ() + 0.5D,
                player.getYRot(), 0.0F);
        if (!level.addFreshEntity(boss)) {
            reply(level, anchor, player, "failed");
            return true;
        }
        // ⑥ 消耗 1 件供奉物（创造模式不消耗，与祭坛供奉的既有口径一致）
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        reply(level, anchor, player, "success");
        return true;
    }

    /**
     * <b>多方块结构校验钩子（后续接入点）</b>：本轮恒返回 {@code true}。
     * <p>用户已拍板「使用召唤仪式，后续补多方块结构」⇒ 结构判定<b>只在这一处</b>接：
     * 把"以哭泣黑曜石为锚点的下界多方块祭坛是否成型"写进本方法，结构不符返回 {@code false}，
     * 调用点 ② 会据此<b>拒绝召唤并回执 {@code message.forbidden_hex.agaitolos.summon.structure_invalid}</b>。
     * <p>刻意返回 {@code false} 的"副作用"为零：本方法只读世界（不许在此改方块/生成实体），
     * 这样将来加结构门槛不会顺带改变已有存档的场地快照。
     */
    public static boolean structureValid(ServerLevel level, BlockPos anchor) {
        return true;
    }

    /** 半径内是否已有该 BOSS（防"同一位置无限制刷"） */
    private static boolean hasBossNear(ServerLevel level, BlockPos anchor) {
        Vec3 center = Vec3.atCenterOf(anchor);
        double r2 = DUPLICATE_RADIUS * DUPLICATE_RADIUS;
        // 先取方盒再按球半径筛一遍（与实体侧击飞/场地查询同一手法）
        for (AgaitolosEntity boss : level.getEntitiesOfClass(AgaitolosEntity.class,
                AABB.ofSize(center, DUPLICATE_RADIUS * 2.0D, DUPLICATE_RADIUS * 2.0D, DUPLICATE_RADIUS * 2.0D))) {
            if (boss.distanceToSqr(center) <= r2) {
                return true;
            }
        }
        return false;
    }

    /** 双语回执：向锚点附近玩家广播一条 {@code message.forbidden_hex.agaitolos.summon.*} */
    private static void reply(ServerLevel level, BlockPos anchor, Player player, String key) {
        Component message = Component.translatable("message.forbidden_hex.agaitolos.summon." + key);
        player.sendSystemMessage(message);
        double rangeSqr = ANNOUNCE_RANGE * ANNOUNCE_RANGE;
        for (ServerPlayer other : level.players()) {
            if (other == player || other.distanceToSqr(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D) > rangeSqr) {
                continue;
            }
            other.sendSystemMessage(message);
        }
    }
}

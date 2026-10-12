package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.arena.NetherPrisonArena;
import com.example.akaishi.forbidden.boss.agaitolos.summon.AgaitolosSummonRitual;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 下界牢狱的<b>平台生效层</b>（Forge）：召唤仪式的右键入口 + 场地方块的不可破坏。
 * <p>
 * <b>为什么这两条必须落在 Forge 侧</b>：
 * <ol>
 *   <li><b>右键锚点</b>：原版"潜行且手上有物品"时会跳过方块自身的 {@code use()}，直接用物品放置，
 *       方块侧的钩子根本收不到这次交互；{@code RightClickBlock} 不受该门控影响（与终端微缩同一口径）。
 *       同时它也是唯一能在"方块 {@code use()} 之前"接管、并给出拒绝回执的位置。</li>
 *   <li><b>不可破坏</b>：common 层没有"破坏/爆炸"钩子；{@code BlockEvent.BreakEvent} 与
 *       {@code ExplosionEvent.Detonate} 是平台侧唯一能在方块被移除前拦下的入口。</li>
 * </ol>
 * 业务判定全部在 common（{@link AgaitolosSummonRitual} / {@link NetherPrisonArena}），本类只做接线与拦截。
 * <p>
 * 注册见 {@code AkaishiModForge} 的 forge 事件总线（与 {@code AgaitolosDoomHandler} 同一处）。
 */
public final class AgaitolosArenaEvents {

    public static final AgaitolosArenaEvents INSTANCE = new AgaitolosArenaEvents();

    private AgaitolosArenaEvents() {
    }

    /**
     * 召唤仪式入口：下界 + 哭泣黑曜石锚点 + 手持供奉物右键。
     * <p>成功与拒绝<b>都要</b>取消事件：拒绝时若不取消，物品（及后续可能的方块交互）会继续走原版流程，
     * 玩家会看到"回执说了不能召唤，但手上物品还是被用掉了"这种所见非所得。
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.getBlockState(event.getPos()).is(AgaitolosSummonRitual.ANCHOR_BLOCK)) {
            return;
        }
        if (!AgaitolosSummonRitual.isOffering(event.getEntity().getItemInHand(event.getHand()))) {
            return;
        }
        if (AgaitolosSummonRitual.trySummon(level, event.getPos(), event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
        }
    }

    /** 不可破坏①：牢狱范围内的方块禁止被玩家破坏（规格 §0「下界牢狱为不可破坏的牢狱方块」） */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && NetherPrisonArena.isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * 不可破坏②：把牢狱范围内的方块从爆炸波及表里剔除。
     * <p>与①分开的理由：{@code BreakEvent} 只管"玩家/工具挖"，TNT、凋零头、爬行者、其它模组引发的爆炸
     * 走的是 {@code Explosion#finalizeExplosion} 的同一张表，只有在这一层剔除才能真正做到"炸不穿"。
     */
    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf((BlockPos pos) -> NetherPrisonArena.isProtected(level, pos));
    }
}

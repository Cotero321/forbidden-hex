package com.example.akaishi.forbidden.life.altar;

import com.example.akaishi.forbidden.api.sanity.SanityServices;
import com.example.akaishi.forbidden.block.entity.AkaishiMotherAltarBlockEntity;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.multiblock.AkaishiGoatAltarTiersStructure;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinFirstEncounters;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 母神祭坛「生命融合仪式」：进度蓄满后按 {@link AkaishiAltarRecipe} 校验祭品，齐备即消耗祭品凝出产物。
 * <p>共 5 条配方（1 旧 + 4 新），靠巨坛供奉槽祭品区分；详见 {@link AkaishiAltarRecipe}。
 * <p>产物放回巨坛供奉槽（沿用悬浮旋转展示），玩家经界面或空手右键取回。
 * <p>注能期间每推进 10% 落一道纯视觉雷（子祭坛轮流，进度满时落巨坛中央），见 {@link #strikeProgressBolts}。
 */
public final class AkaishiAltarRitual {

    /** 旧配方基因纯度门槛（不含）：低于或等于该值的序列不被旧仪式接受 */
    public static final int MIN_GENE_PURITY = 50;
    /** 完成反馈的广播半径（格） */
    private static final double ANNOUNCE_RANGE = 64.0D;
    /** 仪式落雷档数：进度每推进 1/10 落一道，共 10 档（第 10 档即进度满） */
    private static final long BOLT_MILESTONES = 10L;
    /** 完成爆发时「不可名状」的时长（tick）：15s，足够玩家看完一轮强化表现 */
    private static final int BURST_DURATION = 300;
    /** 完成爆发时「不可名状」的等级：II 级（放大器 1），画面扭曲与呓语随之加剧 */
    private static final int BURST_AMPLIFIER = 1;

    /** 配方命中结果：命中的配方 + 参与消耗的外圈 8 座坐标 */
    public record Match(AkaishiAltarRecipe recipe, List<BlockPos> outer) {
    }

    private AkaishiAltarRitual() {
    }

    /**
     * 进度推进时的仪式落雷（仅服务端调用）。
     * <p>每跨过一个 10% 档位落一道纯视觉雷：第 1~9 档依次击中外圈 8 座子祭坛（超出按 {@code size} 循环），
     * 第 10 档（进度满）击中巨坛正中央，呼应"能量注入的节拍"。
     * <p>档位直接由进度推导，进度单调递增、结算成功后清零，故无需额外落雷状态，也不会重复触发。
     *
     * @param maxProgress 当前配方的蓄能阈值（新/旧配方不同，故不能取编译期常量）
     */
    public static void strikeProgressBolts(ServerLevel level, BlockPos hostPos,
                                           long before, long after, long maxProgress) {
        long step = Math.max(1L, maxProgress / BOLT_MILESTONES);
        long first = before / step + 1;
        long last = after / step;
        if (first > last) {
            return;
        }
        BlockPos origin = AkaishiGoatAltarTiersStructure.findOrigin(level, hostPos);
        if (origin == null) {
            return;
        }
        List<BlockPos> outer = AkaishiGoatAltarTiersStructure.collectOuterAltars(origin);
        for (long milestone = first; milestone <= last; milestone++) {
            if (milestone >= BOLT_MILESTONES) {
                // 第 10 档：进度满，雷落巨坛 2×2 交界处正上方
                strike(level, origin.getX() + 8.0D, origin.getY() + 2.0D, origin.getZ() + 8.0D);
            } else if (!outer.isEmpty()) {
                BlockPos target = outer.get((int) ((milestone - 1) % outer.size()));
                strike(level, target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
            }
        }
    }

    /** 落一道纯视觉雷电：有雷柱与雷声，但不点燃方块、不伤害实体，避免破坏祭坛结构或伤害玩家 */
    private static void strike(ServerLevel level, double x, double y, double z) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(x, y, z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /**
     * 只读匹配当前祭品配方（无任何消耗/产出副作用），供界面、氛围音与结算共用。
     * <p>遍历配方表，跳过等级不达标的配方（新四套需三级祭坛，D133），
     * 以主祭品谓词区分后逐座消耗外圈配额；全部配额恰好清空才算命中。
     *
     * @return 命中时返回配方 + 外圈坐标，否则返回 {@code null}
     */
    @Nullable
    public static Match match(ServerLevel level, BlockPos hostPos, AkaishiMotherAltarBlockEntity host) {
        ItemStack hostStack = host.getOffering();
        if (hostStack.isEmpty()) {
            return null;
        }
        BlockPos origin = AkaishiGoatAltarTiersStructure.findOrigin(level, hostPos);
        if (origin == null) {
            return null;
        }
        List<BlockPos> outer = AkaishiGoatAltarTiersStructure.collectOuterAltars(origin);
        if (outer.size() != AkaishiAltarRecipe.OUTER_SLOTS) {
            return null;
        }
        int tier = host.getStructureTier();
        // 【禁忌秘典·预留接线点（本轮默认不接）】将来若要用秘典产出的"已解锁键"锁住某条仪式，
        // 判定必须放在<b>知道玩家身份</b>的地方，而不是本方法里 —— 这里只认祭品，拿不到是谁在供奉。
        // 接法：在 AkaishiMotherAltarMenu 的供奉动作处（或先由方块实体记下供奉者 UUID，再回到本方法）
        // 调 CodexUnlocks.requireUnlock(player, 该配方对应的键)，不通过就不消耗祭品、不产出。
        // 键的命名约定见 CodexUnlocks（forbidden_hex:codex/<节点路径>）。
        // 现状：一行都不接 ⇒ 五条配方全部照旧可用，老存档玩法不变。
        for (AkaishiAltarRecipe recipe : AkaishiAltarRecipe.all()) {
            if (tier < recipe.requiredTier() || !recipe.hostOffering().test(hostStack)) {
                continue;
            }
            if (matchesOuter(level, outer, recipe)) {
                return new Match(recipe, outer);
            }
        }
        return null;
    }

    /** 逐座消耗外圈配额：每座子祭坛只能命中一类尚有余量的要求，全部要求恰好清空即通过 */
    private static boolean matchesOuter(ServerLevel level, List<BlockPos> outer, AkaishiAltarRecipe recipe) {
        List<AkaishiAltarRecipe.Requirement> requirements = recipe.outer();
        int[] remaining = new int[requirements.size()];
        for (int i = 0; i < remaining.length; i++) {
            remaining[i] = requirements.get(i).count();
        }
        for (BlockPos pos : outer) {
            if (!(level.getBlockEntity(pos) instanceof AkaishiMotherAltarBlockEntity altar) || !altar.hasOffering()) {
                return false;
            }
            ItemStack stack = altar.getOffering();
            boolean consumed = false;
            for (int i = 0; i < remaining.length; i++) {
                if (remaining[i] > 0 && requirements.get(i).test().test(stack)) {
                    remaining[i]--;
                    consumed = true;
                    break;
                }
            }
            if (!consumed) {
                return false;
            }
        }
        for (int left : remaining) {
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 只读预检：祭品配方是否齐备（含巨坛供奉槽与外圈 8 座）。
     * 仅做判定，不消耗也不产出，供界面进度条显示与氛围音"工作中"档位使用。
     */
    public static boolean isRecipeReady(ServerLevel level, BlockPos hostPos, AkaishiMotherAltarBlockEntity host) {
        return match(level, hostPos, host) != null;
    }

    /**
     * 尝试结算仪式（仅服务端调用）。
     *
     * @param hostPos 巨坛主座坐标（结构原点 offset(7,0,7)）
     * @return true = 配方齐备并已完成消耗与产出；false = 配方不齐，进度保持当前值冻结等待补齐
     */
    public static boolean tryComplete(ServerLevel level, BlockPos hostPos, AkaishiMotherAltarBlockEntity host) {
        Match match = match(level, hostPos, host);
        if (match == null) {
            return false;
        }
        // 消耗外圈 8 件，巨坛供奉槽换为产物
        for (BlockPos pos : match.outer()) {
            if (level.getBlockEntity(pos) instanceof AkaishiMotherAltarBlockEntity altar) {
                altar.takeOffering();
            }
        }
        host.setOffering(new ItemStack(match.recipe().output().get()));
        announce(level, hostPos, match.recipe());
        return true;
    }

    /**
     * 仪式完成反馈：向巨坛附近的玩家施加强化「不可名状」爆发并散出粒子。
     * <p>四套新配方各播报专属仪式名（D176）；旧配方维持原「纯表现无文本」口径，不新增广播。
     */
    private static void announce(ServerLevel level, BlockPos hostPos, AkaishiAltarRecipe recipe) {
        double cx = hostPos.getX() + 0.5D;
        double cy = hostPos.getY() + 0.5D;
        double cz = hostPos.getZ() + 0.5D;
        double rangeSqr = ANNOUNCE_RANGE * ANNOUNCE_RANGE;
        String ritualKey = recipe.ritualName();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(cx, cy, cz) > rangeSqr) {
                continue;
            }
            // 完成爆发：叠加 II 级「不可名状」，客户端画面扭曲/噪点/低语文字随之加剧
            // 套装集齐者在所有施加入口统一 +1 级（D73/D191）
            player.addEffect(new MobEffectInstance(AkaishiForbiddenEffects.UNNAMEABLE.get(),
                    BURST_DURATION, BURST_AMPLIFIER, true, false, true));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    AkaishiForbiddenSounds.UNNAMEABLE_WHISPER.get(), SoundSource.PLAYERS, 1.0F, 0.85F);
            if (ritualKey != null) {
                player.sendSystemMessage(Component.translatable("message.forbidden_hex.altar.ritual.done",
                        Component.translatable(ritualKey)));
            }
            // 首见：初次完成母神祭坛仪式（行为类，环境轮询表达不了"完成了一次仪式"；
            // 是否首次由理智系统按玩家存档判定，本处只负责上报"发生了什么"）。
            // P3d 拆除 P3b 的 AltarRitualHooks 临时桥后直调理智服务（祭坛与理智现已同模块，语义不变）。
            SanityServices.get().reportFirstEncounter(player, SanityBuiltinFirstEncounters.MOTHER_ALTAR);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                hostPos.getX() + 0.5D, hostPos.getY() + 1.5D, hostPos.getZ() + 0.5D,
                48, 1.0D, 0.6D, 1.0D, 0.04D);
    }
}

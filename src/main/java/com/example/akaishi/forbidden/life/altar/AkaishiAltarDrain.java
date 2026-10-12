package com.example.akaishi.forbidden.life.altar;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.block.entity.AkaishiMotherAltarBlockEntity;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.effect.AkaishiDrainHooks;
import com.example.akaishi.effect.ScreenFlashS2C;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 母神祭坛「仪式吸取」（③ 阶段，D146~D182）。
 * <p>配方齐备期间由主座持续抽取周边生物的生命，按理论抽血量折算生命能量灌入巨坛，
 * 与发射器注能并存为双通道（D152），发射器链路零改动。
 * <ul>
 *   <li>半径 {@code altarDrainRadius}、间隔 {@code altarDrainIntervalTicks}、单次最多 {@code altarDrainMaxTargets} 只（按距离由近到远，D163）</li>
 *   <li>每只抽 {@code max(最大生命 × altarDrainHealthPercent, 1)} 点血，残血即抽干致死（D175）</li>
 *   <li>能量按<b>理论</b>抽血量折算（D178），单只单次封顶 {@code altarDrainMaxEnergyPerTarget}（D174）</li>
 *   <li>被吸死 = 无掉落、无经验、不算击杀（D158，经 {@link AkaishiDrainHooks} 交由平台侧豁免）</li>
 *   <li>伤害类型 {@code forbidden_hex:altar_drain}：穿甲 + 无视无敌帧 + 无击退（D179）</li>
 * </ul>
 */
public final class AkaishiAltarDrain {

    /** 自定义伤害类型键：数据层见 {@code data/forbidden_hex/damage_type/altar_drain.json} */
    public static final ResourceKey<DamageType> ALTAR_DRAIN = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(AkaishiMod.MOD_ID, "altar_drain"));

    /** 被吸玩家反馈（聊天低语 + 泛红）的节流间隔（tick）：5s（D180） */
    private static final int WARN_INTERVAL = 100;
    /** 泛红持续时长（tick）：与侵蚀跑满同一强度与时长 */
    private static final int WARN_FLASH_TICKS = 90;
    /** 节流记录的留存时长（tick）：超时剔除，避免玩家历史 UUID 无限堆积 */
    private static final long WARN_RETENTION = 12_000L;
    /** 粒子连线取样点数：沿"目标 → 巨坛"直线撒点，兼顾可见性与发包量 */
    private static final int LINK_POINTS = 4;

    /** 被吸玩家 → 上次反馈的游戏刻（节流用，并发安全） */
    private static final Map<UUID, Long> LAST_WARN = new ConcurrentHashMap<>();

    private AkaishiAltarDrain() {
    }

    /** 构造吸取伤害源（伤害类型注册于数据包，须运行期从注册表取 holder） */
    public static DamageSource source(ServerLevel level) {
        Registry<DamageType> registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return new DamageSource(registry.getHolderOrThrow(ALTAR_DRAIN));
    }

    /**
     * 执行一次吸取（仅服务端、仅主座调用，调用方须已确认成型 + 祭品齐备）。
     *
     * @return 本次实际回灌巨坛的生命能量
     */
    public static long drain(ServerLevel level, BlockPos hostPos, AkaishiMotherAltarBlockEntity host) {
        if (!ModConfig.altarDrainEnabled || ModConfig.altarDrainMaxTargets <= 0) {
            return 0L;
        }
        double cx = hostPos.getX() + 0.5D;
        double cy = hostPos.getY() + 0.5D;
        double cz = hostPos.getZ() + 0.5D;
        double radius = Math.max(1.0D, ModConfig.altarDrainRadius);
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius))) {
            if (entity.isAlive() && !entity.isSpectator() && isDrainable(entity)) {
                targets.add(entity);
            }
        }
        if (targets.isEmpty()) {
            return 0L;
        }
        targets.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(cx, cy, cz)));
        DamageSource source = source(level);
        long total = 0L;
        int limit = Math.min(ModConfig.altarDrainMaxTargets, targets.size());
        for (int i = 0; i < limit; i++) {
            long energy = drainOne(level, hostPos, targets.get(i), source);
            if (energy <= 0L) {
                continue;
            }
            long before = host.getProgress();
            long absorbed = host.absorbLifeEnergy(energy);
            total += absorbed;
            // 满值即停吸（D173）：进度回落说明本刻已结算清零，或已无处可灌，剩余目标不再抽取
            if (absorbed <= 0L || host.getProgress() < before) {
                break;
            }
        }
        if (total > 0L) {
            // 低语音效变调：每次吸取随机微调音调，避免机械重复感（D159）
            level.playSound(null, cx, cy, cz, AkaishiForbiddenSounds.UNNAMEABLE_WHISPER.get(), SoundSource.BLOCKS,
                    0.8F, 0.55F + level.random.nextFloat() * 0.25F);
            cleanWarnTable(level.getGameTime());
        }
        return total;
    }

    /** 可吸目标：全部生物含玩家（D147），仅豁免创造模式玩家（D151）；装饰性盔甲架不参与，避免误毁 */
    private static boolean isDrainable(LivingEntity entity) {
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Player player
                && ModConfig.altarDrainExemptCreative && player.isCreative()) {
            return false;
        }
        return true;
    }

    /** 抽取单只：返回按理论抽血量折算的能量（D178），封顶 {@code altarDrainMaxEnergyPerTarget}（0 = 不限制） */
    private static long drainOne(ServerLevel level, BlockPos hostPos, LivingEntity target, DamageSource source) {
        float current = target.getHealth();
        if (current <= 0.0F) {
            return 0L;
        }
        // 理论抽血量：最大生命 × 比例，至少 1 点——既是掉血量上限，也是计能依据（D178）
        // 比例已被配置钳制在 [0,1]，故 maxHealth < 1 时结果必被外层下限抬到 1，无需再夹一次 maxHealth
        float theoretical = Math.max(target.getMaxHealth() * (float) ModConfig.altarDrainHealthPercent, 1.0F);
        // 实际抽血量：残血时抽干，不足即致死（D175）
        float actual = Math.min(theoretical, current);
        boolean lethal = actual >= current;
        if (lethal) {
            AkaishiDrainHooks.markDrained(target);
        }
        target.hurt(source, actual);
        if (lethal && target.isAlive()) {
            // 兜底：目标因无敌等原因未死，撤销标记，避免其日后正常死亡被误吞掉落
            AkaishiDrainHooks.clearDrained(target);
        }
        linkParticles(level, hostPos, target);
        if (target instanceof ServerPlayer player) {
            warnDrainedPlayer(level, player);
        }
        long energy = (long) (theoretical * (double) ModConfig.altarDrainEnergyPerHp);
        long cap = ModConfig.altarDrainMaxEnergyPerTarget;
        return cap > 0L ? Math.min(cap, energy) : energy;
    }

    /** 红色粒子连线：自被吸目标指向巨坛（D155） */
    private static void linkParticles(ServerLevel level, BlockPos hostPos, LivingEntity target) {
        double tx = target.getX();
        double ty = target.getY() + target.getBbHeight() * 0.5D;
        double tz = target.getZ();
        double dx = hostPos.getX() + 0.5D - tx;
        double dy = hostPos.getY() + 0.5D - ty;
        double dz = hostPos.getZ() + 0.5D - tz;
        for (int i = 1; i <= LINK_POINTS; i++) {
            double t = (double) i / (LINK_POINTS + 1);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    tx + dx * t, ty + dy * t, tz + dz * t, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 被吸玩家反馈（D180）：聊天低语 + 屏幕边缘泛红，按 {@link #WARN_INTERVAL} 节流 */
    private static void warnDrainedPlayer(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        Long last = LAST_WARN.get(player.getUUID());
        if (last != null && now - last < WARN_INTERVAL) {
            return;
        }
        LAST_WARN.put(player.getUUID(), now);
        player.sendSystemMessage(Component.translatable("message.forbidden_hex.altar.drain_warning"));
        ScreenFlashS2C.sendToPlayer(player, WARN_FLASH_TICKS, 1.0F);
    }

    /** 剔除长时间未再被吸取的记录，避免节流表随历史玩家无限增长 */
    private static void cleanWarnTable(long now) {
        LAST_WARN.entrySet().removeIf(entry -> now - entry.getValue() > WARN_RETENTION);
    }
}

package com.example.akaishi.forbidden.api.magic;

import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Set;

/**
 * 魔法服务注册中心（接口隔离 + 依赖倒置）：
 * 实现层注册具体实现，业务层与附属只依赖 {@link IMagicService} 抽象。
 *
 * <p>与 {@code SanityServices} 同一形态：未注册时返回<b>只读空实现兜底</b>，
 * 保证 {@link #get()} <b>永不返回 null</b> —— 魔法系统尚未启用时，
 * 附属的读代码不会 NPE，只是读到 0 / 写入无效。
 */
public final class MagicServices {

    /**
     * 魔法 API 面版本（不是服务的实现版本）。
     *
     * <p><b>用途</b>：附属可对自己的编译期基线做断言 ——
     * {@code if (MagicServices.API_VERSION < 2) return;} 用于在旧核心上安全降级。
     * 只要方法是<b>追加</b>（不删改既有签名）就不必自增；删除或改语义时必须自增。
     */
    public static final int API_VERSION = 1;

    /** 未注册时的兜底：读全为 0 / 空集，写全部无效，保证调用方永远拿到非 null */
    private static final IMagicService FALLBACK = new IMagicService() {
        @Override
        public MagicValues values(Player player) {
            return MagicValues.ABSENT;
        }

        @Override
        public Set<String> unlockedWords(Player player) {
            return Set.of();
        }

        @Override
        public boolean isUnlocked(Player player, String wordId) {
            return false;
        }

        @Override
        public boolean unlockWord(Player player, String wordId) {
            return false;
        }

        @Override
        public boolean setLevel(Player player, int level) {
            return false;
        }

        @Override
        public void refillPools(Player player, float ratio) {
        }

        @Override
        public CastOutcome cast(Player player, List<String> wordIds, String mainInkId, int overlayInks,
                float channelSeconds, int volleyShots) {
            return CastOutcome.rejected(CastResult.SERVICE_UNAVAILABLE);
        }
    };

    private static volatile IMagicService instance;

    private MagicServices() {
    }

    /** 注册实现（重复注册以最后一次为准）—— 由实现层调用，附属不要调 */
    public static void register(IMagicService service) {
        if (service != null) {
            instance = service;
        }
    }

    /** 取当前实现；未注册时返回只读空实现兜底（永不返回 null） */
    public static IMagicService get() {
        IMagicService s = instance;
        return s != null ? s : FALLBACK;
    }

    /** 魔法系统是否已就绪（实现层已注册）。附属在依赖魔法数值前建议先断言此值 */
    public static boolean isAvailable() {
        return instance != null;
    }
}

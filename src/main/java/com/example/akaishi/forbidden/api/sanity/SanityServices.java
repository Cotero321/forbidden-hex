package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 理智服务注册中心（接口隔离 + 依赖倒置）：
 * 内部实现层注册具体实现，业务层与附属只依赖 {@link ISanityService} 抽象。
 *
 * <p>与 {@code ValueServices} 同一形态：未注册时返回<b>只读空实现兜底</b>，
 * 保证 {@link #get()} 永不返回 null——理智系统尚未启用时，附属的读代码不会 NPE，
 * 只是读到 0 / 写入无效。
 */
public final class SanityServices {

    /**
     * 理智 API 面版本（不是服务的实现版本）。
     *
     * <p><b>用途</b>：附属可对自己的编译期基线做断言——{@code if (SanityServices.API_VERSION < 2) return;}
     * 用于在旧核心上安全降级，而不是抛异常或直接崩。
     * 只要方法是<b>追加</b>（不删改既有签名）就不必自增；删除或改语义时必须自增。
     */
    public static final int API_VERSION = 1;

    /** 未注册时的零值兜底：读全为 0，写全部无效，保证调用方永远拿到非 null */
    private static final ISanityService FALLBACK = new ISanityService() {
        @Override
        public SanityValues snapshot(Player player) {
            return SanityValues.ZERO;
        }

        @Override
        public float getSan(Player player) {
            return 0f;
        }

        @Override
        public float getSanc(Player player) {
            return 0f;
        }

        @Override
        public float getCog(Player player) {
            return 0f;
        }

        @Override
        public float getProtection(Player player) {
            return 0f;
        }

        @Override
        public float getTempCut(Player player) {
            return 0f;
        }

        @Override
        public float getEffectiveMax(Player player) {
            return 0f;
        }

        @Override
        public void setSan(Player player, float value) {
        }

        @Override
        public void setSanc(Player player, float value) {
        }

        @Override
        public void setCog(Player player, float value) {
        }

        @Override
        public void setProtection(Player player, float value) {
        }

        @Override
        public void setTempCut(Player player, float value) {
        }

        @Override
        public void addSan(Player player, float delta) {
        }

        @Override
        public void addSanc(Player player, float delta) {
        }

        @Override
        public void addCog(Player player, float delta) {
        }

        @Override
        public void addProtection(Player player, float delta) {
        }

        @Override
        public void addTempCut(Player player, float delta) {
        }

        @Override
        public boolean reportFirstEncounter(Player player, ResourceLocation encounterId) {
            return false;
        }

        @Override
        public int version() {
            return 0;
        }
    };

    private static volatile ISanityService instance;

    private SanityServices() {
    }

    /** 注册实现（重复注册以最后一次为准，便于配置热重载时替换）——由内部实现层调用，附属不要调 */
    public static void register(ISanityService service) {
        if (service != null) {
            instance = service;
        }
    }

    /** 取当前实现；未注册时返回只读空实现兜底（永不返回 null） */
    public static ISanityService get() {
        ISanityService s = instance;
        return s != null ? s : FALLBACK;
    }

    /** 当前实现版本号；未注册时 0 */
    public static int version() {
        return get().version();
    }

    /** 理智系统是否已就绪（实现层已注册）。附属在依赖理智数值前建议先断言此值 */
    public static boolean isAvailable() {
        return instance != null;
    }
}

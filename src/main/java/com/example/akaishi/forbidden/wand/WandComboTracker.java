package com.example.akaishi.forbidden.wand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 30 秒连击账本（定案 3.4）：30s 内连续施放<b>同一法术</b>才累计第 2/3 次；
 * 超时或换法术即重置。崩解 / 壁垒核的"第三次"机制读它。
 *
 * <p><b>"同一法术"的判据</b>：词序列（逗号拼接）—— 同一句式无论从哪根杖放出都算同一法术，
 * 换杖不重置（连击是玩家的手感，不是物品的）。
 *
 * <p>只在服务端主线程读写，但仍用并发容器守住调试命令等其他线程查询的情形。
 */
public final class WandComboTracker {

    /** 连击窗口（tick）：30 秒 */
    public static final int WINDOW_TICKS = 30 * 20;

    private static final Map<UUID, String> LAST_SPELL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COUNT = new ConcurrentHashMap<>();

    private WandComboTracker() {
    }

    /**
     * 试算：若此刻施放 {@code spellKey}，将是连击第几发（1 = 第一发）。
     * <b>只查不记</b> —— 记录仅在施法成功后由 {@link #commit} 落账（失败不消耗连击）。
     */
    public static int peek(UUID playerId, String spellKey, long gameTime) {
        String last = LAST_SPELL.get(playerId);
        Long time = LAST_TIME.get(playerId);
        Integer count = COUNT.get(playerId);
        if (last != null && last.equals(spellKey) && time != null && count != null
                && gameTime - time <= WINDOW_TICKS) {
            return count + 1;
        }
        return 1;
    }

    /** 施法成功后落账（记下 peek 的结果） */
    public static void commit(UUID playerId, String spellKey, long gameTime) {
        int next = peek(playerId, spellKey, gameTime);
        LAST_SPELL.put(playerId, spellKey);
        LAST_TIME.put(playerId, gameTime);
        COUNT.put(playerId, next);
    }

    /** 离线清理（防泄漏；结算节拍兜底调用） */
    public static void clear(UUID playerId) {
        LAST_SPELL.remove(playerId);
        LAST_TIME.remove(playerId);
        COUNT.remove(playerId);
    }

    /** 按服务器在线名单回收离线者的连击记录（结算节拍兜底调用） */
    public static void cleanupOffline(net.minecraft.server.MinecraftServer server) {
        if ((LAST_SPELL.isEmpty() && LAST_TIME.isEmpty() && COUNT.isEmpty()) || server == null) {
            return;
        }
        LAST_SPELL.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        LAST_TIME.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        COUNT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }
}

package com.example.akaishi.forbidden.sanity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阈值惩罚的「上限削减账本」：<b>逐档独立记账</b>的一份持久化小的状态。
 *
 * <p><b>为什么不能只存一个 tempCut</b>：本期口径是"每档独立记账、可叠加、可单独取消、可单独到期"——
 * 单值 {@code tempCut} 反推不出"当前是哪几档在削、各削多少、各自还剩多久"，
 * 于是"回升离开某档只取消那一份"和"某档 5 分钟到期自动移除"两件事都无法表达。
 * 账本按档位（下标对齐 {@link SanityThresholds#LEVELS}）存 {@code (档位, 削减额, 到期刻)}，
 * 总量 = 各档之和，即 {@code tempCut}。
 *
 * <p><b>为什么还要 {@code credited} 位掩码</b>：档位贡献有<b>寿命</b>（{@value #CUT_DURATION_TICKS} tick），
 * 到期即移除；但玩家可能一直待在同一档里 —— 若每 tick 按"当前百分比该不该削"重新补发，
 * 计时就永远重置、5 分钟形同虚设。位掩码记录"这一档<b>本次逗留期内已经发过一份</b>"，
 * 因此：到期后不会被补发；只有<b>真的离开该档再回来</b>（离开时清位）才重新发一份。
 * 掩码同时让"登录 / 死亡重生后立刻补齐当前档位"成为可能（掩码会随存档落盘）。
 *
 * <p><b>存档容错</b>（对齐 {@link SanityState} 的既有承诺）：
 * <ul>
 *   <li>未知档位的条目<b>原样保留</b>（读进来的档位值不查表、不丢弃，退出时原样写回），
 *       并计入总量 —— 避免"存了却不生效"的死数据；</li>
 *   <li>缺键安全默认：{@code level} 缺键的条目跳过；{@code amount} 缺键按 0；{@code expire} 缺键按 0
 *       （= 已到期，下一次结算即移除）；</li>
 *   <li>全流程不抛异常、不中断登录。</li>
 * </ul>
 */
public final class SanityCutLedger {

    /** NBT 键（位于躯体 capability 的 {@code sanity} 段内） */
    public static final String TAG_CUTS = "threshold_cuts";
    /** 已发放档位的位掩码（int；位下标 = {@link SanityThresholds#LEVELS} 下标） */
    public static final String TAG_CREDITED = "threshold_credited";
    /**
     * "本账本最近一次写出的 tempCut"（float，随存档落盘）。
     *
     * <p><b>为什么必须落盘</b>：它是"这个 tempCut 是我们写的，可以安全覆盖/清零"的唯一凭据
     * （写回判据见 {@code SanityPenaltySettlement#settle} 的 {@code ours}）。
     * 若不落盘，玩家在低档下线、账本条目又在离线/运行期过期被清（{@code tempCut} 却按旧值留在存档里），
     * 重登后"总量=0 而 tempCut>0"会被判成"不是我们写的"从而<b>永不清零</b> —— 上限被永久夹住（幽灵削减）。
     * 落盘后可复现"上次写出的值"，无论条目是被在线过期还是离线过期清掉，都能自愈。
     */
    public static final String TAG_APPLIED = "threshold_applied";
    private static final String TAG_LEVEL = "level";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_EXPIRE = "expire";

    /** 每档贡献的存活时长（tick）：6000 = 5 分钟（待调手感值） */
    public static final int CUT_DURATION_TICKS = 6000;

    /**
     * 各档进入时的一次性削减基数（待调手感值），下标对齐 {@link SanityThresholds#LEVELS}
     * （80/60/40/20/0 ⇒ 5/10/15/20/25）；实际削减 = 基数 × {@link SanityCogCurve#sancEfficiency}(cog)。
     */
    public static final float[] CUT_BASE = {5f, 10f, 15f, 20f, 25f};

    /** 档位值 → 条目；键为百分比档位（未知档位同样原样持有） */
    private final Map<Float, Entry> entries = new LinkedHashMap<>();
    /** 已发放掩码 */
    private int credited;
    /** 本账本最近一次写出的总量（凭据，落盘见 {@link #TAG_APPLIED}）：用于"只清理自己写下的值" */
    private float applied;

    /** 单档条目：削减额 + 到期刻（游戏刻） */
    private static final class Entry {
        private float amount;
        private long expire;

        Entry(float amount, long expire) {
            this.amount = amount;
            this.expire = expire;
        }
    }

    // ------------------------------------------------------------------
    // 记账操作
    // ------------------------------------------------------------------

    /** 发放/刷新某档的一份贡献（同档重复进入 ⇒ 覆盖刷新计时与金额，<b>不叠加第二份</b>） */
    public void grant(int index, float amount, long expireTick) {
        float level = levelOf(index);
        if (level < 0f || amount <= 0f) {
            return;
        }
        entries.put(level, new Entry(amount, expireTick));
    }

    /** 取消某档的贡献；返回是否真的移除了条目 */
    public boolean revoke(int index) {
        float level = levelOf(index);
        return level >= 0f && entries.remove(level) != null;
    }

    /** 该档当前是否还有（未过期的）条目 */
    public boolean has(int index) {
        float level = levelOf(index);
        return level >= 0f && entries.containsKey(level);
    }

    /** 移除已到期条目（逐档过期判定，<b>唯一</b>的过期落点） */
    public void pruneExpired(long now) {
        if (entries.isEmpty()) {
            return;
        }
        entries.entrySet().removeIf(e -> e.getValue().expire <= now);
    }

    /**
     * 减免总量（击杀奖励的"恢复 5 点临时 SANC"走这里）。
     *
     * <p>从<b>最深档</b>（下标最大 = 最靠近 0%）开始扣减，扣到 0 的条目直接移除但<b>保留 credited 位</b>：
     * 奖励在本次逗留期内有效，不会被下一次结算补发掉。
     */
    public void relieve(float amount) {
        float remaining = amount;
        List<Float> keys = new ArrayList<>(entries.keySet());
        // 档位值升序（80 → 0）等价于下标升序（0 → 4），从尾部（最深档）往前扣
        keys.sort((a, b) -> Float.compare(indexOf(b), indexOf(a)));
        for (Float key : keys) {
            if (remaining <= 0f) {
                break;
            }
            Entry entry = entries.get(key);
            if (entry == null) {
                continue;
            }
            float taken = Math.min(entry.amount, remaining);
            entry.amount -= taken;
            remaining -= taken;
            if (entry.amount <= 0f) {
                entries.remove(key);
            }
        }
    }

    /** 清零（死亡重生清临时值、调试用） */
    public void clear() {
        entries.clear();
        credited = 0;
        applied = 0f;
    }

    /** 各档贡献之和 = 本账本贡献的 tempCut */
    public float total() {
        float sum = 0f;
        for (Entry entry : entries.values()) {
            sum += entry.amount;
        }
        return sum;
    }

    /** 已发放掩码/总量等只读视图（诊断指令用） */
    public int creditedMask() {
        return credited;
    }

    public boolean credited(int index) {
        return index >= 0 && (credited & (1 << index)) != 0;
    }

    public void setCredited(int index, boolean value) {
        if (index < 0) {
            return;
        }
        if (value) {
            credited |= 1 << index;
        } else {
            credited &= ~(1 << index);
        }
    }

    /** 本账本最近一次写出的 tempCut 值（落盘见 {@link #TAG_APPLIED}） */
    public float applied() {
        return applied;
    }

    public void setApplied(float value) {
        this.applied = Math.max(0f, value);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** 只在有内容时写入（空账本不落盘） */
    public ListTag save() {
        ListTag list = new ListTag();
        for (Map.Entry<Float, Entry> e : entries.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putFloat(TAG_LEVEL, e.getKey());
            item.putFloat(TAG_AMOUNT, e.getValue().amount);
            item.putLong(TAG_EXPIRE, e.getValue().expire);
            list.add(item);
        }
        return list;
    }

    /**
     * 读档：未知档位原样保留，缺键走安全默认，任何非法条目跳过而不抛异常。
     *
     * @param creditedMask 已发放位掩码
     * @param applied      上次写出的 tempCut（{@link #TAG_APPLIED}）；老存档缺该键时由调用方给出兜底
     *                     （账本非空则以存档里的 tempCut 兜底，使旧档已存在的幽灵削减也能自愈）
     */
    public void load(ListTag list, int creditedMask, float applied) {
        entries.clear();
        credited = creditedMask;
        this.applied = Math.max(0f, Float.isFinite(applied) ? applied : 0f);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag item) || !item.contains(TAG_LEVEL)) {
                continue;
            }
            float level = item.getFloat(TAG_LEVEL);
            if (!Float.isFinite(level) || level < 0f || level > 100f) {
                continue;
            }
            float amount = Math.max(0f, item.getFloat(TAG_AMOUNT));
            if (amount <= 0f) {
                continue; // 0 额条目无信息量
            }
            entries.put(level, new Entry(amount, Math.max(0L, item.getLong(TAG_EXPIRE))));
        }
    }

    // ------------------------------------------------------------------
    // 档位 ↔ 下标
    // ------------------------------------------------------------------

    /** 下标 → 档位百分比；越界返回 -1 */
    private static float levelOf(int index) {
        return index >= 0 && index < SanityThresholds.LEVELS.length ? SanityThresholds.LEVELS[index] : -1f;
    }

    /** 档位百分比 → 下标；非核心档位返回 -1（未知档位仍会被保留，只是不参与核心记账） */
    public static int indexOfLevel(float level) {
        for (int i = 0; i < SanityThresholds.LEVELS.length; i++) {
            if (SanityThresholds.LEVELS[i] == level) {
                return i;
            }
        }
        return -1;
    }

    /** 档位值 → 下标（用于排序；未知档位排到最浅处） */
    private static int indexOf(float level) {
        int index = indexOfLevel(level);
        return index < 0 ? -1 : index;
    }
}

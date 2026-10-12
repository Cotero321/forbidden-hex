package com.example.akaishi.forbidden.api.magic;

import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Set;

/**
 * 魔法侧对外服务契约（设计记忆 §11）。实现在 {@code forbidden.magic.MagicServiceImpl}，
 * 经 {@link MagicServices#get()} 取用。
 *
 * <p><b>签名纪律</b>：本接口发布后<b>零改动</b>（只允许加方法）。附属只许 import {@code api.magic}。
 *
 * <p><b>服务端权威</b>：所有"变更类"方法（解锁 / 改等级 / 施法）只应在服务端调用；
 * 客户端要显示当前值请读 {@link MagicValues}（由 S2C 快照填充客户端镜像）。
 */
public interface IMagicService {

    /** 纯编译预览，不扣费；旧 cast 接口在执行适配完成前继续保留。 */
    default SpellCompileResult compile(SpellInput input) {
        return SpellPlans.compile(input);
    }

    // ==================== 查询（两端均可调用） ====================

    /**
     * 取玩家当前状态快照。
     *
     * <p>返回的实例与该玩家的内部状态<b>脱钩</b>（不可变），可安全跨线程读。
     * 模块未安装时由门面返回 {@link MagicValues#ABSENT}，故<b>永不返回 null</b>。
     */
    MagicValues values(Player player);

    /** 已解锁的词 id 集合（只读快照；含未知 id —— 附属卸载后不清档）。模块未安装时为空集 */
    Set<String> unlockedWords(Player player);

    /** 该词是否已解锁 */
    boolean isUnlocked(Player player, String wordId);

    // ==================== 变更（仅服务端） ====================

    /**
     * 解锁一个词（幂等）。
     *
     * @return true 仅当<b>真的新增</b>（重复解锁返回 false）
     */
    boolean unlockWord(Player player, String wordId);

    /**
     * 设置等级（夹取到 [1, 6]）。
     *
     * <p>等级同时决定<b>词数上限</b>与<b>池容量</b>；升高的池容量不会自动补满。
     *
     * @return true 表示等级确实发生了变化
     */
    boolean setLevel(Player player, int level);

    /** 把三池补满到当前容量的 [0, 1] 比例（1.0 = 全满；调试 / 药剂用） */
    void refillPools(Player player, float ratio);

    // ==================== 施法 ====================

    /**
     * 提交一次施法：<b>解析 → 判定 → 公式结算 → 门槛校验 → 扣费</b>，
     * 并返回结果与数值快照。**不产生实际效果** —— 效果由执行层（P5）据快照产生。
     *
     * <p>校验顺序（先廉后贵，且拒绝一律发生在扣费之前）：
     * ① 句法（空 / 未知词 / 段溢出 / 无核心）；② 冲突；③ 解锁；④ 词数上限；
     * ⑤ 冷却 / 单槽 / 过载；⑥ 星墨源约束；⑦ 池与 SAN 门槛。
     *
     * @param wordIds        词 id 序列（按玩家书写顺序；由内部按角色归位）
     * @param mainInkId      主墨 id
     * @param overlayInks    K：逐词覆盖墨种数（主墨不计）
     * @param channelSeconds X3 引导秒数（非 X3 传 0）
     * @param volleyShots    X7 发数（非 X7 传 0）
     * @return 结果；{@link CastOutcome#result()} 为 {@link CastResult#SUCCESS} 时
     *         {@link CastOutcome#snapshot()} 才非 {@code null}
     */
    CastOutcome cast(Player player, List<String> wordIds, String mainInkId, int overlayInks,
            float channelSeconds, int volleyShots);

    /**
     * 一次施法的结果：结果码 + （成功时）数值快照。
     *
     * @param result   结果码；非 {@code SUCCESS} 表示被拒绝（未扣费、未进冷却）
     * @param snapshot 成功时的数值快照，否则 {@code null}
     */
    record CastOutcome(CastResult result, SpellSnapshot snapshot, String detail) {
        public CastOutcome(CastResult result, SpellSnapshot snapshot) {
            this(result, snapshot, "");
        }

        public static CastOutcome rejected(CastResult result, String detail) {
            return new CastOutcome(result, null, detail);
        }

        /** 被拒绝（无快照） */
        public static CastOutcome rejected(CastResult result) {
            return new CastOutcome(result, null);
        }

        /** 受理成功 */
        public static CastOutcome success(SpellSnapshot snapshot) {
            return new CastOutcome(CastResult.SUCCESS, snapshot);
        }

        public boolean ok() {
            return result == CastResult.SUCCESS;
        }
    }
}

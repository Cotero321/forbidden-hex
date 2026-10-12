package com.example.akaishi.forbidden.codex;

/**
 * 秘典界面用的「一条条件」快照（服务端求值 → 客户端只显示）。
 *
 * <p><b>为什么把条件结果下发而不是只发进度</b>：条件的六个数据源（认知值、首见记档、前置节点、
 * 背包物品、所在地点、挨过的伤害）全都在<b>服务端权威状态</b>里，客户端拿不到；而界面必须逐条显示
 * "满足 / 未满足、缺的是什么"。于是服务端把"这一条是什么、够不够、数字是多少"压成一条扁平记录发下去，
 * 客户端只负责把 {@link #kind()} 翻成当前语言的句子（服务端不知道客户端语言，绝不在这里拼文案）。
 *
 * <p><b>两个字节的分工</b>：{@link #kind()} 说明"这是六种条件里的哪一种"，
 * {@link #variant()} 是<b>该种内部的分支</b>（子类型），见下方常量。分开的理由是
 * 界面主分支只按 kind 分派（六种语句模板），variant 只影响同一句模板里的措辞
 * （"去过：某地" / "挨过：某疼"），不必为每种分支再造一个 kind。
 *
 * <p>{@link #arg()} 的含义按 {@link #kind()} 分派：
 * <ul>
 *   <li>{@link #KIND_COG} —— 无参（空串）；数值看 {@link #required()} / {@link #current()}；</li>
 *   <li>{@link #KIND_FIRST_SEEN} —— 首见 id 的<b>路径</b>（如 {@code warden}），客户端据此取短名；</li>
 *   <li>{@link #KIND_PREREQ} —— 前置节点的<b>完整 id</b>，客户端从静态表取节点名；</li>
 *   <li>{@link #KIND_ITEM} —— 物品的<b>完整 id</b>（如 {@code minecraft:echo_shard}），
 *       数量看 {@link #required()} / {@link #current()}（浮点承载整数，界面取整显示）；</li>
 *   <li>{@link #KIND_LOCATION} —— 目标地点的完整 id（群系 / 维度 / 结构），子类型看 variant；</li>
 *   <li>{@link #KIND_DAMAGE} —— 伤害类型或伤害类型标签的完整 id，形式看 variant。</li>
 * </ul>
 *
 * <p><b>为什么数量用 float 承载</b>：认知道具是浮点，物品是整数。为了让"一条条件"在下发时只有一个
 * 定长结构（读写两端不易错位），统一用 float；物品条目的取值恒为整数，显示端取整即可。
 *
 * @param kind      条件种类（见本类常量）
 * @param variant   种类内部的分支（见本类常量；无分支的种类恒 0）
 * @param satisfied 是否已满足（界面据此上色）
 * @param arg       种类相关参数（见上）
 * @param required  门槛值（{@link #KIND_COG} 的认知值 / {@link #KIND_ITEM} 的需求个数）
 * @param current   当前值（{@link #KIND_COG} 的认知值 / {@link #KIND_ITEM} 的持有个数）
 */
public record CodexCondition(byte kind, byte variant, boolean satisfied, String arg,
                             float required, float current) {

    // ===== 六种条件 =====

    /** 认知值门槛 */
    public static final byte KIND_COG = 0;
    /** 首见要求（复用理智系统的首见记档） */
    public static final byte KIND_FIRST_SEEN = 1;
    /** 前置节点（必须先学完） */
    public static final byte KIND_PREREQ = 2;
    /** 持有若干某物品 */
    public static final byte KIND_ITEM = 3;
    /** 去过某地（群系 / 维度 / 结构） */
    public static final byte KIND_LOCATION = 4;
    /** 曾被某种伤害命中过 */
    public static final byte KIND_DAMAGE = 5;

    // ===== KIND_ITEM 的分支 =====

    /** 只是持有（读完不消耗） */
    public static final byte ITEM_KEEP = 0;
    /** 读完会消耗掉这些物品 */
    public static final byte ITEM_CONSUME = 1;

    // ===== KIND_LOCATION 的分支 =====

    /** 群系 id */
    public static final byte LOC_BIOME = 0;
    /** 群系标签 */
    public static final byte LOC_BIOME_TAG = 1;
    /** 维度 id */
    public static final byte LOC_DIMENSION = 2;
    /** 结构 id */
    public static final byte LOC_STRUCTURE = 3;

    // ===== KIND_DAMAGE 的分支 =====

    /** 单个伤害类型 id */
    public static final byte DAMAGE_TYPE = 0;
    /** 伤害类型标签 */
    public static final byte DAMAGE_TAG = 1;

    /** 认知门槛条件 */
    public static CodexCondition cog(float required, float current) {
        return new CodexCondition(KIND_COG, (byte) 0, current >= required, "", required, current);
    }

    /** 首见条件：{@code arg} 为首见 id 路径 */
    public static CodexCondition firstSeen(String path, boolean satisfied) {
        return new CodexCondition(KIND_FIRST_SEEN, (byte) 0, satisfied, path, 0f, 0f);
    }

    /** 前置节点条件：{@code arg} 为节点完整 id */
    public static CodexCondition prereq(String nodeId, boolean satisfied) {
        return new CodexCondition(KIND_PREREQ, (byte) 0, satisfied, nodeId, 0f, 0f);
    }

    /** 物品条件：{@code arg} 为物品完整 id；{@code consume} 决定界面是否提示"会被消耗" */
    public static CodexCondition item(String itemId, int required, int current, boolean consume) {
        return new CodexCondition(KIND_ITEM, consume ? ITEM_CONSUME : ITEM_KEEP,
                current >= required, itemId, required, current);
    }

    /** 地点条件：{@code variant} 为子类型，{@code targetId} 为群系 / 维度 / 结构的完整 id */
    public static CodexCondition location(byte variant, String targetId, boolean satisfied) {
        return new CodexCondition(KIND_LOCATION, variant, satisfied, targetId, 0f, 0f);
    }

    /** 伤害条件：{@code variant} 区分伤害类型 id 与标签，{@code targetId} 为对应完整 id */
    public static CodexCondition damage(byte variant, String targetId, boolean satisfied) {
        return new CodexCondition(KIND_DAMAGE, variant, satisfied, targetId, 0f, 0f);
    }
}

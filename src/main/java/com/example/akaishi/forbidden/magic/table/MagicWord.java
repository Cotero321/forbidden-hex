package com.example.akaishi.forbidden.magic.table;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>词表</b>：51 个词根的静态定义（动作 19、作用点 14、结构 11、性质 7；设计记忆 §4 全表 + §8.3 词权重表）。
 * v20：8 个原 unsupported 词（召/丘/轴/尾/杯/源/痕/层）更换 E/X 后并入 B 组，
 * B 组从 6 词扩展为 14 词；C 组从 15 降至 11，D 组从 10 降至 8，A 组从 20 降至 19。
 *
 * <p><b>为什么用 enum 而不是 Map + 字符串常量</b>：词是<b>闭集</b>（§11「词与源先闭」，
 * 附属不得增词）。闭集用 enum，拼写错误<b>编译期</b>就被拦下，比"常量类集中定义"更彻底。
 * 反之 {@code 效果原语}与{@code 墨}对附属开放，走注册表（Map + id），两者刻意不同。
 *
 * <p><b>词 id 为什么是拼音而非中文单字</b>：1.20.1 的 {@code ResourceLocation}
 * 只接受 {@code [a-z0-9/._-]} —— 一旦词 id 需要进资源路径、同步包或数据包，中文会直接失败。
 * 故 {@link #id()} 取无音调拼音，中文单字另存于 {@link #label()}（显示 / 手册 / 法阵用）。
 * 拼音撞车的 6 组（基迹汲 / 启旗 / 交角 / 缝峰 / 钉顶 / 召罩）用语义后缀区分。
 *
 * <p><b>关于 B / C / D 组的 {@code delivery}（X）</b>：设计记忆只给 A 组逐词定了 X
 * （§19.8 / §20.5），B/C/D 组只给了 E。但 §19.7 的网格校验要求<b>每词都有 (E, X)</b>，
 * 故本表按同一口径补全 —— <b>补全项标注为「X 本次补全」，待复核</b>。
 */
public enum MagicWord {

    // ===================== A 动作（19，v20 迁出「召」至 B 组） =====================

    LIU("liu", "流", "𝔞", Role.ACTION, MagicIds.Effect.DAMAGE, MagicIds.Delivery.CHANNEL,
            1.0f, 1.2f, 1.0f, "持续束流（沿路径持续作用）", "传导：蔓延至相邻目标", ""),
    YIN("yin", "引", "𝔠", Role.ACTION, MagicIds.Effect.DAMAGE, MagicIds.Delivery.INSTANT,
            1.0f, 0.8f, 1.2f, "引导（可操控的投射物 / 引导线）", "引导：可微调方向", ""),
    QI_OPEN("qi_open", "启", "𝔡", Role.ACTION, MagicIds.Effect.BREAK_UNLOCK, MagicIds.Delivery.INSTANT,
            0.8f, 1.0f, 1.0f, "开启解锁（门 / 容器 / 封锁状态）", "解除：剥离禁锢与封印", ""),
    DUAN("duan", "断", "𝔣", Role.ACTION, MagicIds.Effect.BREAK_TERMINATE, MagicIds.Delivery.INSTANT,
            1.0f, 0.6f, 1.0f, "切断（终止目标的持续效果与连接）", "终止：打断 / 关闭", ""),
    QIAN("qian", "牵", "𝔧", Role.ACTION, MagicIds.Effect.MOVE, MagicIds.Delivery.INSTANT,
            0.9f, 0.8f, 1.1f, "牵引（把目标拖向指定位置）", "位移：拉近 / 推开",
            "「不可逆」为参数（原「钩」并入，§20.3）"),
    QU("qu", "曲", "𝔨", Role.ACTION, MagicIds.Effect.WEAKEN, MagicIds.Delivery.INSTANT,
            0.9f, 1.2f, 1.0f, "扭曲（目标畸变、弱点放大）", "畸变：放大负面效果", ""),
    BIAO("biao", "标", "𝔭", Role.ACTION, MagicIds.Effect.SENSE, MagicIds.Delivery.INSTANT,
            0.8f, 1.3f, 1.2f, "刻印标记（被追踪、可被后续法术导向）", "追踪：自动导向命中目标", ""),
    DING_NAIL("ding_nail", "钉", "𝔱", Role.ACTION, MagicIds.Effect.BIND, MagicIds.Delivery.INSTANT,
            1.0f, 1.0f, 0.9f, "钉定（禁止移动 / 定身 / 固定方块）", "锁定：位置与状态固定",
            "参数 缚身(默认) / **封能** ← 逐词覆盖墨「序」（§25.1）"),
    // ★v20：「召」从 A 组 SUMMON 改为 B 组 DAMAGE/VOLLEY，原 SUMMON 无执行器
    //   移至 B 组（见下方 POINT 段）
    LIE("lie", "裂", "𝔉", Role.ACTION, MagicIds.Effect.BREAK_SHIELD, MagicIds.Delivery.INSTANT,
            1.3f, 0.6f, 1.0f, "撕裂（破开防御 / 结界 / 方块）", "破开：无视防护", ""),
    ZHE("zhe", "折", "ℑ", Role.ACTION, MagicIds.Effect.TELEPORT, MagicIds.Delivery.INSTANT,
            0.8f, 0.8f, 1.3f, "折叠空间（短距穿梭、压缩距离）", "折叠：距离压缩 / 穿透", ""),
    JI_DRAW("ji_draw", "汲", "𝔙", Role.ACTION, MagicIds.Effect.GATHER, MagicIds.Delivery.CHANNEL,
            0.9f, 1.2f, 1.1f, "汲取（持续吸取物质 / 能量 / 动量）", "汲取：吸取一部分回来",
            "原「钩」改此（§20.3）"),
    SHE("she", "蛇", "𝔓", Role.ACTION, MagicIds.Effect.BIND, MagicIds.Delivery.ATTACH,
            0.9f, 1.4f, 0.9f, "缠绕（持续束缚）", "缠绕：束缚", ""),
    JIAO_CROSS("jiao_cross", "交", "𝔘", Role.ACTION, MagicIds.Effect.LINK, MagicIds.Delivery.INSTANT,
            1.0f, 1.0f, 1.0f, "交叠干涉（两股效果合并 / 抵消）", "干涉：叠加或抵消", ""),
    QI_FLAG("qi_flag", "旗", "𝔑", Role.ACTION, MagicIds.Effect.SENSE, MagicIds.Delivery.PLACE,
            0.8f, 1.4f, 1.3f, "立旗宣示（区域标记 + 吸引注意）", "宣示：区域标记 / 吸引注意", ""),
    TOU("tou", "投", "𝔶", Role.ACTION, MagicIds.Effect.DAMAGE, MagicIds.Delivery.PROJECTILE,
            1.0f, 0.8f, 1.2f, "投掷（抛射一枚飞行物，飞行后可被躲）", "投送：远程递送至目标",
            "v7 新增（覆盖 X2，§20.2）"),
    BENG("beng", "迸", "𝔷", Role.ACTION, MagicIds.Effect.DAMAGE, MagicIds.Delivery.VOLLEY,
            0.9f, 0.8f, 1.1f, "迸发（骤发多枚，短间隔连射）", "连击：命中后立即追加一次同类",
            "v7 新增（覆盖 X7，§20.2）"),
    HU("hu", "护", "𝔚", Role.ACTION, MagicIds.Effect.WARD, MagicIds.Delivery.INSTANT,
            0.8f, 1.4f, 0.9f, "立盾（吸收伤害，护持自身或指向目标）", "护持：吸收一部分伤害",
            "v10 新增（防御系，§23.3）"),
    ZHAO_COVER("zhao_cover", "罩", "𝔛", Role.ACTION, MagicIds.Effect.WARD, MagicIds.Delivery.PLACE,
            0.8f, 1.5f, 1.3f, "布罩（罩住一片区域，圈内友方减伤 / 吸收）", "庇荫：范围内生效",
            "v10 新增（防御系，§23.3）"),
    JING("jing", "净", "𝔜", Role.ACTION, MagicIds.Effect.BREAK_TERMINATE, MagicIds.Delivery.INSTANT,
            0.8f, 0.9f, 1.1f, "涤净（驱散目标身上的负面状态）", "涤净：解除一层负面",
            "v10 新增；挂 BREAK_TERMINATE，与「断」以**目标极性**区分（§23.3）"),

    // ===================== B 作用点 / 媒介（14，含 v20 迁入 8 词） =====================

    XI("xi", "隙", "𝔟", Role.POINT, MagicIds.Effect.WEAKEN, MagicIds.Delivery.INSTANT,
            0.8f, 1.2f, 1.0f, "开隙（撕出弱点，受击伤害提高）", "破绽：后续伤害加成", "X 本次补全"),
    GEN("gen", "根", "𝔥", Role.POINT, MagicIds.Effect.LINK, MagicIds.Delivery.ATTACH,
            0.9f, 1.5f, 1.0f, "植根（建立同化网络，持续吸收）", "同化：吸收一部分回来", "X 本次补全"),
    ZHEN("zhen", "针", "𝔦", Role.POINT, MagicIds.Effect.DAMAGE, MagicIds.Delivery.INSTANT,
            1.2f, 0.7f, 0.8f, "针刺（精确穿刺并植入）", "精准：必中 / 穿甲", "X 本次补全（原 DAMAGE_DIRECT）"),
    JI_TRACE("ji_trace", "迹", "𝔮", Role.POINT, MagicIds.Effect.SENSE, MagicIds.Delivery.INSTANT,
            0.7f, 1.3f, 1.4f, "显迹（暴露实体与潜行痕迹）", "追迹：暴露隐藏", "X 本次补全"),
    SI("si", "丝", "𝔰", Role.POINT, MagicIds.Effect.LINK, MagicIds.Delivery.INSTANT,
            0.9f, 1.2f, 1.1f, "结丝（连接两方，传导伤害 / 状态）", "连接：接到第二个目标", "X 本次补全"),
    FENG_SEAM("feng_seam", "缝", "𝔳", Role.POINT, MagicIds.Effect.DAMAGE, MagicIds.Delivery.ATTACH,
            1.0f, 1.5f, 0.9f, "撕缝（留下持续裂口）", "开口：持续伤害（DOT）",
            "X 本次补全（原 DAMAGE_WOUND ⇒ 附着）"),
    // ---- v20 迁入：原 unsupported 词更换 E/X 后并入 B 组 ----
    ZHAO_SUMMON("zhao_summon", "召", "ℭ", Role.POINT, MagicIds.Effect.DAMAGE, MagicIds.Delivery.VOLLEY,
            1.0f, 0.8f, 1.2f, "召唤弹幕（多段伤害轰炸）", "连击：命中后追加",
            "★v20 从 A/SUMMON 改 B/DAMAGE"),
    QIU("qiu", "丘", "𝔪", Role.POINT, MagicIds.Effect.WEAKEN, MagicIds.Delivery.PLACE,
            0.9f, 1.3f, 1.0f, "隆起压制（区域削弱）", "压制：范围降属性",
            "★v20 从 C/SUMMON 改 B/WEAKEN"),
    ZHOU("zhou", "轴", "ℌ", Role.POINT, MagicIds.Effect.DAMAGE, MagicIds.Delivery.CHANNEL,
            1.0f, 1.0f, 1.0f, "沿轴持续穿透伤害", "贯穿：直线持续",
            "★v20 从 C/TEMPORAL 改 B/DAMAGE"),
    WEI("wei", "尾", "𝔒", Role.POINT, MagicIds.Effect.LINK, MagicIds.Delivery.ATTACH,
            0.9f, 1.2f, 1.1f, "尾迹传导（延迟链跳）", "延迟传导：滞后连跳",
            "★v20 从 D/TEMPORAL 改 B/LINK"),
    BEI("bei", "杯", "ℜ", Role.POINT, MagicIds.Effect.FIELD, MagicIds.Delivery.PLACE,
            1.0f, 1.4f, 1.2f, "杯区域（持续范围伤害）", "蓄积：范围持续",
            "★v20 从 C/VESSEL 改 B/FIELD"),
    YUAN("yuan", "源", "𝔄", Role.POINT, MagicIds.Effect.BOOST, MagicIds.Delivery.INSTANT,
            1.1f, 1.2f, 1.0f, "增源（临时增幅属性）", "增幅：提升属性",
            "★v20 从 C/TOLL 改 B/BOOST"),
    HEN("hen", "痕", "𝔯", Role.POINT, MagicIds.Effect.BIND, MagicIds.Delivery.ATTACH,
            1.2f, 1.3f, 0.9f, "留痕束缚（持续定身）", "禁锢：持续锁位",
            "★v20 从 D/TOLL 改 B/BIND"),
    CENG("ceng", "层", "𝔈", Role.POINT, MagicIds.Effect.SENSE, MagicIds.Delivery.INSTANT,
            0.8f, 1.3f, 1.2f, "叠层感知（暴露多层信息）", "透视：揭示隐情",
            "★v20 从 D/参数修改器 改 B/SENSE"),

    // ===================== C 结构（11，v20 迁出 4 词至 B 组） =====================

    JI_BASE("ji_base", "基", "𝔢", Role.STRUCTURE, MagicIds.Effect.FIELD, MagicIds.Delivery.PLACE,
            0.8f, 1.4f, 1.1f, "筑基（建立基底 / 锚点，抗冲稳固）", "稳固：延长持续、免疫打断",
            "参数 地面(默认) / **垂直** ← 覆盖墨「壤」（§25.1）；原「柱」并入（§23.2）"),
    WO("wo", "涡", "𝔤", Role.STRUCTURE, MagicIds.Effect.GATHER, MagicIds.Delivery.PLACE,
            0.9f, 1.1f, 1.2f, "涡旋（吸引中心，卷入实体 / 物品）", "吸引：拉向中心", "X 本次补全"),
    // ★v20：「丘」从 C 组 SUMMON 改为 B 组 WEAKEN/PLACE，原 SUMMON 无执行器
    //   移至 B 组（见下方 POINT 段）
    DIAN("dian", "点", "𝔬", Role.STRUCTURE, MagicIds.Effect.DAMAGE, MagicIds.Delivery.INSTANT,
            1.3f, 0.6f, 0.7f, "奇点（压缩聚焦，集中爆发）", "聚焦：单体化、威力集中", "X 本次补全"),
    WA("wa", "洼", "𝔲", Role.STRUCTURE, MagicIds.Effect.GATHER, MagicIds.Delivery.INSTANT,
            0.8f, 1.3f, 1.2f, "洼陷（汇聚凹区：减速 / 吸附）", "汇聚：聚拢", "X 本次补全"),
    LIAN("lian", "涟", "𝔴", Role.STRUCTURE, MagicIds.Effect.DAMAGE, MagicIds.Delivery.INSTANT,
            1.0f, 0.9f, 1.4f, "涟漪（外扩波及、连锁）", "波及：连锁到邻近", "X 本次补全（原 DAMAGE_CHAIN）"),
    // ★v20：「源」从 C 组 TOLL 改为 B 组 BOOST/INSTANT，原 TOLL 无执行器
    //   移至 B 组（见下方 POINT 段）
    JIE("jie", "界", "𝔅", Role.STRUCTURE, MagicIds.Effect.FIELD, MagicIds.Delivery.PLACE,
            0.9f, 1.5f, 1.3f, "结界（封闭区域，进出受限）", "封锁：区域隔离", "X 本次补全"),
    MEN("men", "门", "𝔇", Role.STRUCTURE, MagicIds.Effect.TELEPORT, MagicIds.Delivery.INSTANT,
            0.8f, 0.8f, 1.5f, "开门（短距 / 维度穿行）", "通道：效果可穿空", "X 本次补全"),
    XUAN("xuan", "旋", "𝔊", Role.STRUCTURE, MagicIds.Effect.DAMAGE, MagicIds.Delivery.VOLLEY,
            1.0f, 1.1f, 1.1f, "螺旋（绕轴旋转的环绕效果 / 弹幕）", "环绕：围绕目标", "X 本次补全（环绕弹幕 ⇒ 连发）"),
    // ★v20：「轴」从 C 组 TEMPORAL 改为 B 组 DAMAGE/CHANNEL，原 TEMPORAL 无执行器
    //   移至 B 组（见下方 POINT 段）
    HUAN("huan", "环", "𝔐", Role.STRUCTURE, MagicIds.Effect.FIELD, MagicIds.Delivery.PLACE,
            0.9f, 1.4f, 1.3f, "成环（仪式圈，圈内持续作用）", "环域：沿圈生效", "X 本次补全"),
    // ★v20：「杯」从 C 组 VESSEL 改为 B 组 FIELD/PLACE，原 VESSEL 无执行器
    //   移至 B 组（见下方 POINT 段）
    GU("gu", "谷", "𝔖", Role.STRUCTURE, MagicIds.Effect.MOVE, MagicIds.Delivery.INSTANT,
            1.1f, 1.0f, 1.1f, "开谷（把目标向下放逐 / 沉降 / 吞没）", "放逐：向下 / 向外", "X 本次补全"),
    BO("bo", "波", "𝔗", Role.STRUCTURE, MagicIds.Effect.DAMAGE, MagicIds.Delivery.PLACE,
            1.0f, 1.1f, 1.4f, "起波（沿介质传导，范围随介质）", "传导：沿介质传播", "X 本次补全（沿介质 ⇒ 布场）"),

    // ===================== D 性质与代价（8，v20 迁出 2 词至 B 组） =====================

    JIAN("jian", "尖", "𝔩", Role.NATURE, MagicIds.Effect.DAMAGE, MagicIds.Delivery.INSTANT,
            1.2f, 0.8f, 0.9f, "成尖（凝聚尖刺，穿透伤害）", "穿透：穿甲 / 贯穿", "X 本次补全"),
    PIAN("pian", "偏", "𝔫", Role.NATURE, MagicIds.Effect.MIND, MagicIds.Delivery.INSTANT,
            0.9f, 1.2f, 1.0f, "致偏（认知偏离：疯狂 / 混乱）", "不稳定：随机偏转、浮动", "X 本次补全"),
    CHA("cha", "叉", "𝔵", Role.NATURE, null, null,
            1.0f, 1.0f, 1.0f, "分叉（一分为多：多发 / 多目标）", "分裂：目标数 +1", "**参数修改器**，消耗 ×1.2（§8.6）"),
    // ★v20：「层」从 D 组参数修改器改为 B 组 SENSE/INSTANT，原无独立效果
    //   移至 B 组（见下方 POINT 段）
    JIAO_ANGLE("jiao_angle", "角", "𝔍", Role.NATURE, MagicIds.Effect.CONCEAL, MagicIds.Delivery.INSTANT,
            0.8f, 1.3f, 0.9f, "隐角（藏匿：隐蔽施法、不留痕）", "隐蔽：不易被察觉", "X 本次补全"),
    FENG_PEAK("feng_peak", "峰", "𝔎", Role.NATURE, MagicIds.Effect.BOOST, MagicIds.Delivery.INSTANT,
            1.2f, 1.2f, 1.0f, "登峰（突破上限，巅峰状态）", "升维：上限 +", "X 本次补全"),
    XIE("xie", "斜", "𝔏", Role.NATURE, MagicIds.Effect.WEAKEN, MagicIds.Delivery.INSTANT,
            0.9f, 1.2f, 1.0f, "倾斜（目标失衡 / 失真：命中与效果下降）", "削弱：降低目标效果", "X 本次补全"),
    // ★v20：「尾」从 D/TEMPORAL 迁入 B/LINK（见 B 组）
    DING_CAP("ding_cap", "顶", "𝔔", Role.NATURE, MagicIds.Effect.WEAKEN, MagicIds.Delivery.INSTANT,
            0.9f, 1.3f, 1.0f, "封顶（压制上限，限制增益）", "封顶：限制上限", "X 本次补全");
    // ★v20：「痕」从 D/TOLL 迁入 B/BIND（见 B 组）

    /** 词在四段文法中的角色（§5 / §11 `MagicWordRole`） */
    public enum Role {
        /** 性质段（D 组）：怎么施 */
        NATURE,
        /** 动作段（A 组）：做什么 */
        ACTION,
        /** 作用点段（B 组）：借由什么 */
        POINT,
        /** 结构段（C 组）：成什么形 */
        STRUCTURE
    }

    private static final Map<String, MagicWord> BY_ID = new HashMap<>();

    static {
        for (MagicWord w : values()) {
            BY_ID.put(w.id, w);
        }
    }

    private final String id;
    private final String label;
    private final String glyph;
    private final Role role;
    private final String effect;
    private final String delivery;
    private final float wP;
    private final float wD;
    private final float wR;
    private final String core;
    private final String asModifier;
    private final String note;

    MagicWord(String id, String label, String glyph, Role role, String effect, String delivery,
            float wP, float wD, float wR, String core, String asModifier, String note) {
        this.id = id;
        this.label = label;
        this.glyph = glyph;
        this.role = role;
        this.effect = effect;
        this.delivery = delivery;
        this.wP = wP;
        this.wD = wD;
        this.wR = wR;
        this.core = core;
        this.asModifier = asModifier;
        this.note = note;
    }

    /** ASCII id（存档 / 同步 / 资源路径安全） */
    public String id() {
        return id;
    }

    /** 中文单字（显示 / 手册 / 法阵） */
    public String label() {
        return label;
    }

    /** 法阵与书写所用符号 */
    public String glyph() {
        return glyph;
    }

    public Role role() {
        return role;
    }

    /** 该词作核心词时给出的效果语义 E；参数修改器返回 {@code null} */
    public String effect() {
        return effect;
    }

    /** 该词作核心词时给出的交付方式 X；参数修改器与 {@code TOLL} 词返回 {@code null} */
    public String delivery() {
        return delivery;
    }

    public float wP() {
        return wP;
    }

    public float wD() {
        return wD;
    }

    public float wR() {
        return wR;
    }

    /** 核心作用（单独成术 / 作核心时） */
    public String core() {
        return core;
    }

    /** 作为修饰时的语义 */
    public String asModifier() {
        return asModifier;
    }

    /** 补记（X 来源 / 并入说明 / 参数分流墨） */
    public String note() {
        return note;
    }

    /** 是否为参数修改器（叉 / 层）：不计入 wP/wD/wR，无 (E, X) */
    public boolean isModifier() {
        return effect == null;
    }

    /** 按 id 取词；未知 id 返回 {@code null}（调用方决定回退口径，禁止静默造词） */
    public static MagicWord byId(String id) {
        return BY_ID.get(id);
    }

    /** 该 id 是否是词表中的词（词是闭集 —— 服务层据此拒绝附属塞入的未知 id） */
    public static boolean exists(String id) {
        return id != null && BY_ID.containsKey(id);
    }

    /** 全部 id（插入序 = 枚举声明序，供自检 / 调试遍历） */
    public static Map<String, MagicWord> byIdView() {
        return Collections.unmodifiableMap(BY_ID);
    }
}

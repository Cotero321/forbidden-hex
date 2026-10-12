package com.example.akaishi.forbidden.magic.table;

/**
 * <b>源表</b>：4 种魔力源的静态定义（设计记忆 §2）。
 *
 * <p><b>为什么源用 enum</b>：源在 §11 被划为「<b>先闭</b>」—— 附属不得增源。
 * 闭集用 enum，取值穷尽即可被 switch 强制覆盖（少写一个分支编译器就报错）。
 *
 * <p><b>源 = 载体级持久设置</b>（§2）：不是每次施法现选，而是装备 / 法杖上的一档状态。
 * 因此本表描述的是"这台载体付什么账、吃哪种倾向"，而非一次施法的参数。
 * <p>源的识别色同墨色，是<b>跨系统共用色</b>（法阵全局动态、HUD、手册），故在此锁定。
 */
public enum MagicSource {

    /** 灰：理智代价型。附加<b>精神伤害</b>，且是<b>星墨唯一合法驱动</b>。 */
    SAN("san", "灰", 0x9E9E9E,
            "SAN = 消耗 ÷ 100（0.1 步进）",
            "P × 1.3",
            "附加精神伤害；星墨唯一合法驱动"),

    /** 绿：生命代价型。付池打折，但要真掉血。 */
    LIFE("life", "绿", 0x3FBF6F,
            "池 × 0.9 ＋ HP = 消耗 ÷ 200（0.5 步进）",
            "生命系 P × 1.1",
            "对己 → 饱食 ＋ 回血；对敌 → 生命侵蚀"),

    /** 黑：攻击向。与白沿「耀 / 黯」轴镜像。 */
    DARK("dark", "黑", 0x1A1A1A,
            "暗魔池 × 1.0",
            "攻击类 P × 1.2 / 防御类 P × 0.8",
            "耀墨或防御类 → P × 0.8 且消耗 × 2；黯墨 → 消耗 × 0.8"),

    /** 白：防御向。与黑沿「耀 / 黯」轴镜像。 */
    WHITE("white", "白", 0xF2F2F2,
            "白魔池 × 1.0",
            "防御类 P × 1.2 / 攻击类 P × 0.8",
            "黯墨 → P × 0.8 且消耗 × 2；耀墨 → 消耗 × 0.8");

    /** 缺键默认源（§16.2：存档缺 `source` 时回退到此） */
    public static final MagicSource DEFAULT = LIFE;

    private final String id;
    private final String label;
    private final int rgb;
    private final String payment;
    private final String axisPolicy;
    private final String trait;

    MagicSource(String id, String label, int rgb, String payment, String axisPolicy, String trait) {
        this.id = id;
        this.label = label;
        this.rgb = rgb;
        this.payment = payment;
        this.axisPolicy = axisPolicy;
        this.trait = trait;
    }

    /** ASCII id（存档用；`life` 与 §16.2 的默认值一致） */
    public String id() {
        return id;
    }

    /** 中文名（灰 / 绿 / 黑 / 白） */
    public String label() {
        return label;
    }

    /** 识别色（0xRRGGBB） */
    public int rgb() {
        return rgb;
    }

    /** 支付方式说明 */
    public String payment() {
        return payment;
    }

    /** 四轴倾向（P / D / R 与攻击防御类的加成口径） */
    public String axisPolicy() {
        return axisPolicy;
    }

    /** 特性（附加伤害 / 墨交互） */
    public String trait() {
        return trait;
    }

    /** 按 id 取源；未知 id 返回 {@code DEFAULT}（**容错口径**：存档里的未知源不清档、回退默认） */
    public static MagicSource byIdOrDefault(String id) {
        for (MagicSource s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return DEFAULT;
    }
}

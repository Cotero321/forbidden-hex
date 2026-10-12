package com.example.akaishi.forbidden.magic.table;

/**
 * 魔法侧字符串 id 的<b>唯一常量源</b>（设计记忆 §17.2 技术风险 2）。
 *
 * <p><b>为什么集中定义</b>：全表以字符串 id 为键（便于扩展、便于原样存档），
 * 若各表各写字符串字面量，拼写漂移<b>编译器拦不住</b>。集中到此处后，
 * 所有引用都经常量，改名即全表生效；自检器也只需核对常量集与表键集是否一一对应。
 */
public final class MagicIds {

    private MagicIds() {
    }

    /**
     * 效果语义 E 的 id（**20 条**：19 条拆自 §8.2 的 20 原语 + v10 新增 {@code WARD}）。
     * 见设计记忆 §19.5 / §23.3。
     */
    public static final class Effect {

        private Effect() {
        }

        /** 造成伤害（**合并**原 4 条伤害原语：直射 / 引导 / 附着 / 连锁） */
        public static final String DAMAGE = "damage";
        /** 解除封印 / 开启（**拆自**原 18「启」） */
        public static final String BREAK_UNLOCK = "break_unlock";
        /** 终止持续效果与连接（**拆自**原 18「断」） */
        public static final String BREAK_TERMINATE = "break_terminate";
        /** 破开防御 / 结界 / 方块（**拆自**原 18「裂」） */
        public static final String BREAK_SHIELD = "break_shield";
        /** 推 / 拉目标位置 */
        public static final String MOVE = "move";
        /** 自身 / 目标瞬时转移 */
        public static final String TELEPORT = "teleport";
        /** 禁锢（禁止移动 / 束缚） */
        public static final String BIND = "bind";
        /** 削弱属性 / 放大弱点 */
        public static final String WEAKEN = "weaken";
        /** 增幅 / 治疗 */
        public static final String BOOST = "boost";
        /** 心智（疯狂 / 认知偏离） */
        public static final String MIND = "mind";
        /** 感知 / 暴露 */
        public static final String SENSE = "sense";
        /** 隐匿 */
        public static final String CONCEAL = "conceal";
        /** 召唤实体 */
        public static final String SUMMON = "summon";
        /** 区域持续作用 */
        public static final String FIELD = "field";
        /** 聚拢 / 吸引 */
        public static final String GATHER = "gather";
        /** 连接 / 传导 */
        public static final String LINK = "link";
        /** 时序（延迟 / 回响） */
        public static final String TEMPORAL = "temporal";
        /** 承载 */
        public static final String VESSEL = "vessel";
        /** 代价（自伤换增幅 · 参数修改器，不占交付） */
        public static final String TOLL = "toll";
        /** **护盾 / 吸收伤害**（v10 新增，**无历史原语对应**） */
        public static final String WARD = "ward";
    }

    /**
     * 交付方式 X 的 id（**7 种**）。见设计记忆 §19.3（手感画像）/ §21.2（修正系数）。
     */
    public static final class Delivery {
        private Delivery() {
        }

        /** 直射：点一下，同帧判定、0 前摇 ⇒ 干脆 */
        public static final String INSTANT = "instant";
        /** 投射：蓄力再放，飞行 0.5~2s、可被躲 ⇒ 有期待 */
        public static final String PROJECTILE = "projectile";
        /** 引导：按住不放，持续 tick、可被打断 ⇒ 沉重、有风险 */
        public static final String CHANNEL = "channel";
        /** 附着：立刻挂载，之后 tick 结算 ⇒ 埋伏、慢热 */
        public static final String ATTACH = "attach";
        /** 布场：选点放置，之后持续生效 ⇒ 布局、控位 */
        public static final String PLACE = "place";
        /** 回响：延迟 1~3s 复现，无即时反馈 ⇒ 悬念、赌 */
        public static final String ECHO = "echo";
        /** 连发：N 发序列、间隔 0.1s ⇒ 爽快、宣泄 */
        public static final String VOLLEY = "volley";
    }

    /**
     * 魔法墨的 id（**11 种**内置）。见设计记忆 §3.1。
     *
     * <p>墨对附属<b>开放</b>（§11「效果原语 + 墨开放」），故走注册表（Map + id）而非 enum。
     */
    public static final class Ink {

        private Ink() {
        }

        /** 火 / 爆燃 */
        public static final String CHI = "chi";
        /** 深渊 / 虚空 */
        public static final String YUAN = "yuan";
        /** 生长 / 缠绕 */
        public static final String MAN = "man";
        /** 大地 / 稳固 */
        public static final String RANG = "rang";
        /** 腐蚀 / 衰败 */
        public static final String SHI = "shi";
        /** 秩序 / 规则 */
        public static final String XU = "xu";
        /** 混乱 / 随机 */
        public static final String HUN = "hun";
        /** 星 / 星界（**独占**：不可与其他墨同句 ⇒ 星句 K 恒 0） */
        public static final String XING = "xing";
        /** 光 / 神圣 */
        public static final String YAO = "yao";
        /** 暗 / 堕落 */
        public static final String AN = "an";
        /** 风 / 动量 */
        public static final String JU = "ju";
    }
}

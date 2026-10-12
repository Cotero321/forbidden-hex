package com.example.akaishi.forbidden.codex;

/**
 * 秘典知识网络的「族」（画布分簇 + 配色分组 + <b>分册</b>单位）。
 *
 * <p><b>为什么族与三级分类（{@link CodexTier}）分开</b>：分类回答"这一页有多危险"
 * （叙事分级），族回答"这些念头是同一个来源"（画布聚在一起、同色系、装订成同一册）。
 * 两者是正交的：同一族里可以既有简单的想法也有禁忌的知识，反之亦然。
 *
 * <p><b>本轮的族划分按"文本内容"重定</b>（不是按危险度、也不是按出现顺序）：
 * 五个节点的正文其实是三种<b>感知通道</b>——耳（安静下来听见）、眼（被看见、被念出名字）、
 * 身（用疼去听、把东西从身上拿下来），故收敛成三族；详见各常量的注释与
 * {@link CodexTable} 的节点矩阵。
 *
 * <p>族 id 是数据（节点自带），故这里只放几个常量与语言键派生规则，
 * 不在枚举里穷举——将来新增一族只需在表里写新字符串 + 补一条语言键，不必改本类。
 */
public final class CodexFamily {

    /** 静与暗族：先安静下来、再往暗处站住，让"它"来找你（耳：听与被听） */
    public static final String HUSH = "hush";
    /** 名与视族：没有眼睛却会看、名字一念出来就被认得（眼：被注视与名讳） */
    public static final String GAZE = "gaze";
    /** 疼与血族：替它疼、把（血/东西）从自己身上拿下来（身：疼觉与供奉） */
    public static final String BLOOD = "blood";

    private CodexFamily() {
    }

    /** 族显示名语言键 */
    public static String nameKey(String family) {
        return "codex.forbidden_hex.family." + family;
    }

    /**
     * 族题词语言键：一句短题词（目录行与章节扉页共用同一条 —— 一次编写的题词，两处展示口径一致）。
     *
     * <p>与 {@link #nameKey} 同口径（数据侧只派生键，不硬编码文案）。
     */
    public static String mottoKey(String family) {
        return "codex.forbidden_hex.family." + family + ".motto";
    }
}

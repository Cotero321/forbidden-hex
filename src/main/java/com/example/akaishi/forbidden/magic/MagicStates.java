package com.example.akaishi.forbidden.magic;

import com.example.akaishi.forbidden.state.ForbiddenPlayerState;
import net.minecraft.world.entity.player.Player;

/**
 * 魔法状态的<b>取用入口</b>（薄封装，不含业务规则）。
 *
 * <p><b>为什么单独有这么一个类</b>：{@code MagicState} 借住在玩家的躯体 capability 里，
 * 取它需要"拿到躯体状态段 → 按段名取子状态 → 类型校验"三步。这三步散落在结算、同步、
 * 调试命令、界面等处会各自实现一遍（并各自踩空指针），故收成一个入口。
 *
 * <p>业务规则（扣费、门槛、施法的完整流程）属 P3 的服务层，<b>不放在这里</b> ——
 * 本类只回答"这个玩家的魔法状态在哪"。
 */
public final class MagicStates {

    private MagicStates() {
    }

    /**
     * 取玩家的魔法状态；未注册该段（未安装禁忌模块）或非玩家时返回 {@code null}。
     *
     * <p>返回 {@code null} 是<b>正常情况</b>（内容模块未安装），调用方须判空 ——
     * 与 {@code SanityServiceImpl.state} 的口径一致。
     */
    public static MagicState state(Player player) {
        if (player == null) {
            return null;
        }
        ForbiddenPlayerState state = ForbiddenPlayerState.of(player);
        return state == null ? null : state.magic();
    }

    /** 三池的索引（与 {@link MagicState#pool(int)} / {@link MagicState#setPool(int, float)} 对应） */
    public static final int POOL_LIFE = 0;
    public static final int POOL_DARK = 1;
    public static final int POOL_WHITE = 2;
    // 池的种数与"等级 → 词数上限"都不在此处再定义一份 ——
    // 单一真源分别是 api 的 MagicValues.POOL_COUNT 与 MagicValues.wordLimitForLevel

    /**
     * 该源用哪一池付账。<b>返回 -1 表示"不扣池"</b>。
     *
     * <p><b>灰源（SAN）必须返回 -1</b>：§2 明写它付的是理智值，与三池无关。
     * 若把它映射到某个池，等于让灰源玩家"既扣 SAN 又扣池"，是双倍收费。
     *
     * <p>放在本类而不是服务实现里，是因为<b>扣费与退费两处都要用它</b>
     * （施法入口在服务层、打断返还在状态机）—— 各写一份必然漂移。
     */
    public static int poolIndexOf(com.example.akaishi.forbidden.magic.table.MagicSource source) {
        return switch (source) {
            case SAN -> -1;
            case LIFE -> POOL_LIFE;
            case DARK -> POOL_DARK;
            case WHITE -> POOL_WHITE;
        };
    }

    /** {@link #poolIndexOf} 的逆映射（池索引 → 源；载体溢流等"按池结算"的场合用它，SAN 无池不会出现） */
    public static com.example.akaishi.forbidden.magic.table.MagicSource sourceOfPool(int index) {
        return switch (index) {
            case POOL_LIFE -> com.example.akaishi.forbidden.magic.table.MagicSource.LIFE;
            case POOL_DARK -> com.example.akaishi.forbidden.magic.table.MagicSource.DARK;
            default -> com.example.akaishi.forbidden.magic.table.MagicSource.WHITE;
        };
    }
}

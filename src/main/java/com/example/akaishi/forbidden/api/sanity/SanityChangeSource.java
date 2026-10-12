package com.example.akaishi.forbidden.api.sanity;

/**
 * 数值变更来源。
 *
 * <p><b>为什么需要它</b>：同一次扣减可能来自环境规则、食补、阈值钩子或附属写入，
 * 监听方（例如 HUD 表现、战斗数值联动）常需要按来源分流——例如"环境扣减才播屏幕抖动，食补补满不播"。
 * 由变更方打标签，比让监听方反推要可靠得多。
 *
 * <p>{@link #EXTERNAL} 是附属经 {@link ISanityService} 写入时的标签；
 * 内部实现层必须把它当作"外部写入"统一打标，不得让附属伪装成环境扣减。
 */
public enum SanityChangeSource {

    /** 环境周期规则（{@link SanityRuleRegistry} 里的规则）结算 */
    ENVIRONMENT,

    /** 食补（{@link SanityFoodRegistry} 里的档位）结算 */
    FOOD,

    /** 首见事件（{@link SanityFirstRegistry} 里的条目）结算 */
    FIRST_ENCOUNTER,

    /** SANC 恢复来源（{@link SanityRestoreRegistry} 里的条目）结算 */
    RESTORE,

    /** 阈值钩子（{@link SanityThresholdRegistry} 里的条目）调整 */
    THRESHOLD,

    /** 指令写入 */
    COMMAND,

    /** 附属经 {@link ISanityService} 主动写入（含 {@link ISanityService#reportFirstEncounter}） */
    EXTERNAL,

    /** 核心内部未分类变更（保护自然衰减、上限回归、数据修复等） */
    INTERNAL
}

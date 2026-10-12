package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.magic.cast.MagicCastSelfCheck;
import com.example.akaishi.forbidden.magic.formula.MagicFormulaSelfCheck;
import com.example.akaishi.forbidden.magic.judge.MagicJudgeSelfCheck;
import com.example.akaishi.forbidden.magic.table.MagicTableValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 魔法侧自检的<b>平台侧入口</b>（dev 环境 {@code -Dakaishi.gametest=1} 时于服务端启动后同步执行）。
 *
 * <p><b>为什么校验逻辑不写在这里</b>：表 / 公式 / 判定链都是 common 数据，校验不该被平台绑住 ——
 * 那样 fabric 侧就要重写一遍。故真正逻辑在 common 的四处，本类只做平台接缝与日志汇总：
 * {@link MagicTableValidator}（静态表）、{@link MagicFormulaSelfCheck}（真实公式）、
 * {@link MagicJudgeSelfCheck}（解析与判定链）、{@link MagicCastSelfCheck}（执行层骨架与状态机不变量）。
 *
 * <p><b>待接</b>：「墨 → 物品」核对要等墨物品注册完成后才能做（现在墨还不是物品）。
 */
public final class AkaishiMagicRegistryChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AkaishiMagicRegistryChecker.class);

    private AkaishiMagicRegistryChecker() {
    }

    /**
     * 同步跑完四套校验（无 tick 依赖）。
     *
     * @return {@code 1} = 全部通过；{@code 0} = 存在违例
     */
    public static int run() {
        int tables = report("表", MagicTableValidator.inspect());
        int formulas = report("公式", MagicFormulaSelfCheck.inspect());
        int judges = report("判定", MagicJudgeSelfCheck.inspect());
        int casts = report("执行", MagicCastSelfCheck.inspect());
        int plans = report("效果计划", com.example.akaishi.forbidden.magic.formula.SpellPlanSelfCheck.inspect());
        int wands = report("法杖", com.example.akaishi.forbidden.wand.WandTableValidator.inspect());
        int violations = tables + formulas + judges + casts + plans + wands;

        if (violations == 0) {
            LOGGER.info("[AkaishiMagic] 自检：PASS（静态表 + 公式不变量 + 解析判定 + 执行骨架 + 效果计划 + 法杖配件 六套全通过）");
            return 1;
        }
        LOGGER.error("[AkaishiMagic] 自检：FAIL，共 {} 处违例（表 {} / 公式 {} / 判定 {} / 执行 {} / 效果计划 {} / 法杖 {}）",
                violations, tables, formulas, judges, casts, plans, wands);
        return 0;
    }

    private static int report(String stage, List<String> violations) {
        for (String v : violations) {
            LOGGER.error("[AkaishiMagic] [FAIL][{}] {}", stage, v);
        }
        return violations.size();
    }
}

package com.example.akaishi.forbidden.command;

import com.example.akaishi.forbidden.api.sanity.ISanityRule;
import com.example.akaishi.forbidden.api.sanity.ISanityService;
import com.example.akaishi.forbidden.api.sanity.SanityContext;
import com.example.akaishi.forbidden.api.sanity.SanityFoodRegistry;
import com.example.akaishi.forbidden.api.sanity.SanityRuleRegistry;
import com.example.akaishi.forbidden.api.sanity.SanityServices;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.SanityEnvironmentSettlement;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinRules;
import com.example.akaishi.forbidden.sanity.content.SanityDarkCycle;
import com.example.akaishi.forbidden.sanity.content.SanityFoodService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 理智系统调试指令：{@code /akaishi sanity get|set|env|food}（仅管理员，权限等级 2）。
 *
 * <p><b>为什么要有它</b>：理智的输入全部是"环境/进食 + 时间"，靠手搓环境（挖到 Y&lt;0、只留 3~7 亮度、
 * 潜入深海沟、爬上下界顶部）验证一轮要几十分钟，且看不到"节流剩余 / 单次暴露累计 / 暗处冷却"这些内部量。
 * 本指令把这四个内部量直接摊开，并提供"强制结算一次"与"清零食补链"两个快捷动作，
 * 让"每 1200t 扣 1""第 3 口只回 10%"这类手感能在几十秒内验完。
 *
 * <p><b>总开关语义</b>：{@code sanityEnabled = false} 时<b>仍可查询</b>（get 照常输出五层数值与规则状态，
 * 便于确认"确实没在扣"），但 {@code set/env/food} 一律拒绝并提示——否则"关掉开关还能改数值"
 * 会让"关闭 = 整套不结算"的承诺自相矛盾。
 */
public final class AkaishiSanityCommand {

    private static final String ARG_VALUE = "value";
    private static final String ARG_RULE = "rule";
    private static final String ARG_ITEM = "item";

    /** 可写的五个数值层 */
    private enum Field {
        SAN("san"), SANC("sanc"), COG("cog"), PROTECTION("protection"), TEMP_CUT("tempcut");

        private final String key;

        Field(String key) {
            this.key = key;
        }
    }

    /** 规则 id 补全（含内置与附属：注册表里有什么就提示什么） */
    private static final SuggestionProvider<CommandSourceStack> RULE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    SanityRuleRegistry.getAll().stream().map(rule -> rule.id().toString()).toList(), builder);

    /** 食补物品 id 补全 */
    private static final SuggestionProvider<CommandSourceStack> FOOD_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    SanityFoodRegistry.getAll().stream().map(profile -> profile.itemId().toString()).toList(), builder);

    private AkaishiSanityCommand() {
    }

    /** 注册指令树（由 {@code ModCommands.build} 调用） */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("akaishi")
                .then(Commands.literal("sanity")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("get").executes(AkaishiSanityCommand::get))
                        .then(Commands.literal("set")
                                .then(fieldBranch("san", Field.SAN))
                                .then(fieldBranch("sanc", Field.SANC))
                                .then(fieldBranch("cog", Field.COG))
                                .then(fieldBranch("protection", Field.PROTECTION))
                                .then(fieldBranch("tempcut", Field.TEMP_CUT)))
                        .then(Commands.literal("env")
                                .then(Commands.argument(ARG_RULE, StringArgumentType.word())
                                        .suggests(RULE_SUGGESTIONS)
                                        .executes(AkaishiSanityCommand::env)))
                        .then(Commands.literal("food")
                                .then(Commands.argument(ARG_ITEM, StringArgumentType.word())
                                        .suggests(FOOD_SUGGESTIONS)
                                        .executes(AkaishiSanityCommand::food)))));
    }

    /** {@code get}：五层数值 + 各规则当前状态（是否命中 / 节流剩余 / 单次累计 / 暗处冷却） */
    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.forbidden_hex.sanity.noState"));
            return 0;
        }
        long now = player.level().getGameTime();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("commands.forbidden_hex.sanity.get.header",
                fmt(state.san()), fmt(state.sanc()), fmt(state.cog()), fmt(state.protection()),
                fmt(state.tempCut()), fmt(state.effectiveMax())), false);
        // 规则状态：用一次性上下文求 applies（与结算同一份口径），只读、不推进任何状态
        SanityContext context = SanityEnvironmentSettlement.debugContext(player.serverLevel(), player);
        List<ISanityRule> rules = new ArrayList<>(SanityRuleRegistry.getAll());
        rules.sort(Comparator.comparing(rule -> rule.id().toString()));
        for (ISanityRule rule : rules) {
            String id = rule.id().toString();
            boolean applies;
            try {
                applies = rule.applies(context);
            } catch (Throwable t) {
                applies = false; // 与结算层同口径：判定异常视为不命中（此处只为打印）
            }
            SanityState.RuleRuntime runtime = state.ruleStates().get(id);
            int throttle = runtime == null ? 0 : runtime.throttle();
            float accumulated = runtime == null ? 0f : runtime.accumulated();
            int period = rule.periodTicks();
            Component applyText = Component.translatable(applies
                    ? "commands.forbidden_hex.sanity.rule.on" : "commands.forbidden_hex.sanity.rule.off");
            Component capText = rule.capPerExposure() > 0
                    ? Component.literal(String.valueOf(rule.capPerExposure()))
                    : Component.translatable("commands.forbidden_hex.sanity.cap.none");
            boolean dark = SanityBuiltinRules.isDarkRule(rule.id());
            long cooldown = dark ? SanityDarkCycle.cooldownRemaining(state, now) : 0L;
            source.sendSuccess(() -> Component.translatable("commands.forbidden_hex.sanity.get.rule",
                    id, applyText, Math.max(0, period - throttle), period, fmt(accumulated), capText, cooldown), false);
        }
        source.sendSuccess(() -> Component.translatable("commands.forbidden_hex.sanity.get.dark",
                fmt(state.darkExposure()), SanityDarkCycle.cooldownRemaining(state, now),
                Component.translatable(state.darkCycleEnded()
                        ? "commands.forbidden_hex.sanity.yes" : "commands.forbidden_hex.sanity.no")), false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code set <字段> <值>}：五层数值直写（走对外 API 的写入漏斗，夹取/回调/阈值一律照常） */
    private static int set(CommandContext<CommandSourceStack> ctx, Field field) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (refuseWhenDisabled(source)) {
            return 0;
        }
        float value = FloatArgumentType.getFloat(ctx, ARG_VALUE);
        ISanityService service = SanityServices.get();
        switch (field) {
            case SAN -> service.setSan(player, value);
            case SANC -> service.setSanc(player, value);
            case COG -> service.setCog(player, value);
            case PROTECTION -> service.setProtection(player, value);
            case TEMP_CUT -> service.setTempCut(player, value);
        }
        source.sendSuccess(() -> Component.translatable("commands.forbidden_hex.sanity.set.ok",
                Component.translatable("commands.forbidden_hex.sanity.field." + field.key), fmt(value)), true);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code env <规则 id>}：强制结算该规则一次（绕过节流，仍守单次累计上限与否决门） */
    private static int env(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (refuseWhenDisabled(source)) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, ARG_RULE);
        ISanityRule rule = SanityRuleRegistry.get(id);
        if (rule == null) {
            source.sendFailure(Component.translatable("commands.forbidden_hex.sanity.env.unknown", id));
            return 0;
        }
        boolean debited = SanityEnvironmentSettlement.settleRuleNow(player, rule);
        source.sendSuccess(() -> Component.translatable(debited
                ? "commands.forbidden_hex.sanity.env.ok" : "commands.forbidden_hex.sanity.env.blocked", id), true);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code food <物品 id>}：清空该物品的食补窗口与链计数（下次食用回到第 1 档，便于验衰减） */
    private static int food(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (refuseWhenDisabled(source)) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, ARG_ITEM);
        ResourceLocation itemId = ResourceLocation.tryParse(raw);
        if (itemId == null || !SanityFoodService.resetChain(player, itemId)) {
            source.sendFailure(Component.translatable("commands.forbidden_hex.sanity.food.unknown", raw));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.forbidden_hex.sanity.food.ok", raw), true);
        return Command.SINGLE_SUCCESS;
    }

    /** 总开关关闭时统一拒绝写入类子命令；返回 true 表示已拒绝 */
    private static boolean refuseWhenDisabled(CommandSourceStack source) {
        if (ModConfig.sanityEnabled) {
            return false;
        }
        source.sendFailure(Component.translatable("commands.forbidden_hex.sanity.disabled"));
        return true;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> fieldBranch(String name, Field field) {
        return Commands.literal(name)
                .then(Commands.argument(ARG_VALUE, FloatArgumentType.floatArg(0f))
                        .executes(ctx -> set(ctx, field)));
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}

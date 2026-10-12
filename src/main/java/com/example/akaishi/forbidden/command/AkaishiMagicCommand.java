package com.example.akaishi.forbidden.command;

import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 魔法侧调试指令：{@code /akaishi magic unlockall | maxlevel | resetwords}（仅管理员，权限等级 2）。
 *
 * <p><b>为什么要有它</b>：词的解锁与等级提升要按正常的习得链路走，验证铭文台时
 * “能不能写满 8 词 / 各分类符文是否都正常显示”需要先把词库与等级开满，
 * 否则每次都要先去刷一遍解锁流程。本指令直接把这层打开，仅用于调试。
 */
public final class AkaishiMagicCommand {

    private AkaishiMagicCommand() {
    }

    /** 注册指令树（由平台入口在 {@code RegisterCommandsEvent} 中调用） */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("akaishi")
                .then(Commands.literal("magic")
                        .requires(src -> src.hasPermission(2))
                        // 开满等级 + 解锁全部词
                        .then(Commands.literal("unlockall").executes(AkaishiMagicCommand::unlockAll))
                        // 只把等级拉到上限
                        .then(Commands.literal("maxlevel").executes(AkaishiMagicCommand::maxLevel))
                        // 清空已解锁词（回到未解锁态，便于复验）
                        .then(Commands.literal("resetwords").executes(AkaishiMagicCommand::resetWords))));
    }

    private static int unlockAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicState state = MagicStates.state(player);
        if (state == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.forbidden_hex.magic.noState"));
            return 0;
        }
        state.setLevel(MagicState.LEVEL_MAX);
        int added = 0;
        for (MagicWord word : MagicWord.values()) {
            if (state.unlockWord(word.id())) {
                added++;
            }
        }
        int total = MagicWord.values().length;
        int addedFinal = added;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.forbidden_hex.magic.unlockall.ok", MagicState.LEVEL_MAX, total, addedFinal), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int maxLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicState state = MagicStates.state(player);
        if (state == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.forbidden_hex.magic.noState"));
            return 0;
        }
        state.setLevel(MagicState.LEVEL_MAX);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.forbidden_hex.magic.maxlevel.ok", MagicState.LEVEL_MAX), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetWords(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicState state = MagicStates.state(player);
        if (state == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.forbidden_hex.magic.noState"));
            return 0;
        }
        state.clearWords();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.forbidden_hex.magic.resetwords.ok"), true);
        return Command.SINGLE_SUCCESS;
    }
}

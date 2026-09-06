package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.google.common.base.Splitter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The multi-page subcommands under {@code /hologram}: {@code /hologram page <name> add <text…>} appends a page,
 * {@code remove <index>} drops one, and {@code list} shows the page set. A page may carry several lines, the
 * {@code add} text is split on a literal {@code \n}, so {@code add Welcome\nto spawn} adds a two-line page;
 * blank segments are dropped (a spacer uses a non-blank placeholder, like every hologram line). Page indices
 * are 1-based at the command boundary and converted to 0-based for the use cases. Collected here so the main
 * {@code /hologram} command class stays focused while keeping the single literal intact.
 */
@NullMarked
final class HologramPageCommand extends HologramCommandSupport {

    private static final Splitter LINE_SPLITTER = Splitter.on("\\n");

    HologramPageCommand(
            HologramServices services, Messages messages, Supplier<? extends Collection<String>> hologramNames) {
        super(services, messages, hologramNames);
    }

    /** The single {@code page} subcommand node the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(Commands.literal("page")
                .then(nameArgument("name")
                        .then(Commands.literal("add")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::add)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(this::remove)))
                        .then(Commands.literal("list").executes(this::list))));
    }

    private int add(CommandContext<CommandSourceStack> ctx) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        List<HologramLine> lines = LINE_SPLITTER.splitToList(ctx.getArgument("text", String.class)).stream()
                .map(String::strip)
                .filter(segment -> !segment.isBlank())
                .map(HologramLine::new)
                .toList();
        if (lines.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_MIN_ONE_LINE, Map.of());
            return 0;
        }
        services.addPage().add(actor(ctx), nameArg(ctx), lines);
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        services.removePage().remove(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class) - 1);
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        services.listPages().list(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }
}

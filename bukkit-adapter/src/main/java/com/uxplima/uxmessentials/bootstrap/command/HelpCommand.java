package com.uxplima.uxmessentials.bootstrap.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.google.common.base.Splitter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /help [page|query]}: the cross-cutting command listing (docs/13-i18n §7). It is owned by no
 * feature context, so it lives in the bootstrap command surface alongside {@code /uxmess} and {@code /lang}.
 * Open to everyone (permission {@code uxmessentials.help}, default true); per-line filtering uses each
 * command's own permission, so the page shows only the commands the sender can actually run.
 *
 * <p>The listing reads the same resolved command surface the plugin registers: the catalog-renamed,
 * module-filtered registrations the {@code LifecycleEvents.COMMANDS} handler publishes, supplied lazily so
 * {@code /help} reflects every operator rename and disable. Each entry is a clickable, hoverable line in the
 * sender's locale carrying the effective name, its short description (or its aliases when it carries none),
 * paginated with clickable prev/next navigation.
 */
@NullMarked
public final class HelpCommand implements CommandRegistration {

    private static final String LITERAL = "help";
    private static final String PERMISSION = "uxmessentials.help";
    private static final String DESCRIPTION = "List the commands you can use.";
    private static final int PAGE_SIZE = 8;
    private static final Splitter SPACE = Splitter.on(' ').omitEmptyStrings();

    private final Supplier<List<CommandRegistration>> commands;
    private final Messages messages;
    private final CommandFeedback feedback;

    public HelpCommand(Supplier<List<CommandRegistration>> commands, Messages messages) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.feedback = new CommandFeedback(messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, ""))
                .then(Commands.argument("page-or-query", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, ctx.getArgument("page-or-query", String.class))))
                .build();
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    private int run(CommandContext<CommandSourceStack> ctx, String argument) {
        CommandSourceStack source = ctx.getSource();
        CommandSender sender = source.getSender();
        String query = isPageNumber(argument) ? "" : argument.trim();
        List<HelpEntry> entries = visibleEntries(source, query);
        if (entries.isEmpty()) {
            sendEmpty(sender, query);
            return Command.SINGLE_SUCCESS;
        }
        int pages = pageCount(entries.size());
        int page = clampPage(pageOf(argument), pages);
        renderPage(sender, entries, page, pages);
        return Command.SINGLE_SUCCESS;
    }

    private List<HelpEntry> visibleEntries(CommandSourceStack source, String query) {
        List<HelpEntry> out = new ArrayList<>();
        for (CommandRegistration registration : commands.get()) {
            LiteralCommandNode<CommandSourceStack> node = registration.build();
            if (!node.canUse(source)) {
                continue;
            }
            HelpEntry entry = new HelpEntry(node.getLiteral(), registration.description(), registration.aliases());
            if (entry.matches(query)) {
                out.add(entry);
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    private void renderPage(CommandSender sender, List<HelpEntry> entries, int page, int pages) {
        feedback.send(sender, SharedMessageKey.HELP_HEADER, Map.of("page", str(page), "pages", str(pages)));
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());
        for (HelpEntry entry : entries.subList(start, end)) {
            sendEntry(sender, entry);
        }
        if (pages > 1) {
            sendFooter(sender, page, pages);
        }
    }

    private void sendEntry(CommandSender sender, HelpEntry entry) {
        if (entry.hasDescription()) {
            feedback.send(
                    sender,
                    SharedMessageKey.HELP_ENTRY,
                    Map.of("command", entry.name(), "description", entry.description()));
        } else {
            feedback.send(
                    sender,
                    SharedMessageKey.HELP_ENTRY_ALIASES,
                    Map.of("command", entry.name(), "aliases", entry.aliasList()));
        }
    }

    private void sendFooter(CommandSender sender, int page, int pages) {
        String prev = page > 1 ? resolve(sender, SharedMessageKey.HELP_FOOTER_PREV, page - 1) : " ";
        String next = page < pages ? resolve(sender, SharedMessageKey.HELP_FOOTER_NEXT, page + 1) : " ";
        feedback.send(
                sender,
                SharedMessageKey.HELP_FOOTER,
                Map.of("prev", prev, "next", next, "page", str(page), "pages", str(pages)));
    }

    private void sendEmpty(CommandSender sender, String query) {
        SharedMessageKey key = query.isEmpty() ? SharedMessageKey.HELP_EMPTY : SharedMessageKey.HELP_NO_MATCH;
        feedback.send(sender, key, Map.of("query", query));
    }

    private String resolve(CommandSender sender, SharedMessageKey key, int target) {
        return messages.resolve(CommandFeedback.refOf(sender), key, Map.of("target", str(target)));
    }

    private static int pageCount(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static int clampPage(int requested, int pages) {
        return Math.min(Math.max(requested, 1), pages);
    }

    /** The page number requested by {@code argument}, or 1 when it is a query or absent. */
    private static int pageOf(String argument) {
        return isPageNumber(argument) ? Integer.parseInt(argument.trim()) : 1;
    }

    private static boolean isPageNumber(String argument) {
        String trimmed = argument.trim();
        if (trimmed.isEmpty() || trimmed.length() > 9) {
            return false;
        }
        return trimmed.chars().allMatch(Character::isDigit);
    }

    private static String str(int value) {
        return Integer.toString(value);
    }

    /** One listed command: its effective name, short description, and the aliases it answers to. */
    private record HelpEntry(String name, String description, List<String> aliases) {

        boolean hasDescription() {
            return !description.isBlank();
        }

        String aliasList() {
            return String.join(", ", aliases);
        }

        boolean matches(String query) {
            if (query.isEmpty()) {
                return true;
            }
            String needle = query.toLowerCase(Locale.ROOT);
            return SPACE.splitToList(needle).stream().allMatch(this::containsToken);
        }

        private boolean containsToken(String token) {
            if (name.toLowerCase(Locale.ROOT).contains(token)
                    || description.toLowerCase(Locale.ROOT).contains(token)) {
                return true;
            }
            return aliases.stream()
                    .anyMatch(alias -> alias.toLowerCase(Locale.ROOT).contains(token));
        }
    }
}

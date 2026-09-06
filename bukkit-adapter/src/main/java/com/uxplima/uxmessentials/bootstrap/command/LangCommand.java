package com.uxplima.uxmessentials.bootstrap.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /lang}, the per-player {@code /lang} locale override (docs/13-i18n §7). Cross-cutting rather
 * than owned by a feature context, so it lives in the bootstrap command surface alongside
 * {@code /uxmess}. Permission node {@code uxmessentials.lang.use} (default true).
 *
 * <pre>
 *   /lang          show the current resolved locale, the override state, and the available locales
 *   /lang &lt;code&gt;   set the override (must be a loaded locale), e.g. /lang tr
 *   /lang reset    clear the override so resolution falls back to the client locale
 * </pre>
 *
 * <p>Every reply resolves through a {@link SharedMessageKey} and delivers via the {@link MessageSink},
 * so a {@code /lang} confirmation itself renders in the player's now-current locale. {@code /lang tr}
 * validates against {@link LocaleCatalog#loadedLocales()}; an unknown or unloaded code yields
 * {@link SharedMessageKey#LANG_UNKNOWN}.
 */
@NullMarked
public final class LangCommand implements CommandRegistration {

    private static final String LITERAL = "lang";
    private static final String PERMISSION = "uxmessentials.lang.use";
    private static final String RESET_LITERAL = "reset";

    private final LocaleStore overrides;
    private final LocaleCatalog catalog;
    private final Messages messages;
    private final MessageSink sink;
    private final CommandFeedback feedback;

    public LangCommand(LocaleStore overrides, LocaleCatalog catalog, Messages messages, MessageSink sink) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.feedback = new CommandFeedback(messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runStatus)
                .then(Commands.literal(RESET_LITERAL).executes(this::runReset))
                .then(Commands.argument("code", StringArgumentType.word()).executes(this::runSet))
                .build();
    }

    @Override
    public String description() {
        return "Set or clear your personal language override.";
    }

    private int runStatus(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer,
                        SharedMessageKey.LANG_STATUS,
                        Map.of("locale", currentName(player), "available", availableCodes())));
        return Command.SINGLE_SUCCESS;
    }

    private int runReset(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        overrides.clearOverride(viewer);
        sink.deliver(viewer, messages.resolve(viewer, SharedMessageKey.LANG_RESET, Map.of()));
        player.updateCommands();
        return Command.SINGLE_SUCCESS;
    }

    private int runSet(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        String code = ctx.getArgument("code", String.class);
        Locale requested = Locale.forLanguageTag(code);
        if (!isLoaded(requested)) {
            sink.deliver(
                    viewer,
                    messages.resolve(
                            viewer,
                            SharedMessageKey.LANG_UNKNOWN,
                            Map.of("code", code, "available", availableCodes())));
            return 0;
        }
        overrides.setOverride(viewer, requested);
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer, SharedMessageKey.LANG_SET, Map.of("locale", requested.getDisplayLanguage(requested))));
        player.updateCommands();
        return Command.SINGLE_SUCCESS;
    }

    private boolean isLoaded(Locale requested) {
        String language = requested.getLanguage();
        return !language.isEmpty()
                && catalog.loadedLocales().stream()
                        .anyMatch(loaded -> loaded.getLanguage().equals(language));
    }

    private String currentName(Player player) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        Locale resolved = overrides.override(viewer).orElseGet(player::locale);
        return resolved.getDisplayLanguage(resolved);
    }

    private String availableCodes() {
        return catalog.loadedLocales().stream()
                .map(Locale::getLanguage)
                .filter(language -> !language.isEmpty())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /** The invoking player, or {@code null} (after the players-only reply) for a console source. */
    private @org.jspecify.annotations.Nullable Player playerOf(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        // A console has no client locale and no PDC to store an override; reply directly rather than
        // through the entity-scheduler sink (which no-ops for a non-player), mirroring the feature
        // commands' players-only path.
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of());
        return null;
    }
}

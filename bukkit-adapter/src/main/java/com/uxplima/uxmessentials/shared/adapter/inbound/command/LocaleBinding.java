package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Binds the requesting player's resolved locale into {@link LocaleScope#CURRENT} at the inbound command
 * boundary (docs/13-i18n §5).
 *
 * <p>A command runs on the player's region thread, where {@code Player.locale()} is a legal call. This
 * decorator captures that client locale once at dispatch, folds in any persisted {@code /lang} override,
 * and binds the result as a {@link ScopedValue} for the duration of the handler, so every synchronous
 * {@code messages.resolve(...)} the handler makes renders in the right language without threading a
 * {@code Locale} through any signature. Work the handler defers off-tick through the {@code Scheduler}
 * port re-binds the captured locale with {@link LocaleScope#runWith} (the {@code ScopedValue} does not
 * cross an executor hop on its own); the binding here covers the on-thread portion and is the single
 * place client-locale capture touches the Bukkit API.
 *
 * <p>It wraps a built Brigadier tree by rebuilding each node with the same requirement and a
 * scope-binding {@link Command}, so the binding applies uniformly to every executor in the command
 * the root and every argument leaf: rather than each handler opening its own scope.
 *
 * <p>Being the one wrapper every published command passes through, this is also the last line of defence
 * against a handler bug. An unexpected {@link RuntimeException} from an executor would otherwise reach
 * Paper's dispatcher and leave the player with a raw red "unexpected error" plus a console stacktrace; here
 * it is caught, recorded for the operator with the offending input, and answered with the localized
 * {@link SharedMessageKey#COMMAND_ERROR} line, reporting the command handled so Brigadier adds no usage spam.
 * A {@link CommandSyntaxException} is Brigadier's own control flow (usage / parse failures) and is always
 * rethrown unchanged.
 */
@NullMarked
public final class LocaleBinding {

    private final LocaleStore overrides;
    private final Locale serverDefault;
    private final CommandFeedback feedback;
    private final Logger log;

    public LocaleBinding(LocaleStore overrides, Locale serverDefault, Messages messages, Logger log) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.serverDefault = Objects.requireNonNull(serverDefault, "serverDefault");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Wrap {@code registration} so every executor in its tree runs with the locale bound. */
    public CommandRegistration wrap(CommandRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        return new BoundRegistration(registration, this);
    }

    private LiteralCommandNode<CommandSourceStack> rebind(LiteralCommandNode<CommandSourceStack> node) {
        LiteralArgumentBuilder<CommandSourceStack> builder =
                io.papermc.paper.command.brigadier.Commands.literal(node.getLiteral());
        if (node.getRequirement() != null) {
            builder.requires(node.getRequirement());
        }
        if (node.getCommand() != null) {
            builder.executes(wrapCommand(node.getCommand()));
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(rebindChild(child));
        }
        return builder.build();
    }

    private com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> rebindChild(
            CommandNode<CommandSourceStack> child) {
        @SuppressWarnings("unchecked") // createBuilder() reproduces the node's own builder type
        com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> builder =
                (com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?>) child.createBuilder();
        if (child.getCommand() != null) {
            builder.executes(wrapCommand(child.getCommand()));
        }
        for (CommandNode<CommandSourceStack> grandchild : child.getChildren()) {
            builder.then(rebindChild(grandchild));
        }
        return builder;
    }

    private Command<CommandSourceStack> wrapCommand(Command<CommandSourceStack> delegate) {
        return ctx -> runBound(ctx, delegate);
    }

    private int runBound(CommandContext<CommandSourceStack> ctx, Command<CommandSourceStack> delegate)
            throws CommandSyntaxException {
        if (ctx.getSource().getSender() instanceof Player player) {
            return runInLocale(ctx, delegate, player);
        }
        try {
            return delegate.run(ctx); // console / command block has no client locale to bind
        } catch (RuntimeException bug) {
            // CommandSyntaxException is checked, so it slips past this catch and surfaces as Brigadier expects.
            return reportFailure(ctx, ctx.getSource().getSender(), bug);
        }
    }

    private int runInLocale(CommandContext<CommandSourceStack> ctx, Command<CommandSourceStack> delegate, Player player)
            throws CommandSyntaxException {
        Locale resolved = resolveFor(player);
        int[] result = new int[1];
        CommandSyntaxException[] syntax = new CommandSyntaxException[1];
        RuntimeException[] bug = new RuntimeException[1];
        LocaleScope.runWith(resolved, () -> {
            try {
                result[0] = delegate.run(ctx);
            } catch (CommandSyntaxException control) {
                syntax[0] = control; // Brigadier control flow: rethrown untouched below.
            } catch (RuntimeException failure) {
                bug[0] = failure;
                feedback.send(player, SharedMessageKey.COMMAND_ERROR); // in scope: renders in the bound locale
                result[0] = Command.SINGLE_SUCCESS;
            }
        });
        if (syntax[0] != null) {
            throw syntax[0];
        }
        if (bug[0] != null) {
            logFailure(ctx, bug[0]); // the player already saw the friendly reply inside the scope
        }
        return result[0];
    }

    /** Answer a non-player source with the friendly line, record the fault, and report the command handled. */
    private int reportFailure(CommandContext<CommandSourceStack> ctx, CommandSender sender, RuntimeException bug) {
        feedback.send(sender, SharedMessageKey.COMMAND_ERROR);
        logFailure(ctx, bug);
        return Command.SINGLE_SUCCESS;
    }

    private void logFailure(CommandContext<CommandSourceStack> ctx, RuntimeException bug) {
        log.error("event=command_failed input=" + ctx.getInput(), bug);
    }

    private Locale resolveFor(Player player) {
        PlayerRef ref = BukkitRefs.toRef(player);
        Optional<Locale> override = overrides.override(ref);
        if (override.isPresent()) {
            return override.get();
        }
        Locale client = player.locale();
        return client.getLanguage().isEmpty() ? serverDefault : client;
    }

    /** A {@link CommandRegistration} whose built tree is rebound to carry the locale scope. */
    private record BoundRegistration(CommandRegistration delegate, LocaleBinding binding)
            implements CommandRegistration {

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return binding.rebind(delegate.build());
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public List<String> aliases() {
            return delegate.aliases();
        }

        @Override
        public Optional<Command<CommandSourceStack>> guiRoot() {
            return delegate.guiRoot();
        }
    }
}

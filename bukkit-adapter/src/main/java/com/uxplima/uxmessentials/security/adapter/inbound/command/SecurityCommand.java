package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.security.adapter.VerificationController;
import com.uxplima.uxmessentials.security.application.FactorScope;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.security.application.ForceReverification.ForceResult;
import com.uxplima.uxmessentials.security.application.ResetFactors;
import com.uxplima.uxmessentials.security.application.ResetFactors.ResetResult;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /security}: the operator surface over <em>another</em> player's second factors. {@code status <player>} to
 * see which factors they hold, {@code force <player>} to push them back into verification, and {@code reset <player>
 * [totp|pin|all]} to clear a factor they can no longer prove.
 *
 * <p>These verbs live here rather than on {@code /2fa} and {@code /pin} on purpose. Those two are self-service and
 * factor-scoped: each manages exactly one of the caller's own factors. An admin verb on either would blur both lines
 * at once, since forcing and resetting act on someone else and are not specific to one factor. One admin root also
 * means one place to gate: the whole tree needs {@code uxmessentials.security.admin}, with {@code force} and
 * {@code reset} carrying their own nodes on top, so a junior moderator can be given the read without the recovery.
 *
 * <p>{@code reset} is the only path in the module that removes a factor without proving it, which is what makes it
 * the recovery door for a player who has lost their authenticator or forgotten their PIN. Every use is logged with
 * the operator, the target and the scope, because "staff removed someone's second factor" is exactly the event an
 * incident review needs to find. The lookup, the store write and the reply all run off the tick thread.
 */
@NullMarked
public final class SecurityCommand extends SecurityCommandSupport implements CommandRegistration {

    /** Gates the whole {@code /security} tree, including the read-only status verb. */
    public static final String PERMISSION = "uxmessentials.security.admin";

    /** The extra node {@code /security force <player>} carries on top of {@link #PERMISSION}. */
    public static final String FORCE_PERMISSION = "uxmessentials.security.force";

    /** The extra node {@code /security reset <player>} carries on top of {@link #PERMISSION}. */
    public static final String RESET_PERMISSION = "uxmessentials.security.reset";

    private static final List<String> SCOPES = List.of("totp", "pin", "all");

    private final TwoFactorRepository repository;
    private final ForceReverification forceReverification;
    private final ResetFactors resetFactors;
    private final VerificationController verification;
    private final PlayerLookup lookup;
    private final Logger log;

    public SecurityCommand(
            TwoFactorRepository repository,
            ForceReverification forceReverification,
            ResetFactors resetFactors,
            VerificationController verification,
            PlayerLookup lookup,
            Logger log,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink) {
        super(scheduler, messages, sink);
        this.repository = Objects.requireNonNull(repository, "repository");
        this.forceReverification = Objects.requireNonNull(forceReverification, "forceReverification");
        this.resetFactors = Objects.requireNonNull(resetFactors, "resetFactors");
        this.verification = Objects.requireNonNull(verification, "verification");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("security")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("status")
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_ADMIN_USAGE))
                        .then(CommandSuggestions.playerArgument("player").executes(this::status)))
                .then(Commands.literal("force")
                        .requires(src -> src.getSender().hasPermission(FORCE_PERMISSION))
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_ADMIN_FORCE_USAGE))
                        .then(CommandSuggestions.playerArgument("player").executes(this::force)))
                .then(Commands.literal("reset")
                        .requires(src -> src.getSender().hasPermission(RESET_PERMISSION))
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_ADMIN_RESET_USAGE))
                        .then(CommandSuggestions.playerArgument("player")
                                .executes(ctx -> reset(ctx, FactorScope.ALL))
                                .then(Commands.argument("factor", StringArgumentType.word())
                                        .suggests(CommandSuggestions.fromStrings(() -> SCOPES))
                                        .executes(this::resetScoped))))
                .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_ADMIN_USAGE))
                .build();
    }

    @Override
    public String description() {
        return "/security status, force or reset a player's second factors.";
    }

    private int usage(CommandContext<CommandSourceStack> ctx, MessageKey key) {
        reply(ctx.getSource().getSender(), key);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /security status <player>}: which factors the target holds, one line each. */
    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = ctx.getArgument("player", String.class);
        scheduler.async(() -> onTarget(sender, name, target -> {
            TwoFactorRegistration registration = repository.find(target.uuid()).orElse(null);
            Map<String, String> who = Map.of("player", target.name());
            notifySender(sender, SecurityMessageKey.SECURITY_ADMIN_STATUS_HEADER, who);
            if (registration == null || !registration.hasAnyFactor()) {
                notifySender(sender, SecurityMessageKey.SECURITY_ADMIN_STATUS_NONE, who);
                return;
            }
            if (registration.totpEnabled()) {
                notifySender(sender, SecurityMessageKey.SECURITY_ADMIN_STATUS_TOTP, who);
            }
            if (registration.pinSet()) {
                notifySender(sender, SecurityMessageKey.SECURITY_ADMIN_STATUS_PIN, who);
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /security force <player>}: force a target (online or offline) to re-verify. The durable trust revoke and
     * the reply run off the tick thread; when the target is online the verification controller drives them straight
     * back into the freeze.
     */
    private int force(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = ctx.getArgument("player", String.class);
        scheduler.async(() -> onTarget(sender, name, target -> {
            if (forceReverification.force(target.uuid()) == ForceResult.NOT_ENROLLED) {
                notifySender(
                        sender, SecurityMessageKey.SECURITY_ADMIN_FORCE_NOT_ENROLLED, Map.of("player", target.name()));
                return;
            }
            // The trust is revoked (next join re-verifies); if the target is online now, freeze them immediately.
            verification.forceReverify(target);
            notifySender(sender, SecurityMessageKey.SECURITY_ADMIN_FORCE_DONE, Map.of("player", target.name()));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int resetScoped(CommandContext<CommandSourceStack> ctx) {
        String raw = ctx.getArgument("factor", String.class);
        return reset(ctx, scopeOf(raw));
    }

    /** {@code /security reset <player> [scope]}: clear a factor the target can no longer prove, and log it. */
    private int reset(CommandContext<CommandSourceStack> ctx, FactorScope scope) {
        CommandSender sender = ctx.getSource().getSender();
        String name = ctx.getArgument("player", String.class);
        String scopeName = scope.name().toLowerCase(Locale.ROOT);
        scheduler.async(() -> onTarget(sender, name, target -> {
            Map<String, String> placeholders = Map.of("player", target.name(), "factor", scopeName);
            if (resetFactors.reset(target.uuid(), scope) == ResetResult.NOTHING_TO_RESET) {
                notifySender(sender, SecurityMessageKey.SECURITY_ADMIN_RESET_NOTHING, placeholders);
                return;
            }
            // Staff removing someone's second factor without proving it is an auditable event, so it always lands in
            // the log with who did it, to whom, and how much was cleared.
            log.info(
                    "event=security_factor_reset operator={} target={} scope={}",
                    sender.getName(),
                    target.name(),
                    scopeName);
            // A target who is online right now has just had their second factor taken away underneath them. What that
            // should mean for the session they are standing in is the operator's call, not ours.
            verification.onAccessRevoked(target);
            notifySender(sender, SecurityMessageKey.SECURITY_ADMIN_RESET_DONE, placeholders);
        }));
        return Command.SINGLE_SUCCESS;
    }

    /** Resolve {@code name} off-thread and run {@code action} on the match, or tell the caller there is none. */
    private void onTarget(CommandSender sender, String name, Consumer<PlayerRef> action) {
        Optional<PlayerRef> target = lookup.findByName(name);
        if (target.isEmpty()) {
            notifySender(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            return;
        }
        action.accept(target.get());
    }

    /** The scope named by {@code raw}; anything unrecognised is the safest reading, a single factor is not assumed. */
    private static FactorScope scopeOf(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "totp" -> FactorScope.TOTP;
            case "pin" -> FactorScope.PIN;
            default -> FactorScope.ALL;
        };
    }
}

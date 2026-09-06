package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.security.application.ChangePin;
import com.uxplima.uxmessentials.security.application.PinChangeResult;
import com.uxplima.uxmessentials.security.application.PinRemoveResult;
import com.uxplima.uxmessentials.security.application.PinSetResult;
import com.uxplima.uxmessentials.security.application.RemovePin;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /pin}: the numeric PIN factor's own self-service surface, {@code set <pin>} to enrol a first PIN,
 * {@code change <old> <new>} to replace it, and {@code remove <pin>} to drop it. Bare {@code /pin} reports whether
 * one is set. Gated on {@code uxmessentials.security.pin}, which ships {@code true}.
 *
 * <p>This command owns the PIN and nothing else. It never reads, proves, or removes an authenticator factor, exactly
 * as {@code /2fa} never touches a PIN: a player who holds both keeps two independent protections, and neither can be
 * used to strip the other. The operator verbs live on {@code /security}, so nothing here is admin-gated.
 *
 * <p>Every verb is off-thread past its cheap gating: the PBKDF2 hashing is deliberately slow and the store is the DB,
 * so the tick thread only ever sees the argument parse and the usage replies. A plaintext PIN is never logged.
 */
@NullMarked
public final class PinCommand extends SecurityCommandSupport implements CommandRegistration {

    /** The self-service permission every player holds to manage their own PIN. */
    public static final String PERMISSION = "uxmessentials.security.pin";

    private final SetPin setPin;
    private final ChangePin changePin;
    private final RemovePin removePin;
    private final TwoFactorRepository repository;
    private final SecurityConfig.TwoFactorSettings settings;
    private final SelfLock selfLock;
    private final Clock clock;

    public PinCommand(
            SetPin setPin,
            ChangePin changePin,
            RemovePin removePin,
            TwoFactorRepository repository,
            SecurityConfig.TwoFactorSettings settings,
            SelfLock selfLock,
            Clock clock,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink) {
        super(scheduler, messages, sink);
        this.setPin = Objects.requireNonNull(setPin, "setPin");
        this.changePin = Objects.requireNonNull(changePin, "changePin");
        this.removePin = Objects.requireNonNull(removePin, "removePin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.selfLock = Objects.requireNonNull(selfLock, "selfLock");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pin")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("set")
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_PIN_USAGE))
                        .then(Commands.argument("pin", StringArgumentType.word())
                                .executes(this::set)))
                .then(Commands.literal("change")
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_PIN_CHANGE_USAGE))
                        .then(Commands.argument("old", StringArgumentType.word())
                                .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_PIN_CHANGE_USAGE))
                                .then(Commands.argument("new", StringArgumentType.word())
                                        .executes(this::change))))
                .then(Commands.literal("remove")
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_PIN_REMOVE_USAGE))
                        .then(Commands.argument("pin", StringArgumentType.word())
                                .executes(this::remove)))
                .then(Commands.literal("lock").executes(this::lock))
                .executes(this::status)
                .build();
    }

    @Override
    public String description() {
        return "/pin set, change, remove or lock with your numeric PIN second factor.";
    }

    /**
     * What {@code /pin lock} drives: putting the caller back behind their own keypad. The seam keeps this command
     * clear of the join-verification controller, which is the thing that actually owns the freeze.
     */
    @FunctionalInterface
    public interface SelfLock {

        /** Freeze {@code viewer} and show them the keypad, as though they had just joined. */
        void lock(Player player, PlayerRef viewer);
    }

    /**
     * {@code /pin lock}: hand your own session back to the keypad before you step away from the keyboard. It is the
     * cheap answer to the most ordinary way an account is taken, which is not a cracked password but an unattended
     * client someone else sits down at. A player with no PIN has nothing to unlock with, so they are told to set one
     * rather than frozen out of their own session.
     */
    private int lock(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx.getSource().getSender());
        if (player == null) {
            return 0;
        }
        PlayerRef who = ref(player);
        scheduler.async(() -> {
            boolean set = repository
                    .find(who.uuid())
                    .map(TwoFactorRegistration::pinSet)
                    .orElse(false);
            if (!set) {
                notify(who, SecurityMessageKey.SECURITY_PIN_LOCK_NOT_SET);
                return;
            }
            scheduler.onEntity(who, () -> {
                notify(who, SecurityMessageKey.SECURITY_PIN_LOCK_DONE);
                selfLock.lock(player, who);
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int usage(CommandContext<CommandSourceStack> ctx, MessageKey key) {
        reply(ctx.getSource().getSender(), key);
        return Command.SINGLE_SUCCESS;
    }

    /** Bare {@code /pin}: report whether this player holds a PIN. The store read hops off the tick thread. */
    private int status(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx.getSource().getSender());
        if (player == null) {
            return 0;
        }
        PlayerRef who = ref(player);
        scheduler.async(() -> {
            boolean set = repository
                    .find(who.uuid())
                    .map(TwoFactorRegistration::pinSet)
                    .orElse(false);
            notify(who, set ? SecurityMessageKey.SECURITY_PIN_STATUS : SecurityMessageKey.SECURITY_PIN_STATUS_NONE);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player player = enrollingPlayer(ctx);
        if (player == null) {
            return 0;
        }
        String pin = ctx.getArgument("pin", String.class);
        PlayerRef who = ref(player);
        scheduler.async(() -> deliverSet(who, setPin.set(who.uuid(), pin)));
        return Command.SINGLE_SUCCESS;
    }

    private int change(CommandContext<CommandSourceStack> ctx) {
        Player player = enrollingPlayer(ctx);
        if (player == null) {
            return 0;
        }
        String oldPin = ctx.getArgument("old", String.class);
        String newPin = ctx.getArgument("new", String.class);
        PlayerRef who = ref(player);
        scheduler.async(() -> deliverChange(who, changePin.change(who.uuid(), oldPin, newPin, clock.instant())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /pin remove <pin>}: drop the PIN factor after proving it. Unlike set and change this is <b>not</b> gated
     * on the enrolment switches. An operator who turns PIN enrolment off must not strand the players who already
     * hold one with no way to take it back off.
     */
    private int remove(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx.getSource().getSender());
        if (player == null) {
            return 0;
        }
        String pin = ctx.getArgument("pin", String.class);
        PlayerRef who = ref(player);
        scheduler.async(() -> notify(who, removeMessage(removePin.remove(who.uuid(), pin, clock.instant()))));
        return Command.SINGLE_SUCCESS;
    }

    /** The caller as a player, or {@code null} after replying, when they may not enrol a PIN right now. */
    private @Nullable Player enrollingPlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(sender);
        if (player == null) {
            return null;
        }
        if (!settings.enabled() || !settings.pin()) {
            reply(sender, SecurityMessageKey.SECURITY_PIN_FEATURE_DISABLED);
            return null;
        }
        return player;
    }

    private void deliverSet(PlayerRef who, PinSetResult result) {
        switch (result) {
            case SET -> notify(who, SecurityMessageKey.SECURITY_PIN_SET);
            case ALREADY_SET -> notify(who, SecurityMessageKey.SECURITY_PIN_ALREADY_SET);
            case TOO_SHORT -> notifyTooShort(who);
            case TOO_LONG -> notifyTooLong(who);
            case NOT_NUMERIC -> notify(who, SecurityMessageKey.SECURITY_PIN_NOT_NUMERIC);
            case BLOCKED -> notify(who, SecurityMessageKey.SECURITY_PIN_BLOCKED);
        }
    }

    private void deliverChange(PlayerRef who, PinChangeResult result) {
        switch (result) {
            case CHANGED -> notify(who, SecurityMessageKey.SECURITY_PIN_CHANGED);
            case NOT_SET -> notify(who, SecurityMessageKey.SECURITY_PIN_NOT_SET);
            case INVALID_PIN -> notify(who, SecurityMessageKey.SECURITY_PIN_INVALID);
            case LOCKED_OUT -> notify(who, SecurityMessageKey.SECURITY_PIN_LOCKED_OUT);
            case TOO_SHORT -> notifyTooShort(who);
            case TOO_LONG -> notifyTooLong(who);
            case NOT_NUMERIC -> notify(who, SecurityMessageKey.SECURITY_PIN_NOT_NUMERIC);
            case BLOCKED -> notify(who, SecurityMessageKey.SECURITY_PIN_BLOCKED);
        }
    }

    private void notifyTooShort(PlayerRef who) {
        notify(
                who,
                SecurityMessageKey.SECURITY_PIN_TOO_SHORT,
                Map.of("min", String.valueOf(settings.pinPolicy().minLength())));
    }

    private void notifyTooLong(PlayerRef who) {
        notify(
                who,
                SecurityMessageKey.SECURITY_PIN_TOO_LONG,
                Map.of("max", String.valueOf(settings.pinPolicy().maxLength())));
    }

    private static MessageKey removeMessage(PinRemoveResult result) {
        return switch (result) {
            case REMOVED -> SecurityMessageKey.SECURITY_PIN_REMOVED;
            case NOT_SET -> SecurityMessageKey.SECURITY_PIN_NOT_SET;
            case INVALID_PIN -> SecurityMessageKey.SECURITY_PIN_INVALID;
            case LOCKED_OUT -> SecurityMessageKey.SECURITY_PIN_LOCKED_OUT;
        };
    }
}

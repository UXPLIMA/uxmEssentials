package com.uxplima.uxmessentials.security.adapter;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.KeypadActions;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.VerifyResult;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The brain of op-command re-authentication: when the command listener blocks a protected command, this drives the
 * player through the same PIN keypad / TOTP prompt the join freeze uses, and on a successful proof stamps the re-auth
 * window and retries the original command. An un-enrolled player has nothing to prove against, so their command is
 * simply allowed through rather than soft-locked. It shares the keypad view with the join flow, a {@link KeypadRouter}
 * sends each submission to whichever flow the player is currently in.
 *
 * <p>Every registration read and factor verification runs off the tick thread through the injected {@link Scheduler},
 * and every player touch (the prompt, the keypad, the command retry) hops back onto the player's region thread, so the
 * flow is Folia-safe and never blocks a tick on I/O. A submitted PIN or code is held only for the length of the verify
 * and is never logged.
 */
@NullMarked
public final class ReauthController implements KeypadActions {

    private final TwoFactorRepository repository;
    private final VerifyTwoFactor verify;
    private final AttemptLimiter limiter;
    private final ReauthState reauthState;
    private final ReauthSessions sessions;
    private final PinKeypadView keypad;
    private final TotpPrompt totpPrompt;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final Clock clock;

    public ReauthController(
            TwoFactorRepository repository,
            VerifyTwoFactor verify,
            AttemptLimiter limiter,
            ReauthState reauthState,
            ReauthSessions sessions,
            PinKeypadView keypad,
            TotpPrompt totpPrompt,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.verify = Objects.requireNonNull(verify, "verify");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.reauthState = Objects.requireNonNull(reauthState, "reauthState");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.keypad = Objects.requireNonNull(keypad, "keypad");
        this.totpPrompt = Objects.requireNonNull(totpPrompt, "totpPrompt");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Demand a re-auth for {@code commandLine}: decide off-thread whether to prompt or (if un-enrolled) allow it. */
    public void require(Player player, String commandLine) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(commandLine, "commandLine");
        PlayerRef ref = BukkitRefs.toRef(player);
        scheduler.async(() -> resolveRequirement(ref, commandLine));
    }

    /** Drop a leaving player's pending re-auth and verification stamp so a disconnect leaves no lingering state. */
    public void onQuit(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        sessions.clear(viewer.uuid());
        reauthState.clear(viewer.uuid());
    }

    @Override
    public void submit(Player player, PlayerRef viewer, String candidate) {
        verifySubmission(player, viewer, candidate, false);
    }

    @Override
    public void requestTotp(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        if (!sessions.isPending(viewer.uuid())) {
            return;
        }
        // The anvil prompt replaces the keypad window; mark the resulting close as a handoff so it is not reopened.
        keypad.suppressNextClose(viewer);
        totpPrompt.prompt(
                player,
                viewer,
                code -> verifySubmission(player, viewer, code, true),
                () -> reopenKeypad(player, viewer));
    }

    private void resolveRequirement(PlayerRef ref, String commandLine) {
        if (limiter.isLockedOut(ref.uuid(), clock.instant())) {
            // A locked-out account cannot attempt a protected command: deny it, do not prompt or retry the command.
            scheduler.onEntity(ref, () -> notify(ref, SecurityMessageKey.SECURITY_REAUTH_LOCKED_OUT));
            return;
        }
        TwoFactorRegistration registration = repository.find(ref.uuid()).orElse(null);
        if (registration == null || !registration.hasAnyFactor()) {
            // Nothing to prove against: allow the command through rather than soft-locking an un-enrolled player.
            allow(ref, commandLine);
            return;
        }
        sessions.begin(ref.uuid(), commandLine);
        boolean totpEnabled = registration.totpEnabled();
        scheduler.onEntity(ref, () -> promptReauth(ref, totpEnabled));
    }

    private void promptReauth(PlayerRef ref, boolean totpEnabled) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live == null || !live.isOnline()) {
            sessions.clear(ref.uuid());
            return;
        }
        notify(ref, SecurityMessageKey.SECURITY_REAUTH_REQUIRED);
        keypad.open(live, ref, totpEnabled);
    }

    private void verifySubmission(Player player, PlayerRef viewer, String candidate, boolean reopenOnFailure) {
        if (!sessions.isPending(viewer.uuid())) {
            return;
        }
        scheduler.async(() -> {
            VerifyResult result = verify.verify(viewer.uuid(), candidate, clock.instant());
            scheduler.onEntity(viewer, () -> applyResult(player, viewer, result, reopenOnFailure));
        });
    }

    private void applyResult(Player player, PlayerRef viewer, VerifyResult result, boolean reopenOnFailure) {
        switch (result) {
            case SUCCESS, NOT_ENROLLED -> succeed(viewer);
            case INVALID -> fail(player, viewer, reopenOnFailure);
        }
    }

    private void succeed(PlayerRef viewer) {
        String commandLine = sessions.pendingCommand(viewer.uuid());
        sessions.clear(viewer.uuid());
        limiter.recordSuccess(viewer.uuid());
        reauthState.stamp(viewer.uuid(), clock.instant());
        keypad.closeFor(viewer);
        notify(viewer, SecurityMessageKey.SECURITY_REAUTH_SUCCESS);
        if (commandLine != null) {
            redispatch(viewer, commandLine);
        }
    }

    private void fail(Player player, PlayerRef viewer, boolean reopenOnFailure) {
        AttemptLimiter.Outcome outcome = limiter.recordFailure(viewer.uuid(), clock.instant());
        if (outcome.lockedOut()) {
            // Too many wrong proofs on the shared budget, abandon the prompt and lock the account out of every
            // surface.
            sessions.clear(viewer.uuid());
            keypad.closeFor(viewer);
            notify(viewer, SecurityMessageKey.SECURITY_REAUTH_LOCKED_OUT);
            return;
        }
        notify(viewer, SecurityMessageKey.SECURITY_REAUTH_FAILED);
        if (reopenOnFailure) {
            reopenKeypad(player, viewer);
        }
    }

    /** Allow an un-enrolled player's protected command through: stamp the window and retry the original line. */
    private void allow(PlayerRef ref, String commandLine) {
        reauthState.stamp(ref.uuid(), clock.instant());
        scheduler.onEntity(ref, () -> redispatch(ref, commandLine));
    }

    private void reopenKeypad(Player player, PlayerRef viewer) {
        if (sessions.isPending(viewer.uuid())) {
            keypad.open(player, viewer, hasTotp(viewer));
        }
    }

    private boolean hasTotp(PlayerRef viewer) {
        return repository
                .find(viewer.uuid())
                .map(TwoFactorRegistration::totpEnabled)
                .orElse(false);
    }

    /** Re-run the original command now that the window is open; performCommand does not re-fire the guard listener. */
    private void redispatch(PlayerRef viewer, String commandLine) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        String body = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        if (!body.isBlank()) {
            live.performCommand(body);
        }
    }

    private void notify(PlayerRef viewer, SecurityMessageKey key) {
        sink.deliver(viewer, messages.resolve(viewer, key, Map.of()));
    }
}

package com.uxplima.uxmessentials.security.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.security.TrustStores;
import com.uxplima.uxmessentials.persistence.security.TwoFactorRepositories;
import com.uxplima.uxmessentials.security.adapter.inbound.command.ClientInfoCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.IpAltsCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.PinCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.SecurityCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadWindowListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.ReauthCommandListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.SecurityGuardListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.SecurityJoinListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.VerificationFreezeListener;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.BeginTotpEnrollment;
import com.uxplima.uxmessentials.security.application.ChangePin;
import com.uxplima.uxmessentials.security.application.ConfirmTotpEnrollment;
import com.uxplima.uxmessentials.security.application.DisableTotp;
import com.uxplima.uxmessentials.security.application.FindAlts;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.security.application.PendingTotpEnrollments;
import com.uxplima.uxmessentials.security.application.RemovePin;
import com.uxplima.uxmessentials.security.application.ResetFactors;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.LockoutBan;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.ip.IpHistoryRecorder;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the security context's two-factor store, enrolment use cases, and the {@code /2fa}, {@code /pin} and
 * {@code /security} commands over the injected kernel ports and the shared persistence DSL. The store is the jOOQ
 * {@code TwoFactorRepository} (built through the security persistence factory, so no jOOQ type reaches this layer),
 * which hashes the PIN and encrypts the TOTP secret under an AES key-file kept beside the module's config. The
 * pending, un-confirmed TOTP secrets are transient in-memory state held in {@link PendingTotpEnrollments}, cleared on
 * {@link Wired#stop()} so a disable or reload leaves no residual secret.
 */
@NullMarked
public final class SecurityWiring {

    private SecurityWiring() {}

    /** Build the security use cases, commands, and join-verification listeners from {@code ctx} and persistence. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            LockoutBan lockoutBan,
            ServerConnector proxy,
            IpHistoryStore ipHistory,
            IpTokens ipTokens,
            IpHistoryRecorder recorder) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(lockoutBan, "lockoutBan");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(ipHistory, "ipHistory");
        Objects.requireNonNull(ipTokens, "ipTokens");
        Objects.requireNonNull(recorder, "recorder");
        KernelPorts kernel = ctx.kernel();
        SecurityConfig config = SecurityConfig.from(ctx.config());
        SecurityConfig.TwoFactorSettings twoFactor = config.twoFactor();
        Path keyFile = plugin.getDataFolder().toPath().resolve("modules/security/secret.key");
        TwoFactorRepository repository = TwoFactorRepositories.jooq(persistence, keyFile);
        PendingTotpEnrollments pending = new PendingTotpEnrollments();
        // The durable, account-scoped brute-force limiter is shared by every verification surface: the join freeze,
        // the op re-auth keypad, /2fa disable, /pin change and /pin remove. A failed proof on any of them counts
        // toward, and a lockout blocks, all of them. Built from the join-verification lockout policy and duration.
        AttemptLimiter limiter = new AttemptLimiter(
                config.joinVerification().lockoutPolicy(),
                config.joinVerification().lockout());
        BeginTotpEnrollment begin = new BeginTotpEnrollment(new SecretGenerator(), pending, twoFactor.issuer());
        ConfirmTotpEnrollment confirm = new ConfirmTotpEnrollment(repository, pending, twoFactor.codeWindow());
        DisableTotp disable = new DisableTotp(repository, limiter, twoFactor.codeWindow());
        SetPin setPin = new SetPin(repository, twoFactor.pinPolicy());
        ChangePin changePin = new ChangePin(repository, limiter, twoFactor.pinPolicy());
        RemovePin removePin = new RemovePin(repository, limiter);
        Clock clock = Clock.systemUTC();

        // Phase 4: IP/alt guard + ClientID. The associations come from the kernel IP-history store (tokens only on
        // the read side, so this guard never sees an address) and the kernel recorder that writes them; the guard
        // watches that recorder rather than capturing a join of its own. Alongside it are the session-only
        // client-brand registry, the staff notifier that fans an alt / flagged-client notice to the notify-perm
        // holders and mirrors it to the log, and the client guard. Each guard self-gates on its own config flag;
        // the /ipalts and /clientinfo staff reads are published alongside the enrolment verbs.
        ClientBrandRegistry brands = new ClientBrandRegistry();
        SecurityStaffNotifier staffNotifier = new SecurityStaffNotifier(
                plugin.getServer(), kernel.scheduler(), kernel.messages(), kernel.messageSink(), kernel.log());
        IpGuardController ipGuardController = new IpGuardController(
                ipHistory,
                config.ipGuard(),
                kernel.playerLookup(),
                staffNotifier,
                kernel.scheduler(),
                kernel.messages());
        recorder.observe(ipGuardController);
        ClientGuard clientGuard =
                new ClientGuard(brands, config.clientId(), staffNotifier, kernel.scheduler(), kernel.messages());
        FindAlts findAlts = new FindAlts(ipHistory);

        // Phase 2. Join verification: the DB-backed device-trust store, the transient freeze/lockout registry, the
        // keypad GUI, and the controller that decides on join and drives every submitted PIN/code to an unfreeze,
        // re-prompt, or lockout kick. A submitted code is verified through VerifyTwoFactor (TOTP or PIN), off-thread.
        // Built ahead of the commands so /2fa force can drive an online target straight back into the freeze.
        TrustStore trustStore = TrustStores.jooq(persistence);
        VerificationSessions sessions = new VerificationSessions();
        ReauthState reauthState = new ReauthState();
        ReauthSessions reauthSessions = new ReauthSessions();
        PinEnrolmentSessions enrolmentSessions = new PinEnrolmentSessions();
        // The titles over the keypad and the sounds each step makes, shared by the join freeze and the op re-auth so
        // both flows feel the same. The keypad plays the tap sound itself; the controller plays the outcomes.
        VerificationFeedback feedback =
                new VerificationFeedback(config.feedback(), kernel.scheduler(), kernel.messages(), kernel.log());
        PinKeypadView keypad = new PinKeypadView(menus, kernel.messages(), feedback, kernel.scheduler());
        FreezeGameMode freezeGameMode =
                new FreezeGameMode(plugin, config.joinVerification().spectator());
        // The module's own two teleports (into the holding area and back out) announce themselves here, because the
        // freeze cancels teleports and a teleport event names a cause, never an author.
        FreezeTeleports ownTeleports = new FreezeTeleports();
        // Resolved lazily: the plugin enables before the worlds exist, so parsing here would look up a world that
        // is not there yet and turn a good configured value into an "unknown world" warning. Bootstrap warms it
        // once the worlds are up, which is where the warning for a genuinely bad value belongs.
        FreezeHoldingArea holdingArea = new FreezeHoldingArea(
                () -> FreezeHoldingArea.parse(config.joinVerification().holdingArea(), kernel.log())
                        .orElse(null),
                ownTeleports,
                kernel.log());
        VerifyTwoFactor verify = new VerifyTwoFactor(repository, twoFactor.codeWindow());
        TextInputTotpPrompt totpPrompt = new TextInputTotpPrompt(textInput);
        // The create-a-PIN pad: shown to a player holding uxmessentials.security.pin.required who has no factor, so
        // "you must have a PIN" is enforceable rather than advisory. It writes through the same SetPin the /pin
        // command uses, so a PIN made here obeys the same length and blocked-list rules.
        PinEnrolmentController enrolment = new PinEnrolmentController(
                setPin,
                twoFactor.pinPolicy(),
                enrolmentSessions,
                sessions,
                keypad,
                feedback,
                freezeGameMode,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink());
        VerificationController controller = new VerificationController(
                repository,
                verify,
                trustStore,
                sessions,
                limiter,
                reauthState,
                config.joinVerification(),
                keypad,
                totpPrompt,
                freezeGameMode,
                feedback,
                lockoutBan,
                enrolment,
                kernel.permissions(),
                holdingArea,
                proxy,
                ipTokens,
                kernel.events(),
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                kernel.log(),
                clock);
        // The two /security operator verbs. Force revokes the target's device trust (the durable forced state, so
        // their next join re-verifies even from a trusted device), paired with the immediate online freeze driven
        // through the controller above. Reset is the recovery door: the one path that clears a factor without proving
        // it, for a player who has lost their authenticator or forgotten their PIN.
        ForceReverification forceReverification = new ForceReverification(repository, trustStore);
        ResetFactors resetFactors = new ResetFactors(repository, trustStore);

        List<CommandRegistration> commands = List.of(
                new TwoFactorCommand(
                        begin,
                        confirm,
                        disable,
                        repository,
                        twoFactor,
                        clock,
                        kernel.scheduler(),
                        kernel.messages(),
                        kernel.messageSink()),
                new PinCommand(
                        setPin,
                        changePin,
                        removePin,
                        repository,
                        twoFactor,
                        (player, viewer) -> controller.forceReverify(viewer),
                        clock,
                        kernel.scheduler(),
                        kernel.messages(),
                        kernel.messageSink()),
                new SecurityCommand(
                        repository,
                        forceReverification,
                        resetFactors,
                        controller,
                        kernel.playerLookup(),
                        kernel.log(),
                        kernel.scheduler(),
                        kernel.messages(),
                        kernel.messageSink()),
                new IpAltsCommand(
                        findAlts, kernel.playerLookup(), kernel.scheduler(), kernel.messages(), kernel.messageSink()),
                new ClientInfoCommand(
                        brands, kernel.playerLookup(), kernel.scheduler(), kernel.messages(), kernel.messageSink()));

        // Phase 3. Op-command protection: a re-auth controller sharing the same keypad, a router that sends each
        // keypad submission to whichever flow the player is in, and the command listener that blocks a protected
        // command until the player's re-auth window is fresh. The recent-verify check is in-memory; the verify is
        // off-thread. The op-protection sub-feature is gated by its own enabled flag, so a no-op costs nothing.
        SecurityConfig.OpProtection opProtection = config.opProtection();
        ReauthController reauthController = new ReauthController(
                repository,
                verify,
                limiter,
                reauthState,
                reauthSessions,
                keypad,
                totpPrompt,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                clock);
        KeypadRouter router =
                new KeypadRouter(reauthSessions, enrolmentSessions, controller, reauthController, enrolment);
        // The keypad renders through the menu engine: register its per-button actions (routed to whichever verify flow
        // the player is in), its masked-display / digit-label placeholders and its spec now that the router exists. The
        // close listener is the one behaviour the engine does not model: reopening the window if a still-frozen player
        // escapes it.
        keypad.register(menuBindings, router, plugin.getDataFolder().toPath(), kernel.log());
        // On an offline-mode server the login plugin owns the first half of the login, so the second factor waits for
        // it rather than racing it. The hook is reflective and finds nothing on a server with no login plugin, which
        // leaves the plain join hook in charge exactly as before.
        LoginPluginHandoff handoff = new LoginPluginHandoff(plugin, kernel.log());
        if (config.joinVerification().waitForLoginPlugin() && handoff.hook(controller::onAuthenticated)) {
            controller.deferToLoginPlugin();
        }
        List<Listener> listeners = List.of(
                new SecurityJoinListener(controller),
                new VerificationFreezeListener(
                        sessions,
                        config.joinVerification()::restricts,
                        ownTeleports,
                        player -> menus.menuIdOf(player.getOpenInventory().getTopInventory())
                                .isPresent(),
                        kernel.messages(),
                        kernel.messageSink()),
                new PinKeypadWindowListener(menus, keypad, sessions),
                new ReauthCommandListener(
                        opProtection.enabled(), opProtection.policy(), reauthState, reauthController, clock),
                new SecurityGuardListener(clientGuard, brands));
        return new Wired(
                commands,
                listeners,
                pending,
                sessions,
                limiter,
                keypad,
                reauthSessions,
                enrolmentSessions,
                reauthState,
                brands,
                ownTeleports,
                holdingArea,
                handoff,
                repository,
                forceReverification,
                clock);
    }

    /**
     * Everything the security module contributes once wired: the {@code /2fa}, {@code /pin} and {@code /security}
     * command registrations and the join-verification listeners to publish, plus the transient registries cleared on stop (the pending
     * un-confirmed enrolments, the freeze/lockout sessions) and the keypad view whose open windows close on stop, so
     * a disable or reload leaves no residual secret and no locked player.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/freeze/keypad/op-protection Bukkit listeners to register
     * @param pending the pending un-confirmed TOTP enrolments, cleared on module stop
     * @param sessions the freeze registry, cleared on module stop
     * @param limiter the shared durable brute-force limiter, cleared on module stop
     * @param keypad the keypad view whose open windows close on module stop
     * @param reauthSessions the in-flight op-command re-auth prompts, cleared on module stop
     * @param enrolmentSessions the in-flight create-a-PIN entries, cleared on module stop
     * @param reauthState the per-player last-verified stamps, cleared on module stop
     * @param brands the session-only client-brand registry, cleared on module stop
     * @param ownTeleports the module's outstanding teleport exemptions, dropped on module stop
     * @param holdingArea the remembered pre-freeze origins, dropped on module stop
     * @param handoff the login-plugin hook, unregistered on module stop
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            PendingTotpEnrollments pending,
            VerificationSessions sessions,
            AttemptLimiter limiter,
            PinKeypadView keypad,
            ReauthSessions reauthSessions,
            PinEnrolmentSessions enrolmentSessions,
            ReauthState reauthState,
            ClientBrandRegistry brands,
            FreezeTeleports ownTeleports,
            FreezeHoldingArea holdingArea,
            LoginPluginHandoff handoff,
            TwoFactorRepository repository,
            ForceReverification forceReverification,
            Clock clock) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(pending, "pending");
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(limiter, "limiter");
            Objects.requireNonNull(keypad, "keypad");
            Objects.requireNonNull(reauthSessions, "reauthSessions");
            Objects.requireNonNull(enrolmentSessions, "enrolmentSessions");
            Objects.requireNonNull(reauthState, "reauthState");
            Objects.requireNonNull(brands, "brands");
            Objects.requireNonNull(ownTeleports, "ownTeleports");
            Objects.requireNonNull(holdingArea, "holdingArea");
            Objects.requireNonNull(handoff, "handoff");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(forceReverification, "forceReverification");
            Objects.requireNonNull(clock, "clock");
        }

        /** Drop every pending secret, freeze, lockout and re-auth window, close every keypad, and forget every brand. */
        public void stop() {
            pending.clearAll();
            keypad.closeAll();
            sessions.clearAll();
            limiter.clearAll();
            reauthSessions.clearAll();
            enrolmentSessions.clearAll();
            reauthState.clearAll();
            brands.clearAll();
            ownTeleports.clearAll();
            holdingArea.clearAll();
            // The hook lives on another plugin's handler list, so a disable that left it there would keep calling into
            // a module that is no longer running.
            handoff.unhook();
        }
    }
}

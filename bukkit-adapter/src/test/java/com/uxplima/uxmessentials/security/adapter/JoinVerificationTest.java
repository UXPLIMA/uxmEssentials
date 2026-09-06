package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadWindowListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.VerificationFreezeListener;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.LockoutBan;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.FreezeRestriction;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.security.domain.RevokedAccess;
import com.uxplima.uxmessentials.security.domain.SafetyNet;
import com.uxplima.uxmessentials.security.domain.SpectatorPolicy;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.security.domain.event.AccountLockedOut;
import com.uxplima.uxmessentials.security.domain.event.VerificationFailed;
import com.uxplima.uxmessentials.security.domain.event.VerificationPassed;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuOpenEvent;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.IpHashing;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.DamageEvents;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the join-verification freeze end to end: an enrolled player on an untrusted device is frozen
 * on join and cannot move until they verify; the correct PIN (typed on the keypad) and a correct TOTP code both
 * unfreeze; a wrong code counts a failure and re-prompts; the configured number of failures locks the player out; a
 * trusted device skips the prompt entirely; and a non-enrolled player is never frozen.
 */
class JoinVerificationTest {

    /** The tokeniser the controller is wired with, keyed fixed so a test can recompute the same token. */
    private static final IpHashing IP_HASHING =
            new IpHashing("test-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String PIN = "1234";
    private static final int MAX_ATTEMPTS = 3;

    // The keypad slots for the digits used in the click-through test, and the submit button. The pad runs 0-4 across
    // slots 11-15 and 5-9 across 20-24, with the controls on the bottom row.
    private static final int SLOT_1 = 12;
    private static final int SLOT_2 = 13;
    private static final int SLOT_3 = 14;
    private static final int SLOT_4 = 15;
    private static final int SLOT_SUBMIT = 33;

    private ServerMock server;
    private Plugin plugin;
    private FakeRepository repository;
    private FakeTrustStore trustStore;
    private VerificationSessions sessions;
    private AttemptLimiter limiter;
    private ReauthState reauthState;
    private RecordingSink sink;
    private RecordingMessages messages;
    private VerificationFeedback feedback;
    private PinEnrolmentSessions enrolmentSessions;
    private PinEnrolmentController enrolment;
    private HeldPermissions permissions;
    private PinKeypadView keypad;
    private VerificationController controller;

    /** Everything the controller announced, so a test can assert on the facts as well as on the messages. */
    private final List<DomainEvent> events = new ArrayList<>();

    private VerificationFreezeListener freezeListener;
    private MenuListener menuListener;
    private FreezeTeleports ownTeleports;
    private RecordingConnector proxy;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        repository = new FakeRepository();
        trustStore = new FakeTrustStore();
        sessions = new VerificationSessions();
        limiter = new AttemptLimiter(new LockoutPolicy(MAX_ATTEMPTS), Duration.ofMinutes(5));
        reauthState = new ReauthState();
        sink = new RecordingSink();
        Scheduler scheduler = new InlineScheduler();
        messages = new RecordingMessages();
        // The keypad renders through the real menu engine here, so a keypad click routes through the engine to the
        // registered security:pin-* actions exactly as it does in production: the click/drag cancel that locks the
        // window is the engine's, and the digit/submit buttons are its actions.
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        menuListener = new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(menuListener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        feedback = new VerificationFeedback(
                new SecurityConfig.Feedback(true, "", "", "", ""), scheduler, messages, new NoopLogger());
        keypad = new PinKeypadView(menus, messages, feedback, scheduler);
        enrolmentSessions = new PinEnrolmentSessions();
        permissions = new HeldPermissions();
        enrolment = new PinEnrolmentController(
                new SetPin(repository, new PinPolicy(4, 8)),
                new PinPolicy(4, 8),
                enrolmentSessions,
                sessions,
                keypad,
                feedback,
                new FreezeGameMode(plugin, SpectatorPolicy.ADVENTURE),
                scheduler,
                messages,
                sink);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ownTeleports = new FreezeTeleports();
        proxy = new RecordingConnector();
        controller = new VerificationController(
                repository,
                new VerifyTwoFactor(repository, 1),
                trustStore,
                sessions,
                limiter,
                reauthState,
                config(),
                keypad,
                new AutoSubmitTotpPrompt(),
                new FreezeGameMode(plugin, SpectatorPolicy.ADVENTURE),
                feedback,
                LockoutBan.NONE,
                enrolment,
                permissions,
                new FreezeHoldingArea(() -> null, ownTeleports, new NoopLogger()),
                proxy,
                IP_HASHING,
                events::add,
                scheduler,
                messages,
                sink,
                new NoopLogger(),
                clock);
        keypad.register(bindings, controller, specDir(), new NoopLogger());
        freezeListener = new VerificationFreezeListener(
                sessions,
                config()::restricts,
                ownTeleports,
                player -> menus.menuIdOf(player.getOpenInventory().getTopInventory())
                        .isPresent(),
                messages,
                sink);
        server.getPluginManager().registerEvents(freezeListener, plugin);
        server.getPluginManager().registerEvents(new PinKeypadWindowListener(menus, keypad, sessions), plugin);
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped keypad spec from disk. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // MockBukkit's event-simulation helpers are marked for removal but remain the supported way to fire a move/click.
    @SuppressWarnings("removal")
    @Test
    void anEnrolledPlayerIsFrozenOnJoinAndCannotMove() {
        PlayerMock player = joinedPlayerWithPin();

        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        Location destination = player.getLocation().add(5, 0, 0);
        PlayerMoveEvent move = player.simulatePlayerMove(destination);
        assertThat(move.isCancelled()).isTrue();
    }

    @Test
    void aNonEnrolledPlayerIsNeverFrozen() {
        PlayerMock player = addPlayer();

        controller.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    @Test
    void aTrustedDeviceSkipsThePrompt() {
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        trustStore.trust(player.getUniqueId(), IP_HASHING.tokenFor("10.0.0.5"), NOW.plusSeconds(3600));

        controller.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    // MockBukkit's event-simulation helpers are marked for removal but remain the supported way to fire a move/click.
    @SuppressWarnings("removal")
    @Test
    void theCorrectPinTypedOnTheKeypadUnfreezesThePlayer() {
        PlayerMock player = joinedPlayerWithPin();
        InventoryView view = player.getOpenInventory();

        player.simulateInventoryClick(view, SLOT_1);
        player.simulateInventoryClick(view, SLOT_2);
        player.simulateInventoryClick(view, SLOT_3);
        player.simulateInventoryClick(view, SLOT_4);
        player.simulateInventoryClick(view, SLOT_SUBMIT);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sink.delivered).contains("security.verify.success");
        // The verified device is remembered for the next join.
        assertThat(trustStore.isTrusted(player.getUniqueId(), IP_HASHING.tokenFor("10.0.0.5"), NOW))
                .isTrue();
        assertThat(events).containsExactly(new VerificationPassed(ref(player)));
    }

    @Test
    void aDragAcrossTheKeypadIsCancelled() {
        PlayerMock player = joinedPlayerWithPin();
        InventoryView view = player.getOpenInventory();
        InventoryDragEvent drag = new InventoryDragEvent(
                view, null, new ItemStack(Material.STONE), false, Map.of(SLOT_1, new ItemStack(Material.STONE)));

        menuListener.onDrag(drag);

        assertThat(drag.isCancelled()).isTrue(); // the engine locks the frozen keypad: no item may be dragged in or out
    }

    // The frozen re-open invariant: a still-frozen player who escapes the keypad has it reopened, so verification can
    // never be slipped past by closing the window. The open is counted through the engine's MenuOpenEvent so the assert
    // does not hinge on MockBukkit's close-then-open inventory finalisation.
    @SuppressWarnings("removal")
    @Test
    void escapingTheKeypadWhileStillFrozenReopensIt() {
        OpenCounter opens = new OpenCounter();
        server.getPluginManager().registerEvents(opens, plugin);
        PlayerMock player = joinedPlayerWithPin();
        assertThat(opens.count).isEqualTo(1); // the first keypad open

        player.closeInventory(); // the frozen player tries to escape the keypad

        assertThat(sessions.isPending(player.getUniqueId())).isTrue(); // still frozen
        assertThat(opens.count).isEqualTo(2); // and the keypad was reopened
    }

    @SuppressWarnings("removal")
    @Test
    void escapingTheRequiredPinCreationPadReopensIt() {
        OpenCounter opens = new OpenCounter();
        server.getPluginManager().registerEvents(opens, plugin);
        permissions.grant(VerificationController.PIN_REQUIRED_PERMISSION);
        PlayerMock player = addPlayer();
        controller.onJoin(player);
        assertThat(opens.count).isEqualTo(1);

        player.closeInventory();

        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        assertThat(enrolmentSessions.isPending(player.getUniqueId())).isTrue();
        assertThat(opens.count).isEqualTo(2);
    }

    // The counterpart: once a deliberate close is flagged (the TOTP handoff, a verify success, a lockout, a stop), the
    // escaped-window reopen must not fight it, so a flagged close does not reopen.
    @SuppressWarnings("removal")
    @Test
    void aDeliberatelyFlaggedCloseIsNotReopened() {
        OpenCounter opens = new OpenCounter();
        server.getPluginManager().registerEvents(opens, plugin);
        PlayerMock player = joinedPlayerWithPin();
        assertThat(opens.count).isEqualTo(1);

        keypad.suppressNextClose(ref(player)); // e.g. the handoff to the TOTP prompt
        player.closeInventory();

        assertThat(opens.count).isEqualTo(1); // no reopen: the flagged close was left alone
    }

    @Test
    void aCorrectTotpCodeUnfreezesThePlayer() {
        PlayerMock player = addPlayer();
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.enableTotp(player.getUniqueId(), secret);
        controller.onJoin(player);
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        controller.submit(player, ref(player), TotpCode.generate(secret, NOW));

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sink.delivered).contains("security.verify.success");
    }

    @Test
    void aWrongCodeCountsAFailureAndRePrompts() {
        PlayerMock player = joinedPlayerWithPin();

        controller.submit(player, ref(player), "0000");

        assertThat(sessions.isPending(player.getUniqueId())).isTrue(); // still frozen
        assertThat(sink.delivered).contains("security.verify.failed");
        assertThat(events).containsExactly(new VerificationFailed(ref(player), MAX_ATTEMPTS - 1));
    }

    @Test
    void theConfiguredNumberOfFailuresLocksThePlayerOut() {
        PlayerMock player = joinedPlayerWithPin();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            controller.submit(player, ref(player), "0000");
        }

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isTrue();
        // The attempt that spends the last try announces the lockout rather than another failure, and reports it
        // as unbanned, because this fixture has no ban surface to write one to.
        assertThat(events).last().isEqualTo(new AccountLockedOut(ref(player), config().lockout(), false));
    }

    @Test
    void aLockedOutPlayerIsNotFrozenAgainButKeptOut() {
        PlayerMock player = joinedPlayerWithPin();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            controller.submit(player, ref(player), "0000");
        }

        controller.onJoin(player);

        // The rejoin is bounced by the lockout, not turned into a fresh freeze.
        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isTrue();
    }

    // I-1: the failure budget must survive a disconnect/rejoin. Guessing maxAttempts-1, relogging, then one more
    // guess must lock the account out, not hand the attacker a fresh set of attempts.
    @SuppressWarnings("removal")
    @Test
    void theLockoutSurvivesAReconnectAndCannotBeResetByRejoining() {
        PlayerMock player = joinedPlayerWithPin();

        // Two wrong guesses (maxAttempts - 1), then disconnect before the lockout-triggering attempt.
        for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
            controller.submit(player, ref(player), "0000");
        }
        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isFalse();
        controller.onQuit(ref(player));
        player.closeInventory(); // the disconnect drops the open keypad; the quit already cleared the freeze

        // Reconnect: a fresh join re-freezes the player but must NOT reset the accumulated failures.
        controller.onJoin(player);
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        controller.submit(player, ref(player), "0000"); // the maxAttempts-th failure across the two sessions

        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isTrue();
    }

    // I-3: the freeze is established synchronously on join. Before the async enrolment lookup resolves an enrolled
    // player is already pending (a command is cancelled); a non-enrolled player is cleared once the lookup runs.
    @SuppressWarnings("removal")
    @Test
    void anEnrolledPlayerIsFrozenSynchronouslyBeforeTheAsyncLookupResolves() {
        DeferringScheduler deferred = new DeferringScheduler();
        VerificationController optimistic = optimisticController(deferred);
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        optimistic.onJoin(player); // async decision is queued, not yet run

        // In the synchronous window the player is already frozen: a command they fire is cancelled.
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        PlayerCommandPreprocessEvent command = new PlayerCommandPreprocessEvent(player, "/spawn");
        server.getPluginManager().callEvent(command);
        assertThat(command.isCancelled()).isTrue();

        // Once the async lookup resolves, the enrolled player stays frozen.
        deferred.runQueued();
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
    }

    @Test
    void aNonEnrolledPlayerIsFrozenOptimisticallyThenClearedWhenTheLookupResolves() {
        DeferringScheduler deferred = new DeferringScheduler();
        VerificationController optimistic = optimisticController(deferred);
        PlayerMock player = addPlayer(); // no factor enrolled

        optimistic.onJoin(player);

        // The optimistic freeze applies to everyone in the synchronous window, "frozen until proven safe".
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        deferred.runQueued(); // the lookup finds no factor and lifts the freeze

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    // A-1/A-2: a player looking at a keypad cannot fight back or run, so the freeze protects them. Damage of any
    // kind is cancelled, and mobs are stopped from choosing them at all.
    @Test
    void aFrozenPlayerTakesNoDamage() {
        PlayerMock player = joinedPlayerWithPin();
        double before = player.getHealth();

        EntityDamageEvent damage = DamageEvents.of(
                player, DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 5.0);
        server.getPluginManager().callEvent(damage);

        assertThat(damage.isCancelled()).isTrue();
        assertThat(player.getHealth()).isEqualTo(before);
    }

    @Test
    void aFrozenPlayerIsNotChosenAsAMobTarget() {
        PlayerMock player = joinedPlayerWithPin();
        Entity mob = mock(Entity.class);

        EntityTargetEvent target = new EntityTargetEvent(mob, player, TargetReason.CLOSEST_PLAYER);
        server.getPluginManager().callEvent(target);

        assertThat(target.isCancelled()).isTrue();
    }

    // A-3: the freeze is worth nothing if somebody else can move the frozen player out of it.
    @Test
    void aFrozenPlayerCannotBeTeleported() {
        PlayerMock player = joinedPlayerWithPin();
        Location elsewhere = new Location(player.getWorld(), 100, 70, 100);

        PlayerTeleportEvent teleport = new PlayerTeleportEvent(player, player.getLocation(), elsewhere);
        server.getPluginManager().callEvent(teleport);

        assertThat(teleport.isCancelled()).isTrue();
    }

    // C-13: the deny-list is the operator's, not ours. A restriction that is switched off is simply not enforced,
    // and the ones still on are unaffected.
    @Test
    void aRestrictionTurnedOffInTheConfigIsNotEnforced() {
        Set<FreezeRestriction> withoutDamage = EnumSet.complementOf(EnumSet.of(FreezeRestriction.DAMAGE_TAKEN));
        HandlerList.unregisterAll(freezeListener);
        server.getPluginManager()
                .registerEvents(
                        new VerificationFreezeListener(
                                sessions,
                                withoutDamage::contains,
                                ownTeleports,
                                player -> false,
                                new KeyMessages(),
                                sink),
                        plugin);
        PlayerMock player = joinedPlayerWithPin();

        EntityDamageEvent damage = DamageEvents.of(
                player, DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 5.0);
        server.getPluginManager().callEvent(damage);
        assertThat(damage.isCancelled()).isFalse();

        // The restrictions still switched on keep working.
        PlayerTeleportEvent teleport =
                new PlayerTeleportEvent(player, player.getLocation(), new Location(player.getWorld(), 100, 70, 100));
        server.getPluginManager().callEvent(teleport);
        assertThat(teleport.isCancelled()).isTrue();
    }

    // A-5: a spectator cannot click any window the server opens, so a spectator shown the keypad would be stuck with
    // no way to prove anything. They are held in a mode that can click and handed their own mode back on success.
    @Test
    void aFrozenSpectatorIsHeldInAModeThatCanClickAndGetsTheirOwnModeBack() {
        PlayerMock player = addPlayer();
        player.setGameMode(GameMode.SPECTATOR);
        repository.setPin(player.getUniqueId(), PIN);

        controller.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        assertThat(player.getGameMode()).isEqualTo(GameMode.ADVENTURE);

        controller.submit(player, ref(player), PIN);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(player.getGameMode()).isEqualTo(GameMode.SPECTATOR);
    }

    // A-6: the freeze goes on before the decision runs, so a decision that throws would strand the player with no
    // keypad and nothing to press. The safety net settles it either way rather than leaving them hanging.
    @Test
    void aFailedDecisionKicksRatherThanLeavingThePlayerFrozenForever() {
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        repository.failing = true;

        controller.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(player.isOnline()).isFalse();
    }

    @Test
    void aFailedDecisionCanInsteadLetThePlayerInWhenTheSafetyNetSaysSo() {
        VerificationController lenient = controllerWith(new InlineScheduler(), SafetyNet.ALLOW);
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        repository.failing = true;

        lenient.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(player.isOnline()).isTrue();
    }

    // B-8: without a time limit a player who walks away mid-prompt sits at the keypad forever, holding a slot and
    // leaving their account logged in. With one, the freeze ends by itself.
    @Test
    void aFrozenPlayerWhoNeverVerifiesIsKickedWhenTheEntryTimeLimitExpires() {
        DelayedScheduler delayed = new DelayedScheduler();
        VerificationController timed =
                controllerWith(delayed, LockoutBan.NONE, configWithTimeout(Duration.ofMinutes(1)));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        timed.onJoin(player);
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        delayed.runDelayed();

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(player.isOnline()).isFalse();
    }

    // The timer fires long after the freeze that armed it, so it must be able to tell "still the freeze I was started
    // for" from "verified, left, rejoined, frozen again". A stale timer must not kick anyone.
    @Test
    void anExpiredTimeLimitFromAnEarlierFreezeDoesNotTouchALaterOne() {
        DelayedScheduler delayed = new DelayedScheduler();
        VerificationController timed =
                controllerWith(delayed, LockoutBan.NONE, configWithTimeout(Duration.ofMinutes(1)));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        timed.onJoin(player); // freeze one, arms a timer
        timed.submit(player, ref(player), PIN); // verified, freeze one is over
        assertThat(sessions.isPending(player.getUniqueId())).isFalse();

        timed.onJoin(player); // freeze two, a fresh session with its own token

        delayed.runOldestDelayed(); // only freeze one's timer fires; freeze two's is still pending

        // Freeze two is untouched: the stale timer saw a token that was not its own and did nothing.
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        assertThat(player.isOnline()).isTrue();
    }

    // B-9: the lockout is issued as an ordinary ban through the plugin's own ban system rather than this module
    // keeping a private list. When that lands, the ban surface has already removed the player and we do not kick.
    @Test
    void aLockoutIsIssuedAsARealBanRatherThanAPrivateCooldown() {
        RecordingLockoutBan bans = new RecordingLockoutBan(true);
        VerificationController banning = controllerWith(new InlineScheduler(), bans, configWithLockoutBan("guessing"));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        banning.onJoin(player);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            banning.submit(player, ref(player), "0000");
        }

        assertThat(bans.target).isEqualTo(player.getUniqueId());
        assertThat(bans.duration).isEqualTo(Duration.ofMinutes(5));
        assertThat(bans.reason).isEqualTo("guessing");
        // The ban surface removed them; we did not kick on top of it.
        assertThat(player.isOnline()).isTrue();
    }

    @Test
    void aRefusedBanFallsBackToTheKickSoALockedOutPlayerIsNeverLeftPlaying() {
        RecordingLockoutBan refusing = new RecordingLockoutBan(false);
        VerificationController banning =
                controllerWith(new InlineScheduler(), refusing, configWithLockoutBan("guessing"));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        banning.onJoin(player);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            banning.submit(player, ref(player), "0000");
        }

        assertThat(player.isOnline()).isFalse();
    }

    // B-7: "you must have a PIN" means nothing if joining without one is the way past it. A player the server
    // requires a PIN from is held at the create pad instead of waved through, and sets one there.
    @Test
    void aPlayerTheServerRequiresAPinFromIsHeldUntilTheySetOne() {
        permissions.grant(VerificationController.PIN_REQUIRED_PERMISSION);
        PlayerMock player = addPlayer(); // no factor at all

        controller.onJoin(player);

        // Not waved through: still frozen, and at the create pad rather than the verify pad.
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        assertThat(enrolmentSessions.isPending(player.getUniqueId())).isTrue();

        // The same PIN twice creates it, and the freeze that was holding them ends with it.
        enrolment.submit(player, ref(player), "8391");
        enrolment.submit(player, ref(player), "8391");

        assertThat(repository.verifyPin(player.getUniqueId(), "8391")).isTrue();
        assertThat(enrolmentSessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    // Asking for it twice is the only thing between a mistyped PIN and an account nobody can get into tomorrow.
    @Test
    void aMistypedConfirmationStoresNothingAndStartsOver() {
        permissions.grant(VerificationController.PIN_REQUIRED_PERMISSION);
        PlayerMock player = addPlayer();
        controller.onJoin(player);

        enrolment.submit(player, ref(player), "8391");
        enrolment.submit(player, ref(player), "8392"); // a slip on the confirmation

        assertThat(repository.find(player.getUniqueId())).isEmpty();
        assertThat(enrolmentSessions.isPending(player.getUniqueId())).isTrue();
        assertThat(enrolmentSessions.firstEntry(player.getUniqueId())).isEmpty(); // back to the first step
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
    }

    // B-10: a blocked PIN is well-formed, which is exactly why the length rules cannot catch it. It is refused at the
    // first entry, so the player hears it once rather than after typing it twice.
    @Test
    void aBlockedPinIsRefusedAtTheFirstEntry() {
        permissions.grant(VerificationController.PIN_REQUIRED_PERMISSION);
        PlayerMock player = addPlayer();
        controller.onJoin(player);

        blockedEnrolment().submit(player, ref(player), "1234");

        assertThat(enrolmentSessions.firstEntry(player.getUniqueId())).isEmpty();
        assertThat(repository.find(player.getUniqueId())).isEmpty();
    }

    @Test
    void pinPolicyRefusalsRenderTheirConfiguredBounds() {
        permissions.grant(VerificationController.PIN_REQUIRED_PERMISSION);
        PlayerMock player = addPlayer();
        controller.onJoin(player);

        enrolment.submit(player, ref(player), "123");
        enrolment.submit(player, ref(player), "123456789");

        assertThat(messages.placeholdersFor(SecurityMessageKey.SECURITY_PIN_TOO_SHORT))
                .containsEntry("min", "4");
        assertThat(messages.placeholdersFor(SecurityMessageKey.SECURITY_PIN_TOO_LONG))
                .containsEntry("max", "8");
    }

    // C-18: a network that verifies on a lobby and plays elsewhere gets what it is for. The transfer happens only
    // after the proof, so nothing unverified ever lands on the server worth reaching.
    @Test
    void aVerifiedPlayerIsHandedToTheConfiguredBackend() {
        VerificationController transferring =
                controllerWith(new InlineScheduler(), LockoutBan.NONE, configTransferringTo("survival"));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        transferring.onJoin(player);

        transferring.submit(player, ref(player), PIN);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(proxy.server).isEqualTo("survival");
    }

    // The default: no backend named, so nobody is moved anywhere. A single-server install must not acquire a proxy
    // hop it never asked for.
    @Test
    void withNoBackendNamedAVerifiedPlayerStaysWhereTheyAre() {
        PlayerMock player = joinedPlayerWithPin();

        controller.submit(player, ref(player), PIN);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(proxy.server).isNull();
    }

    // A wrong PIN must not send anybody anywhere: the transfer is the reward for the proof, not for the attempt.
    @Test
    void aFailedProofTransfersNobody() {
        VerificationController transferring =
                controllerWith(new InlineScheduler(), LockoutBan.NONE, configTransferringTo("survival"));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        transferring.onJoin(player);

        transferring.submit(player, ref(player), "9999");

        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        assertThat(proxy.server).isNull();
    }

    // With no proxy in front there is nothing to hand them to. That is a logged no-op, not a failed verification:
    // losing the hop must never cost the player the account they just proved.
    @Test
    void anUnreachableProxyDoesNotCostThePlayerTheirVerification() {
        proxy.available = false;
        VerificationController transferring =
                controllerWith(new InlineScheduler(), LockoutBan.NONE, configTransferringTo("survival"));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        transferring.onJoin(player);

        transferring.submit(player, ref(player), PIN);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sink.delivered).contains("security.verify.success");
        assertThat(proxy.server).isNull();
    }

    // C-20: on an offline-mode server the login plugin owns the first half of the login. Until it says the name
    // belongs to the person typing it, there is no account to ask a second factor of, so the join hook stands down.
    @Test
    void theJoinHookStandsDownWhileALoginPluginOwnsTheLogin() {
        controller.deferToLoginPlugin();
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        controller.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    // ...and the login plugin saying so is what starts it, which is the ordering "two factor" has always meant.
    @Test
    void theLoginPluginSayingAPlayerIsAuthenticatedStartsTheVerification() {
        controller.deferToLoginPlugin();
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        controller.onAuthenticated(player);

        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        assertThat(sink.delivered).contains("security.verify.prompt");
    }

    // A player with no factor is nobody's problem either way, so the deferred path still lets them straight in.
    @Test
    void anUnenrolledPlayerIsNotFrozenOnTheLoginPluginPath() {
        controller.deferToLoginPlugin();
        PlayerMock player = addPlayer();

        controller.onAuthenticated(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    // C-19: the holding area is a preference and the verification is not. A destination that cannot be reached is a
    // logged no-op that leaves them where they are, still frozen, still able to prove who they are.
    @Test
    void aHoldingAreaThatCannotBeReachedDoesNotCostThePlayerTheirVerification() {
        PlayerMock player = addPlayer();
        Location holding = new Location(player.getWorld(), 200, 80, 200);
        VerificationController held = new VerificationController(
                repository,
                new VerifyTwoFactor(repository, 1),
                trustStore,
                sessions,
                limiter,
                reauthState,
                config(),
                keypad,
                new AutoSubmitTotpPrompt(),
                new FreezeGameMode(plugin, SpectatorPolicy.ADVENTURE),
                feedback,
                LockoutBan.NONE,
                enrolment,
                permissions,
                new FreezeHoldingArea(() -> holding, ownTeleports, new NoopLogger()),
                proxy,
                IP_HASHING,
                events::add,
                new InlineScheduler(),
                new KeyMessages(),
                sink,
                new NoopLogger(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        repository.setPin(player.getUniqueId(), PIN);

        held.onJoin(player);
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        held.submit(player, ref(player), PIN);
        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sink.delivered).contains("security.verify.success");
    }

    // The exemption is consumed, never left standing: a refused move must not leave a hole somebody else's teleport
    // could fall into.
    @Test
    void aRefusedOwnTeleportLeavesNoStandingExemption() {
        PlayerMock player = addPlayer();
        Location elsewhere = new Location(player.getWorld(), 300, 80, 300);

        assertThat(ownTeleports.teleport(player, elsewhere)).isFalse();

        assertThat(ownTeleports.consume(player.getUniqueId())).isFalse();
    }

    // C-17: staff clearing a factor out from under an online player is the operator's call, not ours. The default
    // sends them straight back through verification, so a reset ends with a factor rather than without one.
    @Test
    void theDefaultRevokedAccessPolicySendsThePlayerBackThroughVerification() {
        VerificationController revoking =
                controllerWith(new InlineScheduler(), LockoutBan.NONE, configRevoking(RevokedAccess.REVERIFY));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        revoking.onAccessRevoked(ref(player));

        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        assertThat(sink.delivered).contains("security.verify.prompt");
    }

    // On a server where a reset is routine help-desk work, it must not interrupt the session it lands in.
    @Test
    void theNothingRevokedAccessPolicyLeavesThePlayerPlaying() {
        VerificationController revoking =
                controllerWith(new InlineScheduler(), LockoutBan.NONE, configRevoking(RevokedAccess.NOTHING));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        revoking.onAccessRevoked(ref(player));

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(player.isOnline()).isTrue();
    }

    // The strictest reading: nothing of the revoked session carries on, and the next join decides afresh.
    @Test
    void theKickRevokedAccessPolicyDisconnectsThePlayer() {
        VerificationController revoking =
                controllerWith(new InlineScheduler(), LockoutBan.NONE, configRevoking(RevokedAccess.KICK));
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        revoking.onAccessRevoked(ref(player));

        assertThat(player.isOnline()).isFalse();
    }

    // With no factor left and the server requiring a PIN, the reverify lands on the create pad: the player is made
    // to set a new one rather than quietly left with no factor at all.
    @Test
    void aRevokedPlayerWhoMustHoldAPinIsPutStraightOnTheCreatePad() {
        VerificationController revoking =
                controllerWith(new InlineScheduler(), LockoutBan.NONE, configRevoking(RevokedAccess.REVERIFY));
        PlayerMock player = addPlayer();
        permissions.grant(VerificationController.PIN_REQUIRED_PERMISSION);

        revoking.onAccessRevoked(ref(player));

        assertThat(enrolmentSessions.isPending(player.getUniqueId())).isTrue();
        assertThat(sink.delivered).contains("security.pin.create-prompt");
    }

    // A holding area is read from one config line, so the parse has to say no clearly rather than half-succeed.
    @Test
    void aBlankOrMalformedHoldingAreaIsRefused() {
        assertThat(FreezeHoldingArea.parse("", new NoopLogger())).isEmpty();
        assertThat(FreezeHoldingArea.parse("world,1,2", new NoopLogger())).isEmpty();
        assertThat(FreezeHoldingArea.parse("world,1,2,x", new NoopLogger())).isEmpty();
        assertThat(FreezeHoldingArea.parse("nosuchworld,1,2,3", new NoopLogger()))
                .isEmpty();
    }

    @Test
    void aWellFormedHoldingAreaIsParsedWithItsOptionalFacing() {
        assertThat(FreezeHoldingArea.parse("world, 1.5, 64, -2.5", new NoopLogger()))
                .hasValueSatisfying(location -> {
                    assertThat(location.getX()).isEqualTo(1.5);
                    assertThat(location.getY()).isEqualTo(64.0);
                    assertThat(location.getZ()).isEqualTo(-2.5);
                });
        assertThat(FreezeHoldingArea.parse("world,0,64,0,90,15", new NoopLogger()))
                .hasValueSatisfying(location -> {
                    assertThat(location.getYaw()).isEqualTo(90f);
                    assertThat(location.getPitch()).isEqualTo(15f);
                });
    }

    /** The same enrolment flow, but over a policy that blocks the obvious PINs. */
    private PinEnrolmentController blockedEnrolment() {
        return new PinEnrolmentController(
                new SetPin(repository, new PinPolicy(4, 8, Set.of("1234"))),
                new PinPolicy(4, 8, Set.of("1234")),
                enrolmentSessions,
                sessions,
                keypad,
                feedback,
                new FreezeGameMode(plugin, SpectatorPolicy.ADVENTURE),
                new InlineScheduler(),
                new KeyMessages(),
                sink);
    }

    private VerificationController optimisticController(Scheduler scheduler) {
        return controllerWith(scheduler, SafetyNet.KICK);
    }

    private VerificationController controllerWith(Scheduler scheduler, SafetyNet safetyNet) {
        return controllerWith(scheduler, LockoutBan.NONE, config(EnumSet.allOf(FreezeRestriction.class), safetyNet));
    }

    private VerificationController controllerWith(
            Scheduler scheduler, LockoutBan lockoutBan, SecurityConfig.JoinVerification join) {
        return new VerificationController(
                repository,
                new VerifyTwoFactor(repository, 1),
                trustStore,
                sessions,
                limiter,
                reauthState,
                join,
                keypad,
                new AutoSubmitTotpPrompt(),
                new FreezeGameMode(plugin, SpectatorPolicy.ADVENTURE),
                feedback,
                lockoutBan,
                enrolment,
                permissions,
                new FreezeHoldingArea(() -> null, ownTeleports, new NoopLogger()),
                proxy,
                IP_HASHING,
                events::add,
                scheduler,
                new KeyMessages(),
                sink,
                new NoopLogger(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PlayerMock joinedPlayerWithPin() {
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        controller.onJoin(player);
        return player;
    }

    private PlayerMock addPlayer() {
        PlayerMock player = server.addPlayer();
        player.setAddress(new InetSocketAddress("10.0.0.5", 30_000));
        return player;
    }

    private static PlayerRef ref(PlayerMock player) {
        return BukkitRefs.toRef(player);
    }

    private static SecurityConfig.JoinVerification config() {
        return config(EnumSet.allOf(FreezeRestriction.class), SafetyNet.KICK);
    }

    /** Device trust is off here so a re-join in the same test re-freezes rather than being waved through. */
    private static SecurityConfig.JoinVerification configWithTimeout(Duration timeout) {
        return join(EnumSet.allOf(FreezeRestriction.class), SafetyNet.KICK, timeout, false, "", false);
    }

    private static SecurityConfig.JoinVerification configWithLockoutBan(String reason) {
        return join(EnumSet.allOf(FreezeRestriction.class), SafetyNet.KICK, Duration.ZERO, true, reason, true);
    }

    private static SecurityConfig.JoinVerification config(Set<FreezeRestriction> restrictions, SafetyNet safetyNet) {
        return join(restrictions, safetyNet, Duration.ZERO, false, "", true);
    }

    /** The same config, with a proxy backend named as the place a verified player goes next. */
    private static SecurityConfig.JoinVerification configTransferringTo(String backend) {
        return new SecurityConfig.JoinVerification(
                true,
                false,
                Duration.ofHours(24),
                MAX_ATTEMPTS,
                Duration.ofMinutes(5),
                EnumSet.allOf(FreezeRestriction.class),
                SpectatorPolicy.ADVENTURE,
                SafetyNet.KICK,
                Duration.ZERO,
                false,
                "",
                "",
                backend,
                true,
                RevokedAccess.REVERIFY);
    }

    private static SecurityConfig.JoinVerification join(
            Set<FreezeRestriction> restrictions,
            SafetyNet safetyNet,
            Duration entryTimeout,
            boolean lockoutBans,
            String lockoutBanReason,
            boolean trustDevices) {
        return new SecurityConfig.JoinVerification(
                true,
                trustDevices,
                Duration.ofHours(24),
                MAX_ATTEMPTS,
                Duration.ofMinutes(5),
                restrictions,
                SpectatorPolicy.ADVENTURE,
                safetyNet,
                entryTimeout,
                lockoutBans,
                lockoutBanReason,
                "",
                "",
                true,
                RevokedAccess.REVERIFY);
    }

    /** The same config, with the operator's answer to "a factor was just taken away from an online player". */
    private static SecurityConfig.JoinVerification configRevoking(RevokedAccess policy) {
        return new SecurityConfig.JoinVerification(
                true,
                false,
                Duration.ofHours(24),
                MAX_ATTEMPTS,
                Duration.ofMinutes(5),
                EnumSet.allOf(FreezeRestriction.class),
                SpectatorPolicy.ADVENTURE,
                SafetyNet.KICK,
                Duration.ZERO,
                false,
                "",
                "",
                "",
                true,
                policy);
    }

    /** Records the backend a verified player was sent to, standing in for a proxy this test has none of. */
    private static final class RecordingConnector implements ServerConnector {
        private boolean available = true;
        private @Nullable String server;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void connect(Player player, String target) {
            this.server = target;
        }
    }

    /** Records what the lockout asked the ban surface for, and whether that surface said it applied it. */
    private static final class RecordingLockoutBan implements LockoutBan {
        private final boolean applies;
        private @Nullable UUID target;
        private @Nullable Duration duration;
        private @Nullable String reason;

        RecordingLockoutBan(boolean applies) {
            this.applies = applies;
        }

        @Override
        public boolean ban(PlayerRef target, Duration duration, String reason) {
            this.target = target.uuid();
            this.duration = duration;
            this.reason = reason;
            return applies;
        }
    }

    /** Runs everything inline except delayed work, which is held so a test can fire the time limit on demand. */
    private static final class DelayedScheduler implements Scheduler {
        private final List<Runnable> delayed = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delayed.add(task);
        }

        void runDelayed() {
            List<Runnable> snapshot = new ArrayList<>(delayed);
            delayed.clear();
            snapshot.forEach(Runnable::run);
        }

        /** Fire only the timer armed first, leaving any armed since queued, so a stale timer can be tested alone. */
        void runOldestDelayed() {
            delayed.remove(0).run();
        }
    }

    /** A permissions stub whose held set a test can grow, for the nodes the join decision consults. */
    private static final class HeldPermissions implements Permissions {
        private final Set<String> held = new HashSet<>();

        void grant(String node) {
            held.add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return held.contains(node);
        }

        @Override
        public Permissions.QuotaResult resolveQuota(
                PlayerRef who, Permissions.QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            // The join decision only ever asks has(); no quota node is involved in this flow.
            return Permissions.QuotaResult.limited(configDefault);
        }
    }

    /** A no-op TOTP prompt for the tests that do not exercise the anvil/chat handoff. */
    private static final class AutoSubmitTotpPrompt implements TotpPrompt {
        @Override
        public void prompt(
                org.bukkit.entity.Player player, PlayerRef viewer, Consumer<String> onSubmit, Runnable onCancel) {
            // The keypad digit path covers verification; this seam is left inert here.
        }
    }

    /** Counts every keypad open the engine fires, so the reopen invariant is asserted through the engine's own event. */
    private static final class OpenCounter implements Listener {
        private int count;

        // Invoked reflectively by Bukkit's event bus, so it reads as unused to static analysis.
        @SuppressWarnings("UnusedMethod")
        @EventHandler
        public void onOpen(MenuOpenEvent event) {
            if (PinKeypadView.isKeypadSpec(event.getMenuId())) {
                count++;
            }
        }
    }

    /** Swallows the menu-spec loader's diagnostics; the shipped keypad spec loads cleanly from the source tree. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** An in-memory two-factor store mirroring the jOOQ store's contract, keeping the PIN as plaintext for the test. */
    private static final class FakeRepository implements TwoFactorRepository {
        private record Row(
                @Nullable TwoFactorSecret secret, @Nullable String pin, long lastTotpStep) {

            Row(@Nullable TwoFactorSecret secret, @Nullable String pin) {
                this(secret, pin, 0L);
            }
        }

        private final Map<UUID, Row> rows = new HashMap<>();

        /** When set, every lookup throws, standing in for an unreachable database. */
        private boolean failing;

        @Override
        public Optional<TwoFactorRegistration> find(UUID playerId) {
            if (failing) {
                throw new IllegalStateException("the store is unreachable");
            }
            Row row = rows.get(playerId);
            return row == null
                    ? Optional.empty()
                    : Optional.of(new TwoFactorRegistration(
                            playerId, row.secret(), row.pin() != null, NOW, row.lastTotpStep()));
        }

        @Override
        public void enableTotp(UUID playerId, TwoFactorSecret secret) {
            Row existing = rows.get(playerId);
            rows.put(playerId, new Row(secret, existing == null ? null : existing.pin()));
        }

        @Override
        public void setPin(UUID playerId, String plaintextPin) {
            Row existing = rows.get(playerId);
            rows.put(playerId, new Row(existing == null ? null : existing.secret(), plaintextPin));
        }

        @Override
        public boolean verifyPin(UUID playerId, String candidate) {
            Row row = rows.get(playerId);
            return row != null && row.pin() != null && row.pin().equals(candidate);
        }

        @Override
        public void clearTotp(UUID playerId) {
            // The verification tests never remove a factor mid-flight; the removal paths have their own coverage.
        }

        @Override
        public void clearPin(UUID playerId) {
            // The verification tests never remove a factor mid-flight; the removal paths have their own coverage.
        }

        @Override
        public void recordTotpStep(UUID playerId, long step) {
            Row row = rows.get(playerId);
            if (row != null && step > row.lastTotpStep()) {
                rows.put(playerId, new Row(row.secret(), row.pin(), step));
            }
        }

        @Override
        public void delete(UUID playerId) {
            rows.remove(playerId);
        }
    }

    /** An in-memory device-trust store keyed by player-and-hash, with an expiry. */
    private static final class FakeTrustStore implements TrustStore {
        private final Map<String, Instant> trusts = new HashMap<>();

        @Override
        public boolean isTrusted(UUID playerId, String ipHash, Instant now) {
            Instant until = trusts.get(playerId + "|" + ipHash);
            return until != null && now.isBefore(until);
        }

        @Override
        public void trust(UUID playerId, String ipHash, Instant until) {
            trusts.put(playerId + "|" + ipHash, until);
        }

        @Override
        public void revoke(UUID playerId) {
            trusts.keySet().removeIf(key -> key.startsWith(playerId + "|"));
        }
    }

    /** Resolves every key to its dotted catalog id so the tests can assert which message was delivered. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Resolves to key ids while retaining the latest placeholder map supplied for each message. */
    private static final class RecordingMessages implements Messages {
        private final Map<String, Map<String, String>> placeholders = new HashMap<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> values) {
            placeholders.put(key.key(), Map.copyOf(values));
            return key.key();
        }

        Map<String, String> placeholdersFor(MessageKey key) {
            return placeholders.getOrDefault(key.key(), Map.of());
        }
    }

    /** Records every delivered message so a test can assert the player saw the expected line. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    /** Runs every scheduler hop inline so the async verify/DB work resolves synchronously in the test. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Defers {@code async} work to a manual drain so the synchronous join window can be inspected before it runs. */
    private static final class DeferringScheduler implements Scheduler {
        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            queued.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            queued.add(task);
        }

        void runQueued() {
            List<Runnable> snapshot = new ArrayList<>(queued);
            queued.clear();
            snapshot.forEach(Runnable::run);
        }
    }
}

package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryRequestRegistry;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemorySpawnDirectory;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PdcTeleportFlags;
import com.uxplima.uxmessentials.teleport.adapter.outbound.VanillaFallbackSpawnDirectory;
import com.uxplima.uxmessentials.teleport.application.AcceptTeleport;
import com.uxplima.uxmessentials.teleport.application.RequestTeleport;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of two teleport command paths against a real (mock) Bukkit server: the {@code /tpa}
 * accept handshake (request stored, then resolved on accept) and {@code /spawn} resolution to a world's
 * spawn through the outbound adapters. The outbound adapters are the real ones, the request registry,
 * the spawn directory over the live world spawn, the PDC flags, and the async executor, so this exercises
 * the same wiring the Brigadier commands drive. The scheduler is a synchronous test double so the
 * teleport effect is observable without ticking the Folia entity scheduler.
 */
class TeleportCommandPathTest {

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private RequestTeleport requestTeleport;
    private AcceptTeleport acceptTeleport;
    private ResolveSpawn resolveSpawn;
    private InMemoryRequestRegistry requests;
    private RecordingExecutor executor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        world = server.addSimpleWorld("world");

        Logger log = new NoopLogger();
        DomainEventPublisher events = new InProcessDomainEventPublisher(log);
        TeleportSettings settings = new TeleportSettings(new ZeroWaitConfig());
        Notifier notifier = new Notifier(new KeyMessages(), new CapturingSink());
        requests = new InMemoryRequestRegistry(true);
        VanillaFallbackSpawnDirectory spawns = new VanillaFallbackSpawnDirectory(new InMemorySpawnDirectory(), server);
        // MockBukkit stubs Entity#teleportAsync (UnimplementedOperationException), so the recording
        // executor stands in for the entity hop; the real AsyncTeleportExecutor is exercised on a live
        // server. Every other adapter here (registry, spawn directory, PDC flags, world lookup) is real.
        executor = new RecordingExecutor();
        TeleportEngine engine = new TeleportEngine(
                new NoCooldowns(),
                new ImmediateWarmups(),
                executor,
                notifier,
                events,
                settings,
                com.uxplima.uxmessentials.teleport.application.port.JailGate.NEVER);
        requestTeleport = new RequestTeleport(
                requests,
                new PdcTeleportFlags(plugin),
                notifier,
                events,
                settings,
                com.uxplima.uxmessentials.teleport.application.port.JailGate.NEVER,
                Clock.systemUTC());
        acceptTeleport = new AcceptTeleport(requests, engine, notifier, events, Clock.systemUTC());
        resolveSpawn = new ResolveSpawn(spawns, worldLookup(), engine, notifier);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tpaRequestThenAcceptResolvesTheHandshake() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        Result<?, TeleportError> requested =
                requestTeleport.request(BukkitRefs.toRef(alice), BukkitRefs.toRef(bob), RequestDirection.TO_TARGET);

        assertThat(requested.isOk()).isTrue();
        assertThat(requests.pendingFor(BukkitRefs.toRef(bob))).hasSize(1);

        Result<Unit, TeleportError> accepted =
                acceptTeleport.accept(BukkitRefs.toRef(bob), BukkitRefs.toPosition(bob.getLocation()));

        assertThat(accepted.isOk()).isTrue();
        assertThat(requests.pendingFor(BukkitRefs.toRef(bob))).isEmpty(); // resolved and removed
        assertThat(executor.hops).isEqualTo(1); // the accepted request drove the mover's hop
    }

    @Test
    void denyResolvesWithoutTeleporting() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        requestTeleport.request(BukkitRefs.toRef(alice), BukkitRefs.toRef(bob), RequestDirection.TO_TARGET);

        Result<Unit, TeleportError> denied = acceptTeleport.deny(BukkitRefs.toRef(bob));

        assertThat(denied.isOk()).isTrue();
        assertThat(requests.pendingFor(BukkitRefs.toRef(bob))).isEmpty();
        assertThat(executor.hops).isZero(); // a denied request never teleports anyone
    }

    @Test
    void acceptWithNoPendingRequestIsRejected() {
        PlayerMock bob = server.addPlayer("Bob");

        Result<Unit, TeleportError> accepted =
                acceptTeleport.accept(BukkitRefs.toRef(bob), BukkitRefs.toPosition(bob.getLocation()));

        assertThat(accepted.isErr()).isTrue();
        assertThat(accepted.errorOrThrow()).isEqualTo(TeleportError.NO_PENDING_REQUEST);
    }

    @Test
    void spawnResolvesToTheWorldSpawnAndDrivesTheHop() {
        world.setSpawnLocation(100, 65, 100);
        PlayerMock player = server.addPlayer("Carol");

        Result<Unit, TeleportError> result =
                resolveSpawn.toDefaultSpawn(BukkitRefs.toRef(player), BukkitRefs.toRef(world));

        assertThat(result.isOk()).isTrue();
        assertThat(executor.hops).isEqualTo(1);
        Position landed = executor.lastDestination().position();
        assertThat(landed.blockX()).isEqualTo(100); // the live world spawn the directory read back
        assertThat(landed.blockZ()).isEqualTo(100);
    }

    @Test
    void spawnIsUnresolvedWhenTheWorldHasNoSpawnHandle() {
        PlayerMock player = server.addPlayer("Dave");
        WorldRef ghostWorld = new WorldRef(UUID.randomUUID(), "ghost");

        Result<Unit, TeleportError> result = resolveSpawn.toDefaultSpawn(BukkitRefs.toRef(player), ghostWorld);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.SPAWN_UNRESOLVED);
    }

    private com.uxplima.uxmessentials.shared.application.port.WorldLookup worldLookup() {
        return new com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitWorldLookup();
    }

    /** Records each hop so a test can assert that (and where) a teleport was issued. */
    private static final class RecordingExecutor implements TeleportExecutor {
        int hops;
        private com.uxplima.uxmessentials.teleport.domain.@org.jspecify.annotations.Nullable Destination last;

        @Override
        public void teleport(
                PlayerRef who, com.uxplima.uxmessentials.teleport.domain.Destination destination, TeleportKind kind) {
            hops++;
            last = destination;
        }

        com.uxplima.uxmessentials.teleport.domain.Destination lastDestination() {
            return java.util.Objects.requireNonNull(last, "no teleport recorded");
        }
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class ImmediateWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        final AtomicInteger delivered = new AtomicInteger();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.incrementAndGet();
        }
    }

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

    /** A config where warmups and cooldowns are zero so the handshake completes deterministically. */
    private record ZeroWaitConfig() implements com.uxplima.uxmessentials.shared.application.port.ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return "default-warmup".equals(path) || "default-cooldown".equals(path) ? 0 : fallback;
        }
    }
}

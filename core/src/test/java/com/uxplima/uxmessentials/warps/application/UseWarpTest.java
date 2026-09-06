package com.uxplima.uxmessentials.warps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /warp <name> <player>} staff send path through {@link UseWarp#useFor}. A self-use delegates to
 * the same method with actor == recipient (proven elsewhere by {@code WarpCommandPathTest}); these cases
 * pin the cross-player behaviour: the <em>recipient</em> is the one teleported and the one the access gate
 * is checked against, and the actor is told the send succeeded.
 */
class UseWarpTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeWarpRepository repository;
    private RecordingTeleporter teleporter;
    private RecordingPermissions permissions;
    private CapturingSink sink;
    private Notifier notifier;
    private PlayerRef actor;
    private PlayerRef recipient;

    @BeforeEach
    void setUp() {
        repository = new FakeWarpRepository();
        teleporter = new RecordingTeleporter();
        permissions = new RecordingPermissions();
        sink = new CapturingSink();
        notifier = new Notifier(new KeyMessages(), sink);
        actor = new PlayerRef(UUID.randomUUID(), "Staff");
        recipient = new PlayerRef(UUID.randomUUID(), "Target");
    }

    @Test
    void useForSendsRecipientToWarpAndNotifiesActor() {
        repository.save(warp("shop"));
        permissions.grant(recipient, WarpName.of("shop").useNode());

        Result<Unit, WarpError> result = useWarp().useFor(actor, recipient, WarpName.of("shop"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.lastWho).isEqualTo(recipient); // the recipient moves, never the actor
        assertThat(permissions.lastChecked).isEqualTo(recipient); // the gate is checked against the recipient
        assertThat(sink.textFor(actor)).contains(WarpsMessageKey.WARP_SENT.key());
    }

    @Test
    void useForRefusedWhenRecipientLacksAccessAndNoHopHappens() {
        repository.save(warp("shop"));
        // recipient holds no per-warp node

        Result<Unit, WarpError> result = useWarp().useFor(actor, recipient, WarpName.of("shop"));

        assertThat(result.errorOrThrow()).isEqualTo(WarpError.NO_PERMISSION);
        assertThat(teleporter.lastWho).isNull();
    }

    @Test
    void selfUseDoesNotSendTheCrossPlayerNotice() {
        repository.save(warp("shop"));
        permissions.grant(actor, WarpName.of("shop").useNode());

        useWarp().use(actor, WarpName.of("shop"));

        assertThat(teleporter.lastWho).isEqualTo(actor);
        assertThat(sink.textFor(actor)).doesNotContain(WarpsMessageKey.WARP_SENT.key());
    }

    @Test
    void visitorCounterWriteIsDeferredToTheAsyncSchedulerNotTheCallingThread() {
        repository.save(warp("shop"));
        permissions.grant(recipient, WarpName.of("shop").useNode());
        CapturingScheduler scheduler = new CapturingScheduler();
        UseWarp useWarp = new UseWarp(
                repository,
                new WarpAccess(permissions, Optional.empty()),
                teleporter,
                notifier,
                pos -> true,
                permissions,
                scheduler);

        useWarp.useFor(actor, recipient, WarpName.of("shop"));

        // The teleport ran on the calling thread; the DB write did not. It was handed to scheduler.async, so the
        // counter is still unwritten until that queued task runs off the region thread.
        assertThat(teleporter.lastWho).isEqualTo(recipient);
        assertThat(repository.find(WarpName.of("shop")).orElseThrow().visitors())
                .isZero();

        scheduler.runAll();

        assertThat(repository.find(WarpName.of("shop")).orElseThrow().visitors())
                .isEqualTo(1L);
    }

    private UseWarp useWarp() {
        WarpAccess access = new WarpAccess(permissions, Optional.empty());
        return new UseWarp(repository, access, teleporter, notifier, pos -> true, permissions, new InlineScheduler());
    }

    /** Runs region/entity/global work inline but captures {@code async} tasks so a test can prove deferral. */
    private static final class CapturingScheduler implements Scheduler {
        private final List<Runnable> asyncTasks = new ArrayList<>();

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
            asyncTasks.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            asyncTasks.add(task);
        }

        void runAll() {
            List<Runnable> pending = List.copyOf(asyncTasks);
            asyncTasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    private Warp warp(String name) {
        return Warp.create(WarpName.of(name), Position.of(WORLD, 0, 64, 0), actor, java.time.Instant.EPOCH);
    }

    private static final class FakeWarpRepository implements WarpRepository {
        private final Map<String, Warp> byName = new LinkedHashMap<>();

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            byName.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            byName.remove(name.value());
        }

        @Override
        public void rate(WarpName name, java.util.UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    private static final class RecordingTeleporter implements WarpTeleporter {
        private @Nullable PlayerRef lastWho;

        @Override
        public void teleportTo(PlayerRef who, Warp warp) {
            lastWho = who;
        }
    }

    /** Grants explicit nodes per player and records the last player a node was checked against. */
    private static final class RecordingPermissions implements Permissions {
        private final Map<UUID, java.util.Set<String>> granted = new LinkedHashMap<>();
        private @Nullable PlayerRef lastChecked;

        void grant(PlayerRef who, String node) {
            granted.computeIfAbsent(who.uuid(), u -> new java.util.HashSet<>()).add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            lastChecked = who;
            return granted.getOrDefault(who.uuid(), java.util.Set.of()).contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(fallback);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every rendered line delivered to each viewer so the actor's feedback can be asserted. */
    private static final class CapturingSink implements MessageSink {
        private final Map<UUID, List<String>> delivered = new LinkedHashMap<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.computeIfAbsent(viewer.uuid(), u -> new ArrayList<>()).add(renderedText);
        }

        List<String> textFor(PlayerRef who) {
            return delivered.getOrDefault(who.uuid(), List.of());
        }
    }
}

package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The player-warps command paths through the real use cases against an in-memory repository and a recording
 * teleporter: the same wiring the Brigadier handlers drive, minus Bukkit. It proves that {@code /setpwarp}
 * persists a warp under its globally-unique name and re-anchors in place, that a name already held by another
 * player is refused, that a set past the resolved per-owner limit is refused, that {@code /pwarp} delegates
 * execution to the teleport context, that the fail-closed access gate lets an owner reach their own private warp
 * but refuses another player until the warp is public, that {@code /pwarp del} archives only the caller's own
 * warp, that {@code /pwarps} lists own warps and only a player's public warps, and that the visibility toggles
 * flip the access axis.
 */
class PlayerWarpCommandPathTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakePlayerWarpRepository repository;
    private RecordingTeleporter teleporter;
    private Notifier notifier;
    private DomainEventPublisher events;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new FakePlayerWarpRepository();
        teleporter = new RecordingTeleporter();
        notifier = new Notifier(new KeyMessages(), new CapturingSink());
        events = new CapturingEvents();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void setPwarpPersistsTheWarpUnderItsGlobalName() {
        Result<Unit, PlayerWarpError> result =
                setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(10, 64, 20));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.existsByName(PlayerWarpName.of("base"))).isTrue();
        PlayerWarp stored = repository.findByName(PlayerWarpName.of("base")).orElseThrow();
        assertThat(stored.owner().uuid()).isEqualTo(alice.uuid());
        assertThat(stored.ownerName()).isEqualTo("Alice");
        assertThat(stored.id()).isPresent();
    }

    @Test
    void aVetoedSetPwarpWritesNothingAndDoesNotSpendTheOwnersQuota() {
        List<DomainEvent> published = new ArrayList<>();
        SetPlayerWarp refusing = new SetPlayerWarp(
                repository,
                new PlayerWarpQuota(new StubPermissions(10), 10),
                notifier,
                published::add,
                proposal -> false,
                Clock.system(ZoneOffset.UTC),
                List.of());

        Result<Unit, PlayerWarpError> result = refusing.set(alice, "Alice", PlayerWarpName.of("base"), at(10, 64, 20));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.VETOED);
        assertThat(repository.existsByName(PlayerWarpName.of("base"))).isFalse();
        assertThat(repository.ownedBy(alice)).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void reanchoringAnOwnedWarpIsNotSomethingAnotherPluginCanRefuse() {
        // The warp already exists and keeps its identity; whoever cared about it was asked when it was created.
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> moved =
                setWarp(10, proposal -> false).set(alice, "Alice", PlayerWarpName.of("base"), at(100, 70, 100));

        assertThat(moved.isOk()).isTrue();
        assertThat(repository
                        .findByName(PlayerWarpName.of("base"))
                        .orElseThrow()
                        .location()
                        .blockX())
                .isEqualTo(100);
    }

    @Test
    void setPwarpOnTheOwnersOwnNameReanchorsInPlace() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> moved =
                setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(100, 70, 100));

        assertThat(moved.isOk()).isTrue();
        assertThat(repository.ownedBy(alice)).hasSize(1);
        assertThat(repository
                        .findByName(PlayerWarpName.of("base"))
                        .orElseThrow()
                        .location()
                        .blockX())
                .isEqualTo(100);
    }

    @Test
    void setPwarpOnANameOwnedByAnotherPlayerIsRefused() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> taken = setWarp(10).set(bob, "Bob", PlayerWarpName.of("base"), at(5, 5, 5));

        assertThat(taken.errorOrThrow()).isEqualTo(PlayerWarpError.NAME_TAKEN);
        assertThat(repository.ownedBy(bob)).isEmpty();
        assertThat(repository
                        .findByName(PlayerWarpName.of("base"))
                        .orElseThrow()
                        .owner()
                        .uuid())
                .isEqualTo(alice.uuid());
    }

    @Test
    void setPwarpOnAReservedVerbNameIsRefused() {
        // "set" is a /pwarp verb literal; a warp named after it would be shadowed and unreachable, so creation is
        // refused before any row is written: the whole point of reserving the command-verb tokens.
        Result<Unit, PlayerWarpError> result = setWarp(10).set(alice, "Alice", PlayerWarpName.of("set"), at(0, 0, 0));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.RESERVED_NAME);
        assertThat(repository.existsByName(PlayerWarpName.of("set"))).isFalse();
        assertThat(repository.ownedBy(alice)).isEmpty();
    }

    @Test
    void setPwarpPastTheResolvedLimitIsRefused() {
        setWarp(2).set(alice, "Alice", PlayerWarpName.of("one"), at(0, 0, 0));
        setWarp(2).set(alice, "Alice", PlayerWarpName.of("two"), at(1, 1, 1));

        Result<Unit, PlayerWarpError> third = setWarp(2).set(alice, "Alice", PlayerWarpName.of("three"), at(2, 2, 2));

        assertThat(third.errorOrThrow()).isEqualTo(PlayerWarpError.LIMIT_REACHED);
        assertThat(repository.count(alice)).isEqualTo(2);
    }

    @Test
    void pwarpDelegatesExecutionToTheTeleportContext() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(7, 64, 7));

        Result<Unit, PlayerWarpError> result = usePwarp().useFor(alice, PlayerWarpName.of("base"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.hops).isEqualTo(1);
        assertThat(teleporter.lastWarp.name()).isEqualTo(PlayerWarpName.of("base"));
    }

    @Test
    void anotherPlayersPrivateWarpIsRefused() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> result = usePwarp().useFor(bob, PlayerWarpName.of("base"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_PUBLIC);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void aPublicWarpIsUsableByAnotherPlayer() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));
        visibility().setPublic(alice, PlayerWarpName.of("base"));

        Result<Unit, PlayerWarpError> result = usePwarp().useFor(bob, PlayerWarpName.of("base"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void aMissingWarpIsRejected() {
        Result<Unit, PlayerWarpError> result = usePwarp().useFor(alice, PlayerWarpName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void delPwarpArchivesTheOwnersWarp() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> result = archive().archive(alice, PlayerWarpName.of("base"));

        assertThat(result.isOk()).isTrue();
        // Archive is recoverable: the row is kept, retired to ARCHIVED, not deleted.
        assertThat(repository
                        .findByName(PlayerWarpName.of("base"))
                        .orElseThrow()
                        .status())
                .isEqualTo(WarpStatus.ARCHIVED);
    }

    @Test
    void delPwarpOnAnotherPlayersWarpIsRefused() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> result = archive().archive(bob, PlayerWarpName.of("base"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(repository.existsByName(PlayerWarpName.of("base"))).isTrue();
    }

    private ArchivePlayerWarp archive() {
        return new ArchivePlayerWarp(
                repository, new WarpAuthorization(new NoMembers()), notifier, events, Clock.system(ZoneOffset.UTC));
    }

    @Test
    void pwarpsOwnListsOwnedWarps() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("farm"), at(1, 1, 1));

        List<PlayerWarp> owned = new ListPlayerWarps(repository, notifier).own(alice);

        assertThat(owned.stream().map(w -> w.name().value())).containsExactly("base", "farm");
    }

    @Test
    void pwarpsForAnotherPlayerListsOnlyTheirPublicWarps() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("secret"), at(1, 1, 1));
        visibility().setPublic(alice, PlayerWarpName.of("base"));

        List<PlayerWarp> shown = new ListPlayerWarps(repository, notifier).publicOf(bob, alice, "Alice");

        assertThat(shown.stream().map(w -> w.name().value())).containsExactly("base");
    }

    @Test
    void crossOwnerEntriesUseTheOtherOwnerEntryKeyCarryingTheOwner() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));
        visibility().setPublic(alice, PlayerWarpName.of("base"));
        RecordingSink sink = new RecordingSink();
        Notifier recording = new Notifier(new KeyMessages(), sink);

        new ListPlayerWarps(repository, recording).publicOf(bob, alice, "Alice");

        // The cross-owner entry must resolve through the other-owner key (whose template runs /pwarp <warp>
        // <owner>), never the own-list entry (which would click-run /pwarp <warp> and hit the viewer's warp).
        assertThat(sink.delivered).contains(PlayerwarpsMessageKey.PWARP_LIST_OTHER_ENTRY.key());
        assertThat(sink.delivered).doesNotContain(PlayerwarpsMessageKey.PWARP_LIST_ENTRY.key());
    }

    @Test
    void visibilityTogglesFlipTheAccessAxis() {
        setWarp(10).set(alice, "Alice", PlayerWarpName.of("base"), at(0, 0, 0));

        visibility().setPublic(alice, PlayerWarpName.of("base"));
        assertThat(repository
                        .findByName(PlayerWarpName.of("base"))
                        .orElseThrow()
                        .access())
                .isEqualTo(WarpAccess.PUBLIC);

        visibility().setPrivate(alice, PlayerWarpName.of("base"));
        assertThat(repository
                        .findByName(PlayerWarpName.of("base"))
                        .orElseThrow()
                        .access())
                .isEqualTo(WarpAccess.PRIVATE);
    }

    private SetPlayerWarp setWarp(int limit) {
        return setWarp(limit, DomainGate.allowAll());
    }

    private SetPlayerWarp setWarp(int limit, DomainGate gate) {
        PlayerWarpQuota quota = new PlayerWarpQuota(new StubPermissions(limit), limit);
        return new SetPlayerWarp(repository, quota, notifier, events, gate, Clock.system(ZoneOffset.UTC), List.of());
    }

    private UsePlayerWarp usePwarp() {
        // The command paths under test exercise only the owner-reaches-own and public-admits-non-member rules, so
        // the ban/member/whitelist/password stores are empty no-ops and the cooldown gate is open; the economy seam
        // stays absent (a priced warp would teleport for free): none of these paths set a price.
        return new UsePlayerWarp(
                repository,
                teleporter,
                notifier,
                pos -> true,
                new StubPermissions(10),
                new NoBans(),
                new NoMembers(),
                new NoWhitelist(),
                new NoPasswords(),
                new OpenCooldowns(),
                Optional.empty(),
                "local",
                Optional.empty(),
                Clock.system(ZoneOffset.UTC));
    }

    private SetPlayerWarpVisibility visibility() {
        return new SetPlayerWarpVisibility(repository, notifier, Clock.system(ZoneOffset.UTC));
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }

    /**
     * A map-backed {@link PlayerWarpRepository} keyed by the global warp name, assigning a surrogate id on
     * insert and keeping warps in insertion order.
     */
    private static final class FakePlayerWarpRepository implements PlayerWarpRepository {
        private final Map<PlayerWarpName, PlayerWarp> byName = new LinkedHashMap<>();
        private long nextId = 0L;

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return byName.values().stream()
                    .filter(warp -> warp.id().equals(Optional.of(id)))
                    .findFirst();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return byName.values().stream()
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .toList();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return byName.values().stream()
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .filter(warp -> warp.status() == WarpStatus.ACTIVE && warp.access() == WarpAccess.PUBLIC)
                    .toList();
        }

        @Override
        public int count(PlayerRef owner) {
            return (int) byName.values().stream()
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .count();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return byName.containsKey(name);
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            PlayerWarpId id = warp.id().orElseGet(() -> new PlayerWarpId(++nextId));
            byName.put(warp.name(), warp.id().isPresent() ? warp : warp.withId(id));
            return id;
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            byName.values().removeIf(warp -> warp.id().equals(Optional.of(id)));
        }

        @Override
        public void recordVisit(PlayerWarpId id) {
            // Visit counting is an atomic store-side write exercised in the persistence tests, not here.
        }

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {
            // Rating rollups are asserted in the persistence tests; the command-path fake ignores them.
        }

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {
            // Favourite counts are asserted in the persistence tests; the command-path fake ignores them.
        }
    }

    private static final class RecordingTeleporter implements PlayerWarpTeleporter {
        int hops;
        private PlayerWarp lastWarp = PlayerWarp.create(
                new PlayerRef(new UUID(0L, 0L), "none"),
                "none",
                PlayerWarpName.of("none"),
                Position.of(WORLD, 0, 0, 0),
                java.time.Instant.EPOCH);

        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {
            hops++;
            lastWarp = warp;
        }
    }

    /** A stub permissions port resolving every quota to the constructor limit. */
    private static final class StubPermissions implements Permissions {
        private final long limit;

        StubPermissions(long limit) {
            this.limit = limit;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(limit);
        }
    }

    /** No player is ever banned. */
    private static final class NoBans implements WarpBanStore {
        @Override
        public void ban(PlayerWarpId warp, BanRecord record) {}

        @Override
        public void unban(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<BanRecord> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    /** No warp has members. */
    private static final class NoMembers implements WarpMemberStore {
        @Override
        public void put(PlayerWarpId warp, WarpMember member) {}

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<WarpMember> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    /** Nobody is whitelisted. */
    private static final class NoWhitelist implements WarpWhitelistStore {
        @Override
        public void add(PlayerWarpId warp, UUID player) {}

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public boolean contains(PlayerWarpId warp, UUID player) {
            return false;
        }

        @Override
        public List<UUID> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    /** No warp has a password. */
    private static final class NoPasswords implements PlayerWarpPasswordStore {
        @Override
        public void set(PlayerWarpId warp, String plaintext) {}

        @Override
        public void clear(PlayerWarpId warp) {}

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            return false;
        }
    }

    /** No cooldown is ever active. */
    private static final class OpenCooldowns implements Cooldowns {
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: feedback delivery is not under test here
        }
    }

    /** A sink that records every rendered text it is handed, for asserting which message key was emitted. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private static final class CapturingEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded: event publication is asserted elsewhere
        }
    }
}

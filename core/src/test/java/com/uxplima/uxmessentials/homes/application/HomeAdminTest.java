package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HomeAdmin}'s staff override operations, set, clearAll, and info. The
 * repository, notifier, and event publisher are in-memory fakes so no adapters are involved.
 */
class HomeAdminTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "StaffMember");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "TargetPlayer");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneOffset.UTC);

    private FakeHomeRepository repository;
    private FakeInviteRepository invites;
    private CapturingNotifier notifier;
    private RecordingPublisher events;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        invites = new FakeInviteRepository();
        notifier = new CapturingNotifier();
        events = new RecordingPublisher();
    }

    // --- setHome ---

    @Test
    void setHomeCreatesHomeForTargetEvenWhenTheyAreAtTheirNormalQuota() {
        // The target already has 3 homes; a normal player might be capped at 3, but setHome bypasses the limit.
        repository.save(Home.create(TARGET, HomeSlot.of(0), at(0, 64, 0), CLOCK.instant()));
        repository.save(Home.create(TARGET, HomeSlot.of(1), at(0, 64, 0), CLOCK.instant()));
        repository.save(Home.create(TARGET, HomeSlot.of(2), at(0, 64, 0), CLOCK.instant()));

        Result<Unit, HomeError> result = useCase().setHome(ACTOR, TARGET, HomeSlot.of(5), at(10, 65, 20));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.findSlot(TARGET, HomeSlot.of(5))).isPresent();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_ADMIN_SET);
    }

    @Test
    void setHomeOnOccupiedSlotReAnchorsItKeepingLabelAndIcon() {
        // Pre-populate slot 0 with a label and icon.
        Home existing = Home.create(TARGET, HomeSlot.of(0), at(0, 64, 0), CLOCK.instant())
                .withLabel(Optional.of(HomeLabel.of("Castle")), CLOCK.instant())
                .withIcon(Optional.of(HomeIcon.of("DIAMOND_BLOCK")), CLOCK.instant());
        repository.save(existing);

        Result<Unit, HomeError> result = useCase().setHome(ACTOR, TARGET, HomeSlot.of(0), at(99, 70, 99));

        assertThat(result.isOk()).isTrue();
        Home updated = repository.findSlot(TARGET, HomeSlot.of(0)).orElseThrow();
        // Position changed.
        assertThat(updated.location().blockX()).isEqualTo(99);
        // Cosmetic fields preserved.
        assertThat(updated.label()).contains(HomeLabel.of("Castle"));
        assertThat(updated.icon()).contains(HomeIcon.of("DIAMOND_BLOCK"));
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_ADMIN_SET);
    }

    @Test
    void setHomeOnSlotOutOfAdminRangeReturnsError() {
        // Slot index 1000 is at or past MAX_ADMIN_SLOTS = 1000.
        Result<Unit, HomeError> result = useCase().setHome(ACTOR, TARGET, HomeSlot.of(1000), at(0, 64, 0));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.SLOT_OUT_OF_RANGE);
    }

    // --- clearAll ---

    @Test
    void clearAllRemovesAllHomesAndReportsCorrectCount() {
        repository.save(Home.create(TARGET, HomeSlot.of(0), at(0, 64, 0), CLOCK.instant()));
        repository.save(Home.create(TARGET, HomeSlot.of(1), at(1, 64, 1), CLOCK.instant()));
        repository.save(Home.create(TARGET, HomeSlot.of(2), at(2, 64, 2), CLOCK.instant()));

        Result<Unit, HomeError> result = useCase().clearAll(ACTOR, TARGET);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.load(TARGET).count()).isZero();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_ADMIN_CLEARED);
        // The count placeholder should reflect 3 (pre-clear count).
        assertThat(notifier.lastPlaceholders).containsEntry("count", "3");
    }

    @Test
    void clearAllOnOwnerWithNoHomesReturnsOkWithCountZero() {
        Result<Unit, HomeError> result = useCase().clearAll(ACTOR, TARGET);

        assertThat(result.isOk()).isTrue();
        assertThat(notifier.lastPlaceholders).containsEntry("count", "0");
    }

    @Test
    void clearAllCascadesToInvites() {
        repository.save(Home.create(TARGET, HomeSlot.of(0), at(0, 64, 0), CLOCK.instant()));
        invites.addInvite(TARGET, HomeSlot.of(0), UUID.randomUUID());

        useCase().clearAll(ACTOR, TARGET);

        assertThat(invites.hasAnyInvites(TARGET)).isFalse();
    }

    @Test
    void deleteCascadesToInvites() {
        repository.save(Home.create(TARGET, HomeSlot.of(0), at(0, 64, 0), CLOCK.instant()));
        invites.addInvite(TARGET, HomeSlot.of(0), UUID.randomUUID());

        useCase().delete(ACTOR, TARGET, HomeSlot.of(0));

        assertThat(invites.invites(TARGET, HomeSlot.of(0))).isEmpty();
    }

    // --- info ---

    @Test
    void infoPresentReturnsHomeAndNotifiesWithPlaceholders() {
        Position loc = at(42, 70, -100);
        Home home = Home.create(TARGET, HomeSlot.of(2), loc, CLOCK.instant())
                .withLabel(Optional.of(HomeLabel.of("Farm")), CLOCK.instant());
        repository.save(home);

        Optional<Home> found = useCase().info(ACTOR, TARGET, HomeSlot.of(2));

        assertThat(found).isPresent();
        assertThat(found.get().location().blockX()).isEqualTo(42);
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_ADMIN_INFO);
        assertThat(notifier.lastPlaceholders)
                .containsEntry("player", TARGET.name())
                .containsEntry("slot", "3")
                .containsEntry("label", "Farm")
                .containsEntry("world", WORLD.name())
                .containsEntry("x", "42")
                .containsEntry("y", "70")
                .containsEntry("z", "-100")
                .containsEntry("icon", "");
    }

    @Test
    void infoPresentWithIconPassesIconPlaceholder() {
        Home home = Home.create(TARGET, HomeSlot.of(0), at(0, 64, 0), CLOCK.instant())
                .withIcon(Optional.of(HomeIcon.of("GRASS_BLOCK")), CLOCK.instant());
        repository.save(home);

        useCase().info(ACTOR, TARGET, HomeSlot.of(0));

        assertThat(notifier.lastPlaceholders).containsEntry("icon", "GRASS_BLOCK");
    }

    @Test
    void infoMissingNotifiesNotFoundAndReturnsEmpty() {
        Optional<Home> found = useCase().info(ACTOR, TARGET, HomeSlot.of(7));

        assertThat(found).isEmpty();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_NOT_FOUND);
    }

    // --- helpers ---

    private HomeAdmin useCase() {
        return new HomeAdmin(repository, invites, NoOpTeleporter.INSTANCE, notifier.notifier(), events, CLOCK);
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }

    /** In-memory per-owner repository; keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        // Maps owner UUID to a slot-keyed home map.
        private final Map<UUID, Map<HomeSlot, Home>> store = new java.util.HashMap<>();

        private Map<HomeSlot, Home> slots(PlayerRef owner) {
            return store.computeIfAbsent(owner.uuid(), k -> new TreeMap<>(Comparator.comparingInt(HomeSlot::index)));
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, List.copyOf(slots(owner).values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return slots(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(slots(owner).get(slot));
        }

        @Override
        public void save(Home home) {
            slots(home.owner()).put(home.slot(), home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            slots(owner).remove(slot);
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            slots(owner).clear();
        }
    }

    /** In-memory invite store keyed by (owner, slot); records bulk clears so the cascade can be asserted. */
    private static final class FakeInviteRepository implements HomeInviteRepository {
        private final Map<UUID, Map<HomeSlot, Set<UUID>>> store = new java.util.HashMap<>();

        private Set<UUID> guests(PlayerRef owner, HomeSlot slot) {
            return store.computeIfAbsent(owner.uuid(), k -> new java.util.HashMap<>())
                    .computeIfAbsent(slot, k -> new java.util.HashSet<>());
        }

        @Override
        public Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
            return Set.copyOf(guests(owner, slot));
        }

        @Override
        public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            guests(owner, slot).add(invited);
        }

        @Override
        public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            guests(owner, slot).remove(invited);
        }

        @Override
        public void removeAll(PlayerRef owner, HomeSlot slot) {
            Map<HomeSlot, Set<UUID>> bySlot = store.get(owner.uuid());
            if (bySlot != null) {
                bySlot.remove(slot);
            }
        }

        @Override
        public void removeAllForOwner(PlayerRef owner) {
            store.remove(owner.uuid());
        }

        boolean hasAnyInvites(PlayerRef owner) {
            Map<HomeSlot, Set<UUID>> bySlot = store.get(owner.uuid());
            return bySlot != null && bySlot.values().stream().anyMatch(s -> !s.isEmpty());
        }
    }

    private enum NoOpTeleporter implements HomeTeleporter {
        INSTANCE;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            // no-op in admin unit tests
        }
    }

    /** Records the last key and placeholders sent through the notifier. */
    private static final class CapturingNotifier {
        private @Nullable MessageKey lastKey;
        private Map<String, String> lastPlaceholders = Map.of();

        Notifier notifier() {
            Messages messages = (viewer, key, placeholders) -> {
                lastKey = key;
                lastPlaceholders = placeholders;
                return key.key();
            };
            MessageSink sink = (viewer, renderedText) -> {};
            return new Notifier(messages, sink);
        }
    }

    private static final class RecordingPublisher implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}

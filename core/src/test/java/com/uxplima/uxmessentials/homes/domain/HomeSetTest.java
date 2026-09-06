package com.uxplima.uxmessentials.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.homes.domain.event.HomeIconChanged;
import com.uxplima.uxmessentials.homes.domain.event.HomeRelocated;
import com.uxplima.uxmessentials.homes.domain.event.HomeRenamed;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * The {@code HomeSet} aggregate invariants under the slot model: a slot stays in range and holds at most
 * one home, the count-vs-limit quota gate, the slot-ascending ordering, the lowest-free-slot search, and
 * the events each transition raises. The aggregate is pure, no clock or repository, so every rule is
 * asserted here in isolation from the application layer.
 */
class HomeSetTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant NOW = Instant.parse("2026-05-30T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-05-30T13:00:00Z");
    private static final int MAX_SLOTS = 9;

    @Test
    void createUnderLimitCreatesAndRaisesHomeCreated() {
        HomeSet set = HomeSet.empty(OWNER);

        HomeSet.Change change = set.createAt(HomeSlot.of(0), at(1, 2, 3), HomeLimit.of(3), MAX_SLOTS, NOW)
                .orElseThrow();

        assertThat(change.result().count()).isEqualTo(1);
        assertThat(change.event()).get().isInstanceOf(HomeCreated.class);
        assertThat(change.result().find(HomeSlot.of(0))).isPresent();
        assertThat(change.home().createdAt()).isEqualTo(NOW);
        assertThat(change.home().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createIntoATakenSlotIsRejected() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0)));

        var result = set.createAt(HomeSlot.of(0), at(0, 0, 0), HomeLimit.of(5), MAX_SLOTS, NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.SLOT_TAKEN);
    }

    @Test
    void createIntoAnOutOfRangeSlotIsRejected() {
        HomeSet set = HomeSet.empty(OWNER);

        var result = set.createAt(HomeSlot.of(MAX_SLOTS), at(0, 0, 0), HomeLimit.of(MAX_SLOTS + 5), MAX_SLOTS, NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.SLOT_OUT_OF_RANGE);
    }

    @Test
    void createAtTheLimitIsRejectedWithLimitReached() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0), home(1)));

        var result = set.createAt(HomeSlot.of(2), at(0, 0, 0), HomeLimit.of(2), MAX_SLOTS, NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.LIMIT_REACHED);
    }

    @Test
    void unlimitedQuotaNeverRejectsANewHomeWhileInRange() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0), home(1), home(2)));

        var result = set.createAt(HomeSlot.of(3), at(0, 0, 0), HomeLimit.noLimit(), MAX_SLOTS, NOW);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().result().count()).isEqualTo(4);
    }

    @Test
    void relocateMovesTheHomeKeepingCreationTimeAndRaisesHomeRelocated() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0)));

        HomeSet.Change change = set.relocate(HomeSlot.of(0), at(9, 9, 9), LATER).orElseThrow();

        Home moved = change.home();
        assertThat(moved.location().blockX()).isEqualTo(9);
        assertThat(moved.createdAt()).isEqualTo(NOW);
        assertThat(moved.updatedAt()).isEqualTo(LATER);
        assertThat(change.event()).get().isInstanceOf(HomeRelocated.class);
    }

    @Test
    void relocateOfAnEmptySlotIsRejected() {
        HomeSet set = HomeSet.empty(OWNER);

        assertThat(set.relocate(HomeSlot.of(0), at(1, 1, 1), NOW).errorOrThrow())
                .isEqualTo(HomeError.NOT_FOUND);
    }

    @Test
    void renameLabelSetsTheLabelAndRaisesHomeRenamed() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0)));

        HomeSet.Change change = set.renameLabel(HomeSlot.of(0), Optional.of(HomeLabel.of("Base Camp")), LATER)
                .orElseThrow();

        assertThat(change.home().label()).contains(HomeLabel.of("Base Camp"));
        assertThat(change.home().updatedAt()).isEqualTo(LATER);
        assertThat(change.event()).get().isInstanceOf(HomeRenamed.class);
    }

    @Test
    void renameLabelCanClearTheLabel() {
        Home labelled = home(0).withLabel(Optional.of(HomeLabel.of("Base")), NOW);
        HomeSet set = HomeSet.of(OWNER, List.of(labelled));

        HomeSet.Change change =
                set.renameLabel(HomeSlot.of(0), Optional.empty(), LATER).orElseThrow();

        assertThat(change.home().label()).isEmpty();
    }

    @Test
    void renameLabelOfAnEmptySlotIsRejected() {
        HomeSet set = HomeSet.empty(OWNER);

        assertThat(set.renameLabel(HomeSlot.of(0), Optional.of(HomeLabel.of("x")), NOW)
                        .errorOrThrow())
                .isEqualTo(HomeError.NOT_FOUND);
    }

    @Test
    void setIconSetsTheIconAndRaisesHomeIconChanged() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0)));

        HomeSet.Change change = set.setIcon(HomeSlot.of(0), Optional.of(HomeIcon.of("grass_block")), LATER)
                .orElseThrow();

        assertThat(change.home().icon()).contains(HomeIcon.of("GRASS_BLOCK"));
        assertThat(change.event()).get().isInstanceOf(HomeIconChanged.class);
    }

    @Test
    void setIconOfAnEmptySlotIsRejected() {
        HomeSet set = HomeSet.empty(OWNER);

        assertThat(set.setIcon(HomeSlot.of(0), Optional.of(HomeIcon.of("STONE")), NOW)
                        .errorOrThrow())
                .isEqualTo(HomeError.NOT_FOUND);
    }

    @Test
    void deleteRemovesTheHomeAndRaisesHomeDeleted() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0), home(1)));

        HomeSet.Change change = set.delete(HomeSlot.of(0)).orElseThrow();

        assertThat(change.result().count()).isEqualTo(1);
        assertThat(change.result().find(HomeSlot.of(0))).isEmpty();
        assertThat(change.event()).get().isInstanceOf(HomeDeleted.class);
    }

    @Test
    void deletingAnEmptySlotIsRejected() {
        HomeSet set = HomeSet.empty(OWNER);

        assertThat(set.delete(HomeSlot.of(0)).errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
    }

    @Test
    void allIsOrderedBySlotIndexRegardlessOfInsertionOrder() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(4), home(1), home(7)));

        assertThat(set.all().stream().map(h -> h.slot().index())).containsExactly(1, 4, 7);
    }

    @Test
    void firstFreeSlotIsTheLowestUnusedIndex() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0), home(2)));

        assertThat(set.firstFreeSlot(MAX_SLOTS)).contains(HomeSlot.of(1));
    }

    @Test
    void firstFreeSlotIsEmptyWhenEverySlotIsTaken() {
        HomeSet set = HomeSet.of(OWNER, List.of(home(0), home(1)));

        assertThat(set.firstFreeSlot(2)).isEmpty();
    }

    private static Home home(int slotIndex) {
        return Home.create(OWNER, HomeSlot.of(slotIndex), at(0, 64, 0), NOW);
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }
}

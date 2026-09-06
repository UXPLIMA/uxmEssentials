package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.KeyMessages;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DirectTeleporter;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HologramConvenienceTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef NETHER = new WorldRef(UUID.randomUUID(), "world_nether");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "op");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private Notifier notifier;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        notifier = new Notifier(new KeyMessages(), sink);
    }

    private Hologram store(String name, double x, double y, double z, String... lines) {
        List<HologramLine> ls = new ArrayList<>();
        for (String line : lines) {
            ls.add(new HologramLine(line));
        }
        Hologram hologram = Hologram.create(
                HologramName.of(name), new Position(WORLD, x, y, z, 0f, 0f), ls, Instant.ofEpochMilli(1));
        repository.save(hologram);
        return hologram;
    }

    private List<String> feedback() {
        return sink.textFor(ACTOR);
    }

    // ---- copy ----

    @Test
    void copyDuplicatesEveryPropertyUnderTheNewNameAtTheSameLocation() {
        repository.save(store("src", 3, 64, 7, "a", "b")
                .withRefreshIntervalTicks(40)
                .withAppearance(Appearance.defaults().withBillboard(Billboard.FIXED))
                .withVisibility(Visibility.restrictedTo("uxmessentials.hologram.see.vip"))
                .withRotation(Rotation.of(90f, 30f)));

        new CopyHologram(repository, view, notifier).copy(ACTOR, HologramName.of("src"), HologramName.of("dest"));

        Hologram clone = repository.find(HologramName.of("dest")).orElseThrow();
        Hologram src = repository.find(HologramName.of("src")).orElseThrow();
        assertThat(clone.location()).isEqualTo(src.location());
        assertThat(clone.lines()).isEqualTo(src.lines());
        assertThat(clone.appearance()).isEqualTo(src.appearance());
        assertThat(clone.visibility()).isEqualTo(src.visibility());
        assertThat(clone.rotation()).isEqualTo(src.rotation());
        assertThat(clone.refreshIntervalTicks()).isEqualTo(40);
        assertThat(view.rendered).extracting(h -> h.name().value()).contains("dest");
        assertThat(feedback()).contains("hologram.copied");
    }

    @Test
    void copyRejectsAMissingSource() {
        var result =
                new CopyHologram(repository, view, notifier).copy(ACTOR, HologramName.of("nope"), HologramName.of("d"));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(repository.exists(HologramName.of("d"))).isFalse();
        assertThat(feedback()).contains("hologram.not-found");
    }

    @Test
    void copyRejectsAnExistingDestination() {
        store("src", 0, 0, 0, "a");
        store("dest", 9, 9, 9, "z");

        var result = new CopyHologram(repository, view, notifier)
                .copy(ACTOR, HologramName.of("src"), HologramName.of("dest"));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NAME_TAKEN);
        // The pre-existing dest is untouched.
        assertThat(repository
                        .find(HologramName.of("dest"))
                        .orElseThrow()
                        .location()
                        .blockX())
                .isEqualTo(9);
        assertThat(feedback()).contains("hologram.name-taken");
    }

    // ---- info ----

    @Test
    void infoRendersTheStructuredProperties() {
        repository.save(store("spawn", 1, 64, 2, "line").withRotation(Rotation.of(45f, 10f)));

        new DescribeHologram(repository, notifier).describe(ACTOR, HologramName.of("spawn"));

        assertThat(feedback())
                .contains(
                        "hologram.info.header",
                        "hologram.info.location",
                        "hologram.info.lines",
                        "hologram.info.type",
                        "hologram.info.billboard",
                        "hologram.info.background",
                        "hologram.info.scale",
                        "hologram.info.view-range",
                        "hologram.info.visibility",
                        "hologram.info.refresh",
                        "hologram.info.rotation",
                        "hologram.info.alignment",
                        "hologram.info.see-through",
                        "hologram.info.translation",
                        "hologram.info.shadow");
    }

    @Test
    void infoRejectsAMissingHologram() {
        var result = new DescribeHologram(repository, notifier).describe(ACTOR, HologramName.of("ghost"));

        assertThat(result.isErr()).isTrue();
        assertThat(feedback()).containsExactly("hologram.not-found");
    }

    @Test
    void infoShowsTheLinkedNpcWhenLinked() {
        repository.save(store("spawn", 1, 64, 2, "line").linkedTo("guard"));

        new DescribeHologram(repository, notifier).describe(ACTOR, HologramName.of("spawn"));

        assertThat(feedback()).contains("hologram.info.linked-npc");
    }

    // ---- nearby ----

    @Test
    void nearbyListsOnlyHologramsWithinTheRadiusNearestFirst() {
        store("close", 5, 64, 0, "a"); // 5 blocks
        store("mid", 12, 64, 0, "b"); // 12 blocks
        store("far", 200, 64, 0, "c"); // 200 blocks: outside radius 16
        repository.save(Hologram.create(
                HologramName.of("other-world"),
                new Position(NETHER, 1, 64, 0, 0f, 0f),
                List.of(new HologramLine("x")),
                Instant.ofEpochMilli(1)));

        Position origin = new Position(WORLD, 0, 64, 0, 0f, 0f);
        List<Hologram> matches = new NearbyHolograms(repository, notifier).nearby(ACTOR, origin, 16);

        assertThat(matches).extracting(h -> h.name().value()).containsExactly("close", "mid");
        assertThat(feedback()).contains("hologram.nearby.header", "hologram.nearby.entry");
    }

    @Test
    void nearbyReportsEmptyWhenNothingIsInRange() {
        store("far", 500, 64, 0, "c");

        Position origin = new Position(WORLD, 0, 64, 0, 0f, 0f);
        List<Hologram> matches = new NearbyHolograms(repository, notifier).nearby(ACTOR, origin, 16);

        assertThat(matches).isEmpty();
        assertThat(feedback()).contains("hologram.nearby.empty");
    }

    @Test
    void nearbyClampsAnOversizedRadius() {
        assertThat(NearbyHolograms.clampRadius(99_999)).isEqualTo(NearbyHolograms.MAX_RADIUS);
        assertThat(NearbyHolograms.clampRadius(0)).isEqualTo(1);
    }

    // ---- center ----

    @Test
    void centerSnapsToTheBlockCentreKeepingY() {
        store("spawn", 10.8, 64.3, 20.1, "line");

        new CenterHologram(repository, view, notifier).center(ACTOR, HologramName.of("spawn"));

        Position at = repository.find(HologramName.of("spawn")).orElseThrow().location();
        assertThat(at.x()).isEqualTo(10.5);
        assertThat(at.z()).isEqualTo(20.5);
        assertThat(at.y()).isEqualTo(64.3);
        assertThat(view.rendered).isNotEmpty();
        assertThat(feedback()).contains("hologram.centered");
    }

    @Test
    void centerRejectsAMissingHologram() {
        var result = new CenterHologram(repository, view, notifier).center(ACTOR, HologramName.of("ghost"));

        assertThat(result.isErr()).isTrue();
        assertThat(feedback()).contains("hologram.not-found");
    }

    // ---- rotate ----

    @Test
    void rotateStoresTheRotationAndReRenders() {
        store("spawn", 0, 64, 0, "line");

        new RotateHologram(repository, view, notifier).rotate(ACTOR, HologramName.of("spawn"), Rotation.of(90f, 45f));

        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().rotation())
                .isEqualTo(Rotation.of(90f, 45f));
        assertThat(view.rendered).isNotEmpty();
        assertThat(feedback()).contains("hologram.rotated");
    }

    @Test
    void rotateRejectsAMissingHologram() {
        var result =
                new RotateHologram(repository, view, notifier).rotate(ACTOR, HologramName.of("ghost"), Rotation.NONE);

        assertThat(result.isErr()).isTrue();
        assertThat(feedback()).contains("hologram.not-found");
    }

    // ---- teleport ----

    @Test
    void teleportSendsTheActorToTheHologramLocation() {
        store("spawn", 3, 70, 9, "line");
        RecordingTeleporter teleporter = new RecordingTeleporter();

        new TeleportToHologram(repository, teleporter, notifier).teleport(ACTOR, HologramName.of("spawn"));

        assertThat(teleporter.who).isEqualTo(ACTOR);
        Position destination = java.util.Objects.requireNonNull(teleporter.destination);
        assertThat(destination.blockX()).isEqualTo(3);
        assertThat(destination.blockZ()).isEqualTo(9);
        assertThat(feedback()).contains("hologram.teleported");
    }

    @Test
    void teleportRejectsAMissingHologramWithoutMovingTheActor() {
        RecordingTeleporter teleporter = new RecordingTeleporter();

        var result = new TeleportToHologram(repository, teleporter, notifier).teleport(ACTOR, HologramName.of("ghost"));

        assertThat(result.isErr()).isTrue();
        assertThat(teleporter.who).isNull();
        assertThat(feedback()).contains("hologram.not-found");
    }

    // ---- insertline ----

    @Test
    void insertLinePlacesTheLineBeforeTheIndex() {
        store("spawn", 0, 64, 0, "one", "two");

        // index 0-based 1 == insert before the second line
        new InsertHologramLine(repository, view, notifier)
                .insert(ACTOR, HologramName.of("spawn"), 1, new HologramLine("between"));

        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().lines())
                .extracting(HologramLine::value)
                .containsExactly("one", "between", "two");
        assertThat(feedback()).contains("hologram.line.inserted");
    }

    @Test
    void insertLineAppendsWhenTheIndexIsPastTheEnd() {
        store("spawn", 0, 64, 0, "one", "two");

        new InsertHologramLine(repository, view, notifier)
                .insert(ACTOR, HologramName.of("spawn"), 50, new HologramLine("end"));

        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().lines())
                .extracting(HologramLine::value)
                .containsExactly("one", "two", "end");
    }

    @Test
    void insertLineRejectsAMissingHologram() {
        var result = new InsertHologramLine(repository, view, notifier)
                .insert(ACTOR, HologramName.of("ghost"), 0, new HologramLine("x"));

        assertThat(result.isErr()).isTrue();
        assertThat(feedback()).contains("hologram.not-found");
    }

    /** Records the single teleport request so the use-case side effect can be asserted. */
    private static final class RecordingTeleporter implements DirectTeleporter {
        private @org.jspecify.annotations.Nullable PlayerRef who;
        private @org.jspecify.annotations.Nullable Position destination;

        @Override
        public void teleport(PlayerRef who, Position destination) {
            this.who = who;
            this.destination = destination;
        }
    }
}

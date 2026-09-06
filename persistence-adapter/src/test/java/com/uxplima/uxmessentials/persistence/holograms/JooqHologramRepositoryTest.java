package com.uxplima.uxmessentials.persistence.holograms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramPage;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.persistence.jooq.tables.HologramLines;
import com.uxplima.uxmessentials.persistence.jooq.tables.Holograms;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqHologramRepository} against the default embedded SQLite backend with the
 * Flyway V13 baseline applied. It proves the round-trip (save → find) of the name row and its ordered lines,
 * the name-key upsert (a re-anchor / line edit overwrites in place and rewrites the lines rather than leaving
 * stale rows), the delete removing both the name row and its lines, the {@code exists} check, and the
 * creation-order list.
 */
class JooqHologramRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqHologramRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqHologramRepository(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsAHologramWithItsOrderedLines() {
        repository.save(hologram("spawn", 10, 64, 20, "one", "two", "three"));

        Optional<Hologram> loaded = repository.find(HologramName.of("spawn"));

        assertThat(loaded).isPresent();
        Hologram reloaded = loaded.orElseThrow();
        assertThat(reloaded.location().blockX()).isEqualTo(10);
        assertThat(reloaded.location().blockZ()).isEqualTo(20);
        assertThat(reloaded.location().world().name()).isEqualTo("world");
        assertThat(reloaded.lines()).map(HologramLine::value).containsExactly("one", "two", "three");
    }

    @Test
    void saveUpsertsOnTheNameKeyAndRewritesTheLines() {
        repository.save(hologram("spawn", 0, 0, 0, "old-a", "old-b"));
        repository.save(hologram("spawn", 100, 70, 100, "new")); // same name. A re-anchor with fewer lines

        assertThat(repository.all()).hasSize(1);
        Hologram reanchored = repository.find(HologramName.of("spawn")).orElseThrow();
        assertThat(reanchored.location().blockX()).isEqualTo(100);
        assertThat(reanchored.lines()).map(HologramLine::value).containsExactly("new"); // no stale line rows
    }

    @Test
    void existsReflectsWhetherAHologramIsStored() {
        assertThat(repository.exists(HologramName.of("spawn"))).isFalse();

        repository.save(hologram("spawn", 0, 0, 0, "line"));

        assertThat(repository.exists(HologramName.of("spawn"))).isTrue();
    }

    @Test
    void deleteRemovesTheNameRowAndItsLines() {
        repository.save(hologram("spawn", 0, 0, 0, "a", "b"));
        repository.save(hologram("pvp", 1, 1, 1, "c"));

        repository.delete(HologramName.of("spawn"));

        assertThat(repository.exists(HologramName.of("spawn"))).isFalse();
        assertThat(repository.all()).hasSize(1);
        // A re-created hologram under the freed name starts with only its own lines, proving no orphans.
        repository.save(hologram("spawn", 5, 5, 5, "fresh"));
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().lines())
                .map(HologramLine::value)
                .containsExactly("fresh");
    }

    @Test
    void allPreservesCreationOrder() {
        repository.save(hologramAt("first", Instant.ofEpochMilli(1_000)));
        repository.save(hologramAt("second", Instant.ofEpochMilli(2_000)));
        repository.save(hologramAt("third", Instant.ofEpochMilli(3_000)));

        assertThat(repository.all().stream().map(h -> h.name().value())).containsExactly("first", "second", "third");
    }

    @Test
    void roundTripsAppearanceAndRefreshInterval() {
        Appearance styled = Appearance.defaults()
                .withBillboard(Billboard.VERTICAL)
                .withBackgroundArgb(0x80112233)
                .withTextShadow(true)
                .withBrightness(15, 7)
                .withScale(2.5f)
                .withLineWidth(120)
                .withViewRange(3.0f)
                .withGlowArgb(0x80ff8800)
                .withTextOpacity(200);
        repository.save(
                hologram("spawn", 1, 64, 1, "line").withAppearance(styled).withRefreshIntervalTicks(40));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.appearance()).isEqualTo(styled);
        assertThat(loaded.refreshIntervalTicks()).isEqualTo(40);
    }

    @Test
    void roundTripsTheNewDisplayProperties() {
        Appearance styled = Appearance.defaults()
                .withSeeThrough(true)
                .withAlignment(TextAlignment.LEFT)
                .withShadowRadius(1.5f)
                .withShadowStrength(0.8f)
                .withScale(2f, 3f, 4f)
                .withTranslation(0.5f, 1.0f, -0.5f);
        repository.save(hologram("spawn", 1, 64, 1, "line").withAppearance(styled));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.appearance()).isEqualTo(styled);
        assertThat(loaded.appearance().alignment()).isEqualTo(TextAlignment.LEFT);
        assertThat(loaded.appearance().seeThrough()).isTrue();
        assertThat(loaded.appearance().transform().scaleY()).isEqualTo(3f);
        assertThat(loaded.appearance().transform().translationX()).isEqualTo(0.5f);
        assertThat(loaded.appearance().shadowRadius()).isEqualTo(1.5f);
        assertThat(loaded.appearance().shadowStrength()).isEqualTo(0.8f);
    }

    @Test
    void aPreV48UniformScaleRowReadsBackUniformOnEveryAxis() {
        // A pre-V48 row carries the V35 uniform `scale` only (scale_y/z, translation, alignment, see-through,
        // shadow columns NULL): insert it directly and prove the per-axis scale reads back uniform.
        Holograms holograms = Holograms.HOLOGRAMS;
        HologramLines lines = HologramLines.HOLOGRAM_LINES;
        persistence.dsl().transaction(config -> {
            config.dsl()
                    .insertInto(holograms)
                    .set(holograms.NAME, "legacy")
                    .set(holograms.WORLD, WORLD.uid().toString())
                    .set(holograms.WORLD_NAME, "world")
                    .set(holograms.X, 0.0)
                    .set(holograms.Y, 64.0)
                    .set(holograms.Z, 0.0)
                    .set(holograms.YAW, 0.0f)
                    .set(holograms.PITCH, 0.0f)
                    .set(holograms.CREATED_AT, 1_000L)
                    .set(holograms.SCALE, 2.5f)
                    .execute();
            config.dsl()
                    .insertInto(lines)
                    .set(lines.HOLOGRAM, "legacy")
                    .set(lines.IDX, 0)
                    .set(lines.TEXT, "line")
                    .execute();
        });

        Hologram loaded = repository.find(HologramName.of("legacy")).orElseThrow();

        assertThat(loaded.appearance().transform().scaleX()).isEqualTo(2.5f);
        assertThat(loaded.appearance().transform().scaleY()).isEqualTo(2.5f);
        assertThat(loaded.appearance().transform().scaleZ()).isEqualTo(2.5f);
        assertThat(loaded.appearance().transform().isUniformScale()).isTrue();
        assertThat(loaded.appearance().alignment()).isEqualTo(TextAlignment.CENTER);
        assertThat(loaded.appearance().seeThrough()).isFalse();
        assertThat(loaded.appearance().hasShadowRadius()).isFalse();
    }

    @Test
    void roundTripsTheLinkedNpcName() {
        repository.save(hologram("spawn", 1, 64, 1, "line").linkedTo("guide"));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.isLinked()).isTrue();
        assertThat(loaded.linkedNpcName()).isEqualTo("guide");
    }

    @Test
    void unlinkingClearsTheStoredNpcName() {
        repository.save(hologram("spawn", 1, 64, 1, "line").linkedTo("guide"));
        repository.save(repository.find(HologramName.of("spawn")).orElseThrow().unlinked());

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.isLinked()).isFalse();
        assertThat(loaded.linkedNpcName()).isNull();
    }

    @Test
    void aPreV50RowWithNoLinkedNpcNameReadsBackUnlinked() {
        // A pre-V50 row leaves linked_npc_name NULL: insert it directly and prove it reads back as unlinked.
        Holograms holograms = Holograms.HOLOGRAMS;
        HologramLines lines = HologramLines.HOLOGRAM_LINES;
        persistence.dsl().transaction(config -> {
            config.dsl()
                    .insertInto(holograms)
                    .set(holograms.NAME, "legacy")
                    .set(holograms.WORLD, WORLD.uid().toString())
                    .set(holograms.WORLD_NAME, "world")
                    .set(holograms.X, 0.0)
                    .set(holograms.Y, 64.0)
                    .set(holograms.Z, 0.0)
                    .set(holograms.YAW, 0.0f)
                    .set(holograms.PITCH, 0.0f)
                    .set(holograms.CREATED_AT, 1_000L)
                    .execute();
            config.dsl()
                    .insertInto(lines)
                    .set(lines.HOLOGRAM, "legacy")
                    .set(lines.IDX, 0)
                    .set(lines.TEXT, "line")
                    .execute();
        });

        Hologram loaded = repository.find(HologramName.of("legacy")).orElseThrow();

        assertThat(loaded.isLinked()).isFalse();
        assertThat(loaded.linkedNpcName()).isNull();
    }

    @Test
    void roundTripsAnItemHologramType() {
        repository.save(Hologram.createItem(
                HologramName.of("loot"), Position.of(WORLD, 1, 64, 1), "DIAMOND", Instant.ofEpochMilli(1_000)));

        Hologram loaded = repository.find(HologramName.of("loot")).orElseThrow();

        assertThat(loaded.type()).isEqualTo(com.uxplima.uxmessentials.holograms.domain.HologramType.ITEM);
        assertThat(loaded.itemMaterial()).isEqualTo("DIAMOND");
        assertThat(loaded.blockData()).isNull();
        assertThat(loaded.lineCount()).isZero();
    }

    @Test
    void roundTripsABlockHologramType() {
        repository.save(Hologram.createBlock(
                HologramName.of("statue"),
                Position.of(WORLD, 1, 64, 1),
                "minecraft:oak_log[axis=y]",
                Instant.ofEpochMilli(1_000)));

        Hologram loaded = repository.find(HologramName.of("statue")).orElseThrow();

        assertThat(loaded.type()).isEqualTo(com.uxplima.uxmessentials.holograms.domain.HologramType.BLOCK);
        assertThat(loaded.blockData()).isEqualTo("minecraft:oak_log[axis=y]");
        assertThat(loaded.itemMaterial()).isNull();
    }

    @Test
    void roundTripsAHeadHologramType() {
        repository.save(Hologram.createHead(
                HologramName.of("greeter"),
                Position.of(WORLD, 1, 64, 1),
                "dGVzdC10ZXh0dXJl",
                Instant.ofEpochMilli(1_000)));

        Hologram loaded = repository.find(HologramName.of("greeter")).orElseThrow();

        assertThat(loaded.type()).isEqualTo(com.uxplima.uxmessentials.holograms.domain.HologramType.HEAD);
        assertThat(loaded.headTexture()).isEqualTo("dGVzdC10ZXh0dXJl");
        assertThat(loaded.itemMaterial()).isNull();
        assertThat(loaded.blockData()).isNull();
        assertThat(loaded.lineCount()).isZero();
    }

    @Test
    void roundTripsAnEntityHologramType() {
        repository.save(Hologram.createEntity(
                HologramName.of("guard"), Position.of(WORLD, 1, 64, 1), "ZOMBIE", Instant.ofEpochMilli(1_000)));

        Hologram loaded = repository.find(HologramName.of("guard")).orElseThrow();

        assertThat(loaded.type()).isEqualTo(com.uxplima.uxmessentials.holograms.domain.HologramType.ENTITY);
        assertThat(loaded.entityType()).isEqualTo("ZOMBIE");
        assertThat(loaded.itemMaterial()).isNull();
        assertThat(loaded.lineCount()).isZero();
    }

    @Test
    void roundTripsALeaderboard() {
        repository.save(hologram("top", 1, 64, 1, "line")
                .withLeaderboard(new com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec("balance", 5)));

        Hologram loaded = repository.find(HologramName.of("top")).orElseThrow();

        com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec spec =
                java.util.Objects.requireNonNull(loaded.leaderboard());
        assertThat(spec.providerId()).isEqualTo("balance");
        assertThat(spec.limit()).isEqualTo(5);
    }

    @Test
    void roundTripsAClickCommand() {
        repository.save(hologram("spawn", 1, 64, 1, "line").withClickCommand("warp pvp"));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.clickCommand()).isEqualTo("warp pvp");
        // A pre-V54 / never-clickable hologram reads back with no click command.
        repository.save(hologram("plain", 2, 64, 2, "line"));
        assertThat(repository.find(HologramName.of("plain")).orElseThrow().clickCommand())
                .isNull();
    }

    @Test
    void persistsAClickCommandAndLeaderboardAppliedToAnAlreadySavedHologram() {
        // The row exists after the first save, so the second save is an UPDATE: the V54/V56 columns must be
        // written on conflict, not only on the initial insert, or the setting is lost on the next restart.
        repository.save(hologram("spawn", 1, 64, 1, "line"));
        Hologram base = repository.find(HologramName.of("spawn")).orElseThrow();
        repository.save(base.withClickCommand("warp pvp")
                .withLeaderboard(new com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec("balance", 7)));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();
        assertThat(loaded.clickCommand()).isEqualTo("warp pvp");
        com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec spec =
                java.util.Objects.requireNonNull(loaded.leaderboard());
        assertThat(spec.providerId()).isEqualTo("balance");
        assertThat(spec.limit()).isEqualTo(7);
    }

    @Test
    void persistsAModelSwitchAppliedToAnAlreadySavedHologram() {
        // The same UPDATE-path coverage for the V53/V55 model columns (head texture / entity type).
        repository.save(hologram("spawn", 1, 64, 1, "line"));
        Hologram base = repository.find(HologramName.of("spawn")).orElseThrow();
        repository.save(base.asHead("dGVzdC10ZXh0dXJl"));

        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().headTexture())
                .isEqualTo("dGVzdC10ZXh0dXJl");
    }

    @Test
    void roundTripsTheViewerBlacklistAndDropsItOnDelete() {
        UUID banned = UUID.randomUUID();
        repository.save(hologram("spawn", 1, 64, 1, "line"));
        repository.addToBlacklist(HologramName.of("spawn"), banned);

        assertThat(repository.blacklisted(HologramName.of("spawn"))).containsExactly(banned);

        repository.removeFromBlacklist(HologramName.of("spawn"), banned);
        assertThat(repository.blacklisted(HologramName.of("spawn"))).isEmpty();

        // Re-add then delete the hologram: the blacklist rows go with it (no orphans under a reused name).
        repository.addToBlacklist(HologramName.of("spawn"), banned);
        repository.delete(HologramName.of("spawn"));
        assertThat(repository.blacklisted(HologramName.of("spawn"))).isEmpty();
    }

    @Test
    void roundTripsTheGrowUpFlagIncludingTheUpdatePath() {
        repository.save(hologram("spawn", 1, 64, 1, "line"));
        Hologram base = repository.find(HologramName.of("spawn")).orElseThrow();
        // A fresh row grows downward (the existing default); the V58 column reads back false.
        assertThat(base.growUp()).isFalse();

        repository.save(base.withGrowUp(true));

        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().growUp())
                .isTrue();
    }

    @Test
    void roundTripsTheActionChainAndDropsItOnDelete() {
        repository.save(hologram("spawn", 1, 64, 1, "line")
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi")));
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().actions())
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.trigger()).isEqualTo(ClickTrigger.ANY);
                    assertThat(a.type()).isEqualTo(ClickActionType.MESSAGE);
                    assertThat(a.value()).isEqualTo("hi");
                });

        repository.delete(HologramName.of("spawn"));
        // Re-create under the freed name; the actions must not resurface from a stale child row.
        repository.save(hologram("spawn", 1, 64, 1, "line"));
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().actions())
                .isEmpty();
    }

    @Test
    void roundTripsTheActionChainInOrder() {
        ClickAction first = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "<green>welcome");
        ClickAction second = new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.RUN_CONSOLE, "say hi {player}");
        ClickAction third = new ClickAction(ClickTrigger.ANY, ClickActionType.SOUND, "ui.button.click:1:2");
        repository.save(hologram("spawn", 1, 64, 1, "line")
                .withActionAdded(first)
                .withActionAdded(second)
                .withActionAdded(third));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.actions()).containsExactly(first, second, third);
    }

    @Test
    void replacesTheActionChainOnSaveLeavingNoStaleRows() {
        repository.save(hologram("spawn", 1, 64, 1, "line")
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "one"))
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "two")));

        ClickAction kept = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.ACTIONBAR, "only");
        repository.save(repository
                .find(HologramName.of("spawn"))
                .orElseThrow()
                .withActionsCleared()
                .withActionAdded(kept));

        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().actions())
                .containsExactly(kept);
    }

    @Test
    void aFreshHologramDefaultsToAnEmptyActionChain() {
        repository.save(hologram("spawn", 1, 64, 1, "line"));

        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().actions())
                .isEmpty();
    }

    @Test
    void roundTripsAMultiPageHologram() {
        repository.save(hologram("paged", 1, 64, 1, "p0-a", "p0-b")
                .withPageAppended(HologramPage.of(List.of(new HologramLine("p1-a")))));

        Hologram loaded = repository.find(HologramName.of("paged")).orElseThrow();

        assertThat(loaded.isMultiPage()).isTrue();
        assertThat(loaded.pageCount()).isEqualTo(2);
        // Page 0 is the content line set (hologram_lines); the extra page comes from hologram_pages.
        assertThat(loaded.lines().stream().map(HologramLine::value)).containsExactly("p0-a", "p0-b");
        assertThat(loaded.pageLines(1).stream().map(HologramLine::value)).containsExactly("p1-a");
    }

    @Test
    void removingPagesCollapsesToSinglePageAndClearsTheStalePageRows() {
        repository.save(
                hologram("paged", 1, 64, 1, "p0").withPageAppended(HologramPage.of(List.of(new HologramLine("p1")))));
        Hologram twoPage = repository.find(HologramName.of("paged")).orElseThrow();
        repository.save(twoPage.withPageRemoved(1));

        Hologram loaded = repository.find(HologramName.of("paged")).orElseThrow();
        assertThat(loaded.isMultiPage()).isFalse();
        assertThat(loaded.pageCount()).isEqualTo(1);
        assertThat(loaded.lines().stream().map(HologramLine::value)).containsExactly("p0");
    }

    @Test
    void aRowWithNoTypeColumnReadsBackAsText() {
        // A pre-V37 row: insert the name row directly leaving the type/item/block columns NULL, plus one line.
        Holograms holograms = Holograms.HOLOGRAMS;
        HologramLines lines = HologramLines.HOLOGRAM_LINES;
        persistence.dsl().transaction(config -> {
            config.dsl()
                    .insertInto(holograms)
                    .set(holograms.NAME, "legacy")
                    .set(holograms.WORLD, WORLD.uid().toString())
                    .set(holograms.WORLD_NAME, "world")
                    .set(holograms.X, 0.0)
                    .set(holograms.Y, 64.0)
                    .set(holograms.Z, 0.0)
                    .set(holograms.YAW, 0.0f)
                    .set(holograms.PITCH, 0.0f)
                    .set(holograms.CREATED_AT, 1_000L)
                    .execute();
            config.dsl()
                    .insertInto(lines)
                    .set(lines.HOLOGRAM, "legacy")
                    .set(lines.IDX, 0)
                    .set(lines.TEXT, "line")
                    .execute();
        });

        Hologram loaded = repository.find(HologramName.of("legacy")).orElseThrow();

        assertThat(loaded.type()).isEqualTo(com.uxplima.uxmessentials.holograms.domain.HologramType.TEXT);
        assertThat(loaded.itemMaterial()).isNull();
    }

    @Test
    void roundTripsAStoredRotation() {
        repository.save(hologram("spawn", 1, 64, 1, "line").withRotation(Rotation.of(90f, 30f)));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.rotation()).isEqualTo(Rotation.of(90f, 30f));
    }

    @Test
    void aFreshHologramDefaultsToNoRotation() {
        repository.save(hologram("spawn", 1, 64, 1, "line"));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.rotation()).isEqualTo(Rotation.NONE);
    }

    @Test
    void roundTripsPermissionVisibilityAndDistance() {
        Visibility gated =
                Visibility.restrictedTo("uxmessentials.hologram.see.vip").withDistance(48);
        repository.save(hologram("spawn", 1, 64, 1, "line").withVisibility(gated));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.visibility()).isEqualTo(gated);
        assertThat(loaded.visibility().mode()).isEqualTo(Visibility.Mode.PERMISSION);
        assertThat(loaded.visibility().permission()).isEqualTo("uxmessentials.hologram.see.vip");
        assertThat(loaded.visibility().distance()).isEqualTo(48);
    }

    @Test
    void roundTripsManualVisibilityMode() {
        repository.save(hologram("spawn", 1, 64, 1, "line").withVisibility(Visibility.manual()));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.visibility().mode()).isEqualTo(Visibility.Mode.MANUAL);
        assertThat(loaded.visibility().isManual()).isTrue();
        assertThat(loaded.visibility().permission()).isNull();
    }

    @Test
    void manualViewersStartEmptyAndRoundTripAddAndRemove() {
        repository.save(hologram("spawn", 1, 64, 1, "line").withVisibility(Visibility.manual()));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        HologramName spawn = HologramName.of("spawn");

        assertThat(repository.manualViewers(spawn)).isEmpty();

        repository.showTo(spawn, alice);
        repository.showTo(spawn, bob);
        assertThat(repository.manualViewers(spawn)).containsExactlyInAnyOrder(alice, bob);

        // Idempotent: showing again does not duplicate.
        repository.showTo(spawn, alice);
        assertThat(repository.manualViewers(spawn)).containsExactlyInAnyOrder(alice, bob);

        repository.hideFrom(spawn, alice);
        assertThat(repository.manualViewers(spawn)).containsExactly(bob);

        // Hiding an absent viewer is a no-op.
        repository.hideFrom(spawn, alice);
        assertThat(repository.manualViewers(spawn)).containsExactly(bob);
    }

    @Test
    void deleteRemovesTheManualViewers() {
        repository.save(hologram("spawn", 1, 64, 1, "line").withVisibility(Visibility.manual()));
        HologramName spawn = HologramName.of("spawn");
        repository.showTo(spawn, UUID.randomUUID());

        repository.delete(spawn);

        assertThat(repository.manualViewers(spawn)).isEmpty();
        // A hologram re-created under the freed name starts with no viewers, proving no orphans.
        repository.save(hologram("spawn", 1, 64, 1, "line").withVisibility(Visibility.manual()));
        assertThat(repository.manualViewers(spawn)).isEmpty();
    }

    @Test
    void aRowWithNoVisibilityColumnsReadsBackAsEveryone() {
        // A pre-V36 row: insert the name row directly leaving every visibility column NULL, plus one line.
        Holograms holograms = Holograms.HOLOGRAMS;
        HologramLines lines = HologramLines.HOLOGRAM_LINES;
        persistence.dsl().transaction(config -> {
            config.dsl()
                    .insertInto(holograms)
                    .set(holograms.NAME, "legacy")
                    .set(holograms.WORLD, WORLD.uid().toString())
                    .set(holograms.WORLD_NAME, "world")
                    .set(holograms.X, 0.0)
                    .set(holograms.Y, 64.0)
                    .set(holograms.Z, 0.0)
                    .set(holograms.YAW, 0.0f)
                    .set(holograms.PITCH, 0.0f)
                    .set(holograms.CREATED_AT, 1_000L)
                    .execute();
            config.dsl()
                    .insertInto(lines)
                    .set(lines.HOLOGRAM, "legacy")
                    .set(lines.IDX, 0)
                    .set(lines.TEXT, "line")
                    .execute();
        });

        Hologram loaded = repository.find(HologramName.of("legacy")).orElseThrow();

        assertThat(loaded.visibility()).isEqualTo(Visibility.everyone());
    }

    @Test
    void aRowWithNoAppearanceColumnsReadsBackAsDefaults() {
        // A pre-V35 row: insert the name row directly leaving every appearance column NULL, plus one line. The
        // insert runs in a transaction so it commits exactly as the repository's own save path does.
        Holograms holograms = Holograms.HOLOGRAMS;
        HologramLines lines = HologramLines.HOLOGRAM_LINES;
        persistence.dsl().transaction(config -> {
            config.dsl()
                    .insertInto(holograms)
                    .set(holograms.NAME, "legacy")
                    .set(holograms.WORLD, WORLD.uid().toString())
                    .set(holograms.WORLD_NAME, "world")
                    .set(holograms.X, 0.0)
                    .set(holograms.Y, 64.0)
                    .set(holograms.Z, 0.0)
                    .set(holograms.YAW, 0.0f)
                    .set(holograms.PITCH, 0.0f)
                    .set(holograms.CREATED_AT, 1_000L)
                    .execute();
            config.dsl()
                    .insertInto(lines)
                    .set(lines.HOLOGRAM, "legacy")
                    .set(lines.IDX, 0)
                    .set(lines.TEXT, "line")
                    .execute();
        });

        Hologram loaded = repository.find(HologramName.of("legacy")).orElseThrow();

        assertThat(loaded.appearance()).isEqualTo(Appearance.defaults());
        assertThat(loaded.refreshIntervalTicks()).isZero();
    }

    private Hologram hologram(String name, double x, double y, double z, String... lines) {
        return Hologram.create(
                HologramName.of(name),
                Position.of(WORLD, x, y, z),
                List.of(lines).stream().map(HologramLine::new).toList(),
                Instant.ofEpochMilli(1_000));
    }

    private Hologram hologramAt(String name, Instant createdAt) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), createdAt);
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
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
            return fallback;
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
}

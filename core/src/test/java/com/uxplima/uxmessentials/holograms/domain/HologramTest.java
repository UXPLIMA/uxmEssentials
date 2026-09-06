package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class HologramTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private static Hologram twoLine() {
        return Hologram.create(
                HologramName.of("spawn"),
                AT,
                List.of(new HologramLine("one"), new HologramLine("two")),
                Instant.ofEpochMilli(1_000));
    }

    @Test
    void rejectsAnEmptyLineList() {
        assertThatThrownBy(() -> Hologram.create(HologramName.of("x"), AT, List.of(), Instant.ofEpochMilli(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movedToKeepsNameLinesAndCreationTime() {
        Hologram moved = twoLine().movedTo(Position.of(WORLD, 9, 70, 9));

        assertThat(moved.name().value()).isEqualTo("spawn");
        assertThat(moved.lines()).hasSize(2);
        assertThat(moved.createdAt()).isEqualTo(Instant.ofEpochMilli(1_000));
        assertThat(moved.location().blockX()).isEqualTo(9);
    }

    @Test
    void freshHologramIsNotLinked() {
        Hologram hologram = twoLine();

        assertThat(hologram.isLinked()).isFalse();
        assertThat(hologram.linkedNpcName()).isNull();
    }

    @Test
    void linkedToStoresTheNpcNameKeepingItsStoredLocation() {
        Hologram linked = twoLine().linkedTo("guide");

        assertThat(linked.isLinked()).isTrue();
        assertThat(linked.linkedNpcName()).isEqualTo("guide");
        // The own location is untouched, so an unlink restores it.
        assertThat(linked.location()).isEqualTo(AT);
        assertThat(linked.lines()).hasSize(2);
        assertThat(linked.name().value()).isEqualTo("spawn");
    }

    @Test
    void unlinkedClearsTheNpcLink() {
        Hologram unlinked = twoLine().linkedTo("guide").unlinked();

        assertThat(unlinked.isLinked()).isFalse();
        assertThat(unlinked.linkedNpcName()).isNull();
        assertThat(unlinked.location()).isEqualTo(AT);
    }

    @Test
    void otherEditsPreserveTheNpcLink() {
        Hologram linked = twoLine().linkedTo("guide");

        assertThat(linked.withRefreshIntervalTicks(40).linkedNpcName()).isEqualTo("guide");
        assertThat(linked.withAppearance(Appearance.defaults().withScale(2f)).linkedNpcName())
                .isEqualTo("guide");
        assertThat(linked.withLineAppended(new HologramLine("three")).linkedNpcName())
                .isEqualTo("guide");
        assertThat(linked.movedTo(Position.of(WORLD, 5, 5, 5)).linkedNpcName()).isEqualTo("guide");
        assertThat(linked.renamedTo(HologramName.of("copy")).linkedNpcName()).isEqualTo("guide");
    }

    @Test
    void withLineAppendedAddsAtTheEnd() {
        Hologram three = twoLine().withLineAppended(new HologramLine("three"));

        assertThat(three.lines()).map(HologramLine::value).containsExactly("one", "two", "three");
    }

    @Test
    void withLineReplacedSwapsTheLine() {
        Hologram edited = twoLine().withLineReplaced(0, new HologramLine("first"));

        assertThat(edited.lines()).map(HologramLine::value).containsExactly("first", "two");
    }

    @Test
    void withLineReplacedRejectsAnOutOfRangeIndex() {
        assertThatThrownBy(() -> twoLine().withLineReplaced(5, new HologramLine("x")))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withLineRemovedDropsTheLine() {
        Hologram one = twoLine().withLineRemoved(0);

        assertThat(one.lines()).map(HologramLine::value).containsExactly("two");
    }

    @Test
    void withLineRemovedRefusesToEmptyTheHologram() {
        Hologram one = twoLine().withLineRemoved(0);

        assertThatThrownBy(() -> one.withLineRemoved(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hologramLineRejectsBlankText() {
        assertThatThrownBy(() -> new HologramLine("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createDefaultsToTheDefaultAppearanceAndStaticInterval() {
        Hologram hologram = twoLine();

        assertThat(hologram.appearance()).isEqualTo(Appearance.defaults());
        assertThat(hologram.visibility()).isEqualTo(Visibility.everyone());
        assertThat(hologram.refreshIntervalTicks()).isZero();
        assertThat(hologram.refreshes()).isFalse();
    }

    @Test
    void withVisibilityKeepsEverythingElseButRetargets() {
        Visibility restricted =
                Visibility.restrictedTo("uxmessentials.hologram.see.vip").withDistance(48);

        Hologram gated = twoLine().withRefreshIntervalTicks(40).withVisibility(restricted);

        assertThat(gated.visibility()).isEqualTo(restricted);
        assertThat(gated.refreshIntervalTicks()).isEqualTo(40);
        assertThat(gated.lines()).hasSize(2);
        assertThat(gated.appearance()).isEqualTo(Appearance.defaults());
        assertThat(gated.name().value()).isEqualTo("spawn");
    }

    @Test
    void withAppearanceKeepsNameLinesAndIntervalButRestyles() {
        Appearance styled = Appearance.defaults().withBillboard(Billboard.FIXED).withScale(2.0f);

        Hologram restyled = twoLine().withRefreshIntervalTicks(40).withAppearance(styled);

        assertThat(restyled.appearance()).isEqualTo(styled);
        assertThat(restyled.refreshIntervalTicks()).isEqualTo(40);
        assertThat(restyled.lines()).hasSize(2);
        assertThat(restyled.name().value()).isEqualTo("spawn");
    }

    @Test
    void withRefreshIntervalMarksTheHologramRefreshing() {
        Hologram refreshing = twoLine().withRefreshIntervalTicks(20);

        assertThat(refreshing.refreshes()).isTrue();
        assertThat(refreshing.refreshIntervalTicks()).isEqualTo(20);
    }

    @Test
    void withRefreshIntervalRejectsANegativeValue() {
        assertThatThrownBy(() -> twoLine().withRefreshIntervalTicks(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createDefaultsToTheTextType() {
        assertThat(twoLine().type()).isEqualTo(HologramType.TEXT);
        assertThat(twoLine().itemMaterial()).isNull();
        assertThat(twoLine().blockData()).isNull();
    }

    @Test
    void createItemHoldsTheMaterialAndNeedsNoLines() {
        Hologram item = Hologram.createItem(HologramName.of("loot"), AT, "DIAMOND", Instant.ofEpochMilli(1_000));

        assertThat(item.type()).isEqualTo(HologramType.ITEM);
        assertThat(item.itemMaterial()).isEqualTo("DIAMOND");
        assertThat(item.lineCount()).isZero();
        assertThat(item.blockData()).isNull();
    }

    @Test
    void createBlockHoldsTheBlockDataAndNeedsNoLines() {
        Hologram block = Hologram.createBlock(
                HologramName.of("statue"), AT, "minecraft:oak_log[axis=y]", Instant.ofEpochMilli(1_000));

        assertThat(block.type()).isEqualTo(HologramType.BLOCK);
        assertThat(block.blockData()).isEqualTo("minecraft:oak_log[axis=y]");
        assertThat(block.lineCount()).isZero();
        assertThat(block.itemMaterial()).isNull();
    }

    @Test
    void createHeadHoldsTheTextureAndNeedsNoLines() {
        Hologram head =
                Hologram.createHead(HologramName.of("greeter"), AT, "dGV4dHVyZQ==", Instant.ofEpochMilli(1_000));

        assertThat(head.type()).isEqualTo(HologramType.HEAD);
        assertThat(head.headTexture()).isEqualTo("dGV4dHVyZQ==");
        assertThat(head.lineCount()).isZero();
        assertThat(head.itemMaterial()).isNull();
    }

    @Test
    void asHeadSwitchesTypeAndContentKeepingName() {
        Hologram head = twoLine().asHead("dGV4dHVyZQ==");

        assertThat(head.type()).isEqualTo(HologramType.HEAD);
        assertThat(head.headTexture()).isEqualTo("dGV4dHVyZQ==");
    }

    @Test
    void createEntityHoldsTheTypeAndNeedsNoLines() {
        Hologram entity = Hologram.createEntity(HologramName.of("guard"), AT, "ZOMBIE", Instant.ofEpochMilli(1_000));

        assertThat(entity.type()).isEqualTo(HologramType.ENTITY);
        assertThat(entity.entityType()).isEqualTo("ZOMBIE");
        assertThat(entity.lineCount()).isZero();
        assertThat(entity.itemMaterial()).isNull();
    }

    @Test
    void asEntitySwitchesTypeAndContent() {
        Hologram entity = twoLine().asEntity("VILLAGER");

        assertThat(entity.type()).isEqualTo(HologramType.ENTITY);
        assertThat(entity.entityType()).isEqualTo("VILLAGER");
    }

    @Test
    void anEntityHologramRejectsABlankType() {
        assertThatThrownBy(() -> Hologram.createEntity(HologramName.of("x"), AT, " ", Instant.ofEpochMilli(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withLeaderboardStoresAndClears() {
        Hologram board = twoLine().withLeaderboard(new LeaderboardSpec("balance", 10));

        LeaderboardSpec spec = java.util.Objects.requireNonNull(board.leaderboard());
        assertThat(spec.providerId()).isEqualTo("balance");
        assertThat(spec.limit()).isEqualTo(10);
        assertThat(board.withLeaderboard(null).leaderboard()).isNull();
    }

    @Test
    void leaderboardSpecRejectsABadLimit() {
        assertThatThrownBy(() -> new LeaderboardSpec("balance", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LeaderboardSpec("balance", LeaderboardSpec.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LeaderboardSpec(" ", 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withLinesReplacesEveryLineKeepingTheRest() {
        Hologram one = twoLine().withRefreshIntervalTicks(40).withLines(List.of(new HologramLine("only")));

        assertThat(one.lines()).map(HologramLine::value).containsExactly("only");
        assertThat(one.refreshIntervalTicks()).isEqualTo(40);
        assertThat(one.name().value()).isEqualTo("spawn");
    }

    @Test
    void withClickCommandStoresAndClearsKeepingEverythingElse() {
        Hologram clickable = twoLine().withRefreshIntervalTicks(40).withClickCommand("warp pvp");

        assertThat(clickable.clickCommand()).isEqualTo("warp pvp");
        assertThat(clickable.lines()).hasSize(2);
        assertThat(clickable.refreshIntervalTicks()).isEqualTo(40);
        assertThat(clickable.withClickCommand(null).clickCommand()).isNull();
    }

    @Test
    void aHeadHologramRejectsABlankTexture() {
        assertThatThrownBy(() -> Hologram.createHead(HologramName.of("x"), AT, "  ", Instant.ofEpochMilli(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asItemSwitchesTypeAndContentKeepingNameAndStyling() {
        Hologram item = twoLine().withRefreshIntervalTicks(40).asItem("EMERALD");

        assertThat(item.type()).isEqualTo(HologramType.ITEM);
        assertThat(item.itemMaterial()).isEqualTo("EMERALD");
        assertThat(item.name().value()).isEqualTo("spawn");
        assertThat(item.refreshIntervalTicks()).isEqualTo(40);
    }

    @Test
    void asBlockSwitchesTypeAndContent() {
        Hologram block = twoLine().asBlock("minecraft:stone");

        assertThat(block.type()).isEqualTo(HologramType.BLOCK);
        assertThat(block.blockData()).isEqualTo("minecraft:stone");
        assertThat(block.itemMaterial()).isNull();
    }

    @Test
    void anItemHologramRejectsAMissingMaterial() {
        assertThatThrownBy(() -> Hologram.createItem(HologramName.of("x"), AT, "  ", Instant.ofEpochMilli(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBlockHologramRejectsMissingBlockData() {
        assertThatThrownBy(() -> Hologram.createBlock(HologramName.of("x"), AT, " ", Instant.ofEpochMilli(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anItemHologramMayHaveItsOnlyLineRemoved() {
        // A relaxed invariant for the non-text types: an item hologram with a label line can drop it to zero.
        Hologram item = Hologram.createItem(HologramName.of("loot"), AT, "DIAMOND", Instant.ofEpochMilli(1_000))
                .withLineAppended(new HologramLine("label"));

        Hologram bare = item.withLineRemoved(0);

        assertThat(bare.lineCount()).isZero();
        assertThat(bare.type()).isEqualTo(HologramType.ITEM);
    }

    @Test
    void aTextHologramStillRefusesToDropItsLastLine() {
        // The TEXT invariant is unchanged: only the non-text types relax it.
        Hologram one = twoLine().withLineRemoved(0);

        assertThatThrownBy(() -> one.withLineRemoved(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createDefaultsToNoRotation() {
        assertThat(twoLine().rotation()).isEqualTo(Rotation.NONE);
    }

    @Test
    void withRotationKeepsEverythingElseButReorients() {
        Hologram spun = twoLine().withRefreshIntervalTicks(40).withRotation(Rotation.of(90f, 30f));

        assertThat(spun.rotation()).isEqualTo(Rotation.of(90f, 30f));
        assertThat(spun.refreshIntervalTicks()).isEqualTo(40);
        assertThat(spun.lines()).hasSize(2);
        assertThat(spun.name().value()).isEqualTo("spawn");
        assertThat(spun.appearance()).isEqualTo(Appearance.defaults());
    }

    @Test
    void withRotationSurvivesAMoveAndAnEdit() {
        Hologram spun = twoLine().withRotation(Rotation.of(45f, 15f)).movedTo(Position.of(WORLD, 5, 5, 5));

        assertThat(spun.rotation()).isEqualTo(Rotation.of(45f, 15f));
        assertThat(spun.withLineAppended(new HologramLine("three")).rotation()).isEqualTo(Rotation.of(45f, 15f));
    }

    @Test
    void withLineInsertedPlacesTheLineBeforeTheIndex() {
        Hologram three = twoLine().withLineInserted(0, new HologramLine("zero"));

        assertThat(three.lines()).map(HologramLine::value).containsExactly("zero", "one", "two");
    }

    @Test
    void withLineInsertedBeforeTheSecondLineSplitsTheList() {
        Hologram three = twoLine().withLineInserted(1, new HologramLine("between"));

        assertThat(three.lines()).map(HologramLine::value).containsExactly("one", "between", "two");
    }

    @Test
    void withLineInsertedAtTheSizeAppends() {
        Hologram three = twoLine().withLineInserted(2, new HologramLine("three"));

        assertThat(three.lines()).map(HologramLine::value).containsExactly("one", "two", "three");
    }

    @Test
    void withLineInsertedBeyondTheSizeAlsoAppends() {
        Hologram three = twoLine().withLineInserted(99, new HologramLine("end"));

        assertThat(three.lines()).map(HologramLine::value).containsExactly("one", "two", "end");
    }

    @Test
    void withLineInsertedRejectsANegativeIndex() {
        assertThatThrownBy(() -> twoLine().withLineInserted(-1, new HologramLine("x")))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void renamedToCopiesEveryPropertyUnderTheNewName() {
        Hologram source = twoLine()
                .withRefreshIntervalTicks(40)
                .withAppearance(Appearance.defaults().withBillboard(Billboard.FIXED))
                .withVisibility(Visibility.restrictedTo("uxmessentials.hologram.see.vip"))
                .withRotation(Rotation.of(90f, 30f));

        Hologram clone = source.renamedTo(HologramName.of("copy"));

        assertThat(clone.name().value()).isEqualTo("copy");
        assertThat(clone.location()).isEqualTo(source.location());
        assertThat(clone.lines()).isEqualTo(source.lines());
        assertThat(clone.appearance()).isEqualTo(source.appearance());
        assertThat(clone.visibility()).isEqualTo(source.visibility());
        assertThat(clone.rotation()).isEqualTo(source.rotation());
        assertThat(clone.refreshIntervalTicks()).isEqualTo(source.refreshIntervalTicks());
        assertThat(clone.type()).isEqualTo(source.type());
        assertThat(clone.createdAt()).isEqualTo(source.createdAt());
    }

    @Test
    void renamedToPreservesAnItemModel() {
        Hologram source = Hologram.createItem(HologramName.of("loot"), AT, "DIAMOND", Instant.ofEpochMilli(1_000));

        Hologram clone = source.renamedTo(HologramName.of("loot-copy"));

        assertThat(clone.type()).isEqualTo(HologramType.ITEM);
        assertThat(clone.itemMaterial()).isEqualTo("DIAMOND");
        assertThat(clone.name().value()).isEqualTo("loot-copy");
    }
}

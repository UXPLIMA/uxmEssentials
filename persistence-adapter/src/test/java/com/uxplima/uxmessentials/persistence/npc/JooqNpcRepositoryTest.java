package com.uxplima.uxmessentials.persistence.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
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
 * End-to-end coverage of {@link JooqNpcRepository} against the default embedded SQLite backend with the Flyway
 * baseline applied. It proves the round-trip (save → find) of the row including a skin, a click command and the
 * look-at-player flag, the entity type (the V42 PLAYER default and a non-player value), the null skin / null
 * command path, the name-key upsert (a move/re-skin overwrites in place), the delete, the {@code exists} check,
 * and the creation-order list.
 */
class JooqNpcRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqNpcRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqNpcRepository(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsAnNpcWithSkinAndCommand() {
        repository.save(Npc.create(
                        NpcName.of("guide"),
                        Position.of(WORLD, 10, 64, 20),
                        new NpcSkin("tex", "sig"),
                        Instant.ofEpochMilli(1_000))
                .withClickCommand("warp spawn")
                .withLookAtPlayer(false));

        Optional<Npc> loaded = repository.find(NpcName.of("guide"));

        assertThat(loaded).isPresent();
        Npc reloaded = loaded.orElseThrow();
        assertThat(reloaded.location().blockX()).isEqualTo(10);
        assertThat(reloaded.location().blockZ()).isEqualTo(20);
        assertThat(reloaded.location().world().name()).isEqualTo("world");
        assertThat(reloaded.skin()).isEqualTo(new NpcSkin("tex", "sig"));
        assertThat(reloaded.clickCommand()).isEqualTo("warp spawn");
        assertThat(reloaded.lookAtPlayer()).isFalse();
    }

    @Test
    void roundTripsEquipmentAndGlow() {
        repository.save(
                Npc.create(NpcName.of("knight"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                        .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                        .withEquipment(EquipmentSlot.MAINHAND, "NETHERITE_SWORD")
                        .withGlowing(true)
                        .withGlowColor("RED"));

        Npc loaded = repository.find(NpcName.of("knight")).orElseThrow();

        assertThat(loaded.equipment())
                .containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .containsEntry(EquipmentSlot.MAINHAND, "NETHERITE_SWORD")
                .hasSize(2);
        assertThat(loaded.glowing()).isTrue();
        assertThat(loaded.glowColor()).isEqualTo("RED");
    }

    @Test
    void roundTripsALongSerializedEquipmentTokenWithoutTruncation() {
        // A serialized + base64 ItemStack token is far longer than the legacy VARCHAR(64) cap. The V45 TEXT
        // column must store it whole: this asserts the full token comes back byte-for-byte, not truncated.
        String longToken = "b64:" + "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIzNDU2Nzg5".repeat(40);
        assertThat(longToken.length()).isGreaterThan(1_000);
        repository.save(
                Npc.create(NpcName.of("knight"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                        .withEquipment(EquipmentSlot.MAINHAND, longToken));

        Npc loaded = repository.find(NpcName.of("knight")).orElseThrow();

        assertThat(loaded.equipment()).containsEntry(EquipmentSlot.MAINHAND, longToken);
    }

    @Test
    void readsALegacyMaterialNameFromTheV40Column() {
        // Simulate an NPC stored before V45: only the legacy equip_head VARCHAR column is set, the V45
        // equip_head_b64 column is NULL. The mapper's fallback must still surface the gear. The data source runs
        // with auto-commit off, so the seed insert is wrapped in a transaction to commit before the read.
        com.uxplima.uxmessentials.persistence.jooq.tables.Npc npc =
                com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;
        persistence
                .dsl()
                .transaction(cfg -> org.jooq
                        .impl
                        .DSL
                        .using(cfg)
                        .insertInto(npc)
                        .set(npc.NAME, "legacy")
                        .set(npc.WORLD, WORLD.uid().toString())
                        .set(npc.WORLD_NAME, WORLD.name())
                        .set(npc.X, 0.0)
                        .set(npc.Y, 64.0)
                        .set(npc.Z, 0.0)
                        .set(npc.YAW, 0.0f)
                        .set(npc.PITCH, 0.0f)
                        .set(npc.LOOK_AT_PLAYER, (short) 1)
                        .set(npc.EQUIP_HEAD, "DIAMOND_HELMET")
                        .set(npc.GLOWING, (short) 0)
                        .set(npc.ENTITY_TYPE, "PLAYER")
                        .set(npc.CREATED_AT, 1_000L)
                        .execute());

        Npc loaded = repository.find(NpcName.of("legacy")).orElseThrow();

        assertThat(loaded.equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
    }

    @Test
    void editingOneSlotPreservesAnotherSlotsLegacyGearAcrossTheV45Migration() {
        // The risky lifecycle: a pre-V45 NPC has its mainhand in the legacy VARCHAR column only. Editing a
        // DIFFERENT slot rewrites the whole row through the upsert, which moves mainhand's value into the new
        // equip_mainhand_b64 column (a bare material name, no b64: prefix) and clears the legacy one. The mapper
        // must still surface mainhand on the next load, so the unedited slot's gear is never lost on first save.
        com.uxplima.uxmessentials.persistence.jooq.tables.Npc npc =
                com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;
        persistence
                .dsl()
                .transaction(cfg -> org.jooq
                        .impl
                        .DSL
                        .using(cfg)
                        .insertInto(npc)
                        .set(npc.NAME, "veteran")
                        .set(npc.WORLD, WORLD.uid().toString())
                        .set(npc.WORLD_NAME, WORLD.name())
                        .set(npc.X, 0.0)
                        .set(npc.Y, 64.0)
                        .set(npc.Z, 0.0)
                        .set(npc.YAW, 0.0f)
                        .set(npc.PITCH, 0.0f)
                        .set(npc.LOOK_AT_PLAYER, (short) 1)
                        .set(npc.EQUIP_MAINHAND, "DIAMOND_SWORD")
                        .set(npc.GLOWING, (short) 0)
                        .set(npc.ENTITY_TYPE, "PLAYER")
                        .set(npc.CREATED_AT, 1_000L)
                        .execute());

        Npc legacy = repository.find(NpcName.of("veteran")).orElseThrow();
        repository.save(legacy.withEquipment(EquipmentSlot.OFFHAND, "SHIELD"));
        Npc reloaded = repository.find(NpcName.of("veteran")).orElseThrow();

        assertThat(reloaded.equipment())
                .containsEntry(EquipmentSlot.MAINHAND, "DIAMOND_SWORD")
                .containsEntry(EquipmentSlot.OFFHAND, "SHIELD")
                .hasSize(2);
    }

    @Test
    void clearingASlotWipesBothColumnsSoNoStaleGearResurfaces() {
        // Clearing a slot must null both the legacy VARCHAR and the new TEXT column. Seed a row carrying a legacy
        // head value, clear that slot, and confirm it stays empty on reload. A stale value in either column would
        // resurface through the new-then-legacy read.
        com.uxplima.uxmessentials.persistence.jooq.tables.Npc npc =
                com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;
        persistence
                .dsl()
                .transaction(cfg -> org.jooq
                        .impl
                        .DSL
                        .using(cfg)
                        .insertInto(npc)
                        .set(npc.NAME, "molting")
                        .set(npc.WORLD, WORLD.uid().toString())
                        .set(npc.WORLD_NAME, WORLD.name())
                        .set(npc.X, 0.0)
                        .set(npc.Y, 64.0)
                        .set(npc.Z, 0.0)
                        .set(npc.YAW, 0.0f)
                        .set(npc.PITCH, 0.0f)
                        .set(npc.LOOK_AT_PLAYER, (short) 1)
                        .set(npc.EQUIP_HEAD, "DIAMOND_HELMET")
                        .set(npc.GLOWING, (short) 0)
                        .set(npc.ENTITY_TYPE, "PLAYER")
                        .set(npc.CREATED_AT, 1_000L)
                        .execute());

        Npc seeded = repository.find(NpcName.of("molting")).orElseThrow();
        repository.save(seeded.withEquipment(EquipmentSlot.HEAD, null));
        Npc reloaded = repository.find(NpcName.of("molting")).orElseThrow();

        assertThat(reloaded.equipment()).doesNotContainKey(EquipmentSlot.HEAD);
        assertThat(reloaded.equipment()).isEmpty();
    }

    @Test
    void defaultsEquipmentEmptyAndGlowOffForACreatedNpc() {
        repository.save(
                Npc.create(NpcName.of("bare"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("bare")).orElseThrow();
        assertThat(loaded.equipment()).isEmpty();
        assertThat(loaded.glowing()).isFalse();
        assertThat(loaded.glowColor()).isNull();
    }

    @Test
    void roundTripsTheOwnerUuid() {
        UUID owner = UUID.randomUUID();
        repository.save(Npc.create(NpcName.of("owned"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000))
                .withOwner(owner));

        assertThat(repository.find(NpcName.of("owned")).orElseThrow().owner()).isEqualTo(owner);
    }

    @Test
    void readsAbsentOwnerAsNull() {
        repository.save(
                Npc.create(NpcName.of("ownerless"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        assertThat(repository.find(NpcName.of("ownerless")).orElseThrow().owner())
                .isNull();
    }

    @Test
    void roundTripsActionsInOrder() {
        ClickAction first = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "<green>welcome");
        ClickAction second = new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.RUN_CONSOLE, "say hi {player}");
        ClickAction third = new ClickAction(ClickTrigger.ANY, ClickActionType.SOUND, "ui.button.click:1:2");
        repository.save(Npc.create(NpcName.of("guide"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withActionAdded(first)
                .withActionAdded(second)
                .withActionAdded(third));

        Npc loaded = repository.find(NpcName.of("guide")).orElseThrow();

        assertThat(loaded.actions()).containsExactly(first, second, third);
    }

    @Test
    void replacesActionsOnSaveLeavingNoStaleRows() {
        repository.save(Npc.create(NpcName.of("guide"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "one"))
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "two")));

        ClickAction kept = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.ACTIONBAR, "only");
        repository.save(repository
                .find(NpcName.of("guide"))
                .orElseThrow()
                .withActionsCleared()
                .withActionAdded(kept));

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().actions()).containsExactly(kept);
    }

    @Test
    void defaultsActionsEmptyForACreatedNpc() {
        repository.save(
                Npc.create(NpcName.of("bare"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        assertThat(repository.find(NpcName.of("bare")).orElseThrow().actions()).isEmpty();
    }

    @Test
    void deleteRemovesTheActionRowsToo() {
        repository.save(Npc.create(NpcName.of("guide"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi")));

        repository.delete(NpcName.of("guide"));
        // Re-create under the same name; the actions must not resurface from a stale child row.
        repository.save(
                Npc.create(NpcName.of("guide"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(2_000)));

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().actions()).isEmpty();
    }

    @Test
    void roundTripsTypeData() {
        repository.save(Npc.create(NpcName.of("baby"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withEntityType("VILLAGER")
                .withTypeData("baby", "true")
                .withTypeData("villager_profession", "librarian")
                .withTypeData("villager_level", "3"));

        Npc loaded = repository.find(NpcName.of("baby")).orElseThrow();

        assertThat(loaded.typeData())
                .containsEntry("baby", "true")
                .containsEntry("villager_profession", "librarian")
                .containsEntry("villager_level", "3")
                .hasSize(3);
    }

    @Test
    void defaultsTypeDataEmptyForACreatedNpc() {
        repository.save(
                Npc.create(NpcName.of("bare"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        assertThat(repository.find(NpcName.of("bare")).orElseThrow().typeData()).isEmpty();
    }

    @Test
    void replacesTypeDataOnSaveLeavingNoStaleRows() {
        repository.save(Npc.create(NpcName.of("slime"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withEntityType("SLIME")
                .withTypeData("size", "4")
                .withTypeData("baby", "true"));

        // Clear size and overwrite baby; the stale size row must not resurface.
        repository.save(repository
                .find(NpcName.of("slime"))
                .orElseThrow()
                .withTypeData("size", null)
                .withTypeData("baby", "false"));

        Npc reloaded = repository.find(NpcName.of("slime")).orElseThrow();
        assertThat(reloaded.typeData())
                .containsEntry("baby", "false")
                .doesNotContainKey("size")
                .hasSize(1);
    }

    @Test
    void deleteRemovesTheTypeDataRowsToo() {
        repository.save(Npc.create(NpcName.of("creep"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withEntityType("CREEPER")
                .withTypeData("charged", "true"));

        repository.delete(NpcName.of("creep"));
        // Re-create under the same name; the type data must not resurface from a stale child row.
        repository.save(
                Npc.create(NpcName.of("creep"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(2_000)));

        assertThat(repository.find(NpcName.of("creep")).orElseThrow().typeData())
                .isEmpty();
    }

    @Test
    void defaultsEntityTypeToPlayerForACreatedNpc() {
        repository.save(
                Npc.create(NpcName.of("plain"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("plain")).orElseThrow();
        assertThat(loaded.entityType()).isEqualTo("PLAYER");
        assertThat(loaded.isPlayerType()).isTrue();
    }

    @Test
    void roundTripsANonPlayerEntityType() {
        repository.save(Npc.create(NpcName.of("mob"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withEntityType("VILLAGER"));

        Npc loaded = repository.find(NpcName.of("mob")).orElseThrow();

        assertThat(loaded.entityType()).isEqualTo("VILLAGER");
        assertThat(loaded.isPlayerType()).isFalse();
        // An upsert to a different type overwrites the stored value in place (the name-key upsert path).
        repository.save(loaded.withEntityType("ZOMBIE"));
        assertThat(repository.find(NpcName.of("mob")).orElseThrow().entityType())
                .isEqualTo("ZOMBIE");
    }

    @Test
    void defaultsPoseAndScaleForACreatedNpc() {
        repository.save(
                Npc.create(NpcName.of("plain"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("plain")).orElseThrow();
        assertThat(loaded.pose()).isEqualTo("STANDING");
        assertThat(loaded.scale()).isEqualTo(1.0);
    }

    @Test
    void roundTripsANonDefaultPoseAndScale() {
        repository.save(
                Npc.create(NpcName.of("statue"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                        .withPose("SITTING")
                        .withScale(2.5));

        Npc loaded = repository.find(NpcName.of("statue")).orElseThrow();

        assertThat(loaded.pose()).isEqualTo("SITTING");
        assertThat(loaded.scale()).isEqualTo(2.5f); // REAL storage round-trips through a float
        // An upsert to a new pose/scale overwrites the stored value in place (the name-key upsert path).
        repository.save(loaded.withPose("SLEEPING").withScale(0.5));
        Npc reloaded = repository.find(NpcName.of("statue")).orElseThrow();
        assertThat(reloaded.pose()).isEqualTo("SLEEPING");
        assertThat(reloaded.scale()).isEqualTo(0.5f);
    }

    @Test
    void reSavingAfterEditingOneFieldKeepsEveryOtherUpsertColumn() {
        // The upsert's doUpdate() set list must overwrite every column the insert writes; a column present on
        // insert but missing from doUpdate would be dropped on the second save (the pose/scale bug caught during
        // implementation). This seeds an NPC carrying a value in every optional column, re-saves it editing only a
        // single unrelated field (the position), and asserts every other field still round-trips, so any column
        // omitted from the update set surfaces here as a lost value rather than in production.
        repository.save(Npc.create(
                        NpcName.of("decked"),
                        Position.of(WORLD, 1, 64, 1),
                        new NpcSkin("tex", "sig"),
                        Instant.ofEpochMilli(1_000))
                .withClickCommand("warp spawn")
                .withLookAtPlayer(false)
                .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withEquipment(EquipmentSlot.MAINHAND, "NETHERITE_SWORD")
                .withGlowing(true)
                .withGlowColor("RED")
                .withEntityType("VILLAGER")
                .withPose("SITTING")
                .withScale(2.5)
                .withTypeData("baby", "true")
                .withTypeData("villager_profession", "librarian")
                .withDisplayName("<gold>Town Guide")
                .withMirrorSkin(true)
                .withCollidable(true)
                .withShowInTab(true)
                .withOnFire(true)
                .withInvisible(true)
                .withSilent(true)
                .withViewDistance(80.0)
                .withTurnDistance(20.0)
                .withInteractionCooldownMillis(2_000));

        // Re-save touching only the location; every other column goes through doUpdate() unchanged.
        repository.save(repository.find(NpcName.of("decked")).orElseThrow().movedTo(Position.of(WORLD, 100, 70, 100)));
        Npc reloaded = repository.find(NpcName.of("decked")).orElseThrow();

        assertThat(reloaded.location().blockX()).isEqualTo(100);
        assertThat(reloaded.skin()).isEqualTo(new NpcSkin("tex", "sig"));
        assertThat(reloaded.clickCommand()).isEqualTo("warp spawn");
        assertThat(reloaded.lookAtPlayer()).isFalse();
        assertThat(reloaded.equipment())
                .containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .containsEntry(EquipmentSlot.MAINHAND, "NETHERITE_SWORD")
                .hasSize(2);
        assertThat(reloaded.glowing()).isTrue();
        assertThat(reloaded.glowColor()).isEqualTo("RED");
        assertThat(reloaded.entityType()).isEqualTo("VILLAGER");
        assertThat(reloaded.pose()).isEqualTo("SITTING");
        assertThat(reloaded.scale()).isEqualTo(2.5f);
        assertThat(reloaded.typeData()).containsEntry("baby", "true").containsEntry("villager_profession", "librarian");
        assertThat(reloaded.displayName()).isEqualTo("<gold>Town Guide");
        assertThat(reloaded.mirrorSkin()).isTrue();
        assertThat(reloaded.collidable()).isTrue();
        assertThat(reloaded.showInTab()).isTrue();
        assertThat(reloaded.onFire()).isTrue();
        assertThat(reloaded.invisible()).isTrue();
        assertThat(reloaded.silent()).isTrue();
        assertThat(reloaded.viewDistance()).isEqualTo(80.0);
        assertThat(reloaded.turnDistance()).isEqualTo(20.0);
        assertThat(reloaded.interactionCooldownMillis()).isEqualTo(2_000);
        assertThat(reloaded.createdAt()).isEqualTo(Instant.ofEpochMilli(1_000));
    }

    @Test
    void defaultsTheExtraAppearanceFieldsForACreatedNpc() {
        repository.save(
                Npc.create(NpcName.of("plain"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("plain")).orElseThrow();
        assertThat(loaded.displayName()).isNull();
        assertThat(loaded.mirrorSkin()).isFalse();
        assertThat(loaded.collidable()).isFalse();
        assertThat(loaded.showInTab()).isFalse();
        assertThat(loaded.onFire()).isFalse();
        assertThat(loaded.invisible()).isFalse();
        assertThat(loaded.silent()).isFalse();
        assertThat(loaded.viewDistance()).isNull();
        assertThat(loaded.turnDistance()).isNull();
        assertThat(loaded.interactionCooldownMillis()).isZero();
    }

    @Test
    void roundTripsASlimSkinVariant() {
        repository.save(Npc.create(
                NpcName.of("alex"),
                Position.of(WORLD, 1, 64, 1),
                new NpcSkin("tex", "sig", true),
                Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("alex")).orElseThrow();

        NpcSkin skin = java.util.Objects.requireNonNull(loaded.skin(), "skin");
        assertThat(skin).isEqualTo(new NpcSkin("tex", "sig", true));
        assertThat(skin.slim()).isTrue();
    }

    @Test
    void roundTripsTheExtraAppearanceFields() {
        repository.save(Npc.create(NpcName.of("rich"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000))
                .withDisplayName("<aqua>Greeter")
                .withMirrorSkin(true)
                .withCollidable(true)
                .withShowInTab(true)
                .withOnFire(true)
                .withInvisible(true)
                .withSilent(true)
                .withViewDistance(64.0)
                .withTurnDistance(10.0)
                .withInteractionCooldownMillis(1_500));

        Npc loaded = repository.find(NpcName.of("rich")).orElseThrow();

        assertThat(loaded.displayName()).isEqualTo("<aqua>Greeter");
        assertThat(loaded.mirrorSkin()).isTrue();
        assertThat(loaded.collidable()).isTrue();
        assertThat(loaded.showInTab()).isTrue();
        assertThat(loaded.onFire()).isTrue();
        assertThat(loaded.invisible()).isTrue();
        assertThat(loaded.silent()).isTrue();
        assertThat(loaded.viewDistance()).isEqualTo(64.0);
        assertThat(loaded.turnDistance()).isEqualTo(10.0);
        assertThat(loaded.interactionCooldownMillis()).isEqualTo(1_500);

        // An upsert clearing the overrides nulls the distance columns and resets the flags in place.
        repository.save(loaded.withViewDistance(null).withTurnDistance(null).withDisplayName(null));
        Npc cleared = repository.find(NpcName.of("rich")).orElseThrow();
        assertThat(cleared.viewDistance()).isNull();
        assertThat(cleared.turnDistance()).isNull();
        assertThat(cleared.displayName()).isNull();
    }

    @Test
    void readsTheV48DefaultsForALegacyRowWithoutTheNewColumnsSet() {
        // Simulate an NPC stored before V48: the row is inserted without the new columns, so the V48 NOT NULL
        // DEFAULTs (0 flags, 0 cooldown) must apply and the nullable display/distance columns read back NULL.
        com.uxplima.uxmessentials.persistence.jooq.tables.Npc npc =
                com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;
        persistence
                .dsl()
                .transaction(cfg -> org.jooq
                        .impl
                        .DSL
                        .using(cfg)
                        .insertInto(npc)
                        .set(npc.NAME, "ancient")
                        .set(npc.WORLD, WORLD.uid().toString())
                        .set(npc.WORLD_NAME, WORLD.name())
                        .set(npc.X, 0.0)
                        .set(npc.Y, 64.0)
                        .set(npc.Z, 0.0)
                        .set(npc.YAW, 0.0f)
                        .set(npc.PITCH, 0.0f)
                        .set(npc.LOOK_AT_PLAYER, (short) 1)
                        .set(npc.GLOWING, (short) 0)
                        .set(npc.ENTITY_TYPE, "PLAYER")
                        .set(npc.CREATED_AT, 1_000L)
                        .execute());

        Npc loaded = repository.find(NpcName.of("ancient")).orElseThrow();

        assertThat(loaded.displayName()).isNull();
        assertThat(loaded.mirrorSkin()).isFalse();
        assertThat(loaded.collidable()).isFalse();
        assertThat(loaded.showInTab()).isFalse();
        assertThat(loaded.onFire()).isFalse();
        assertThat(loaded.invisible()).isFalse();
        assertThat(loaded.silent()).isFalse();
        assertThat(loaded.viewDistance()).isNull();
        assertThat(loaded.turnDistance()).isNull();
        assertThat(loaded.interactionCooldownMillis()).isZero();
        assertThat(loaded.skin()).isNull();
    }

    @Test
    void readsTheV46DefaultsForALegacyRowWithoutPoseOrScaleSet() {
        // Simulate an NPC stored before V46: the row is inserted without the pose/scale columns, so the V46
        // NOT NULL DEFAULTs (STANDING / 1.0) must apply and the mapper must read them back correctly. The data
        // source runs with auto-commit off, so the seed insert is wrapped in a transaction to commit before the read.
        com.uxplima.uxmessentials.persistence.jooq.tables.Npc npc =
                com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;
        persistence
                .dsl()
                .transaction(cfg -> org.jooq
                        .impl
                        .DSL
                        .using(cfg)
                        .insertInto(npc)
                        .set(npc.NAME, "ancient")
                        .set(npc.WORLD, WORLD.uid().toString())
                        .set(npc.WORLD_NAME, WORLD.name())
                        .set(npc.X, 0.0)
                        .set(npc.Y, 64.0)
                        .set(npc.Z, 0.0)
                        .set(npc.YAW, 0.0f)
                        .set(npc.PITCH, 0.0f)
                        .set(npc.LOOK_AT_PLAYER, (short) 1)
                        .set(npc.GLOWING, (short) 0)
                        .set(npc.ENTITY_TYPE, "PLAYER")
                        .set(npc.CREATED_AT, 1_000L)
                        .execute());

        Npc loaded = repository.find(NpcName.of("ancient")).orElseThrow();

        assertThat(loaded.pose()).isEqualTo("STANDING");
        assertThat(loaded.scale()).isEqualTo(1.0f);
    }

    @Test
    void defaultsLookAtPlayerToTrueForACreatedNpc() {
        repository.save(
                Npc.create(NpcName.of("plain"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        assertThat(repository.find(NpcName.of("plain")).orElseThrow().lookAtPlayer())
                .isTrue();
    }

    @Test
    void savesAndFindsAnNpcWithNoSkinOrCommand() {
        repository.save(
                Npc.create(NpcName.of("plain"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("plain")).orElseThrow();

        assertThat(loaded.skin()).isNull();
        assertThat(loaded.clickCommand()).isNull();
        assertThat(loaded.hasSkin()).isFalse();
    }

    @Test
    void roundTripsAnUnsignedSkin() {
        repository.save(Npc.create(
                NpcName.of("guide"),
                Position.of(WORLD, 1, 64, 1),
                NpcSkin.unsigned("tex"),
                Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("guide")).orElseThrow();

        assertThat(loaded.skin()).isEqualTo(NpcSkin.unsigned("tex"));
    }

    @Test
    void saveUpsertsOnTheNameKey() {
        repository.save(npc("guide", 0, 0, 0));
        repository.save(Npc.create(
                        NpcName.of("guide"),
                        Position.of(WORLD, 100, 70, 100),
                        new NpcSkin("tex2", null),
                        Instant.ofEpochMilli(1_000))
                .withClickCommand("spawn"));

        assertThat(repository.all()).hasSize(1);
        Npc updated = repository.find(NpcName.of("guide")).orElseThrow();
        assertThat(updated.location().blockX()).isEqualTo(100);
        assertThat(updated.skin()).isEqualTo(new NpcSkin("tex2", null));
        assertThat(updated.clickCommand()).isEqualTo("spawn");
    }

    @Test
    void existsReflectsWhetherAnNpcIsStored() {
        assertThat(repository.exists(NpcName.of("guide"))).isFalse();

        repository.save(npc("guide", 0, 0, 0));

        assertThat(repository.exists(NpcName.of("guide"))).isTrue();
    }

    @Test
    void deleteRemovesTheRow() {
        repository.save(npc("guide", 0, 0, 0));
        repository.save(npc("shop", 1, 1, 1));

        repository.delete(NpcName.of("guide"));

        assertThat(repository.exists(NpcName.of("guide"))).isFalse();
        assertThat(repository.all()).hasSize(1);
    }

    @Test
    void allPreservesCreationOrder() {
        repository.save(npcAt("first", Instant.ofEpochMilli(1_000)));
        repository.save(npcAt("second", Instant.ofEpochMilli(2_000)));
        repository.save(npcAt("third", Instant.ofEpochMilli(3_000)));

        assertThat(repository.all().stream().map(n -> n.name().value())).containsExactly("first", "second", "third");
    }

    private Npc npc(String name, double x, double y, double z) {
        return Npc.create(NpcName.of(name), Position.of(WORLD, x, y, z), null, Instant.ofEpochMilli(1_000));
    }

    private Npc npcAt(String name, Instant createdAt) {
        return Npc.create(NpcName.of(name), Position.of(WORLD, 0, 64, 0), null, createdAt);
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

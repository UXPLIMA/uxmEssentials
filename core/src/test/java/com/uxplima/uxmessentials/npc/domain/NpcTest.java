package com.uxplima.uxmessentials.npc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.junit.jupiter.api.Test;

class NpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final Position ELSEWHERE = Position.of(WORLD, 9, 70, 9);
    private static final Instant CREATED = Instant.ofEpochMilli(1_000);

    @Test
    void createsAnNpcWithNoSkinAndNoCommand() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.name()).isEqualTo(NpcName.of("guide"));
        assertThat(npc.location()).isEqualTo(AT);
        assertThat(npc.hasSkin()).isFalse();
        assertThat(npc.hasClickCommand()).isFalse();
        assertThat(npc.lookAtPlayer()).isTrue();
        assertThat(npc.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void withLookAtPlayerTogglesAndKeepsEverythingElse() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn");

        Npc unlooking = npc.withLookAtPlayer(false);
        assertThat(unlooking.lookAtPlayer()).isFalse();
        assertThat(unlooking.skin()).isEqualTo(NpcSkin.unsigned("tex"));
        assertThat(unlooking.clickCommand()).isEqualTo("spawn");
        assertThat(unlooking.createdAt()).isEqualTo(CREATED);

        assertThat(unlooking.withLookAtPlayer(true).lookAtPlayer()).isTrue();
    }

    @Test
    void movedToReanchorsAndKeepsEverythingElse() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withLookAtPlayer(false);

        Npc moved = npc.movedTo(ELSEWHERE);

        assertThat(moved.location()).isEqualTo(ELSEWHERE);
        assertThat(moved.skin()).isEqualTo(NpcSkin.unsigned("tex"));
        assertThat(moved.clickCommand()).isEqualTo("spawn");
        assertThat(moved.lookAtPlayer()).isFalse();
        assertThat(moved.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void withSkinReplacesTheSkinAndCanClearIt() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        Npc skinned = npc.withSkin(new NpcSkin("tex", "sig"));
        assertThat(skinned.hasSkin()).isTrue();
        assertThat(skinned.skin()).isEqualTo(new NpcSkin("tex", "sig"));

        assertThat(skinned.withSkin(null).hasSkin()).isFalse();
    }

    @Test
    void withClickCommandBindsAndClears() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        Npc bound = npc.withClickCommand("warp spawn");
        assertThat(bound.hasClickCommand()).isTrue();
        assertThat(bound.clickCommand()).isEqualTo("warp spawn");

        assertThat(bound.withClickCommand(null).hasClickCommand()).isFalse();
    }

    @Test
    void createsWithNoEquipmentAndNoGlow() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.equipment()).isEmpty();
        assertThat(npc.hasEquipment()).isFalse();
        assertThat(npc.glowing()).isFalse();
        assertThat(npc.glowColor()).isNull();
        assertThat(npc.hasGlowColor()).isFalse();
    }

    @Test
    void withEquipmentSetsAndClearsASlotKeepingTheRest() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withEquipment(EquipmentSlot.MAINHAND, "STICK");

        assertThat(npc.equipment())
                .containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .containsEntry(EquipmentSlot.MAINHAND, "STICK")
                .hasSize(2);
        assertThat(npc.hasEquipment()).isTrue();

        Npc cleared = npc.withEquipment(EquipmentSlot.HEAD, null);
        assertThat(cleared.equipment()).doesNotContainKey(EquipmentSlot.HEAD).hasSize(1);
    }

    @Test
    void withEquipmentStoresAnOpaqueTokenVerbatim() {
        // The domain never interprets the equipment value. A serialized-item token is stored and returned
        // byte-for-byte, exactly as a material name would be.
        String token = "b64:rO0ABXNyAB...some-opaque-serialized-payload==";
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withEquipment(EquipmentSlot.MAINHAND, token);

        assertThat(npc.equipment()).containsEntry(EquipmentSlot.MAINHAND, token);
    }

    @Test
    void equipmentMapIsImmutable() {
        Npc npc =
                Npc.create(NpcName.of("guide"), AT, null, CREATED).withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.equipment().put(EquipmentSlot.FEET, "BOOTS"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withGlowingAndColorToggleAndTintKeepingTheRest() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withGlowing(true)
                .withGlowColor("RED");

        assertThat(npc.glowing()).isTrue();
        assertThat(npc.glowColor()).isEqualTo("RED");
        assertThat(npc.hasGlowColor()).isTrue();
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));

        Npc cleared = npc.withGlowColor(null);
        assertThat(cleared.hasGlowColor()).isFalse();
        assertThat(cleared.glowing()).isTrue();
        assertThat(npc.withGlowing(false).glowing()).isFalse();
    }

    @Test
    void movedToAndWithSkinPreserveEquipmentAndGlow() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withGlowing(true)
                .withGlowColor("AQUA");

        Npc moved = npc.movedTo(ELSEWHERE).withSkin(NpcSkin.unsigned("tex"));

        assertThat(moved.equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        assertThat(moved.glowing()).isTrue();
        assertThat(moved.glowColor()).isEqualTo("AQUA");
    }

    @Test
    void everyTransitionPreservesEquipmentAndGlow() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withGlowing(true)
                .withGlowColor("RED");
        ClickAction action = new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi");

        assertThat(npc.movedTo(ELSEWHERE).equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).glowColor()).isEqualTo("RED");
        assertThat(npc.withClickCommand("spawn").glowing()).isTrue();
        assertThat(npc.withLookAtPlayer(false).equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        assertThat(npc.withEntityType("VILLAGER").glowColor()).isEqualTo("RED");
        assertThat(npc.withPose("SITTING").equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        assertThat(npc.withScale(2.0).glowing()).isTrue();
        assertThat(npc.withTypeData("baby", "true").glowColor()).isEqualTo("RED");
        assertThat(npc.withActionAdded(action).equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        Npc withTwo = npc.withActionAdded(action).withActionAdded(action);
        assertThat(withTwo.withActionRemovedAt(0).glowing()).isTrue();
        assertThat(withTwo.withActionsCleared().equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        // Setting one half of the gear/glow trio preserves the other two.
        assertThat(npc.withEquipment(EquipmentSlot.MAINHAND, "STICK").glowColor())
                .isEqualTo("RED");
        assertThat(npc.withGlowing(false).equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        assertThat(npc.withGlowColor("AQUA").equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
    }

    @Test
    void createsWithNoActions() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.actions()).isEmpty();
        assertThat(npc.hasActions()).isFalse();
    }

    @Test
    void withActionAddedAppendsInOrderKeepingTheRest() {
        ClickAction first = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "hi");
        ClickAction second = new ClickAction(ClickTrigger.ANY, ClickActionType.SOUND, "minecraft:ui.button.click");
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withActionAdded(first)
                .withActionAdded(second);

        assertThat(npc.actions()).containsExactly(first, second);
        assertThat(npc.hasActions()).isTrue();
        assertThat(npc.clickCommand()).isEqualTo("spawn");
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));
    }

    @Test
    void withActionRemovedAtDropsTheChosenOne() {
        ClickAction first = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "hi");
        ClickAction second = new ClickAction(ClickTrigger.ANY, ClickActionType.ACTIONBAR, "bye");
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(first)
                .withActionAdded(second);

        assertThat(npc.withActionRemovedAt(0).actions()).containsExactly(second);
        assertThat(npc.withActionRemovedAt(1).actions()).containsExactly(first);
    }

    @Test
    void withActionRemovedAtRejectsAnOutOfRangeIndex() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withActionRemovedAt(1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withActionRemovedAt(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withActionsClearedEmptiesTheList() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi"))
                .withActionsCleared();

        assertThat(npc.actions()).isEmpty();
        assertThat(npc.hasActions()).isFalse();
    }

    @Test
    void actionsListIsImmutable() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> npc.actions().add(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyTransitionPreservesActions() {
        ClickAction action = new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi");
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withActionAdded(action);

        assertThat(npc.movedTo(ELSEWHERE).actions()).containsExactly(action);
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).actions()).containsExactly(action);
        assertThat(npc.withClickCommand("spawn").actions()).containsExactly(action);
        assertThat(npc.withLookAtPlayer(false).actions()).containsExactly(action);
        assertThat(npc.withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET").actions())
                .containsExactly(action);
        assertThat(npc.withGlowing(true).actions()).containsExactly(action);
        assertThat(npc.withGlowColor("RED").actions()).containsExactly(action);
        assertThat(npc.withEntityType("VILLAGER").actions()).containsExactly(action);
        assertThat(npc.withPose("SITTING").actions()).containsExactly(action);
        assertThat(npc.withScale(2.0).actions()).containsExactly(action);
    }

    @Test
    void createsWithTheDefaultPoseAndScale() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.pose()).isEqualTo("STANDING");
        assertThat(npc.hasPose()).isFalse();
        assertThat(npc.scale()).isEqualTo(1.0);
        assertThat(npc.hasScale()).isFalse();
    }

    @Test
    void withPoseUpperCasesAndKeepsEverythingElse() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withPose("sitting");

        assertThat(npc.pose()).isEqualTo("SITTING");
        assertThat(npc.hasPose()).isTrue();
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));
        assertThat(npc.clickCommand()).isEqualTo("spawn");
    }

    @Test
    void withScaleResizesAndKeepsEverythingElse() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withScale(2.5);

        assertThat(npc.scale()).isEqualTo(2.5);
        assertThat(npc.hasScale()).isTrue();
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));
        assertThat(npc.clickCommand()).isEqualTo("spawn");
    }

    @Test
    void rejectsABlankPoseAtConstruction() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Npc.create(NpcName.of("guide"), AT, null, CREATED).withPose(" "))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Npc.create(NpcName.of("guide"), AT, null, CREATED).withPose(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonFiniteOrNonPositiveScale() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withScale(0.0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withScale(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withScale(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withScale(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTransitionPreservesPoseAndScale() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withPose("SITTING")
                .withScale(2.5);
        ClickAction action = new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi");

        assertThat(npc.movedTo(ELSEWHERE).pose()).isEqualTo("SITTING");
        assertThat(npc.movedTo(ELSEWHERE).scale()).isEqualTo(2.5);
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).pose()).isEqualTo("SITTING");
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).scale()).isEqualTo(2.5);
        assertThat(npc.withClickCommand("spawn").pose()).isEqualTo("SITTING");
        assertThat(npc.withLookAtPlayer(false).scale()).isEqualTo(2.5);
        assertThat(npc.withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET").pose())
                .isEqualTo("SITTING");
        assertThat(npc.withGlowing(true).scale()).isEqualTo(2.5);
        assertThat(npc.withGlowColor("RED").pose()).isEqualTo("SITTING");
        assertThat(npc.withEntityType("VILLAGER").scale()).isEqualTo(2.5);
        assertThat(npc.withActionAdded(action).pose()).isEqualTo("SITTING");
        Npc withTwo = npc.withActionAdded(action).withActionAdded(action);
        assertThat(withTwo.withActionRemovedAt(0).scale()).isEqualTo(2.5);
        assertThat(withTwo.withActionsCleared().pose()).isEqualTo("SITTING");
        // The pose/scale transitions preserve each other.
        assertThat(npc.withScale(3.0).pose()).isEqualTo("SITTING");
        assertThat(npc.withPose("SLEEPING").scale()).isEqualTo(2.5);
    }

    @Test
    void createsWithNoTypeData() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.typeData()).isEmpty();
        assertThat(npc.hasTypeData()).isFalse();
    }

    @Test
    void withTypeDataAddsReplacesAndRemovesAKeyKeepingTheRest() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withTypeData("baby", "true")
                .withTypeData("size", "4");

        assertThat(npc.typeData())
                .containsEntry("baby", "true")
                .containsEntry("size", "4")
                .hasSize(2);
        assertThat(npc.hasTypeData()).isTrue();
        assertThat(npc.clickCommand()).isEqualTo("spawn");
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));

        // Replacing a key keeps the value the latest write set.
        Npc replaced = npc.withTypeData("baby", "false");
        assertThat(replaced.typeData()).containsEntry("baby", "false").containsEntry("size", "4");

        // A null or blank value removes the key.
        Npc removedNull = npc.withTypeData("baby", null);
        assertThat(removedNull.typeData()).doesNotContainKey("baby").containsEntry("size", "4");
        Npc removedBlank = npc.withTypeData("size", " ");
        assertThat(removedBlank.typeData()).doesNotContainKey("size").containsEntry("baby", "true");
    }

    @Test
    void typeDataMapIsImmutable() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withTypeData("baby", "true");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.typeData().put("size", "4"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withTypeDataRejectsABlankKey() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withTypeData(" ", "true"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withTypeData("", "true"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTransitionPreservesTypeData() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withTypeData("baby", "true");
        ClickAction action = new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi");

        assertThat(npc.movedTo(ELSEWHERE).typeData()).containsEntry("baby", "true");
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).typeData()).containsEntry("baby", "true");
        assertThat(npc.withClickCommand("spawn").typeData()).containsEntry("baby", "true");
        assertThat(npc.withLookAtPlayer(false).typeData()).containsEntry("baby", "true");
        assertThat(npc.withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET").typeData())
                .containsEntry("baby", "true");
        assertThat(npc.withGlowing(true).typeData()).containsEntry("baby", "true");
        assertThat(npc.withGlowColor("RED").typeData()).containsEntry("baby", "true");
        assertThat(npc.withEntityType("VILLAGER").typeData()).containsEntry("baby", "true");
        assertThat(npc.withPose("SITTING").typeData()).containsEntry("baby", "true");
        assertThat(npc.withScale(2.0).typeData()).containsEntry("baby", "true");
        assertThat(npc.withActionAdded(action).typeData()).containsEntry("baby", "true");
        Npc withTwo = npc.withActionAdded(action).withActionAdded(action);
        assertThat(withTwo.withActionRemovedAt(0).typeData()).containsEntry("baby", "true");
        assertThat(withTwo.withActionsCleared().typeData()).containsEntry("baby", "true");
    }

    @Test
    void createsAsAPlayerTypeByDefault() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.entityType()).isEqualTo("PLAYER");
        assertThat(npc.isPlayerType()).isTrue();
    }

    @Test
    void withEntityTypeUpperCasesAndSetsPlayerTypeFalseForAMob() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withEntityType("villager");

        assertThat(npc.entityType()).isEqualTo("VILLAGER");
        assertThat(npc.isPlayerType()).isFalse();
        // The skin is preserved across a type flip so switching back to PLAYER restores it.
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));

        Npc backToPlayer = npc.withEntityType("PLAYER");
        assertThat(backToPlayer.isPlayerType()).isTrue();
        assertThat(backToPlayer.skin()).isEqualTo(NpcSkin.unsigned("tex"));
    }

    @Test
    void rejectsABlankEntityTypeAtConstruction() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Npc.create(NpcName.of("guide"), AT, null, CREATED).withEntityType(" "))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Npc.create(NpcName.of("guide"), AT, null, CREATED).withEntityType(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTransitionPreservesEntityType() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withEntityType("ZOMBIE");
        ClickAction action = new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi");

        assertThat(npc.movedTo(ELSEWHERE).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withClickCommand("spawn").entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withLookAtPlayer(false).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET").entityType())
                .isEqualTo("ZOMBIE");
        assertThat(npc.withGlowing(true).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withGlowColor("RED").entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withActionAdded(action).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withPose("SITTING").entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withScale(2.0).entityType()).isEqualTo("ZOMBIE");
        Npc withTwo = npc.withActionAdded(action).withActionAdded(action);
        assertThat(withTwo.withActionRemovedAt(0).entityType()).isEqualTo("ZOMBIE");
        assertThat(withTwo.withActionsCleared().entityType()).isEqualTo("ZOMBIE");
    }

    @Test
    void createsWithTheExpectedAppearanceDefaults() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.displayName()).isNull();
        assertThat(npc.hasDisplayName()).isFalse();
        assertThat(npc.displayNameHidden()).isFalse();
        assertThat(npc.mirrorSkin()).isFalse();
        assertThat(npc.collidable()).isFalse();
        assertThat(npc.showInTab()).isFalse();
        assertThat(npc.viewDistance()).isNull();
        assertThat(npc.turnDistance()).isNull();
        assertThat(npc.onFire()).isFalse();
        assertThat(npc.invisible()).isFalse();
        assertThat(npc.silent()).isFalse();
        assertThat(npc.interactionCooldownMillis()).isZero();
    }

    @Test
    void withDisplayNameSetsAndHidesTheLabelKeepingTheRest() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withDisplayName("<gold>Town Guide");

        assertThat(npc.displayName()).isEqualTo("<gold>Town Guide");
        assertThat(npc.hasDisplayName()).isTrue();
        assertThat(npc.displayNameHidden()).isFalse();
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));
        assertThat(npc.clickCommand()).isEqualTo("spawn");

        // A blank display name hides the label: it is stored as the " " sentinel, which reads as hidden rather
        // than as "no display name set": the two are different render outcomes (no nametag vs the id).
        assertThat(npc.withDisplayName(" ").displayName()).isEqualTo(" ");
        assertThat(npc.withDisplayName(" ").displayNameHidden()).isTrue();
        assertThat(npc.withDisplayName(" ").hasDisplayName()).isFalse();

        // The clear words every command and GUI surface accepts all reach the same hidden state.
        for (String cleared : new String[] {"-", "none", "NONE", "clear", "empty", ""}) {
            assertThat(npc.withDisplayName(cleared).displayNameHidden())
                    .as("display name %s hides the label", cleared)
                    .isTrue();
        }

        // Unset is not hidden: it falls back to rendering the id, which is the default.
        assertThat(npc.withDisplayName(null).hasDisplayName()).isFalse();
        assertThat(npc.withDisplayName(null).displayNameHidden()).isFalse();
        assertThat(npc.withDisplayName("reset").displayName()).isNull();
        assertThat(npc.withDisplayName("default").displayNameHidden()).isFalse();
    }

    @Test
    void togglesTheStateFlagsIndependently() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withOnFire(true)
                .withInvisible(true)
                .withSilent(true);

        assertThat(npc.onFire()).isTrue();
        assertThat(npc.invisible()).isTrue();
        assertThat(npc.silent()).isTrue();

        // Clearing one flag leaves the others set: they do not share state.
        Npc notOnFire = npc.withOnFire(false);
        assertThat(notOnFire.onFire()).isFalse();
        assertThat(notOnFire.invisible()).isTrue();
        assertThat(notOnFire.silent()).isTrue();
    }

    @Test
    void togglesMirrorCollidableAndShowInTab() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withMirrorSkin(true)
                .withCollidable(true)
                .withShowInTab(true);

        assertThat(npc.mirrorSkin()).isTrue();
        assertThat(npc.collidable()).isTrue();
        assertThat(npc.showInTab()).isTrue();

        assertThat(npc.withMirrorSkin(false).mirrorSkin()).isFalse();
        assertThat(npc.withCollidable(false).collidable()).isFalse();
        assertThat(npc.withShowInTab(false).showInTab()).isFalse();
    }

    @Test
    void setsAndClearsThePerNpcDistanceOverrides() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withViewDistance(80.0)
                .withTurnDistance(20.0);

        assertThat(npc.viewDistance()).isEqualTo(80.0);
        assertThat(npc.turnDistance()).isEqualTo(20.0);

        // A null override falls back to the module default.
        assertThat(npc.withViewDistance(null).viewDistance()).isNull();
        assertThat(npc.withTurnDistance(null).turnDistance()).isNull();
    }

    @Test
    void rejectsANegativeOrNonFiniteDistanceOverride() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withViewDistance(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withTurnDistance(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setsAndResetsThePerNpcInteractionCooldown() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withInteractionCooldownMillis(2_000);

        assertThat(npc.interactionCooldownMillis()).isEqualTo(2_000);

        // Zero resets to the module-wide default.
        assertThat(npc.withInteractionCooldownMillis(0).interactionCooldownMillis())
                .isZero();
    }

    @Test
    void rejectsANegativeInteractionCooldown() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withInteractionCooldownMillis(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTransitionPreservesTheNewFields() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withDisplayName("Guide")
                .withMirrorSkin(true)
                .withCollidable(true)
                .withShowInTab(true)
                .withViewDistance(80.0)
                .withTurnDistance(20.0)
                .withOnFire(true)
                .withInvisible(true)
                .withSilent(true)
                .withInteractionCooldownMillis(2_000);

        // A move, a re-skin, and a click rebind each carry the full set of appearance fields forward.
        assertThat(npc.movedTo(ELSEWHERE).displayName()).isEqualTo("Guide");
        assertThat(npc.movedTo(ELSEWHERE).interactionCooldownMillis()).isEqualTo(2_000);
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).mirrorSkin()).isTrue();
        assertThat(npc.withClickCommand("spawn").collidable()).isTrue();
        assertThat(npc.withScale(2.0).showInTab()).isTrue();
        assertThat(npc.withEntityType("VILLAGER").viewDistance()).isEqualTo(80.0);
        assertThat(npc.withGlowing(true).turnDistance()).isEqualTo(20.0);
        assertThat(npc.withPose("SITTING").onFire()).isTrue();
        assertThat(npc.withLookAtPlayer(false).invisible()).isTrue();
        assertThat(npc.withTypeData("baby", "true").silent()).isTrue();
        assertThat(npc.withDisplayName("Other").interactionCooldownMillis()).isEqualTo(2_000);
    }
}

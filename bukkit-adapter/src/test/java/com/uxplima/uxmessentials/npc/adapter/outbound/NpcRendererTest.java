package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.npc.NamedColor;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link NpcRenderer} dispatch under MockBukkit with a fake {@link com.uxplima.uxmlib.packet.npc.NpcPackets}:
 * a render to an in-range viewer sends one spawn bundle (tab-add + spawn) whose entry is added unlisted and kept (no
 * tab-remove on spawn); the renderer tracks which NPCs each viewer has been shown; a delete and a forget-then-quit both
 * leave no ghost, and the delete path sends the tab-remove that drops the kept entry; and an out-of-range viewer is
 * never spawned (and is removed when it falls out of range). A non-player NPC instead spawns through {@code spawnEntity}
 * with no tab entry, still wears its equipment/glow, fails soft on an unknown stored type, and re-renders cleanly
 * (remove + re-add) when its type changes.
 */
class NpcRendererTest {

    private ServerMock server;
    private RecordingNpcPackets packets;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        packets = new RecordingNpcPackets();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersOneSpawnBundleWithAnUnlistedKeptEntryForAnInRangeViewer() {
        PlayerMock viewer = server.addPlayer();
        InlineScheduler scheduler = new InlineScheduler();
        NpcRenderer renderer = newRenderer(scheduler, new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)); // one block away, in range

        // Exactly one bundle (tab-add + spawn) reached the viewer, plus the two look packets.
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.bundles).hasSize(1);
        assertThat(packets.bundles.get(0)).hasSize(2); // tab-add + spawn travel together
        assertThat(packets.tabAdds).hasSize(1);
        assertThat(packets.spawns).hasSize(1);
        // The entry is added unlisted so the body renders without a tab-list row, and it is kept, no tab-remove
        // is sent on spawn (removing the entry would de-render the fake player; the renderer drops it on despawn).
        assertThat(packets.tabAdds.get(0).listed()).isFalse();
        assertThat(packets.tabRemovesSentTo(viewer.getUniqueId())).isEmpty();
    }

    @Test
    void linksTheSpawnUuidToTheTabAddUuidSoTheSkinResolves() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0));

        UUID expected = RenderedNpc.profileIdFor("guide");
        assertThat(packets.tabAdds.get(0).profileId()).isEqualTo(expected);
        assertThat(packets.spawns.get(0).profileId()).isEqualTo(expected);
        // The spawn's profile uuid must equal the tab-add's, or the client won't attach the skin to the entity.
        assertThat(packets.spawns.get(0).profileId())
                .isEqualTo(packets.tabAdds.get(0).profileId());
    }

    @Test
    void carriesTheStoredSkinOnTheTabAdd() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npc(locationOf(viewer), new NpcSkin("tex", "sig")));

        TabSkin skin = java.util.Objects.requireNonNull(packets.tabAdds.get(0).skin(), "tab skin");
        assertThat(skin.textureValue()).isEqualTo("tex");
        assertThat(skin.signature()).isEqualTo("sig");
    }

    @Test
    void forcesTheSlimModelOnTheTabAddForASlimSkin() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        String classic = java.util.Base64.getEncoder()
                .encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures/abc\"}}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        renderer.render(npc(locationOf(viewer), new NpcSkin(classic, "sig", true)));

        TabSkin skin = java.util.Objects.requireNonNull(packets.tabAdds.get(0).skin(), "tab skin");
        String decoded = new String(
                java.util.Base64.getDecoder().decode(skin.textureValue()), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decoded).contains("\"model\":\"slim\"");
        assertThat(skin.signature()).isNull();
    }

    @Test
    void doesNotSpawnAnOutOfRangeViewer() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 100.0)); // far away, out of range

        assertThat(packets.spawns).isEmpty();
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).isEmpty();
    }

    @Test
    void despawnRemovesTheNpcFromEveryViewerThatHadIt() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0));

        renderer.despawn(NpcName.of("guide"));

        // The fake player is removed from the viewer (entity-remove + tab-remove); no ghost is left behind. Since
        // the kept entry is no longer auto-removed after spawn, the despawn path must send the tab-remove itself,
        // or the player-info entry would leak.
        assertThat(packets.removes).hasSize(1);
        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.tabRemovesSentTo(viewer.getUniqueId())).hasSize(1);
    }

    @Test
    void forgetDropsTheViewerWithoutAnyRemovePacket() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0));
        int sendsBefore = packets.removes.size();

        // On quit the channel is closing, so forget only drops the tracking. No remove packet is sent to a dead
        // client.
        renderer.forget(viewer);

        assertThat(packets.removes).hasSize(sendsBefore);
        // A re-render after forget shows the NPC again (the tracking was cleared, so it is not an unchanged no-op).
        renderer.render(npcAt(viewer, 1.0));
        assertThat(packets.spawns).hasSize(2);
    }

    @Test
    void refreshDoesNotReSpawnAStationaryInRangeViewer() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0)); // shown once

        renderer.refresh();
        renderer.refresh();

        // The refresh tick is idempotent: a stationary, already-shown viewer gets no second spawn bundle and no
        // second tab-add: this is the fix for the once-a-second re-spawn flood and tab flicker (B1).
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.spawns).hasSize(1);
        assertThat(packets.tabAdds).hasSize(1);
        // No churn: a stationary refresh sends neither a remove nor a tab-remove: the kept entry is never touched.
        assertThat(packets.removesSentTo(viewer.getUniqueId())).isEmpty();
        assertThat(packets.tabRemovesSentTo(viewer.getUniqueId())).isEmpty();
    }

    @Test
    void reSkinReRendersAnAlreadyShownViewerWithTheNewSkin() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npc(locationOf(viewer), new NpcSkin("oldtex", "oldsig"))); // shown with the old skin

        renderer.render(npc(locationOf(viewer), new NpcSkin("newtex", "newsig"))); // re-skin edit

        // The explicit render forces a fresh re-render for the already-shown viewer: a remove, then a second spawn
        // bundle carrying the NEW skin (S1), not the skipped no-op the idempotent refresh would do.
        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).hasSize(2);
        assertThat(packets.spawns).hasSize(2);
        assertThat(packets.tabAdds).hasSize(2);
        TabSkin reskinned =
                java.util.Objects.requireNonNull(packets.tabAdds.get(1).skin(), "re-skin");
        assertThat(reskinned.textureValue()).isEqualTo("newtex");
        assertThat(reskinned.signature()).isEqualTo("newsig");
        // The entity id is reused across the re-render (no reallocation on an edit), so the spawn pair share one id.
        assertThat(packets.spawns.get(1).entityId())
                .isEqualTo(packets.spawns.get(0).entityId());
    }

    @Test
    void moveReRendersAnAlreadyShownViewerAtTheNewLocation() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0)); // shown near the viewer

        renderer.render(npcAt(viewer, 5.0)); // moved, still in range

        // A move of an in-range, already-shown NPC forces a remove-then-spawn under the same id at the new position.
        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.spawns).hasSize(2);
        assertThat(packets.spawns.get(1).x()).isEqualTo(locationOf(viewer).getX() + 5.0);
        assertThat(packets.spawns.get(1).entityId())
                .isEqualTo(packets.spawns.get(0).entityId());
    }

    @Test
    void removesAnNpcThatMovedOutOfRangeOfAViewer() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0)); // in range -> shown

        Npc moved = npcAt(viewer, 100.0); // same name, now far away
        renderer.render(moved);

        // The move out of range removes the previously-shown fake player from the viewer.
        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
    }

    @Test
    void despawnAllRemovesEveryNpcFromEveryViewerAndClearsTracking() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0));

        renderer.despawnAll();

        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
        // After a full despawn a refresh re-spawns nothing (the live set was cleared).
        assertThatCode(renderer::refresh).doesNotThrowAnyException();
        assertThat(packets.spawns).hasSize(1);
    }

    @Test
    void spawnSchedulesNoDeferredTabRemove() {
        PlayerMock viewer = server.addPlayer();
        CountingDelayScheduler scheduler = new CountingDelayScheduler();
        NpcRenderer renderer = newRenderer(scheduler, new NoopLogger());

        renderer.render(npcAt(viewer, 1.0));

        // The spawn bundle goes out, but nothing is deferred: the kept unlisted entry needs no later tab-remove,
        // so the spawn path schedules zero asyncAfter hops and sends no tab-remove at all.
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(scheduler.asyncAfterCalls).isZero();
        assertThat(packets.tabRemoves).isEmpty();
    }

    @Test
    void lookTickAimsAnInRangeViewerOfALookingNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 2.0)); // shown, and within the 16-block look range
        int looksBefore = packets.looksSentTo(viewer.getUniqueId()).size();

        renderer.lookTick();

        // The look tick re-aims the NPC at this viewer: a head-look and a body-look on top of the spawn pair.
        assertThat(packets.looksSentTo(viewer.getUniqueId())).hasSize(looksBefore + 2);
    }

    @Test
    void lookTickIgnoresAnNpcWithLookAtPlayerOff() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 2.0).withLookAtPlayer(false));
        int looksBefore = packets.looksSentTo(viewer.getUniqueId()).size();

        renderer.lookTick();

        assertThat(packets.looksSentTo(viewer.getUniqueId())).hasSize(looksBefore);
    }

    @Test
    void lookTickIgnoresAViewerBeyondTheLookRange() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 30.0)); // shown (render range 48) but past the 16-block look range
        int looksBefore = packets.looksSentTo(viewer.getUniqueId()).size();

        renderer.lookTick();

        assertThat(packets.looksSentTo(viewer.getUniqueId())).hasSize(looksBefore);
    }

    @Test
    void appliesEquipmentAndGlowOnSpawnForAnInRangeViewer() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0)
                .withEquipment(com.uxplima.uxmessentials.npc.domain.EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withGlowing(true)
                .withGlowColor("RED");
        renderer.render(npc);

        // The spawn carries the equipment packet (one resolved slot) and the shared-flags byte (glow bit) + the
        // team that tints the outline (and carries the collision rule, both on one team).
        assertThat(packets.equipments).hasSize(1);
        assertThat(packets.equipments.get(0).items()).containsKey(com.uxplima.uxmlib.packet.npc.EquipmentSlot.HEAD);
        assertThat(packets.sharedFlags).hasSize(1);
        assertThat(packets.sharedFlags.get(0).glowing()).isTrue();
        assertThat(packets.collidables).hasSize(1);
        assertThat(packets.collidables.get(0).color()).isEqualTo(com.uxplima.uxmlib.packet.npc.NamedColor.RED);
    }

    @Test
    void composesOnFireGlowAndInvisibleIntoOneSharedFlagsPacket() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0).withOnFire(true).withGlowing(true).withInvisible(true);
        renderer.render(npc);

        // All three flags ride a single shared-flags packet so none overwrites the others' bits.
        assertThat(packets.sharedFlags).hasSize(1);
        assertThat(packets.sharedFlags.get(0).onFire()).isTrue();
        assertThat(packets.sharedFlags.get(0).glowing()).isTrue();
        assertThat(packets.sharedFlags.get(0).invisible()).isTrue();
    }

    @Test
    void sendsTheSilentPacketOnlyForASilentNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withSilent(true));
        assertThat(packets.silents).hasSize(1);
        assertThat(packets.silents.get(0).silent()).isTrue();
    }

    @Test
    void sendsACollisionTeamReflectingTheCollidableFlag() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // A non-collidable NPC (the default) sends a team with the collision rule off and no colour.
        renderer.render(npcAt(viewer, 1.0));
        assertThat(packets.collidables).hasSize(1);
        assertThat(packets.collidables.get(0).collidable()).isFalse();
        assertThat(packets.collidables.get(0).color()).isNull();

        // A collidable, non-glowing NPC needs no team at all: the no-team default already collides with no colour.
        packets.collidables.clear();
        renderer.render(npcAt(viewer, 1.0).withCollidable(true));
        assertThat(packets.collidables).isEmpty();
    }

    @Test
    void rendersTheDisplayNameAsTheProfileNameKeepingTheId() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withDisplayName("Greeter"));

        // The shown name above the head (the player-info profile name) is the display name, not the id.
        assertThat(packets.tabAdds.get(0).name()).isEqualTo("Greeter");
        // A visible label needs no team of its own, so the nametag is left at the client default.
        assertThat(packets.collidables)
                .allSatisfy(team -> assertThat(team.hideNametag()).isFalse());
    }

    @Test
    void hidesTheNametagOfAnNpcWhoseDisplayNameWasCleared() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withDisplayName("none"));

        // A cleared label hides the nametag through the team's visibility rule, not by sending a blank profile
        // name: the entry keeps the id so the team membership and the tab entry both stay well-formed.
        assertThat(packets.tabAdds.get(0).name()).isEqualTo("guide");
        assertThat(packets.collidables).hasSize(1);
        assertThat(packets.collidables.get(0).memberName()).isEqualTo("guide");
        assertThat(packets.collidables.get(0).hideNametag()).isTrue();
    }

    @Test
    void keepsTheNametagOfAnNpcThatNeverHadADisplayName() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // The default NPC is non-collidable, so it already sends a team; that team must not hide the nametag.
        renderer.render(npcAt(viewer, 1.0));

        assertThat(packets.tabAdds.get(0).name()).isEqualTo("guide");
        assertThat(packets.collidables).hasSize(1);
        assertThat(packets.collidables.get(0).hideNametag()).isFalse();
    }

    @Test
    void listsTheTabEntryWhenShowInTabIsOn() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withShowInTab(true));

        // The operator opted the NPC into the tab list, so the entry is added listed; it is kept (no tab-remove on
        // spawn) exactly like the unlisted default.
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.tabAdds.get(0).listed()).isTrue();
        assertThat(packets.tabRemovesSentTo(viewer.getUniqueId())).isEmpty();
    }

    @Test
    void addsTheTabEntryUnlistedByDefault() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)); // showInTab defaults off

        // The default fake-player NPC renders its body via an unlisted entry, present to the client but never a
        // tab-list row, so no phantom row appears and no remove is needed.
        assertThat(packets.tabAdds.get(0).listed()).isFalse();
        assertThat(packets.tabRemovesSentTo(viewer.getUniqueId())).isEmpty();
    }

    @Test
    void mirrorSkinSpawnsEachViewerWithTheirOwnProfileSkin() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // A mirror-skin NPC carries no stored skin; the tab-add uses the viewer's own profile skin (or none under
        // MockBukkit, which is the fail-soft path): the point is it does not throw and the spawn still goes out.
        assertThatCode(() -> renderer.render(npcAt(viewer, 1.0).withMirrorSkin(true)))
                .doesNotThrowAnyException();
        assertThat(packets.tabAdds).hasSize(1);
        assertThat(packets.spawns).hasSize(1);
    }

    @Test
    void honoursThePerNpcViewDistanceOverride() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // 40 blocks away is inside the global 48 range but outside a per-NPC 10-block override, so it is not shown.
        renderer.render(npcAt(viewer, 40.0).withViewDistance(10.0));
        assertThat(packets.spawns).isEmpty();

        // The same distance is inside a per-NPC 60-block override, so the NPC is shown.
        renderer.render(npcAt(viewer, 40.0).withViewDistance(60.0));
        assertThat(packets.spawns).hasSize(1);
    }

    @Test
    void honoursThePerNpcTurnDistanceOverride() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 20.0).withTurnDistance(4.0)); // shown (render 48) but past a 4-block turn range
        int looksBefore = packets.looksSentTo(viewer.getUniqueId()).size();

        renderer.lookTick();

        // The viewer is past the per-NPC turn distance, so no look packets are sent despite being within look range.
        assertThat(packets.looksSentTo(viewer.getUniqueId())).hasSize(looksBefore);
    }

    @Test
    void sendsNoEquipmentOrGlowForAPlainNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0));

        assertThat(packets.equipments).isEmpty();
        assertThat(packets.sharedFlags).isEmpty(); // not glowing, not on fire, not invisible
        assertThat(packets.glowColors).isEmpty();
        assertThat(packets.silents).isEmpty();
    }

    @Test
    void appliesPoseAndScaleOnSpawnForAnInRangeViewer() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0).withPose("SITTING").withScale(2.5);
        renderer.render(npc);

        // A non-default pose and a non-default scale each ship their packet on spawn.
        assertThat(packets.poses).hasSize(1);
        assertThat(packets.poses.get(0).pose()).isEqualTo(com.uxplima.uxmlib.packet.npc.NpcPose.SITTING);
        assertThat(packets.scales).hasSize(1);
        assertThat(packets.scales.get(0).scale()).isEqualTo(2.5);
    }

    @Test
    void sendsNoPoseOrScaleForADefaultNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)); // STANDING, scale 1.0. The entity already renders this way

        assertThat(packets.poses).isEmpty();
        assertThat(packets.scales).isEmpty();
    }

    @Test
    void failsSoftAndSkipsThePosePacketForAnUnknownStoredPose() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // An unknown pose name renders standing rather than failing, no pose packet, but the spawn still went out.
        Npc npc = npcAt(viewer, 1.0).withPose("NOT_A_REAL_POSE");
        assertThatCode(() -> renderer.render(npc)).doesNotThrowAnyException();

        assertThat(packets.poses).isEmpty();
        assertThat(packets.spawns).hasSize(1);
    }

    @Test
    void appliesBabyAndVillagerDataToABabyVillagerNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0)
                .withEntityType("VILLAGER")
                .withTypeData("baby", "true")
                .withTypeData("villager_profession", "librarian")
                .withTypeData("villager_type", "desert")
                .withTypeData("villager_level", "3");
        renderer.render(npc);

        // A baby villager carries both the baby flag (villager is ageable) and one grouped villager-data packet.
        assertThat(packets.babies).hasSize(1);
        assertThat(packets.babies.get(0).baby()).isTrue();
        assertThat(packets.villagerDatas).hasSize(1);
        assertThat(packets.villagerDatas.get(0).profession()).isEqualTo("librarian");
        assertThat(packets.villagerDatas.get(0).type()).isEqualTo("desert");
        assertThat(packets.villagerDatas.get(0).level()).isEqualTo(3);
        // The three villager_* keys collapse into a single villagerData call, not three.
        assertThat(packets.villagerDatas).hasSize(1);
    }

    @Test
    void appliesSlimeSizeToASlimeNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("SLIME").withTypeData("size", "4"));

        assertThat(packets.slimeSizes).hasSize(1);
        assertThat(packets.slimeSizes.get(0).size()).isEqualTo(4);
    }

    @Test
    void appliesChargedToACreeperNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("CREEPER").withTypeData("charged", "true"));

        assertThat(packets.chargeds).hasSize(1);
        assertThat(packets.chargeds.get(0).charged()).isTrue();
    }

    @Test
    void appliesHorseColourAndMarkingsToAHorseNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("HORSE")
                .withTypeData("horse_color", "3")
                .withTypeData("horse_markings", "2"));

        // The colour and markings collapse into a single horseVariant packet carrying both components.
        assertThat(packets.horseVariants).hasSize(1);
        assertThat(packets.horseVariants.get(0).color()).isEqualTo(3);
        assertThat(packets.horseVariants.get(0).markings()).isEqualTo(2);
    }

    @Test
    void appliesHorseColourAloneWithDefaultMarkings() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("HORSE").withTypeData("horse_color", "5"));

        // A colour without markings still ships, with markings defaulted to none (0).
        assertThat(packets.horseVariants).hasSize(1);
        assertThat(packets.horseVariants.get(0).color()).isEqualTo(5);
        assertThat(packets.horseVariants.get(0).markings()).isEqualTo(0);
    }

    @Test
    void appliesHorseStyleAsTheMarkingsToAHorseNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("HORSE")
                .withTypeData("horse_color", "1")
                .withTypeData("horse_style", "white_dots"));

        // horse_style is the named alias of the markings id: WHITE_DOTS is markings 3.
        assertThat(packets.horseVariants).hasSize(1);
        assertThat(packets.horseVariants.get(0).color()).isEqualTo(1);
        assertThat(packets.horseVariants.get(0).markings()).isEqualTo(3);
    }

    @Test
    void prefersHorseStyleOverARawMarkingsWhenBothAreSet() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("HORSE")
                .withTypeData("horse_markings", "1")
                .withTypeData("horse_style", "black_dots"));

        // The named style wins over a raw markings id when both are set: BLACK_DOTS is markings 4.
        assertThat(packets.horseVariants).hasSize(1);
        assertThat(packets.horseVariants.get(0).markings()).isEqualTo(4);
    }

    @Test
    void appliesLlamaVariantToALlamaNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("LLAMA").withTypeData("llama_variant", "2"));

        assertThat(packets.llamaVariants).hasSize(1);
        assertThat(packets.llamaVariants.get(0).variant()).isEqualTo(2);
    }

    @Test
    void appliesSheepColourFromARawIdToASheepNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("SHEEP").withTypeData("sheep_color", "14"));

        assertThat(packets.sheepWools).hasSize(1);
        assertThat(packets.sheepWools.get(0).color()).isEqualTo(14);
        assertThat(packets.sheepWools.get(0).sheared()).isFalse();
    }

    @Test
    void appliesSheepColourFromADyeColorNameToASheepNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // A human-friendly DyeColor name resolves to its wool id (RED == 14) at apply time.
        renderer.render(npcAt(viewer, 1.0).withEntityType("SHEEP").withTypeData("sheep_color", "red"));

        assertThat(packets.sheepWools).hasSize(1);
        assertThat(packets.sheepWools.get(0).color()).isEqualTo(org.bukkit.DyeColor.RED.getWoolData());
    }

    @Test
    void composesSheepColourAndShearedIntoOneWoolPacket() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("SHEEP")
                .withTypeData("sheep_color", "red")
                .withTypeData("sheep_sheared", "true"));

        assertThat(packets.sheepWools).hasSize(1);
        assertThat(packets.sheepWools.get(0).color()).isEqualTo(org.bukkit.DyeColor.RED.getWoolData());
        assertThat(packets.sheepWools.get(0).sheared()).isTrue();
    }

    @Test
    void appliesWolfCollarFromADyeColorNameToAWolfNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("WOLF").withTypeData("wolf_collar", "red"));

        assertThat(packets.wolfCollars).hasSize(1);
        assertThat(packets.wolfCollars.get(0).color()).isEqualTo(org.bukkit.DyeColor.RED.getWoolData());
    }

    @Test
    void appliesShulkerColorFromADyeColorNameToAShulkerNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("SHULKER").withTypeData("shulker_color", "red"));

        assertThat(packets.shulkerColors).hasSize(1);
        assertThat(packets.shulkerColors.get(0).color()).isEqualTo((int) org.bukkit.DyeColor.RED.getWoolData());
    }

    @Test
    void appliesShulkerPeekToAShulkerNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("SHULKER").withTypeData("shulker_peek", "100"));

        assertThat(packets.shulkerPeeks).hasSize(1);
        assertThat(packets.shulkerPeeks.get(0).peek()).isEqualTo(100);
    }

    @Test
    void appliesPandaGeneToAPandaNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("PANDA").withTypeData("panda_gene", "4"));

        assertThat(packets.pandaGenes).hasSize(1);
        assertThat(packets.pandaGenes.get(0).gene()).isEqualTo(4);
    }

    @Test
    void appliesGoatScreamingToAGoatNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("GOAT").withTypeData("goat_screaming", "true"));

        assertThat(packets.goatScreamings).hasSize(1);
        assertThat(packets.goatScreamings.get(0).screaming()).isTrue();
    }

    @Test
    void appliesAllayDancingToAnAllayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("ALLAY").withTypeData("allay_dancing", "true"));

        assertThat(packets.allayDancings).hasSize(1);
        assertThat(packets.allayDancings.get(0).dancing()).isTrue();
    }

    @Test
    void appliesPiglinDancingToAPiglinNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("PIGLIN").withTypeData("piglin_dancing", "true"));

        assertThat(packets.piglinDancings).hasSize(1);
        assertThat(packets.piglinDancings.get(0).dancing()).isTrue();
    }

    @Test
    void appliesCamelDashToACamelNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("CAMEL").withTypeData("camel_dash", "true"));

        assertThat(packets.camelDashes).hasSize(1);
        assertThat(packets.camelDashes.get(0).dashing()).isTrue();
    }

    @Test
    void composesBeeNectarRollingAndStungIntoOneFlagsPacket() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("BEE")
                .withTypeData("bee_nectar", "true")
                .withTypeData("bee_stung", "true"));

        assertThat(packets.beeFlags).hasSize(1);
        assertThat(packets.beeFlags.get(0).nectar()).isTrue();
        assertThat(packets.beeFlags.get(0).rolling()).isFalse();
        assertThat(packets.beeFlags.get(0).stung()).isTrue();
    }

    @Test
    void appliesVexChargingToAVexNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("VEX").withTypeData("vex_charging", "true"));

        assertThat(packets.vexChargings).hasSize(1);
        assertThat(packets.vexChargings.get(0).charging()).isTrue();
    }

    @Test
    void appliesTropicalFishVariantToATropicalFishNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("TROPICAL_FISH").withTypeData("tropical_fish", "7"));

        assertThat(packets.tropicalFishVariants).hasSize(1);
        assertThat(packets.tropicalFishVariants.get(0).variantIndex()).isEqualTo(7);
    }

    @Test
    void composesArmorStandClientFlagsIntoOnePacket() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("ARMOR_STAND")
                .withTypeData("armor_stand_small", "true")
                .withTypeData("armor_stand_arms", "true")
                .withTypeData("armor_stand_marker", "false"));

        assertThat(packets.armorStandFlags).hasSize(1);
        assertThat(packets.armorStandFlags.get(0).small()).isTrue();
        assertThat(packets.armorStandFlags.get(0).showArms()).isTrue();
        assertThat(packets.armorStandFlags.get(0).noBasePlate()).isFalse();
        assertThat(packets.armorStandFlags.get(0).marker()).isFalse();
    }

    @Test
    void appliesAnArmorStandPoseFromAnAngleTriple() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(
                npcAt(viewer, 1.0).withEntityType("ARMOR_STAND").withTypeData("armor_stand_left_arm", "10,-20,30.5"));

        assertThat(packets.armorStandPoses).hasSize(1);
        assertThat(packets.armorStandPoses.get(0).part())
                .isEqualTo(com.uxplima.uxmlib.packet.npc.ArmorStandPart.LEFT_ARM);
        assertThat(packets.armorStandPoses.get(0).x()).isEqualTo(10f);
        assertThat(packets.armorStandPoses.get(0).y()).isEqualTo(-20f);
        assertThat(packets.armorStandPoses.get(0).z()).isEqualTo(30.5f);
    }

    @Test
    void sizesAnInteractionNpcsHitbox() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("INTERACTION")
                .withTypeData("interaction_width", "1.5")
                .withTypeData("interaction_height", "2.0"));

        assertThat(packets.interactionSizes).hasSize(1);
        assertThat(packets.interactionSizes.get(0).width()).isEqualTo(1.5f);
        assertThat(packets.interactionSizes.get(0).height()).isEqualTo(2.0f);
    }

    @Test
    void showsTheBlockOnABlockDisplayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("BLOCK_DISPLAY").withTypeData("block", "stone"));

        assertThat(packets.blockDisplayStates).hasSize(1);
        assertThat(packets.blockDisplayStates.get(0).material()).isEqualTo(org.bukkit.Material.STONE);
    }

    @Test
    void showsTheItemOnAnItemDisplayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("ITEM_DISPLAY").withTypeData("item", "DIAMOND"));

        assertThat(packets.itemDisplayItems).hasSize(1);
        assertThat(packets.itemDisplayItems.get(0).material()).isEqualTo(org.bukkit.Material.DIAMOND);
    }

    @Test
    void showsTheTextOnATextDisplayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("TEXT_DISPLAY").withTypeData("text", "<red>Welcome"));

        assertThat(packets.textDisplayTexts).hasSize(1);
        assertThat(packets.textDisplayTexts.get(0).plain()).isEqualTo("Welcome");
    }

    @Test
    void appliesScaleAndBillboardToADisplayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("ITEM_DISPLAY")
                .withTypeData("item", "DIAMOND")
                .withTypeData("display_scale", "2.0")
                .withTypeData("display_billboard", "center"));

        assertThat(packets.displayScales).hasSize(1);
        assertThat(packets.displayScales.get(0).scale()).isEqualTo(2.0f);
        assertThat(packets.displayBillboards).hasSize(1);
        assertThat(packets.displayBillboards.get(0).constraint()).isEqualTo((byte) 3);
    }

    @Test
    void appliesBackgroundToATextDisplayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("TEXT_DISPLAY")
                .withTypeData("text", "Hi")
                .withTypeData("text_background", "#80FF0000"));

        assertThat(packets.textDisplayBackgrounds).hasSize(1);
        assertThat(packets.textDisplayBackgrounds.get(0).argb()).isEqualTo(0x80FF0000);
    }

    @Test
    void appliesLineWidthToATextDisplayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("TEXT_DISPLAY")
                .withTypeData("text", "Hi")
                .withTypeData("text_line_width", "150"));

        assertThat(packets.textDisplayLineWidths).hasSize(1);
        assertThat(packets.textDisplayLineWidths.get(0).lineWidth()).isEqualTo(150);
    }

    @Test
    void appliesNonUniformScaleAndTranslationToADisplayNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0)
                .withEntityType("BLOCK_DISPLAY")
                .withTypeData("block", "stone")
                .withTypeData("display_scale", "2,1,3")
                .withTypeData("display_translation", "0,-0.5,0"));

        assertThat(packets.displayScaleXyzs).hasSize(1);
        assertThat(packets.displayScaleXyzs.get(0).x()).isEqualTo(2f);
        assertThat(packets.displayScaleXyzs.get(0).z()).isEqualTo(3f);
        assertThat(packets.displayTranslations).hasSize(1);
        assertThat(packets.displayTranslations.get(0).y()).isEqualTo(-0.5f);
    }

    @Test
    void appliesParrotVariantToAParrotNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("PARROT").withTypeData("parrot_variant", "4"));

        assertThat(packets.parrotVariants).hasSize(1);
        assertThat(packets.parrotVariants.get(0).variant()).isEqualTo(4);
    }

    @Test
    void appliesAxolotlVariantToAnAxolotlNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("AXOLOTL").withTypeData("axolotl_variant", "3"));

        assertThat(packets.axolotlVariants).hasSize(1);
        assertThat(packets.axolotlVariants.get(0).variant()).isEqualTo(3);
    }

    @Test
    void appliesFoxTypeToAFoxNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("FOX").withTypeData("fox_type", "1"));

        assertThat(packets.foxTypes).hasSize(1);
        assertThat(packets.foxTypes.get(0).type()).isEqualTo(1);
    }

    @Test
    void appliesRabbitTypeIncludingTheKillerVariantToARabbitNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("RABBIT").withTypeData("rabbit_type", "99"));

        assertThat(packets.rabbitTypes).hasSize(1);
        assertThat(packets.rabbitTypes.get(0).type()).isEqualTo(99);
    }

    @Test
    void appliesCatVariantToACatNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("CAT").withTypeData("cat_variant", "calico"));

        // The cat variant resolves by name; the recording fake stands in for the live-registry holder.
        assertThat(packets.catVariants).hasSize(1);
        assertThat(packets.catVariants.get(0).name()).isEqualTo("calico");
    }

    @Test
    void appliesFrogVariantToAFrogNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("FROG").withTypeData("frog_variant", "warm"));

        assertThat(packets.frogVariants).hasSize(1);
        assertThat(packets.frogVariants.get(0).name()).isEqualTo("warm");
    }

    @Test
    void appliesWolfChickenCowPigCoatVariants() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("WOLF").withTypeData("wolf_variant", "ashen"));
        renderer.render(npcAt(viewer, 2.0).withEntityType("CHICKEN").withTypeData("chicken_variant", "warm"));
        renderer.render(npcAt(viewer, 3.0).withEntityType("COW").withTypeData("cow_variant", "cold"));
        renderer.render(npcAt(viewer, 4.0).withEntityType("PIG").withTypeData("pig_variant", "temperate"));

        assertThat(packets.wolfVariants).hasSize(1);
        assertThat(packets.wolfVariants.get(0).name()).isEqualTo("ashen");
        assertThat(packets.chickenVariants.get(0).name()).isEqualTo("warm");
        assertThat(packets.cowVariants.get(0).name()).isEqualTo("cold");
        assertThat(packets.pigVariants.get(0).name()).isEqualTo("temperate");
    }

    @Test
    void appliesAxolotlPlayDeadAndRaiderCelebrating() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("AXOLOTL").withTypeData("axolotl_playing_dead", "true"));
        renderer.render(npcAt(viewer, 2.0).withEntityType("PILLAGER").withTypeData("illager_celebrating", "true"));

        assertThat(packets.axolotlPlayingDeads).hasSize(1);
        assertThat(packets.axolotlPlayingDeads.get(0).value()).isTrue();
        assertThat(packets.raiderCelebratings).hasSize(1);
        assertThat(packets.raiderCelebratings.get(0).value()).isTrue();
    }

    @Test
    void appliesSittingPosesAndFoxPoseAndPandaEating() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("WOLF").withTypeData("wolf_sitting", "true"));
        renderer.render(npcAt(viewer, 2.0).withEntityType("CAT").withTypeData("cat_sitting", "true"));
        renderer.render(npcAt(viewer, 3.0).withEntityType("FOX").withTypeData("fox_pose", "sleeping"));
        renderer.render(npcAt(viewer, 4.0).withEntityType("PANDA").withTypeData("panda_eating", "true"));

        assertThat(packets.tameableSittings).hasSize(2);
        assertThat(packets.tameableSittings).allMatch(rec -> rec.value());
        assertThat(packets.foxFlagsList).hasSize(1);
        assertThat(packets.foxFlagsList.get(0).sleeping()).isTrue();
        assertThat(packets.foxFlagsList.get(0).sitting()).isFalse();
        assertThat(packets.foxFlagsList.get(0).crouching()).isFalse();
        assertThat(packets.pandaEatings).hasSize(1);
        assertThat(packets.pandaEatings.get(0).value()).isTrue();
    }

    @Test
    void appliesSnifferAndArmadilloStates() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("SNIFFER").withTypeData("sniffer_state", "digging"));
        renderer.render(npcAt(viewer, 2.0).withEntityType("ARMADILLO").withTypeData("armadillo_state", "rolling"));

        assertThat(packets.snifferStates).hasSize(1);
        assertThat(packets.snifferStates.get(0).state()).isEqualTo("digging");
        assertThat(packets.armadilloStates).hasSize(1);
        assertThat(packets.armadilloStates.get(0).state()).isEqualTo("rolling");
    }

    @Test
    void appliesUseItemPoseAndShaking() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("ZOMBIE").withTypeData("use_item", "off_hand"));
        renderer.render(npcAt(viewer, 2.0).withEntityType("SKELETON").withTypeData("shaking", "true"));
        renderer.render(npcAt(viewer, 3.0).withEntityType("BLOCK_DISPLAY").withTypeData("use_item", "main_hand"));

        assertThat(packets.useItems).hasSize(1);
        assertThat(packets.useItems.get(0).using()).isTrue();
        assertThat(packets.useItems.get(0).offHand()).isTrue();
        assertThat(packets.frozenTicks).hasSize(1);
        assertThat(packets.frozenTicks.get(0).ticks()).isGreaterThan(140);
    }

    @Test
    void doesNotSendACatVariantToAFrogNorAFrogVariantToACat() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // Each name variant reaches only its own type; the cross pair is skipped, the spawn still goes out.
        assertThatCode(() -> renderer.render(npcAt(viewer, 1.0)
                        .withEntityType("FROG")
                        .withTypeData("cat_variant", "calico")
                        .withTypeData("frog_variant", "warm")))
                .doesNotThrowAnyException();

        assertThat(packets.catVariants).isEmpty(); // a cat variant never reaches a frog
        assertThat(packets.frogVariants).hasSize(1); // the frog's own variant does
        assertThat(packets.spawnEntities).hasSize(1);
    }

    @Test
    void dropsANameVariantPacketWhenTheLibCannotResolveTheHolder() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        packets.dropNameVariants = true; // mimic the live registry being unavailable / the name unresolved

        // A null packet from the lib (its fail-soft signal) is dropped, never sent: the spawn still succeeds.
        assertThatCode(() ->
                        renderer.render(npcAt(viewer, 1.0).withEntityType("CAT").withTypeData("cat_variant", "calico")))
                .doesNotThrowAnyException();

        // The build was attempted (the name reached the right type) but its null result was not sent on.
        assertThat(packets.catVariants).hasSize(1);
        assertThat(packets.sentTo(viewer.getUniqueId(), RecordingNpcPackets.CatVariant.class))
                .isEmpty();
        assertThat(packets.spawnEntities).hasSize(1);
    }

    @Test
    void doesNotSendAVariantToTheWrongTypeFailSoft() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // A horse colour on a cow, a sheep colour on a pig: each unsupported key is skipped, not sent, and the
        // spawn still goes out: a variant value never reaches a type that has no such field.
        assertThatCode(() -> renderer.render(npcAt(viewer, 1.0)
                        .withEntityType("COW")
                        .withTypeData("horse_color", "3")
                        .withTypeData("sheep_color", "red")
                        .withTypeData("fox_type", "1")))
                .doesNotThrowAnyException();

        assertThat(packets.horseVariants).isEmpty();
        assertThat(packets.sheepWools).isEmpty();
        assertThat(packets.foxTypes).isEmpty();
        assertThat(packets.spawnEntities).hasSize(1);
    }

    @Test
    void skipsUnsupportedTypeDataFailSoftWithoutAPacketOrThrow() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // A creeper has no size field and a slime is not a creeper: each unsupported key is skipped, not sent, and
        // the spawn still goes out.
        Npc creeperWithSize = npcAt(viewer, 1.0).withEntityType("CREEPER").withTypeData("size", "4");
        assertThatCode(() -> renderer.render(creeperWithSize)).doesNotThrowAnyException();

        assertThat(packets.slimeSizes).isEmpty();
        assertThat(packets.chargeds).isEmpty();
        assertThat(packets.spawnEntities).hasSize(1);
    }

    @Test
    void doesNotSendBabyToACreeper() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // The key correctness property: a creeper is not ageable, so the baby flag is never sent to it (a wrong
        // index would otherwise land on an unrelated creeper field).
        renderer.render(npcAt(viewer, 1.0).withEntityType("CREEPER").withTypeData("baby", "true"));

        assertThat(packets.babies).isEmpty();
        assertThat(packets.spawnEntities).hasSize(1);
    }

    @Test
    void sendsTheAgeableBabyToABreedingAnimal() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // A cow is a real AgeableMob, so it takes the shared AgeableMob baby packet and none of the monster-family
        // variants.
        renderer.render(npcAt(viewer, 1.0).withEntityType("COW").withTypeData("baby", "true"));

        assertThat(packets.babies).hasSize(1);
        assertThat(packets.babies.get(0).baby()).isTrue();
        assertThat(packets.zombieBabies).isEmpty();
        assertThat(packets.piglinBabies).isEmpty();
        assertThat(packets.zoglinBabies).isEmpty();
    }

    @Test
    void routesTheZombieLineBabyThroughTheZombieAccessorNotTheAgeableOne() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // Zombies extend Monster, not AgeableMob: their baby flag lives at a different data index, so it must go
        // through zombieBaby. Sending the AgeableMob baby packet here would desync the client. Husk/drowned/zombie
        // villager/zombified piglin share the zombie accessor.
        for (String zombieType : new String[] {"ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN"}) {
            packets.babies.clear();
            packets.zombieBabies.clear();

            renderer.render(npcAt(viewer, 1.0).withEntityType(zombieType).withTypeData("baby", "true"));

            assertThat(packets.babies)
                    .as("no AgeableMob baby for %s", zombieType)
                    .isEmpty();
            assertThat(packets.zombieBabies)
                    .as("zombie baby for %s", zombieType)
                    .hasSize(1);
            assertThat(packets.zombieBabies.get(0).baby()).isTrue();
        }
    }

    @Test
    void routesPiglinAndZoglinBabyThroughTheirOwnAccessors() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("PIGLIN").withTypeData("baby", "true"));
        assertThat(packets.babies).isEmpty();
        assertThat(packets.piglinBabies).hasSize(1);
        assertThat(packets.piglinBabies.get(0).baby()).isTrue();

        packets.babies.clear();
        renderer.render(npcAt(viewer, 1.0).withEntityType("ZOGLIN").withTypeData("baby", "false"));
        assertThat(packets.babies).isEmpty();
        assertThat(packets.zoglinBabies).hasSize(1);
        assertThat(packets.zoglinBabies.get(0).baby()).isFalse();
    }

    @Test
    void doesNotSendBabyToAPiglinBruteWhichHasNoBabyForm() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // A piglin brute is Bukkit-Ageable but has no baby field on the server side at all, so it gets nothing.
        renderer.render(npcAt(viewer, 1.0).withEntityType("PIGLIN_BRUTE").withTypeData("baby", "true"));

        assertThat(packets.babies).isEmpty();
        assertThat(packets.zombieBabies).isEmpty();
        assertThat(packets.piglinBabies).isEmpty();
        assertThat(packets.zoglinBabies).isEmpty();
        assertThat(packets.spawnEntities).hasSize(1);
    }

    @Test
    void clampsAnOversizedVillagerLevelToTheTopBadge() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("VILLAGER").withTypeData("villager_level", "99"));

        assertThat(packets.villagerDatas).hasSize(1);
        assertThat(packets.villagerDatas.get(0).level()).isEqualTo(5);
    }

    @Test
    void clampsAnOversizedSlimeSize() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("SLIME").withTypeData("size", "9999"));

        assertThat(packets.slimeSizes).hasSize(1);
        assertThat(packets.slimeSizes.get(0).size()).isEqualTo(16);
    }

    @Test
    void appliesScaleToAMobNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("ZOMBIE").withScale(0.5));

        // Scale applies to every entity type, mob included.
        assertThat(packets.spawnEntities).hasSize(1);
        assertThat(packets.scales).hasSize(1);
        assertThat(packets.scales.get(0).scale()).isEqualTo(0.5);
    }

    @Test
    void equipsAStoredSerializedItemWithItsMetaIntact() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        ItemStack sword = new ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = sword.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
        sword.setItemMeta(meta);
        Npc npc = npcAt(viewer, 1.0)
                .withEquipment(
                        com.uxplima.uxmessentials.npc.domain.EquipmentSlot.MAINHAND,
                        EquipmentPayloads.serialize(sword));
        renderer.render(npc);

        // The serialized payload deserializes back to the full item, enchantments and custom name survive the
        // round-trip through the stored token.
        assertThat(packets.equipments).hasSize(1);
        ItemStack sent = java.util.Objects.requireNonNull(
                packets.equipments.get(0).items().get(com.uxplima.uxmlib.packet.npc.EquipmentSlot.MAINHAND),
                "mainhand item");
        assertThat(sent.getType()).isEqualTo(org.bukkit.Material.DIAMOND_SWORD);
        assertThat(sent.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS))
                .isEqualTo(5);
        assertThat(sent.getItemMeta().displayName()).isEqualTo(net.kyori.adventure.text.Component.text("Excalibur"));
    }

    @Test
    void dropsACorruptSerializedPayloadSoTheSpawnStillSucceeds() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0)
                .withEquipment(com.uxplima.uxmessentials.npc.domain.EquipmentSlot.HEAD, "b64:not-valid-@@@");
        renderer.render(npc);

        // A corrupt serialized token is dropped fail-soft: the equipment packet carries no slots, the spawn went out.
        assertThat(packets.equipments).hasSize(1);
        assertThat(packets.equipments.get(0).items()).isEmpty();
        assertThat(packets.spawns).hasSize(1);
    }

    @Test
    void dropsAnUnknownMaterialSoTheSpawnStillSucceeds() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0)
                .withEquipment(com.uxplima.uxmessentials.npc.domain.EquipmentSlot.HEAD, "NOT_A_REAL_MATERIAL");
        renderer.render(npc);

        // The unknown name is dropped, so the equipment packet carries no slots, the spawn itself still went out.
        assertThat(packets.equipments).hasSize(1);
        assertThat(packets.equipments.get(0).items()).isEmpty();
        assertThat(packets.spawns).hasSize(1);
    }

    @Test
    void glowsWhiteWhenTheColorNameIsUnknown() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0).withGlowing(true).withGlowColor("octarine");
        renderer.render(npc);

        // An unparseable colour falls back to a null (default white) team colour rather than failing the spawn; the
        // glow bit still rides the shared-flags packet and the team carries the (null) colour.
        assertThat(packets.sharedFlags.get(0).glowing()).isTrue();
        assertThat(packets.collidables.get(0).color()).isNull();
    }

    @Test
    void removesTheGlowTeamWhenAGlowingNpcDespawns() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0).withGlowing(true).withGlowColor("RED"));

        renderer.despawn(NpcName.of("guide"));

        // The client-side glow-colour team must be dropped on despawn, or it lingers and can later tint a real
        // player who shares the seated name.
        assertThat(packets.glowColorRemovesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.glowColorRemovesSentTo(viewer.getUniqueId()).get(0).teamName())
                .isEqualTo("guide");
    }

    @Test
    void removesTheGlowTeamWhenTheViewerGoesOutOfRange() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0).withGlowing(true).withGlowColor("AQUA")); // in range -> shown

        renderer.render(npcAt(viewer, 100.0).withGlowing(true).withGlowColor("AQUA")); // same name, now far away

        // Moving out of range removes the fake player and must also drop its now-orphaned glow team.
        assertThat(packets.glowColorRemovesSentTo(viewer.getUniqueId())).hasSize(1);
    }

    @Test
    void spawnsAMobNpcThroughSpawnEntityWithNoTabEntry() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        renderer.render(npcAt(viewer, 1.0).withEntityType("VILLAGER"));

        // A mob spawns via spawnEntity with the canonical key and NO tab entry / no fake-player spawn at all.
        assertThat(packets.spawnEntities).hasSize(1);
        assertThat(packets.spawnEntities.get(0).entityTypeKey()).isEqualTo("minecraft:villager");
        assertThat(packets.spawns).isEmpty();
        assertThat(packets.tabAdds).isEmpty();
        // The mob carries the stable per-NPC entity UUID (no profile behind it).
        assertThat(packets.spawnEntities.get(0).entityUuid()).isEqualTo(RenderedNpc.profileIdFor("guide"));
    }

    @Test
    void appliesEquipmentAndGlowToAMobNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        Npc npc = npcAt(viewer, 1.0)
                .withEntityType("ZOMBIE")
                .withEquipment(com.uxplima.uxmessentials.npc.domain.EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withGlowing(true)
                .withGlowColor("RED");
        renderer.render(npc);

        assertThat(packets.spawnEntities).hasSize(1);
        assertThat(packets.equipments).hasSize(1);
        assertThat(packets.equipments.get(0).items()).containsKey(com.uxplima.uxmlib.packet.npc.EquipmentSlot.HEAD);
        assertThat(packets.sharedFlags.get(0).glowing()).isTrue();
        assertThat(packets.collidables.get(0).color()).isEqualTo(com.uxplima.uxmlib.packet.npc.NamedColor.RED);
    }

    @Test
    void failsSoftAndSkipsSpawningAnUnknownStoredEntityType() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());

        // An entity type that does not resolve to a Bukkit type must not throw on the render thread.
        assertThatCode(() -> renderer.render(npcAt(viewer, 1.0).withEntityType("NOT_A_REAL_TYPE")))
                .doesNotThrowAnyException();

        // Nothing is spawned for the bad type, by either path.
        assertThat(packets.spawnEntities).isEmpty();
        assertThat(packets.spawns).isEmpty();
        assertThat(packets.tabAdds).isEmpty();
    }

    @Test
    void aTypeChangeForcesACleanRemoveAndReAdd() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0)); // shown as a fake player

        renderer.render(npcAt(viewer, 1.0).withEntityType("VILLAGER")); // type change → force re-render

        // The force path removes the old (player) entity and re-adds it as the mob, no ghost of the old type.
        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.spawns).hasSize(1); // the original player spawn only
        assertThat(packets.spawnEntities).hasSize(1); // the new villager spawn
        assertThat(packets.spawnEntities.get(0).entityTypeKey()).isEqualTo("minecraft:villager");
        // The entity id is reused across the type change (the renderer keeps one id per NPC).
        assertThat(packets.spawnEntities.get(0).entityId())
                .isEqualTo(packets.spawns.get(0).entityId());
    }

    @Test
    void warnsOnceForABadStoredTypeNoMatterHowManyRefreshTicksRetryIt() {
        PlayerMock viewer = server.addPlayer();
        CountingLogger logger = new CountingLogger();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), logger);
        renderer.render(npcAt(viewer, 1.0).withEntityType("NOT_A_REAL_TYPE")); // one warn

        // The reconcile retries the unresolvable NPC every tick (the skip never marks the viewer shown), so without
        // the warn-once cache this would log a line per refresh per viewer: an unbounded flood for a bad row.
        renderer.refresh();
        renderer.refresh();
        renderer.refresh();

        assertThat(logger.warns).isEqualTo(1);
        assertThat(packets.spawnEntities).isEmpty();
    }

    @Test
    void reArmsTheBadTypeWarningWhenTheTypeIsChangedToAnotherBadValue() {
        PlayerMock viewer = server.addPlayer();
        CountingLogger logger = new CountingLogger();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), logger);
        renderer.render(npcAt(viewer, 1.0).withEntityType("NOT_A_REAL_TYPE")); // warn #1

        renderer.render(npcAt(viewer, 1.0).withEntityType("ALSO_NOT_REAL")); // a distinct bad value -> warn #2

        // A change to a different unresolvable value is a new problem the operator should see, so it re-warns
        // but only once for the new value, not per tick.
        renderer.refresh();
        renderer.refresh();
        assertThat(logger.warns).isEqualTo(2);
    }

    @Test
    void stopsWarningOnceTheBadTypeIsFixedToAValidOne() {
        PlayerMock viewer = server.addPlayer();
        CountingLogger logger = new CountingLogger();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), logger);
        renderer.render(npcAt(viewer, 1.0).withEntityType("NOT_A_REAL_TYPE")); // warn #1

        renderer.render(npcAt(viewer, 1.0).withEntityType("VILLAGER")); // fixed -> spawns, clears the warned record

        renderer.refresh();
        renderer.refresh();
        // The fix renders the mob and no further warns are logged for the now-valid NPC.
        assertThat(logger.warns).isEqualTo(1);
        assertThat(packets.spawnEntities).hasSize(1);
    }

    @Test
    void aSteadyMobNpcIsNotReSpawnedOnRefresh() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = newRenderer(new InlineScheduler(), new NoopLogger());
        renderer.render(npcAt(viewer, 1.0).withEntityType("VILLAGER")); // shown once as a mob

        renderer.refresh();
        renderer.refresh();

        // The reconcile idempotency holds for a mob too: a stationary, already-shown mob is not re-spawned.
        assertThat(packets.spawnEntities).hasSize(1);
    }

    // Wire the renderer over its spawner from the shared recording packets and the given logger, and the given
    // scheduler for the renderer's own region hops: the same 48/16 ranges the renderer was always built with.
    private NpcRenderer newRenderer(Scheduler scheduler, com.uxplima.uxmessentials.shared.application.port.Logger log) {
        NpcViewSpawner spawner = new NpcViewSpawner(packets, log);
        return new NpcRenderer(packets, spawner, scheduler, 48.0, 16.0);
    }

    private Npc npcAt(Player viewer, double offset) {
        return npc(locationOf(viewer).add(offset, 0, 0), null);
    }

    private static org.bukkit.Location locationOf(Player viewer) {
        return java.util.Objects.requireNonNull(viewer.getLocation(), "viewer location")
                .clone();
    }

    private Npc npc(org.bukkit.Location at, @Nullable NpcSkin skin) {
        // Build the position from the live Bukkit location (same world uid as the viewer) so the renderer's
        // same-world range check resolves a real distance rather than treating the worlds as infinitely apart.
        Position position = com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toPosition(at);
        return Npc.create(NpcName.of("guide"), position, skin, Instant.ofEpochMilli(1_000));
    }

    /** A fake {@link com.uxplima.uxmlib.packet.npc.NpcPackets} recording each built packet and the per-viewer sends. */
    private static final class RecordingNpcPackets implements com.uxplima.uxmlib.packet.npc.NpcPackets {
        private final AtomicInteger nextId = new AtomicInteger(1);
        private final List<TabAdd> tabAdds = new ArrayList<>();
        private final List<Spawn> spawns = new ArrayList<>();
        private final List<SpawnEntity> spawnEntities = new ArrayList<>();
        private final List<UUID> tabRemoves = new ArrayList<>();
        private final List<Integer> removes = new ArrayList<>();
        private final List<List<Object>> bundles = new ArrayList<>();
        private final List<Equipment> equipments = new ArrayList<>();
        private final List<Glow> glows = new ArrayList<>();
        private final List<SharedFlags> sharedFlags = new ArrayList<>();
        private final List<Silent> silents = new ArrayList<>();
        private final List<Collidable> collidables = new ArrayList<>();
        private final List<GlowColor> glowColors = new ArrayList<>();
        private final List<PoseSet> poses = new ArrayList<>();
        private final List<Scale> scales = new ArrayList<>();
        private final List<Baby> babies = new ArrayList<>();
        private final List<ZombieBaby> zombieBabies = new ArrayList<>();
        private final List<PiglinBaby> piglinBabies = new ArrayList<>();
        private final List<ZoglinBaby> zoglinBabies = new ArrayList<>();
        private final List<SlimeSize> slimeSizes = new ArrayList<>();
        private final List<Charged> chargeds = new ArrayList<>();
        private final List<VillagerData> villagerDatas = new ArrayList<>();
        private final List<HorseVariant> horseVariants = new ArrayList<>();
        private final List<LlamaVariant> llamaVariants = new ArrayList<>();
        private final List<SheepWool> sheepWools = new ArrayList<>();
        private final List<WolfCollar> wolfCollars = new ArrayList<>();
        private final List<ShulkerColor> shulkerColors = new ArrayList<>();
        private final List<ShulkerPeek> shulkerPeeks = new ArrayList<>();
        private final List<PandaGene> pandaGenes = new ArrayList<>();
        private final List<GoatScreaming> goatScreamings = new ArrayList<>();
        private final List<AllayDancing> allayDancings = new ArrayList<>();
        private final List<PiglinDancing> piglinDancings = new ArrayList<>();
        private final List<CamelDash> camelDashes = new ArrayList<>();
        private final List<BeeFlags> beeFlags = new ArrayList<>();
        private final List<VexCharging> vexChargings = new ArrayList<>();
        private final List<TropicalFishVariant> tropicalFishVariants = new ArrayList<>();
        private final List<ArmorStandFlags> armorStandFlags = new ArrayList<>();
        private final List<ArmorStandPose> armorStandPoses = new ArrayList<>();
        private final List<InteractionSize> interactionSizes = new ArrayList<>();
        private final List<BlockDisplayState> blockDisplayStates = new ArrayList<>();
        private final List<ItemDisplayItem> itemDisplayItems = new ArrayList<>();
        private final List<TextDisplayText> textDisplayTexts = new ArrayList<>();
        private final List<DisplayScale> displayScales = new ArrayList<>();
        private final List<DisplayScaleXyz> displayScaleXyzs = new ArrayList<>();
        private final List<DisplayTranslation> displayTranslations = new ArrayList<>();
        private final List<DisplayBillboard> displayBillboards = new ArrayList<>();
        private final List<TextDisplayBackground> textDisplayBackgrounds = new ArrayList<>();
        private final List<TextDisplayLineWidth> textDisplayLineWidths = new ArrayList<>();
        private final List<ParrotVariant> parrotVariants = new ArrayList<>();
        private final List<AxolotlVariant> axolotlVariants = new ArrayList<>();
        private final List<FoxType> foxTypes = new ArrayList<>();
        private final List<RabbitType> rabbitTypes = new ArrayList<>();
        private final List<CatVariant> catVariants = new ArrayList<>();
        private final List<FrogVariant> frogVariants = new ArrayList<>();
        private final List<NameVariantRec> wolfVariants = new ArrayList<>();
        private final List<NameVariantRec> chickenVariants = new ArrayList<>();
        private final List<NameVariantRec> cowVariants = new ArrayList<>();
        private final List<NameVariantRec> pigVariants = new ArrayList<>();
        private final List<BoolRec> axolotlPlayingDeads = new ArrayList<>();
        private final List<BoolRec> raiderCelebratings = new ArrayList<>();
        private final List<BoolRec> tameableSittings = new ArrayList<>();
        private final List<BoolRec> pandaEatings = new ArrayList<>();
        private final List<FoxRec> foxFlagsList = new ArrayList<>();
        private final List<StateRec> snifferStates = new ArrayList<>();
        private final List<StateRec> armadilloStates = new ArrayList<>();
        private final List<UseItemRec> useItems = new ArrayList<>();
        private final List<FrozenRec> frozenTicks = new ArrayList<>();
        // When set, catVariant/frogVariant return null to mimic the lib's fail-soft signal (registry not resolved).
        private boolean dropNameVariants;
        private final List<Sent> sent = new ArrayList<>();

        @Override
        public int allocateEntityId() {
            return nextId.getAndIncrement();
        }

        @Override
        public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin) {
            return tabAdd(profileId, name, skin, true);
        }

        @Override
        public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin, boolean listed) {
            TabAdd packet = new TabAdd(profileId, name, skin, listed);
            tabAdds.add(packet);
            return packet;
        }

        @Override
        public Object tabRemove(UUID profileId) {
            return new TabRemove(profileId);
        }

        @Override
        public Object spawnPlayer(int entityId, UUID profileId, double x, double y, double z, float yaw, float pitch) {
            Spawn packet = new Spawn(entityId, profileId, x, y, z);
            spawns.add(packet);
            return packet;
        }

        @Override
        public Object spawnEntity(
                int entityId,
                UUID entityUuid,
                String entityTypeKey,
                double x,
                double y,
                double z,
                float yaw,
                float pitch) {
            SpawnEntity packet = new SpawnEntity(entityId, entityUuid, entityTypeKey, x, y, z);
            spawnEntities.add(packet);
            return packet;
        }

        @Override
        public Object headLook(int entityId, float yaw) {
            return new Look(entityId, yaw, 0f);
        }

        @Override
        public Object bodyLook(int entityId, float yaw, float pitch) {
            return new Look(entityId, yaw, pitch);
        }

        @Override
        public Object teleport(int entityId, double x, double y, double z, float yaw, float pitch) {
            return new Look(entityId, yaw, pitch);
        }

        @Override
        public Object remove(int entityId) {
            return new Remove(entityId);
        }

        @Override
        public Object equipment(int entityId, Map<com.uxplima.uxmlib.packet.npc.EquipmentSlot, ItemStack> items) {
            Equipment packet = new Equipment(entityId, new EnumMap<>(items));
            equipments.add(packet);
            return packet;
        }

        @Override
        public Object glow(int entityId, boolean glowing) {
            Glow packet = new Glow(entityId, glowing);
            glows.add(packet);
            return packet;
        }

        @Override
        public Object sharedFlags(int entityId, boolean onFire, boolean glowing, boolean invisible) {
            SharedFlags packet = new SharedFlags(entityId, onFire, glowing, invisible);
            sharedFlags.add(packet);
            return packet;
        }

        @Override
        public Object silent(int entityId, boolean silent) {
            Silent packet = new Silent(entityId, silent);
            silents.add(packet);
            return packet;
        }

        @Override
        public Object team(
                String teamName,
                String memberName,
                @Nullable NamedColor color,
                boolean collidable,
                boolean hideNametag) {
            Collidable packet = new Collidable(teamName, memberName, color, collidable, hideNametag);
            collidables.add(packet);
            return packet;
        }

        @Override
        public Object glowColor(String teamName, String memberName, @Nullable NamedColor color) {
            GlowColor packet = new GlowColor(teamName, memberName, color);
            glowColors.add(packet);
            return packet;
        }

        @Override
        public Object pose(int entityId, com.uxplima.uxmlib.packet.npc.NpcPose pose) {
            PoseSet packet = new PoseSet(entityId, pose);
            poses.add(packet);
            return packet;
        }

        @Override
        public Object scale(int entityId, double scale) {
            Scale packet = new Scale(entityId, scale);
            scales.add(packet);
            return packet;
        }

        @Override
        public Object baby(int entityId, boolean baby) {
            Baby packet = new Baby(entityId, baby);
            babies.add(packet);
            return packet;
        }

        @Override
        public Object zombieBaby(int entityId, boolean baby) {
            ZombieBaby packet = new ZombieBaby(entityId, baby);
            zombieBabies.add(packet);
            return packet;
        }

        @Override
        public Object piglinBaby(int entityId, boolean baby) {
            PiglinBaby packet = new PiglinBaby(entityId, baby);
            piglinBabies.add(packet);
            return packet;
        }

        @Override
        public Object zoglinBaby(int entityId, boolean baby) {
            ZoglinBaby packet = new ZoglinBaby(entityId, baby);
            zoglinBabies.add(packet);
            return packet;
        }

        @Override
        public Object villagerData(int entityId, String type, String profession, int level) {
            VillagerData packet = new VillagerData(entityId, type, profession, level);
            villagerDatas.add(packet);
            return packet;
        }

        @Override
        public Object slimeSize(int entityId, int size) {
            SlimeSize packet = new SlimeSize(entityId, size);
            slimeSizes.add(packet);
            return packet;
        }

        @Override
        public Object charged(int entityId, boolean charged) {
            Charged packet = new Charged(entityId, charged);
            chargeds.add(packet);
            return packet;
        }

        @Override
        public Object horseVariant(int entityId, int color, int markings) {
            HorseVariant packet = new HorseVariant(entityId, color, markings);
            horseVariants.add(packet);
            return packet;
        }

        @Override
        public Object llamaVariant(int entityId, int variant) {
            LlamaVariant packet = new LlamaVariant(entityId, variant);
            llamaVariants.add(packet);
            return packet;
        }

        @Override
        public Object sheepWool(int entityId, int color, boolean sheared) {
            SheepWool packet = new SheepWool(entityId, color, sheared);
            sheepWools.add(packet);
            return packet;
        }

        @Override
        public Object wolfCollar(int entityId, int color) {
            WolfCollar packet = new WolfCollar(entityId, color);
            wolfCollars.add(packet);
            return packet;
        }

        @Override
        public Object shulkerColor(int entityId, int color) {
            ShulkerColor packet = new ShulkerColor(entityId, color);
            shulkerColors.add(packet);
            return packet;
        }

        @Override
        public Object shulkerPeek(int entityId, int peek) {
            ShulkerPeek packet = new ShulkerPeek(entityId, peek);
            shulkerPeeks.add(packet);
            return packet;
        }

        @Override
        public Object shulkerAttachFace(int entityId, String face) {
            return new Object();
        }

        @Override
        public Object spinAttack(int entityId, boolean spinning) {
            return new Object();
        }

        @Override
        public Object sleepingPosition(int entityId, int x, int y, int z) {
            return new Object();
        }

        @Override
        public Object noSleepingPosition(int entityId) {
            return new Object();
        }

        @Override
        public Object pandaGene(int entityId, int gene) {
            PandaGene packet = new PandaGene(entityId, gene);
            pandaGenes.add(packet);
            return packet;
        }

        @Override
        public Object goatScreaming(int entityId, boolean screaming) {
            GoatScreaming packet = new GoatScreaming(entityId, screaming);
            goatScreamings.add(packet);
            return packet;
        }

        @Override
        public Object allayDancing(int entityId, boolean dancing) {
            AllayDancing packet = new AllayDancing(entityId, dancing);
            allayDancings.add(packet);
            return packet;
        }

        @Override
        public Object piglinDancing(int entityId, boolean dancing) {
            PiglinDancing packet = new PiglinDancing(entityId, dancing);
            piglinDancings.add(packet);
            return packet;
        }

        @Override
        public Object camelDash(int entityId, boolean dashing) {
            CamelDash packet = new CamelDash(entityId, dashing);
            camelDashes.add(packet);
            return packet;
        }

        @Override
        public Object beeFlags(int entityId, boolean nectar, boolean rolling, boolean stung) {
            BeeFlags packet = new BeeFlags(entityId, nectar, rolling, stung);
            beeFlags.add(packet);
            return packet;
        }

        @Override
        public Object vexCharging(int entityId, boolean charging) {
            VexCharging packet = new VexCharging(entityId, charging);
            vexChargings.add(packet);
            return packet;
        }

        @Override
        public Object tropicalFishVariant(int entityId, int variantIndex) {
            TropicalFishVariant packet = new TropicalFishVariant(entityId, variantIndex);
            tropicalFishVariants.add(packet);
            return packet;
        }

        @Override
        public Object armorStandFlags(
                int entityId, boolean small, boolean showArms, boolean noBasePlate, boolean marker) {
            ArmorStandFlags packet = new ArmorStandFlags(entityId, small, showArms, noBasePlate, marker);
            armorStandFlags.add(packet);
            return packet;
        }

        @Override
        public Object armorStandPose(
                int entityId, com.uxplima.uxmlib.packet.npc.ArmorStandPart part, float x, float y, float z) {
            ArmorStandPose packet = new ArmorStandPose(entityId, part, x, y, z);
            armorStandPoses.add(packet);
            return packet;
        }

        @Override
        public Object interactionSize(int entityId, float width, float height) {
            InteractionSize packet = new InteractionSize(entityId, width, height);
            interactionSizes.add(packet);
            return packet;
        }

        @Override
        public Object blockDisplayState(int entityId, org.bukkit.block.data.BlockData blockData) {
            BlockDisplayState packet = new BlockDisplayState(entityId, blockData.getMaterial());
            blockDisplayStates.add(packet);
            return packet;
        }

        @Override
        public Object itemDisplayItem(int entityId, org.bukkit.inventory.ItemStack item) {
            ItemDisplayItem packet = new ItemDisplayItem(entityId, item.getType());
            itemDisplayItems.add(packet);
            return packet;
        }

        @Override
        public Object textDisplayText(int entityId, net.kyori.adventure.text.Component text) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(text);
            TextDisplayText packet = new TextDisplayText(entityId, plain);
            textDisplayTexts.add(packet);
            return packet;
        }

        @Override
        public Object displayScale(int entityId, float scale) {
            DisplayScale packet = new DisplayScale(entityId, scale);
            displayScales.add(packet);
            return packet;
        }

        @Override
        public Object displayScale(int entityId, float x, float y, float z) {
            DisplayScaleXyz packet = new DisplayScaleXyz(entityId, x, y, z);
            displayScaleXyzs.add(packet);
            return packet;
        }

        @Override
        public Object displayTranslation(int entityId, float x, float y, float z) {
            DisplayTranslation packet = new DisplayTranslation(entityId, x, y, z);
            displayTranslations.add(packet);
            return packet;
        }

        @Override
        public Object displayBillboard(int entityId, byte constraint) {
            DisplayBillboard packet = new DisplayBillboard(entityId, constraint);
            displayBillboards.add(packet);
            return packet;
        }

        @Override
        public Object textDisplayBackground(int entityId, int argb) {
            TextDisplayBackground packet = new TextDisplayBackground(entityId, argb);
            textDisplayBackgrounds.add(packet);
            return packet;
        }

        @Override
        public Object textDisplayLineWidth(int entityId, int lineWidth) {
            TextDisplayLineWidth packet = new TextDisplayLineWidth(entityId, lineWidth);
            textDisplayLineWidths.add(packet);
            return packet;
        }

        @Override
        public Object parrotVariant(int entityId, int variant) {
            ParrotVariant packet = new ParrotVariant(entityId, variant);
            parrotVariants.add(packet);
            return packet;
        }

        @Override
        public Object axolotlVariant(int entityId, int variant) {
            AxolotlVariant packet = new AxolotlVariant(entityId, variant);
            axolotlVariants.add(packet);
            return packet;
        }

        @Override
        public Object foxType(int entityId, int type) {
            FoxType packet = new FoxType(entityId, type);
            foxTypes.add(packet);
            return packet;
        }

        @Override
        public Object rabbitType(int entityId, int type) {
            RabbitType packet = new RabbitType(entityId, type);
            rabbitTypes.add(packet);
            return packet;
        }

        @Override
        public @Nullable Object catVariant(int entityId, String name) {
            CatVariant packet = new CatVariant(entityId, name);
            catVariants.add(packet);
            // The real lib resolves a holder off the live registry and returns null when it cannot; mimic the
            // resolved case by default, and the dropped case when the test asks for it.
            return dropNameVariants ? null : packet;
        }

        @Override
        public @Nullable Object frogVariant(int entityId, String name) {
            FrogVariant packet = new FrogVariant(entityId, name);
            frogVariants.add(packet);
            return dropNameVariants ? null : packet;
        }

        @Override
        public @Nullable Object wolfVariant(int entityId, String name) {
            NameVariantRec packet = new NameVariantRec(entityId, name);
            wolfVariants.add(packet);
            return dropNameVariants ? null : packet;
        }

        @Override
        public @Nullable Object chickenVariant(int entityId, String name) {
            NameVariantRec packet = new NameVariantRec(entityId, name);
            chickenVariants.add(packet);
            return dropNameVariants ? null : packet;
        }

        @Override
        public @Nullable Object cowVariant(int entityId, String name) {
            NameVariantRec packet = new NameVariantRec(entityId, name);
            cowVariants.add(packet);
            return dropNameVariants ? null : packet;
        }

        @Override
        public @Nullable Object pigVariant(int entityId, String name) {
            NameVariantRec packet = new NameVariantRec(entityId, name);
            pigVariants.add(packet);
            return dropNameVariants ? null : packet;
        }

        @Override
        public Object axolotlPlayingDead(int entityId, boolean playingDead) {
            BoolRec packet = new BoolRec(entityId, playingDead);
            axolotlPlayingDeads.add(packet);
            return packet;
        }

        @Override
        public Object raiderCelebrating(int entityId, boolean celebrating) {
            BoolRec packet = new BoolRec(entityId, celebrating);
            raiderCelebratings.add(packet);
            return packet;
        }

        @Override
        public Object tameableSitting(int entityId, boolean sitting) {
            BoolRec packet = new BoolRec(entityId, sitting);
            tameableSittings.add(packet);
            return packet;
        }

        @Override
        public Object foxFlags(int entityId, boolean sitting, boolean sleeping, boolean crouching) {
            FoxRec packet = new FoxRec(entityId, sitting, sleeping, crouching);
            foxFlagsList.add(packet);
            return packet;
        }

        @Override
        public Object pandaEating(int entityId, boolean eating) {
            BoolRec packet = new BoolRec(entityId, eating);
            pandaEatings.add(packet);
            return packet;
        }

        @Override
        public Object snifferState(int entityId, String state) {
            StateRec packet = new StateRec(entityId, state);
            snifferStates.add(packet);
            return packet;
        }

        @Override
        public Object armadilloState(int entityId, String state) {
            StateRec packet = new StateRec(entityId, state);
            armadilloStates.add(packet);
            return packet;
        }

        @Override
        public Object usingItem(int entityId, boolean using, boolean offHand) {
            UseItemRec packet = new UseItemRec(entityId, using, offHand);
            useItems.add(packet);
            return packet;
        }

        @Override
        public Object frozenTicks(int entityId, int ticks) {
            FrozenRec packet = new FrozenRec(entityId, ticks);
            frozenTicks.add(packet);
            return packet;
        }

        @Override
        public Object glowColorRemove(String teamName) {
            return new GlowColorRemove(teamName);
        }

        @Override
        public Object bundle(List<Object> built) {
            List<Object> copy = List.copyOf(built);
            bundles.add(copy);
            return new Bundle(copy);
        }

        @Override
        public void send(Player viewer, Object packet) {
            sent.add(new Sent(viewer.getUniqueId(), packet));
            record(packet);
        }

        private void record(Object packet) {
            if (packet instanceof TabRemove tabRemove) {
                tabRemoves.add(tabRemove.profileId());
            } else if (packet instanceof Remove remove) {
                removes.add(remove.entityId());
            }
        }

        List<Bundle> bundlesSentTo(UUID viewer) {
            return sentTo(viewer, Bundle.class);
        }

        List<Look> looksSentTo(UUID viewer) {
            return sentTo(viewer, Look.class);
        }

        List<TabRemove> tabRemovesSentTo(UUID viewer) {
            return sentTo(viewer, TabRemove.class);
        }

        List<Remove> removesSentTo(UUID viewer) {
            return sentTo(viewer, Remove.class);
        }

        List<GlowColorRemove> glowColorRemovesSentTo(UUID viewer) {
            return sentTo(viewer, GlowColorRemove.class);
        }

        private <T> List<T> sentTo(UUID viewer, Class<T> type) {
            List<T> matches = new ArrayList<>();
            for (Sent s : sent) {
                if (s.viewer().equals(viewer) && type.isInstance(s.packet())) {
                    matches.add(type.cast(s.packet()));
                }
            }
            return matches;
        }

        private record Sent(UUID viewer, Object packet) {}

        private record TabAdd(
                UUID profileId, String name, @Nullable TabSkin skin, boolean listed) {}

        private record TabRemove(UUID profileId) {}

        private record Spawn(int entityId, UUID profileId, double x, double y, double z) {}

        private record SpawnEntity(int entityId, UUID entityUuid, String entityTypeKey, double x, double y, double z) {}

        private record Look(int entityId, float yaw, float pitch) {}

        private record Remove(int entityId) {}

        private record Equipment(int entityId, Map<com.uxplima.uxmlib.packet.npc.EquipmentSlot, ItemStack> items) {}

        private record Glow(int entityId, boolean glowing) {}

        private record SharedFlags(int entityId, boolean onFire, boolean glowing, boolean invisible) {}

        private record Silent(int entityId, boolean silent) {}

        private record Collidable(
                String teamName,
                String memberName,
                @Nullable NamedColor color,
                boolean collidable,
                boolean hideNametag) {}

        private record PoseSet(int entityId, com.uxplima.uxmlib.packet.npc.NpcPose pose) {}

        private record Scale(int entityId, double scale) {}

        private record Baby(int entityId, boolean baby) {}

        private record ZombieBaby(int entityId, boolean baby) {}

        private record PiglinBaby(int entityId, boolean baby) {}

        private record ZoglinBaby(int entityId, boolean baby) {}

        private record SlimeSize(int entityId, int size) {}

        private record Charged(int entityId, boolean charged) {}

        private record HorseVariant(int entityId, int color, int markings) {}

        private record LlamaVariant(int entityId, int variant) {}

        private record SheepWool(int entityId, int color, boolean sheared) {}

        private record WolfCollar(int entityId, int color) {}

        private record ShulkerColor(int entityId, int color) {}

        private record ShulkerPeek(int entityId, int peek) {}

        private record PandaGene(int entityId, int gene) {}

        private record GoatScreaming(int entityId, boolean screaming) {}

        private record AllayDancing(int entityId, boolean dancing) {}

        private record PiglinDancing(int entityId, boolean dancing) {}

        private record CamelDash(int entityId, boolean dashing) {}

        private record BeeFlags(int entityId, boolean nectar, boolean rolling, boolean stung) {}

        private record VexCharging(int entityId, boolean charging) {}

        private record TropicalFishVariant(int entityId, int variantIndex) {}

        private record ArmorStandFlags(
                int entityId, boolean small, boolean showArms, boolean noBasePlate, boolean marker) {}

        private record ArmorStandPose(
                int entityId, com.uxplima.uxmlib.packet.npc.ArmorStandPart part, float x, float y, float z) {}

        private record InteractionSize(int entityId, float width, float height) {}

        private record BlockDisplayState(int entityId, org.bukkit.Material material) {}

        private record ItemDisplayItem(int entityId, org.bukkit.Material material) {}

        private record TextDisplayText(int entityId, String plain) {}

        private record DisplayScale(int entityId, float scale) {}

        private record DisplayScaleXyz(int entityId, float x, float y, float z) {}

        private record DisplayTranslation(int entityId, float x, float y, float z) {}

        private record DisplayBillboard(int entityId, byte constraint) {}

        private record TextDisplayBackground(int entityId, int argb) {}

        private record TextDisplayLineWidth(int entityId, int lineWidth) {}

        private record ParrotVariant(int entityId, int variant) {}

        private record AxolotlVariant(int entityId, int variant) {}

        private record FoxType(int entityId, int type) {}

        private record RabbitType(int entityId, int type) {}

        private record CatVariant(int entityId, String name) {}

        private record NameVariantRec(int entityId, String name) {}

        private record BoolRec(int entityId, boolean value) {}

        private record FoxRec(int entityId, boolean sitting, boolean sleeping, boolean crouching) {}

        private record StateRec(int entityId, String state) {}

        private record UseItemRec(int entityId, boolean using, boolean offHand) {}

        private record FrozenRec(int entityId, int ticks) {}

        private record FrogVariant(int entityId, String name) {}

        private record VillagerData(int entityId, String type, String profession, int level) {}

        private record GlowColor(
                String teamName,
                String memberName,
                @Nullable NamedColor color) {}

        private record GlowColorRemove(String teamName) {}

        private record Bundle(List<Object> packets) {}
    }

    /** A logger that swallows every line: the renderer's fail-soft warn has nothing to assert beyond not throwing. */
    private static final class NoopLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A logger that counts warn lines so a test can assert a bad stored type is logged once, not per refresh tick. */
    private static final class CountingLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        private int warns;

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warns++;
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A scheduler that runs every hop inline so the spawn and the deferred tab-hide both complete immediately. */
    private static class InlineScheduler implements Scheduler {
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
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** A scheduler that runs every hop inline but counts {@code asyncAfter} calls, so a test can prove the spawn
     * path defers nothing now that the tab entry is kept rather than removed on a delay. */
    private static final class CountingDelayScheduler extends InlineScheduler {
        private int asyncAfterCalls;

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            asyncAfterCalls++;
            task.run();
        }
    }
}

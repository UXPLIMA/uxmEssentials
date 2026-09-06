package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.uxplima.uxmessentials.survival.application.port.RandomSource;
import com.uxplima.uxmessentials.survival.domain.DropChance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Coverage of head-drop: a player killed by a player always drops their own player head, a mob drops its vanilla head
 * exactly when the seeded draw lands within the configured chance, and a mob with no vanilla head drops nothing.
 */
class HeadDropListenerTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPlayerKilledByAPlayerAlwaysDropsThatPlayersHead() {
        PlayerMock victim = server.addPlayer("Victim");
        PlayerMock killer = server.addPlayer("Killer");
        HeadDropListener listener = new HeadDropListener(true, new DropChance(0.0), Map.of(), rejectingRandom());

        Optional<ItemStack> head = listener.headFor(victim, killer);

        assertThat(head).isPresent();
        assertThat(head.get().getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(head.get().getItemMeta())
                .isInstanceOfSatisfying(
                        SkullMeta.class,
                        meta -> assertThat(meta.getOwningPlayer())
                                .isNotNull()
                                .extracting(OfflinePlayer::getName)
                                .isEqualTo(victim.getName()));
    }

    @Test
    void aPlayerWithoutAPlayerKillerDropsNoHead() {
        PlayerMock victim = server.addPlayer("Victim");
        HeadDropListener listener = new HeadDropListener(true, new DropChance(0.0), Map.of(), rejectingRandom());

        assertThat(listener.headFor(victim, null)).isEmpty();
    }

    @Test
    void pvpHeadsAreSuppressedWhenPlayerHeadOnPvpIsOff() {
        PlayerMock victim = server.addPlayer("Victim");
        PlayerMock killer = server.addPlayer("Killer");
        HeadDropListener listener = new HeadDropListener(false, new DropChance(0.0), Map.of(), rejectingRandom());

        assertThat(listener.headFor(victim, killer)).isEmpty();
    }

    @Test
    void aMobDropsItsHeadWhenTheSeededDrawLandsWithinTheChance() {
        LivingEntity zombie = spawn(EntityType.ZOMBIE);
        // 50% resolves to a threshold of 5000; a draw of 3000 lands within it, so the head drops.
        HeadDropListener listener = new HeadDropListener(false, new DropChance(50.0), Map.of(), fixedDraw(3000));

        Optional<ItemStack> head = listener.headFor(zombie, null);

        assertThat(head).isPresent();
        assertThat(head.get().getType()).isEqualTo(Material.ZOMBIE_HEAD);
    }

    @Test
    void aMobDropsNoHeadWhenTheSeededDrawMissesTheChance() {
        LivingEntity zombie = spawn(EntityType.ZOMBIE);
        // A draw of 7000 is beyond the 5000 threshold, so nothing drops.
        HeadDropListener listener = new HeadDropListener(false, new DropChance(50.0), Map.of(), fixedDraw(7000));

        assertThat(listener.headFor(zombie, null)).isEmpty();
    }

    @Test
    void aPerMobOverrideBeatsTheDefaultChance() {
        LivingEntity zombie = spawn(EntityType.ZOMBIE);
        // Default chance is zero, but the zombie override always drops.
        HeadDropListener listener = new HeadDropListener(
                false, new DropChance(0.0), Map.of(EntityType.ZOMBIE, new DropChance(100.0)), fixedDraw(9999));

        assertThat(listener.headFor(zombie, null)).isPresent();
    }

    @Test
    void aMobWithoutAVanillaHeadDropsNothingEvenAtFullChance() {
        LivingEntity cow = spawn(EntityType.COW);
        HeadDropListener listener = new HeadDropListener(false, new DropChance(100.0), Map.of(), fixedDraw(0));

        assertThat(listener.headFor(cow, null)).isEmpty();
    }

    private LivingEntity spawn(EntityType type) {
        return (LivingEntity) world.spawnEntity(new Location(world, 0, 64, 0), type);
    }

    /** A source that returns a fixed draw regardless of the bound. */
    private static RandomSource fixedDraw(int value) {
        return bound -> value;
    }

    /** A source that fails if drawn from: the player-head path must never roll a chance. */
    private static RandomSource rejectingRandom() {
        return bound -> {
            throw new AssertionError("the player-head path must not draw a chance");
        };
    }
}

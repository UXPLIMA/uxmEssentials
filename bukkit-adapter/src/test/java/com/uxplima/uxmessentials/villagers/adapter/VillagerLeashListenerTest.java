package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerLeashListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of villager leashing: a permitted player holding a lead leashes the villager to themselves (the
 * lead is consumed and the interaction cancelled so the trade window does not open), while a disabled feature, a player
 * without the permission, and an empty hand all leave the villager unleashed.
 *
 * <p>MockBukkit's {@code isLeashed()} reports {@code leashHolder instanceof Mob}, so a <em>player</em> holder never
 * reads back as leashed even though {@code setLeashHolder} succeeds and stores it. The leash's success is therefore
 * asserted through its observable side effects, the interaction is cancelled and one lead is consumed, both of which
 * the listener performs only after {@code setLeashHolder} returns {@code true}.
 */
class VillagerLeashListenerTest {

    private static final String LEASH_PERMISSION = "uxmessentials.villagers.leash";

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private Villager villager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void leashesTheVillagerWhenEnabledHeldLeadAndPermission() {
        grantLeashPermission();
        player.getInventory().setItemInMainHand(new ItemStack(Material.LEAD, 2));
        PlayerInteractEntityEvent event = leadClick();

        listener(true).onInteract(event);

        // The lead attached (setLeashHolder returned true), so the click was cancelled and one lead was consumed.
        assertThat(event.isCancelled()).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1); // one lead consumed
    }

    @Test
    void aDisabledFeatureLeashesNothing() {
        grantLeashPermission();
        player.getInventory().setItemInMainHand(new ItemStack(Material.LEAD));
        PlayerInteractEntityEvent event = leadClick();

        listener(false).onInteract(event);

        assertThat(villager.isLeashed()).isFalse();
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aPlayerWithoutPermissionLeashesNothing() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.LEAD));
        PlayerInteractEntityEvent event = leadClick();

        listener(true).onInteract(event);

        assertThat(villager.isLeashed()).isFalse();
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aClickWithoutALeadLeashesNothing() {
        grantLeashPermission();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        PlayerInteractEntityEvent event = leadClick();

        listener(true).onInteract(event);

        assertThat(villager.isLeashed()).isFalse();
        assertThat(event.isCancelled()).isFalse();
    }

    private VillagerLeashListener listener(boolean enabled) {
        return new VillagerLeashListener(enabled, LEASH_PERMISSION);
    }

    private void grantLeashPermission() {
        player.addAttachment(plugin, LEASH_PERMISSION, true);
    }

    private PlayerInteractEntityEvent leadClick() {
        return new PlayerInteractEntityEvent(player, villager, EquipmentSlot.HAND);
    }
}

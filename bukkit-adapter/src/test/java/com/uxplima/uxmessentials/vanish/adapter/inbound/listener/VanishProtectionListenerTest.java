package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.testing.DamageEvents;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.adapter.outbound.PdcVanishPickup;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link VanishProtectionListener}: a vanished player takes no damage, does not lose hunger,
 * is dropped as a mob target, and does not pick up items unless their preference says so, each behaviour behind its
 * config toggle, and each a no-op when the toggle is off or the player is visible.
 */
class VanishProtectionListenerTest {

    // enabled, silentChests, pickupItems, nightVision, allowFlight, noHunger, noDamage, mobTarget, then the Phase 4
    // fake-join-quit / action-bar / join-vanished toggles and the four fake-message templates (unused by this
    // listener).
    private static final VanishConfig ALL_ON = new VanishConfig(
            true, true, false, true, true, true, true, true, true, true, true, "", "", "", "", false, true, 1);
    private static final VanishConfig ALL_OFF = new VanishConfig(
            true, false, false, false, false, false, false, false, false, false, false, "", "", "", "", false, true, 1);

    private ServerMock server;
    private InMemoryVanishStore store;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        store = new InMemoryVanishStore();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void incomingDamageOnAVanishedPlayerIsCancelledWhenNoDamageOn() {
        PlayerMock alice = vanished("Alice");
        EntityDamageEvent event = damage(alice);

        listener(ALL_ON).onDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void damageIsNotCancelledWhenNoDamageOff() {
        PlayerMock alice = vanished("Alice");
        EntityDamageEvent event = damage(alice);

        listener(ALL_OFF).onDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void damageIsNotCancelledForAVisiblePlayer() {
        PlayerMock bob = server.addPlayer("Bob"); // not vanished
        EntityDamageEvent event = damage(bob);

        listener(ALL_ON).onDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aMobTargetOnAVanishedPlayerIsDropped() {
        PlayerMock alice = vanished("Alice");
        LivingEntity zombie = (LivingEntity) server.getWorld("world").spawnEntity(spot(), EntityType.ZOMBIE);
        EntityTargetLivingEntityEvent event =
                new EntityTargetLivingEntityEvent(zombie, alice, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        listener(ALL_ON).onTarget(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(event.getTarget()).isNull();
    }

    @Test
    void aMobTargetIsKeptWhenMobTargetOff() {
        PlayerMock alice = vanished("Alice");
        LivingEntity zombie = (LivingEntity) server.getWorld("world").spawnEntity(spot(), EntityType.ZOMBIE);
        EntityTargetLivingEntityEvent event =
                new EntityTargetLivingEntityEvent(zombie, alice, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        listener(ALL_OFF).onTarget(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(event.getTarget()).isEqualTo(alice);
    }

    @Test
    void hungerDrainIsCancelledButAnEatIsNot() {
        PlayerMock alice = vanished("Alice");
        alice.setFoodLevel(20);
        VanishProtectionListener listener = listener(ALL_ON);

        FoodLevelChangeEvent drain = new FoodLevelChangeEvent(alice, 18, new ItemStack(org.bukkit.Material.AIR));
        listener.onHunger(drain);
        assertThat(drain.isCancelled()).isTrue(); // the drain is blocked

        alice.setFoodLevel(10);
        FoodLevelChangeEvent eat = new FoodLevelChangeEvent(alice, 16, new ItemStack(org.bukkit.Material.APPLE));
        listener.onHunger(eat);
        assertThat(eat.isCancelled()).isFalse(); // a deliberate rise is left alone
    }

    @Test
    void aVanishedPlayerDoesNotPickUpItemsByDefaultButDoesAfterToggle() {
        PlayerMock alice = vanished("Alice");
        PdcVanishPickup pickup = new PdcVanishPickup(server, false);
        VanishProtectionListener listener = new VanishProtectionListener(store, ALL_ON, pickup);

        EntityPickupItemEvent first = pickupEvent(alice);
        listener.onEntityPickup(first);
        assertThat(first.isCancelled()).isTrue(); // off by default

        pickup.toggle(BukkitRefs.toRef(alice)); // /vanish pickup on

        EntityPickupItemEvent second = pickupEvent(alice);
        listener.onEntityPickup(second);
        assertThat(second.isCancelled()).isFalse(); // honoured after toggle
    }

    private VanishProtectionListener listener(VanishConfig config) {
        return new VanishProtectionListener(store, config, new PdcVanishPickup(server, false));
    }

    private PlayerMock vanished(String name) {
        PlayerMock player = server.addPlayer(name);
        store.vanish(player.getUniqueId(), VanishLevel.DEFAULT);
        return player;
    }

    private EntityPickupItemEvent pickupEvent(PlayerMock player) {
        Item item = player.getWorld().dropItem(player.getLocation(), new ItemStack(org.bukkit.Material.STONE));
        return new EntityPickupItemEvent(player, item, 0);
    }

    private Location spot() {
        return new Location(server.getWorld("world"), 0, 64, 0);
    }

    private static EntityDamageEvent damage(PlayerMock player) {
        return DamageEvents.of(
                player,
                DamageCause.CUSTOM,
                DamageSource.builder(DamageType.GENERIC).build(),
                4.0);
    }
}

package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerBucketListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerBucketItem;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of villager pickup and placement: a permitted, sneaking player's click removes the villager and
 * hands them the captured-villager item, while a disabled feature, a player without the permission, and a non-sneaking
 * click all leave the villager alone. Placing the item back spawns a villager carrying the restored trades.
 */
class VillagerBucketListenerTest {

    private static final String BUCKET_PERMISSION = "uxmessentials.villagers.bucket";

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private Villager villager;
    private VillagerBucketItem bucketItem;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        bucketItem = new VillagerBucketItem();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPermittedSneakingPlayerPicksTheVillagerUp() {
        grantBucketPermission();
        player.setSneaking(true);
        PlayerInteractEntityEvent event = pickupClick();

        listener(true).onPickup(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(villager.isValid()).isFalse();
        assertThat(capturedItemIn(player)).isNotNull();
    }

    @Test
    void aDisabledFeaturePicksNothingUp() {
        grantBucketPermission();
        player.setSneaking(true);
        PlayerInteractEntityEvent event = pickupClick();

        listener(false).onPickup(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(villager.isValid()).isTrue();
        assertThat(capturedItemIn(player)).isNull();
    }

    @Test
    void aPlayerWithoutPermissionPicksNothingUp() {
        player.setSneaking(true);
        PlayerInteractEntityEvent event = pickupClick();

        listener(true).onPickup(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(villager.isValid()).isTrue();
    }

    @Test
    void aNonSneakingClickPicksNothingUp() {
        grantBucketPermission();
        player.setSneaking(false);
        PlayerInteractEntityEvent event = pickupClick();

        listener(true).onPickup(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(villager.isValid()).isTrue();
    }

    @Test
    void placingACapturedVillagerSpawnsItBackWithTheSameTrades() {
        ItemStack captured = bucketItem.capture(villager, net.kyori.adventure.text.Component.text("Captured Villager"));
        player.getInventory().setItemInMainHand(captured);
        Block block = world.getBlockAt(0, 63, 0);
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, captured, block, BlockFace.UP, EquipmentSlot.HAND);

        listener(true).onPlace(event);

        assertThat(event.useItemInHand()).isEqualTo(org.bukkit.event.Event.Result.DENY);
        Optional<Villager> spawned = world.getEntitiesByClass(Villager.class).stream()
                .filter(other -> !other.equals(villager))
                .findFirst();
        assertThat(spawned).isPresent();
        assertThat(spawned.get().getProfession()).isEqualTo(villager.getProfession());
    }

    private VillagerBucketListener listener(boolean enabled) {
        return new VillagerBucketListener(enabled, BUCKET_PERMISSION, bucketItem, new GuiText(new KeyMessages()));
    }

    private void grantBucketPermission() {
        player.addAttachment(plugin, BUCKET_PERMISSION, true);
    }

    private PlayerInteractEntityEvent pickupClick() {
        return new PlayerInteractEntityEvent(player, villager, EquipmentSlot.HAND);
    }

    private @Nullable ItemStack capturedItemIn(PlayerMock target) {
        return Arrays.stream(target.getInventory().getContents())
                .filter(item -> item != null && bucketItem.isBucket(item))
                .findFirst()
                .orElse(null);
    }

    /** Resolves each key to its own id: enough for the GuiText path the tests do not assert text on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}

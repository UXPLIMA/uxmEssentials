package com.uxplima.uxmessentials.kits.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.outbound.BukkitKitGranter;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.adapter.outbound.PdcKitClaims;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the kits outbound adapters against a real (mock) Bukkit server: the
 * {@link PdcKitClaims} one-time-claim store (the PDC stamp survives across reads, a per-kit reset clears one
 * stamp, {@code resetAll} clears every stamp), the {@link KitItemCodec} round-trip (an {@code ItemStack}
 * serialises and deserialises back with its amount), and the {@link BukkitKitGranter} delivering a kit into a
 * player's inventory. These are the real adapters the {@code /kit} and {@code /kit reset} commands drive.
 */
class KitClaimStoreTest {

    private ServerMock server;
    private Plugin plugin;
    private PdcKitClaims claims;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        server.addSimpleWorld("world");
        claims = new PdcKitClaims(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aOneTimeStampSurvivesAcrossReads() {
        PlayerRef alice = ref("Alice");

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isFalse();
        claims.markClaimed(alice, KitId.of("vote"));

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isTrue();
    }

    @Test
    void aPerKitResetClearsOnlyThatStamp() {
        PlayerRef alice = ref("Alice");
        claims.markClaimed(alice, KitId.of("vote"));
        claims.markClaimed(alice, KitId.of("daily"));

        claims.reset(alice, KitId.of("vote"));

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isFalse();
        assertThat(claims.hasClaimed(alice, KitId.of("daily"))).isTrue();
    }

    @Test
    void resetAllClearsEveryStamp() {
        PlayerRef alice = ref("Alice");
        claims.markClaimed(alice, KitId.of("vote"));
        claims.markClaimed(alice, KitId.of("daily"));

        claims.resetAll(alice);

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isFalse();
        assertThat(claims.hasClaimed(alice, KitId.of("daily"))).isFalse();
    }

    @Test
    void anOfflinePlayerHasNoClaimAndMarkingNoOps() {
        PlayerRef ghost = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");

        assertThat(claims.hasClaimed(ghost, KitId.of("vote"))).isFalse();
        claims.markClaimed(ghost, KitId.of("vote")); // no PDC to write: silently no-ops
        assertThat(claims.hasClaimed(ghost, KitId.of("vote"))).isFalse();
    }

    @Test
    void anItemRoundTripsThroughTheCodecWithItsAmount() {
        KitItem encoded = KitItemCodec.encode(new ItemStack(Material.DIAMOND, 16));

        ItemStack decoded = KitItemCodec.decode(encoded);

        assertThat(decoded.getType()).isEqualTo(Material.DIAMOND);
        assertThat(decoded.getAmount()).isEqualTo(16);
        assertThat(encoded.amount()).isEqualTo(16);
    }

    @Test
    void grantingAKitFillsThePlayersInventory() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());
        List<KitItem> items = List.of(KitItemCodec.encode(new ItemStack(Material.IRON_INGOT, 5)));
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("test"))
                        .items(items)
                        .build();

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        assertThat(grant.fitInInventory()).isTrue();
        assertThat(alice.getInventory().contains(Material.IRON_INGOT, 5)).isTrue();
    }

    @Test
    void autoEquipTruePutsArmorAndOffhandItemsInEquipmentSlots() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());

        // Prepare a kit with helmet, chestplate, and shield (offhand)
        List<KitItem> items = List.of(
                KitItemCodec.encode(new ItemStack(Material.IRON_HELMET, 1)),
                KitItemCodec.encode(new ItemStack(Material.DIAMOND_CHESTPLATE, 1)),
                KitItemCodec.encode(new ItemStack(Material.SHIELD, 1)));
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("auto-equip-on"))
                        .items(items)
                        .autoEquip(true)
                        .build();

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        assertThat(grant.fitInInventory()).isTrue();
        // The items should be in their respective equipment slots, not in standard inventory slots
        assertThat(alice.getInventory().getHelmet()).isNotNull();
        assertThat(alice.getInventory().getHelmet().getType()).isEqualTo(Material.IRON_HELMET);
        assertThat(alice.getInventory().getChestplate()).isNotNull();
        assertThat(alice.getInventory().getChestplate().getType()).isEqualTo(Material.DIAMOND_CHESTPLATE);
        assertThat(alice.getInventory().getItemInOffHand()).isNotNull();
        assertThat(alice.getInventory().getItemInOffHand().getType()).isEqualTo(Material.SHIELD);
    }

    @Test
    void autoEquipFalseKeepsEquipmentSlotsEmptyAndPutsItemsInNormalInventory() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());

        // Prepare a kit with helmet, chestplate, and shield (offhand)
        List<KitItem> items = List.of(
                KitItemCodec.encode(new ItemStack(Material.IRON_HELMET, 1)),
                KitItemCodec.encode(new ItemStack(Material.DIAMOND_CHESTPLATE, 1)),
                KitItemCodec.encode(new ItemStack(Material.SHIELD, 1)));
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("auto-equip-off"))
                        .items(items)
                        .autoEquip(false)
                        .build();

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        assertThat(grant.fitInInventory()).isTrue();
        // The equipment slots must remain empty
        assertThat(isSlotEmpty(alice.getInventory().getHelmet())).isTrue();
        assertThat(isSlotEmpty(alice.getInventory().getChestplate())).isTrue();
        assertThat(isSlotEmpty(alice.getInventory().getItemInOffHand())).isTrue();
        // The items must be in the general inventory
        assertThat(alice.getInventory().contains(Material.IRON_HELMET)).isTrue();
        assertThat(alice.getInventory().contains(Material.DIAMOND_CHESTPLATE)).isTrue();
        assertThat(alice.getInventory().contains(Material.SHIELD)).isTrue();
    }

    private boolean isSlotEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR;
    }

    private PlayerRef ref(String name) {
        PlayerMock player = server.addPlayer(name);
        return BukkitRefs.toRef(player);
    }

    @Test
    void grantSpecificSlotsRestoresToSlots() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());

        // Create a kit item with a specific slot (slot 4)
        KitItem item = KitItemCodec.encode(new ItemStack(Material.GOLD_INGOT, 3), 4);
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("slot-test"))
                        .items(List.of(item))
                        .build();

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        assertThat(grant.fitInInventory()).isTrue();
        assertThat(alice.getInventory().getItem(4)).isNotNull();
        assertThat(alice.getInventory().getItem(4).getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(alice.getInventory().getItem(4).getAmount()).isEqualTo(3);
    }

    @Test
    void grantOccupiedSpecificSlotFallsBack() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.getInventory().setItem(4, new ItemStack(Material.COAL, 1));
        KitGranter granter = new BukkitKitGranter(new NoopLogger());

        KitItem item = KitItemCodec.encode(new ItemStack(Material.GOLD_INGOT, 3), 4);
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("slot-test-fallback"))
                        .items(List.of(item))
                        .build();

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        assertThat(grant.fitInInventory()).isTrue();
        // Slot 4 should still be coal
        assertThat(alice.getInventory().getItem(4).getType()).isEqualTo(Material.COAL);
        // Gold ingot should fallback to another slot
        assertThat(alice.getInventory().contains(Material.GOLD_INGOT, 3)).isTrue();
    }

    @Test
    void hasRoomForIsFalseWhenTheInventoryIsFullAndTrueWhenItIsNot() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                kitWith(List.of(KitItemCodec.encode(new ItemStack(Material.DIAMOND, 1))), false);

        assertThat(granter.hasRoomFor(BukkitRefs.toRef(alice), kit)).isTrue();

        fillMainInventory(alice);
        assertThat(granter.hasRoomFor(BukkitRefs.toRef(alice), kit)).isFalse();
    }

    @Test
    void dropIsTheDefaultPolicyAndStillDeliversTheItemWhenTheInventoryIsFull() {
        PlayerMock alice = server.addPlayer("Alice");
        fillMainInventory(alice);
        KitGranter granter = new BukkitKitGranter(new NoopLogger());
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                kitWith(List.of(KitItemCodec.encode(new ItemStack(Material.DIAMOND, 1))), false);

        // DROP is the default policy, so a full inventory never refuses the claim. The item is delivered,
        // either into a remaining slot or dropped at the player's feet (the long-standing behaviour).
        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        boolean inInventory = alice.getInventory().contains(Material.DIAMOND);
        boolean droppedAtFeet = alice.getWorld().getEntities().stream()
                .anyMatch(e -> e instanceof org.bukkit.entity.Item item
                        && item.getItemStack().getType() == Material.DIAMOND);
        assertThat(grant).isNotNull();
        assertThat(inInventory || droppedAtFeet).isTrue();
    }

    private static void fillMainInventory(PlayerMock player) {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        }
    }

    @Test
    void oversizedStacksBypassedWithPermission() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "uxmessentials.oversizedstacks", true);
        KitGranter granter = new BukkitKitGranter(new NoopLogger());

        ItemStack oversized = new ItemStack(Material.COOKIE, 100);
        KitItem item = KitItemCodec.encode(oversized);
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("oversized-test"))
                        .items(List.of(item))
                        .build();

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        assertThat(grant.fitInInventory()).isTrue();
        // It should have been added as a single stack of 100
        boolean foundOversized = false;
        for (ItemStack invItem : alice.getInventory().getContents()) {
            if (invItem != null && invItem.getType() == Material.COOKIE && invItem.getAmount() == 100) {
                foundOversized = true;
                break;
            }
        }
        assertThat(foundOversized).isTrue();
    }

    @Test
    void oversizedStacksSplitWithoutPermission() {
        PlayerMock alice = server.addPlayer("Alice");
        // No permission added
        KitGranter granter = new BukkitKitGranter(new NoopLogger());

        ItemStack oversized = new ItemStack(Material.COOKIE, 100);
        KitItem item = KitItemCodec.encode(oversized);
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("oversized-test-no-perm"))
                        .items(List.of(item))
                        .build();

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        assertThat(grant.fitInInventory()).isTrue();
        // It should not have been added as a single stack of 100; it should be split into 64 and 36
        boolean foundOversized = false;
        int count64 = 0;
        int count36 = 0;
        for (ItemStack invItem : alice.getInventory().getContents()) {
            if (invItem != null && invItem.getType() == Material.COOKIE) {
                if (invItem.getAmount() == 100) {
                    foundOversized = true;
                } else if (invItem.getAmount() == 64) {
                    count64++;
                } else if (invItem.getAmount() == 36) {
                    count36++;
                }
            }
        }
        assertThat(foundOversized).isFalse();
        assertThat(count64).isEqualTo(1);
        assertThat(count36).isEqualTo(1);
    }

    @Test
    void eventsAreFiredAndCanBeCancelled() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());

        com.uxplima.uxmessentials.kits.domain.KitDefinition kitCancel =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("cancel-me"))
                        .build();

        com.uxplima.uxmessentials.kits.domain.KitDefinition kitAllow =
                com.uxplima.uxmessentials.kits.domain.KitDefinition.builder()
                        .id(com.uxplima.uxmessentials.kits.domain.KitId.of("allow-me"))
                        .build();

        // Register event cancellation listener
        server.getPluginManager()
                .registerEvents(
                        new org.bukkit.event.Listener() {
                            @org.bukkit.event.EventHandler
                            @SuppressWarnings("UnusedMethod")
                            public void onPreClaim(
                                    com.uxplima.uxmessentials.kits.adapter.inbound.event.KitPreClaimEvent event) {
                                if (event.getKit().id().value().equals("cancel-me")) {
                                    event.setCancelled(true);
                                }
                            }
                        },
                        plugin);

        // Check preGrant
        assertThat(granter.preGrant(BukkitRefs.toRef(alice), kitCancel)).isFalse();
        assertThat(granter.preGrant(BukkitRefs.toRef(alice), kitAllow)).isTrue();

        // Track claim event firing
        java.util.concurrent.atomic.AtomicBoolean claimEventFired =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.getPluginManager()
                .registerEvents(
                        new org.bukkit.event.Listener() {
                            @org.bukkit.event.EventHandler
                            @SuppressWarnings("UnusedMethod")
                            public void onClaim(
                                    com.uxplima.uxmessentials.kits.adapter.inbound.event.KitClaimEvent event) {
                                if (event.getKit().id().value().equals("allow-me")) {
                                    claimEventFired.set(true);
                                }
                            }
                        },
                        plugin);

        granter.grant(BukkitRefs.toRef(alice), kitAllow);
        assertThat(claimEventFired.get()).isTrue();
    }

    @Test
    void parsePlaceholdersOffLeavesAGrantedItemNameUnchanged() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());
        ItemStack named = namedStack("Reward of {player}");
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit = kitWith(List.of(KitItemCodec.encode(named)), false);

        granter.grant(BukkitRefs.toRef(alice), kit);

        ItemStack granted = firstStack(alice, Material.PAPER);
        assertThat(plain(granted)).isEqualTo("Reward of {player}");
    }

    @Test
    void parsePlaceholdersOnGrantsTheItemWithoutThrowing() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());
        ItemStack named = namedStack("<gold>{player}'s Reward");
        com.uxplima.uxmessentials.kits.domain.KitDefinition kit = kitWith(List.of(KitItemCodec.encode(named)), true);

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), kit);

        // PlaceholderAPI is not loadable under MockBukkit, so the bridge is the identity and the token survives
        // the MiniMessage round-trip: the path runs end to end without throwing and still delivers the item.
        assertThat(grant.fitInInventory()).isTrue();
        ItemStack granted = firstStack(alice, Material.PAPER);
        assertThat(plain(granted)).contains("{player}'s Reward");
    }

    private static ItemStack namedStack(String miniMessageName) {
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        meta.displayName(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(miniMessageName));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack firstStack(PlayerMock player, Material material) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                return stack;
            }
        }
        throw new AssertionError("no " + material + " in the inventory");
    }

    private static String plain(ItemStack stack) {
        net.kyori.adventure.text.Component name =
                java.util.Objects.requireNonNull(stack.getItemMeta()).displayName();
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(java.util.Objects.requireNonNull(name));
    }

    private static com.uxplima.uxmessentials.kits.domain.KitDefinition kitWith(
            List<KitItem> items, boolean parsePlaceholders) {
        return com.uxplima.uxmessentials.kits.domain.KitDefinition.repeatable(
                        com.uxplima.uxmessentials.kits.domain.KitId.of("rewards"), items, java.time.Duration.ZERO)
                .withParsePlaceholders(parsePlaceholders);
    }

    /** A logger that discards every line. */
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

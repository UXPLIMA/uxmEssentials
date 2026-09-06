package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the serialize/deserialize/detect logic that turns a stored equipment token into a real item: a full
 * {@link ItemStack} round-trips through the {@code b64:} payload with its enchantments, name and lore intact, a
 * legacy bare material name resolves to a plain item, a blank or unparseable token resolves to nothing, and the
 * {@code b64:} prefix detection distinguishes the two stored shapes.
 */
class EquipmentPayloadsTest {

    @BeforeEach
    void setUp() {
        // A live MockBukkit server is needed for Material / ItemStack / serializeAsBytes to resolve.
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void roundTripsAFullItemThroughTheBase64Payload() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("Excalibur"));
        meta.lore(List.of(Component.text("Legendary blade")));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        sword.setItemMeta(meta);

        String payload = EquipmentPayloads.serialize(sword);
        Optional<ItemStack> restored = EquipmentPayloads.resolve(payload);

        assertThat(restored).isPresent();
        ItemStack item = restored.orElseThrow();
        assertThat(item.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(item.getEnchantmentLevel(Enchantment.SHARPNESS)).isEqualTo(5);
        ItemMeta restoredMeta = item.getItemMeta();
        assertThat(restoredMeta.displayName()).isEqualTo(Component.text("Excalibur"));
        assertThat(restoredMeta.lore()).containsExactly(Component.text("Legendary blade"));
    }

    @Test
    void capturesAHeldEnchantedItemAsASingleQuantityTokenThatDeserializesBack() {
        // The /npc equip <slot> hand path clones the held item and forces amount 1 (a slot shows one item) before
        // serializing. This asserts that a single-quantity enchanted item round-trips through the stored token.
        ItemStack held = new ItemStack(Material.NETHERITE_SWORD, 1);
        ItemMeta meta = held.getItemMeta();
        meta.displayName(Component.text("Hand of the King"));
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        held.setItemMeta(meta);

        String token = EquipmentPayloads.serialize(held);
        ItemStack restored = EquipmentPayloads.resolve(token).orElseThrow();

        assertThat(restored.getAmount()).isEqualTo(1);
        assertThat(restored.getType()).isEqualTo(Material.NETHERITE_SWORD);
        assertThat(restored.getEnchantmentLevel(Enchantment.FIRE_ASPECT)).isEqualTo(2);
        assertThat(restored.getItemMeta().displayName()).isEqualTo(Component.text("Hand of the King"));
    }

    @Test
    void serializeMarksThePayloadWithTheBase64Prefix() {
        String payload = EquipmentPayloads.serialize(new ItemStack(Material.STONE));

        assertThat(payload).startsWith("b64:");
        assertThat(EquipmentPayloads.isSerialized(payload)).isTrue();
        assertThat(EquipmentPayloads.isSerialized("DIAMOND_HELMET")).isFalse();
    }

    @Test
    void resolvesALegacyBareMaterialNameToAPlainItem() {
        Optional<ItemStack> restored = EquipmentPayloads.resolve("DIAMOND_HELMET");

        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow().getType()).isEqualTo(Material.DIAMOND_HELMET);
    }

    @Test
    void resolvesAnUnknownMaterialNameToNothing() {
        assertThat(EquipmentPayloads.resolve("NOT_A_REAL_MATERIAL")).isEmpty();
    }

    @Test
    void resolvesANonItemMaterialToNothing() {
        // AIR is a Material but not an item: it must not equip an empty stack.
        assertThat(EquipmentPayloads.resolve("AIR")).isEmpty();
    }

    @Test
    void resolvesACorruptBase64PayloadToNothing() {
        assertThat(EquipmentPayloads.resolve("b64:not-valid-base64-@@@")).isEmpty();
    }

    @Test
    void resolvesABlankTokenToNothing() {
        assertThat(EquipmentPayloads.resolve("")).isEmpty();
        assertThat(EquipmentPayloads.resolve("   ")).isEmpty();
    }
}

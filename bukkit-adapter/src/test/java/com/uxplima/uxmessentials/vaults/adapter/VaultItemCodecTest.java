package com.uxplima.uxmessentials.vaults.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * MockBukkit coverage of {@link VaultItemCodec#decodeAll}: the decode-all variant the overflow rescue needs.
 * It must round-trip every stored slot, sizing the result to the array length {@code encode} wrote rather than
 * truncating to a passed size, so an item in a slot beyond the current quota is still visible. The size-bounded
 * {@link VaultItemCodec#decode} is unchanged: it still clamps to the requested size.
 */
class VaultItemCodecTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void decodeAllRoundTripsEveryStoredSlotIncludingTheOverflow() {
        ItemStack[] stored = new ItemStack[54];
        stored[0] = new ItemStack(Material.DIAMOND, 4);
        stored[20] = new ItemStack(Material.EMERALD, 7);
        VaultContents contents = VaultItemCodec.encode(stored);

        ItemStack[] all = VaultItemCodec.decodeAll(contents);

        assertThat(all).hasSize(54);
        assertThat(all[0]).isNotNull();
        assertThat(all[0].getType()).isEqualTo(Material.DIAMOND);
        assertThat(all[0].getAmount()).isEqualTo(4);
        assertThat(all[20]).isNotNull();
        assertThat(all[20].getType()).isEqualTo(Material.EMERALD);
        assertThat(all[20].getAmount()).isEqualTo(7);
    }

    @Test
    void decodeAllOfAnEmptyPayloadIsAZeroLengthArray() {
        assertThat(VaultItemCodec.decodeAll(VaultContents.empty())).isEmpty();
    }

    @Test
    void sizeBoundedDecodeStillTruncatesTheOverflow() {
        ItemStack[] stored = new ItemStack[54];
        stored[0] = new ItemStack(Material.DIAMOND, 4);
        stored[20] = new ItemStack(Material.EMERALD, 7);
        VaultContents contents = VaultItemCodec.encode(stored);

        ItemStack[] bounded = VaultItemCodec.decode(contents, 9);

        assertThat(bounded).hasSize(9);
        assertThat(bounded[0]).isNotNull();
        assertThat(bounded[0].getType()).isEqualTo(Material.DIAMOND);
    }
}

package com.uxplima.uxmessentials.shared.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.BottomSlots;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure raw-slot arithmetic a bottom-inventory menu paints through. For a 54-slot chest top the four
 * corners of the player-inventory mapping must land where Bukkit shows them. The 27 main slots first, the 9 hotbar
 * slots last, and a chest-top raw slot passes through untouched so the caller never paints it into the player
 * inventory. No server is needed: the mapping is plain integer math.
 */
class BottomSlotsTest {

    private static final int TOP = 54;

    @Test
    void firstBottomRawSlotMapsToTheFirstMainStorageSlot() {
        assertThat(BottomSlots.rawToPlayerSlot(54, TOP))
                .as("raw 54, the first slot below a 54-slot top, is main-storage slot 9")
                .isEqualTo(9);
    }

    @Test
    void lastMainStorageRawSlotMapsToPlayerSlot35() {
        assertThat(BottomSlots.rawToPlayerSlot(80, TOP))
                .as("raw 80 is the last of the 27 main slots, player slot 35")
                .isEqualTo(35);
    }

    @Test
    void firstHotbarRawSlotMapsToPlayerSlotZero() {
        assertThat(BottomSlots.rawToPlayerSlot(81, TOP))
                .as("raw 81 is the first hotbar slot, player slot 0")
                .isEqualTo(0);
    }

    @Test
    void lastHotbarRawSlotMapsToPlayerSlot8() {
        assertThat(BottomSlots.rawToPlayerSlot(89, TOP))
                .as("raw 89 is the last hotbar slot, player slot 8")
                .isEqualTo(8);
    }

    @Test
    void aChestTopRawSlotPassesThroughUnchanged() {
        assertThat(BottomSlots.rawToPlayerSlot(4, TOP))
                .as("a raw slot inside the chest top is not a bottom slot and is returned unchanged")
                .isEqualTo(4);
    }
}

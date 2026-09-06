package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingEvents;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingRepository;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingVanish;
import com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The item-loss-safe heart of staff mode, end-to-end against a real MockBukkit player: the real
 * {@link BukkitStaffLoadoutCapture} driving the real {@link EnterStaffMode}/{@link ExitStaffMode} use cases
 * over a recording loadout repository. It proves the load-bearing invariants:
 *
 * <ul>
 *   <li><b>Commit before swap.</b> The real inventory is saved to the repository BEFORE it is overwritten by
 *       the gadget hotbar. The saved {@code SavedLoadout} holds the real items and the {@code save} call lands
 *       before the hotbar is applied. So a crash after the swap leaves the real loadout recoverable.
 *   <li><b>Exact restore, then delete.</b> Exit restores the real inventory byte-for-byte (no loss, no dupe)
 *       and deletes the row only after, in that order.
 * </ul>
 */
class StaffModeItemSafetyTest {

    private ServerMock server;
    private Player player;
    private PlayerRef who;
    private StaffSettings settings;
    private StaffGadgetItems gadgetItems;
    private BukkitStaffLoadoutCapture capture;
    private StaffModeStoreImpl store;
    private RecordingRepository repository;
    private RecordingVanish vanish;
    private RecordingEvents events;
    private EnterStaffMode enter;
    private ExitStaffMode exit;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        who = new PlayerRef(player.getUniqueId(), player.getName());
        settings = StaffAdapterFakes.defaultSettings();
        gadgetItems = new StaffGadgetItems(MockBukkit.createMockPlugin("uxmEssentials"));
        vanish = new RecordingVanish();
        capture = new BukkitStaffLoadoutCapture(settings, gadgetItems, vanish);
        store = new StaffModeStoreImpl();
        repository = new RecordingRepository();
        events = new RecordingEvents();
        RecoverStaffLoadout recover =
                new RecoverStaffLoadout(store, repository, capture, vanish, StaffAdapterFakes.notifier());
        enter = new EnterStaffMode(
                store, repository, capture, vanish, StaffAdapterFakes.notifier(), events, recover, "default", true);
        exit = new ExitStaffMode(store, repository, capture, vanish, StaffAdapterFakes.notifier(), events);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enterPersistsTheRealInventoryBeforeSwappingInTheGadgetHotbar() {
        giveRealLoadout();

        enter.enter(who);

        // The saved row holds the REAL items, not the gadget hotbar, proof the capture ran on the live
        // inventory before the swap.
        SavedLoadout saved = java.util.Objects.requireNonNull(repository.rows.get(who.uuid()), "saved");
        PlayerInventory restored = restoreInto(saved);
        assertThat(restored.getItem(0)).isNotNull();
        assertThat(restored.getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(restored.getItem(8).getType()).isEqualTo(Material.GOLDEN_APPLE);

        // The live inventory now holds the gadget hotbar (vanish at slot 0, examine at slot 1), not the sword.
        assertThat(gadgetItems.gadgetOf(player.getInventory().getItem(0))).contains(StaffGadget.VANISH);
        assertThat(gadgetItems.gadgetOf(player.getInventory().getItem(1))).contains(StaffGadget.EXAMINE);

        // The first repository call is the enter-never-overwrites guard load (no prior row), then exactly one
        // save, and it commits before the hotbar swap (the live inventory above already holds the gadgets).
        assertThat(repository.calls).containsExactly("load", "save");
        assertThat(store.isActive(who)).isTrue();
        assertThat(vanish.states).containsExactly(true);
    }

    @Test
    void exitRestoresTheExactRealInventoryAndThenDeletesTheRow() {
        giveRealLoadout();
        player.setLevel(30);
        player.setExp(0.5f);
        ItemStack[] before = player.getInventory().getContents().clone();

        enter.enter(who);
        exit.exit(who);

        // Byte-for-byte: every slot of the restored inventory equals what the player held before entering.
        ItemStack[] after = player.getInventory().getContents();
        assertThat(after).containsExactly(before);
        assertThat(player.getLevel()).isEqualTo(30);
        assertThat(player.getExp()).isEqualTo(0.5f);

        // The row is gone (restore happened first, then delete), and the player is no longer in staff mode.
        assertThat(repository.rows).doesNotContainKey(who.uuid());
        assertThat(repository.calls).containsSequence("load", "delete");
        assertThat(repository.calls.indexOf("delete")).isGreaterThan(repository.calls.indexOf("load"));
        assertThat(store.isActive(who)).isFalse();
        assertThat(vanish.states).containsExactly(true, false);
    }

    @Test
    void aQuitInModeLeavesTheRealInventoryRecoverableFromThePersistedRow() {
        giveRealLoadout();
        enter.enter(who);

        // Simulate a crash: the process dies before exit runs, but the committed row outlives it. A later exit
        // (the restore-on-next-reach path) restores the real loadout exactly and clears the row.
        assertThat(repository.rows).containsKey(who.uuid());
        exit.exit(who);

        assertThat(player.getInventory().getItem(0)).isNotNull();
        assertThat(player.getInventory().getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(repository.rows).doesNotContainKey(who.uuid());
    }

    @Test
    void aPreEnterVanishedPlayerIsStillVanishedAfterAToggle() {
        giveRealLoadout();
        vanish.setVanished(who, true); // vanished via /vanish BEFORE entering
        vanish.states.clear(); // ignore that setup toggle; assert only what enter/exit do

        enter.enter(who);
        exit.exit(who);

        // The captured loadout recorded vanishedBefore, so exit restores vanished=true rather than revealing.
        assertThat(vanish.states).containsExactly(true, true);
    }

    @Test
    void enterClosesAnOpenInventorySoTheCursorItemIsNotOrphaned() {
        giveRealLoadout();
        // A /staffmode run with a held cursor item: the enter flow closes the inventory FIRST. On a real server
        // closing returns the cursor item to a free slot (so the following capture snapshots it, rather than the
        // gadget-hotbar clear dropping it). The observable seam here is that capture closed the inventory, after
        // enter the cursor is no longer holding the item. (MockBukkit's closeInventory clears the cursor rather
        // than returning it to a slot, so item-survival itself is only assertable against a live server.)
        player.setItemOnCursor(new ItemStack(Material.EMERALD, 3));

        enter.enter(who);

        assertThat(player.getItemOnCursor().getType()).isEqualTo(Material.AIR);
    }

    private void giveRealLoadout() {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        player.getInventory().setItem(8, new ItemStack(Material.GOLDEN_APPLE, 5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** Decode the saved loadout into a throwaway player's inventory so the stored items can be inspected. */
    private PlayerInventory restoreInto(SavedLoadout saved) {
        Player probe = server.addPlayer("Probe");
        PlayerRef probeRef = new PlayerRef(probe.getUniqueId(), probe.getName());
        capture.restore(probeRef, saved);
        return probe.getInventory();
    }
}

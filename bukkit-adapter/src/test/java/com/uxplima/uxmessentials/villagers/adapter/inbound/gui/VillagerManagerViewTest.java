package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the trade-manager window, opened through the real menu engine over the shipped
 * {@code modules/villagers/gui/trade-manager.conf}. The window mirrors the villager's recipes into its item region, an
 * edit to a sell slot and a filled empty triple apply to the live merchant on close, the remove button drops a trade,
 * and the edited set is PDC-serialised so reapplying it (as the load listener would) yields the same trades. The
 * disable toggle flips the villager's per-villager flag. The scheduler is synchronous, so the open and the close-time
 * apply run inline, and clicks and closes are dispatched as real events through the engine's own listener.
 */
class VillagerManagerViewTest {

    /** The region parts of one trade, in the order the spec declares them. */
    private static final int BUY_A = 0;

    private static final int SELL = 2;

    /** The chrome slots the shipped spec puts the first row's remove button and the trading toggle on. */
    private static final int REMOVE_ROW_1_SLOT = 8;

    private static final int TOGGLE_SLOT = 45;

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private PlayerRef editor;
    private Villager villager;
    private PdcVillagerFlags flags;
    private VillagerRecipeStore store;
    private VillagerManagerWindow window;
    private VillagerManagerView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        editor = new PlayerRef(player.getUniqueId(), player.getName());
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        flags = new PdcVillagerFlags();
        store = new VillagerRecipeStore();
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        window = new VillagerManagerWindow(
                new KeyMessages(), engine.menus(), Path.of("no-such-data-folder"), TestMenuEngine.SILENT_LOG);
        view = new VillagerManagerView(new SyncScheduler(), flags, store, window);
        window.register(engine.bindings(), view);
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theWindowReflectsTheVillagersRecipes() {
        villager.setRecipes(List.of(
                recipe(new ItemStack(Material.DIAMOND, 2), new ItemStack(Material.EMERALD, 5)),
                recipe(new ItemStack(Material.BREAD), new ItemStack(Material.WHEAT, 3))));

        Inventory menu = openManager();

        assertThat(menu.getItem(window.slotOf(0, BUY_A))).isEqualTo(new ItemStack(Material.EMERALD, 5));
        assertThat(menu.getItem(window.slotOf(0, SELL))).isEqualTo(new ItemStack(Material.DIAMOND, 2));
        assertThat(menu.getItem(window.slotOf(1, BUY_A))).isEqualTo(new ItemStack(Material.WHEAT, 3));
        assertThat(menu.getItem(window.slotOf(1, SELL))).isEqualTo(new ItemStack(Material.BREAD));
    }

    @Test
    void anEditedSellSlotAppliesToTheLiveMerchantAndRoundTripsThroughPdc() {
        villager.setRecipes(List.of(recipe(new ItemStack(Material.DIAMOND, 1), new ItemStack(Material.EMERALD, 4))));
        Inventory menu = openManager();
        menu.setItem(window.slotOf(0, SELL), new ItemStack(Material.DIAMOND, 3));

        close();

        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult()).isEqualTo(new ItemStack(Material.DIAMOND, 3));
        // The reapply the load listener performs restores the same trades from PDC.
        villager.setRecipes(List.of());
        store.apply(villager);
        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult()).isEqualTo(new ItemStack(Material.DIAMOND, 3));
        assertThat(villager.getRecipes().get(0).getIngredients()).containsExactly(new ItemStack(Material.EMERALD, 4));
    }

    @Test
    void fillingAnEmptyTripleAddsATrade() {
        villager.setRecipes(List.of(recipe(new ItemStack(Material.DIAMOND), new ItemStack(Material.EMERALD))));
        Inventory menu = openManager();
        menu.setItem(window.slotOf(1, BUY_A), new ItemStack(Material.EMERALD));
        menu.setItem(window.slotOf(1, SELL), new ItemStack(Material.STICK, 8));

        close();

        assertThat(villager.getRecipes()).hasSize(2);
        assertThat(villager.getRecipes().get(1).getResult()).isEqualTo(new ItemStack(Material.STICK, 8));
    }

    @Test
    void theRemoveButtonDropsATrade() {
        villager.setRecipes(List.of(
                recipe(new ItemStack(Material.DIAMOND), new ItemStack(Material.EMERALD)),
                recipe(new ItemStack(Material.BREAD), new ItemStack(Material.WHEAT))));
        openManager();

        click(REMOVE_ROW_1_SLOT);
        close();

        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult()).isEqualTo(new ItemStack(Material.BREAD));
    }

    @Test
    void theToggleFlipsTheVillagersDisableFlag() {
        openManager();
        assertThat(flags.tradesDisabled(villager)).isFalse();

        click(TOGGLE_SLOT);

        assertThat(flags.tradesDisabled(villager)).isTrue();
        click(TOGGLE_SLOT);
        assertThat(flags.tradesDisabled(villager)).isFalse();
    }

    /**
     * The item-safety guard for this window: only the declared trade region takes items. A click on the chrome (the
     * remove button, the toggle, the backdrop) is cancelled, so a staff member cannot pull a control item out of the
     * window and end up holding a redstone block, and the redraw a toggle triggers leaves the staked trade stacks
     * exactly where the editor put them rather than repainting over them.
     */
    @Test
    void onlyTheTradeRegionAcceptsItemsAndAToggleRedrawLeavesItAlone() {
        Inventory menu = openManager();
        // Staked on the third trade, which the first row's remove button does not touch.
        menu.setItem(window.slotOf(2, BUY_A), new ItemStack(Material.EMERALD, 7));

        assertThat(click(REMOVE_ROW_1_SLOT).isCancelled()).isTrue();
        assertThat(click(TOGGLE_SLOT).isCancelled()).isTrue();
        assertThat(click(4).isCancelled()).isTrue(); // backdrop
        assertThat(click(window.slotOf(1, SELL)).isCancelled()).isFalse();
        assertThat(menu.getItem(window.slotOf(2, BUY_A))).isEqualTo(new ItemStack(Material.EMERALD, 7));
    }

    private Inventory openManager() {
        view.open(player, editor, villager);
        return player.getOpenInventory().getTopInventory();
    }

    private InventoryClickEvent click(int slot) {
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                slot,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void close() {
        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));
    }

    private static MerchantRecipe recipe(ItemStack result, ItemStack ingredient) {
        MerchantRecipe recipe = new MerchantRecipe(result, 0, 9, false);
        recipe.addIngredient(ingredient);
        return recipe;
    }

    /** Resolves each key to its own id: enough for the text path the tests do not assert wording on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled task inline so the open and the close-time apply happen before the assertions. */
    private static final class SyncScheduler implements Scheduler {
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
}

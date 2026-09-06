package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BaltopMenu;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The baltop golden test: the engine-rendered read-only leaderboard must draw the exact grid the original
 * {@code BaltopGuiView} drew. The snapshot cache holds two ranked owners for the queried currency, so the list draws
 * two PLAYER_HEAD skulls (content slots 0 and 1, the rank surfacing through the {@code baltop_player} token the test
 * catalog echoes back), the prev ARROW at slot 45, the close BARRIER at slot 49, and the next ARROW at slot 53
 * the corner-and-centre bottom-row geometry the original leaderboard used. The engine window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced
 * for this fixture, frozen here as the contract so the old class could be deleted. A click on the first skull
 * through the engine's own {@link MenuListener} proves the migrated path is inert, and the BARRIER at slot 49 is the
 * bound close button.
 */
class BaltopListGoldenTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private BaltopSnapshots snapshots;
    private EconomyNotifier notifier;

    private final Path dataFolder = Path.of("nonexistent");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        snapshots = mock(BaltopSnapshots.class);
        notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameSkullGridNavAndCloseAsTheOldView() {
        when(snapshots.top(eq(COINS), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(row("Alice", 1_000), row("Bob", 500)));

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingALeaderboardRowThroughTheEngineIsInert() {
        when(snapshots.top(eq(COINS), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(row("Alice", 1_000)));
        openEngine();
        Inventory before = player.getOpenInventory().getTopInventory();

        fireClick(0); // the first content cell holds the top-ranked skull

        // The leaderboard is read-only: the window stays this menu's, still open over the same inventory.
        assertThat(player.getOpenInventory().getTopInventory()).isSameAs(before);
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void theCloseSlotHoldsTheBarrierCloseButton() {
        when(snapshots.top(eq(COINS), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(row("Alice", 1_000)));
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();

        ItemStack close = inv.getItem(49);
        assertThat(close).isNotNull();
        assertThat(Objects.requireNonNull(close).getType()).isEqualTo(Material.BARRIER);

        fireClick(49);
        // The generic close action closes the window: the menu's inventory is no longer the player's top inventory
        // (MockBukkit drops the top inventory to null once it is closed).
        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top == null || !(top.getHolder() instanceof MenuHolder)).isTrue();
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code BaltopGuiView} produced for this fixture (Alice then
     * Bob), captured while both paths rendered it identically and frozen here: two PLAYER_HEAD skulls (content slots
     * 0 and 1, the owner surfacing through the {@code baltop_player} token), the prev ARROW at 45, the close BARRIER
     * at 49, and the next ARROW at 53.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PLAYER_HEAD, "Alice"));
        baseline.put(1, new Snapshot(Material.PLAYER_HEAD, "Bob"));
        baseline.put(45, new Snapshot(Material.ARROW, "baltop.gui-previous"));
        baseline.put(49, new Snapshot(Material.BARRIER, "baltop.gui-close"));
        baseline.put(53, new Snapshot(Material.ARROW, "baltop.gui-next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        // The leaderboard spec binds the generic close action, so the shared vocabulary must be registered too.
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        MenuVocabulary.registerConditions(bindings, mock(Permissions.class), mock(Logger.class));
        MenuVocabulary.registerPlaceholders(bindings);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        BaltopMenu menu = new BaltopMenu(menus, snapshots, notifier);
        menu.register(bindings, dataFolder, NOOP);
        menu.open(viewer, COINS);
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private BaltopRow row(String name, long amount) {
        PlayerRef owner = new PlayerRef(UUID.randomUUID(), name);
        return new BaltopRow(owner, Money.of(COINS, new BigDecimal(amount)));
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /**
     * Surfaces the entry name's {@code baltop_player} token so the skull's owner appears; else the bare key. The
     * engine resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the
     * match is by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(EconomyMessageKey.BALTOP_GUI_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("baltop_player", "");
            }
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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

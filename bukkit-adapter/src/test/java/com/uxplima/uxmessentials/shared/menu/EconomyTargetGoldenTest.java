package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.CurrencyPickerMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EcoAdminOps;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyTargetMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The per-player eco-admin manage-screen golden test: the engine-rendered panel must draw the exact window the
 * original {@code EconomyTargetView} drew, and its buttons must keep the behaviour. The panel draws the target's
 * PLAYER_HEAD@4, the Give EMERALD@19 / Take REDSTONE@21 / Set COMPARATOR@23 / Reset TNT@25 buttons, the history
 * BOOK@31, the select-currency SUNFLOWER@38 (shown only when more than one currency is configured) and the back
 * ARROW@40 over a grey-glass backdrop, snapshotted as {@code (slot -> material, plain name)} and asserted equal
 * slot for slot to the baseline the old view produced (frozen here so the old class could be deleted). Give / Take /
 * Set fire the input prompt then run the matching {@code EcoAdmin} op with {@code Money.of(active, amount)} through
 * the package-private apply seam (MockBukkit cannot drive a live anvil); Reset is confirm-gated through the engine
 * confirm; the select-currency button opens the shared engine picker and choosing a currency re-opens this screen
 * with that currency active.
 */
class EconomyTargetGoldenTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int HEAD_SLOT = 4;
    private static final int GIVE_SLOT = 19;
    private static final int TAKE_SLOT = 21;
    private static final int SET_SLOT = 23;
    private static final int RESET_SLOT = 25;
    private static final int HISTORY_SLOT = 31;
    private static final int SELECT_CURRENCY_SLOT = 38;
    private static final int BACK_SLOT = 40;
    private static final int CONFIRM_YES_SLOT = 11;

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .symbol("♦")
            .plural("gems")
            .precision(0)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock admin;
    private PlayerRef adminRef;
    private PlayerRef target;

    private EcoAdmin ecoAdmin;
    private EconomyProvider provider;
    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private TextInput textInput;
    private AnvilInput anvil;
    private EconomyNotifier notifier;
    private TransactionsHistoryMenu historyView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        admin = server.addPlayer("Admin");
        adminRef = new PlayerRef(admin.getUniqueId(), admin.getName());
        target = new PlayerRef(server.addPlayer("Target").getUniqueId(), "Target");

        ecoAdmin = mock(EcoAdmin.class);
        provider = mock(EconomyProvider.class);
        when(provider.balance(eq(target), eq(COINS))).thenReturn(Money.of(COINS, new BigDecimal("12.50")));
        when(provider.balance(eq(target), eq(GEMS))).thenReturn(Money.of(GEMS, 7L));

        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());
        historyView = mock(TransactionsHistoryMenu.class);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameManageScreenAsTheOldView() {
        EconomyTargetMenu menu = wire(coinsAndGems());
        menu.open(admin, adminRef, target, COINS);

        Inventory inv = admin.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(45);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void theHeadLoreCarriesOneBalanceLinePerCurrency() {
        EconomyTargetMenu menu = wire(coinsAndGems());
        menu.open(admin, adminRef, target, COINS);

        ItemStack head = Objects.requireNonNull(
                admin.getOpenInventory().getTopInventory().getItem(HEAD_SLOT), "head");
        // The old view emitted one target-head-lore line per configured currency; the engine splits the joined
        // placeholder block back into one component per balance, so both currencies' lines are present.
        assertThat(TileText.body(head)).hasSize(2);
    }

    // Give / Take / Set capture an amount through the anvil seam, which MockBukkit leaves unimplemented (no
    // player.openAnvil), so the prompt-opening click cannot be exercised in this fixture (see MenuListChildTest).
    // The apply seam, the branch the prompt's submit callback drives, is verified to reach EcoAdmin with
    // Money.of(active, amount) in EconomyAdminGuiTest, which lives in the menu's own package and so can drive the
    // package-private applyAmount. This golden test covers the click-driven flows the engine routes: the render,
    // the reset confirm, and the select-currency picker re-open.

    @Test
    void resetClickOpensTheEngineConfirmAndYesRunsReset() {
        EconomyTargetMenu menu = wire(coinsAndGems());
        menu.open(admin, adminRef, target, COINS);

        fireClick(RESET_SLOT); // opens the engine confirm dialog
        Inventory confirm = admin.getOpenInventory().getTopInventory();
        assertThat(confirm.getHolder()).isInstanceOf(MenuHolder.class);

        fireClick(CONFIRM_YES_SLOT); // yes runs the reset for the active currency
        verify(ecoAdmin).reset(eq(adminRef), eq(target), eq(COINS));
    }

    @Test
    void selectCurrencyClickOpensThePickerAndPickingReopensWithThatActive() {
        EconomyTargetMenu menu = wire(coinsAndGems());
        menu.open(admin, adminRef, target, COINS);

        fireClick(SELECT_CURRENCY_SLOT); // opens the shared engine picker
        Inventory picker = admin.getOpenInventory().getTopInventory();
        assertThat(picker.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(picker.getSize()).isEqualTo(54); // the six-row picker

        fireClick(1); // pick GEMS (registry order: COINS@0, GEMS@1) -> re-opens the manage screen with GEMS active
        // The picker's onPick re-opens this screen for the chosen currency, so the viewer is back on the 45-slot
        // manage window (the give button is present again). EconomyAdminGuiTest then proves a subsequent amount
        // parses against the newly-active currency through the package-private seam.
        Inventory reopened = admin.getOpenInventory().getTopInventory();
        assertThat(reopened.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(reopened.getSize()).isEqualTo(45);
        assertThat(Objects.requireNonNull(reopened.getItem(GIVE_SLOT)).getType())
                .isEqualTo(Material.EMERALD);
    }

    @Test
    void theSelectCurrencyItemIsHiddenWithASingleCurrency() {
        EconomyTargetMenu menu = wire(CurrencyRegistry.single(COINS));
        menu.open(admin, adminRef, target, COINS);

        Inventory inv = admin.getOpenInventory().getTopInventory();
        // The select-currency slot carries only filler when a single currency is configured (the view condition).
        assertThat(Objects.requireNonNull(inv.getItem(SELECT_CURRENCY_SLOT)).getType())
                .isEqualTo(FILLER);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code EconomyTargetView} produced for a two-currency
     * fixture: the PLAYER_HEAD@4, the four action buttons, the history BOOK@31, the select-currency SUNFLOWER@38 and
     * the back ARROW@40, each carrying its catalog key (the test's {@code KeyMessages} returns each key verbatim, so a
     * wrong key or material still mismatches). The grey-glass backdrop fills every other slot.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 45; slot++) {
            baseline.put(slot, new Snapshot(FILLER, ""));
        }
        baseline.put(
                HEAD_SLOT, new Snapshot(Material.PLAYER_HEAD, key(EconomyMessageKey.ECO_ADMIN_GUI_TARGET_HEAD_NAME)));
        baseline.put(GIVE_SLOT, new Snapshot(Material.EMERALD, key(EconomyMessageKey.ECO_ADMIN_GUI_GIVE_NAME)));
        baseline.put(TAKE_SLOT, new Snapshot(Material.REDSTONE, key(EconomyMessageKey.ECO_ADMIN_GUI_TAKE_NAME)));
        baseline.put(SET_SLOT, new Snapshot(Material.COMPARATOR, key(EconomyMessageKey.ECO_ADMIN_GUI_SET_NAME)));
        baseline.put(RESET_SLOT, new Snapshot(Material.TNT, key(EconomyMessageKey.ECO_ADMIN_GUI_RESET_NAME)));
        baseline.put(
                HISTORY_SLOT, new Snapshot(Material.BOOK, key(EconomyMessageKey.ECO_ADMIN_GUI_TARGET_HISTORY_NAME)));
        baseline.put(
                SELECT_CURRENCY_SLOT,
                new Snapshot(Material.SUNFLOWER, key(EconomyMessageKey.ECO_ADMIN_GUI_SELECT_CURRENCY_NAME)));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, key(EconomyMessageKey.ECO_ADMIN_GUI_BACK)));
        return baseline;
    }

    private EconomyTargetMenu wire(CurrencyRegistry currencies) {
        MenuBindings bindings = engine.bindings();
        Menus menus = engine.menus();
        CurrencyPickerMenu picker = new CurrencyPickerMenu(menus, new KeyMessages(), scheduler);
        EconomyTargetMenu menu = new EconomyTargetMenu(
                menus,
                guiText,
                new KeyMessages(),
                scheduler,
                textInput,
                provider,
                new EcoAdminOps(ecoAdmin),
                currencies,
                notifier,
                historyView,
                picker,
                (p, v) -> {});
        picker.register(bindings, specDir(), NOOP);
        menu.register(bindings, specDir(), NOOP);
        return menu;
    }

    private static CurrencyRegistry coinsAndGems() {
        return CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id());
    }

    private static String key(EconomyMessageKey messageKey) {
        return messageKey.key();
    }

    private void fireClick(int slot) {
        InventoryView view = admin.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the menu loads the shipped manage-screen spec. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) {
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

    private record Snapshot(Material material, String name) {}

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
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

package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyExchangeMenu;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.ExchangeRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
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
 * The currency-exchange dashboard golden test: the engine-rendered panel must draw the exact window the original
 * {@code ExchangeGuiView} drew, and its buttons must keep the behaviour. The panel draws the source icon (the
 * source currency's per-currency icon @11), the convert SUNFLOWER@13 (a no-rate BARRIER in the same slot when no
 * rate is configured), the target icon @15 and the close BARRIER@22 over a grey-glass backdrop, snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal slot for slot to the baseline the old view produced
 * (frozen here so the old class could be deleted). The convert button fires the input prompt then runs the exchange
 * use case through the package-private apply seam (MockBukkit cannot drive a live anvil, see
 * EconomyExchangeMenuApplyTest); the source/target icons open the shared engine picker and choosing a currency
 * re-opens this panel with that side switched.
 */
class EconomyExchangeGoldenTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int SOURCE_SLOT = 11;
    private static final int CONVERT_SLOT = 13;
    private static final int TARGET_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .iconMaterial("GOLD_INGOT")
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .symbol("♦")
            .plural("gems")
            .precision(0)
            .iconMaterial("DIAMOND")
            .build();
    private static final Currency RUBIES = Currency.builder(CurrencyId.of("rubies"))
            .symbol("R")
            .plural("rubies")
            .precision(0)
            .iconMaterial("REDSTONE")
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;

    private EconomyProvider provider;
    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private TextInput textInput;
    private AnvilInput anvil;
    private EconomyNotifier notifier;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");

        provider = mock(EconomyProvider.class);
        when(provider.balance(any(PlayerRef.class), any(Currency.class))).thenReturn(Money.of(COINS, BigDecimal.ZERO));
        // A LinkedHashSet so the picker grids the currencies in a deterministic order (COINS@0, GEMS@1, RUBIES@2).
        when(provider.currencies()).thenReturn(new LinkedHashSet<>(List.of(COINS, GEMS, RUBIES)));

        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameExchangeDashboardAsTheOldView() {
        EconomyExchangeMenu menu = wire(ratedRegistry());
        menu.open(player, COINS, GEMS);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void whenNoRateIsConfiguredTheConvertSlotShowsTheNoRateMarker() {
        EconomyExchangeMenu menu = wire(new ExchangeRegistry(List.of()));
        menu.open(player, COINS, GEMS);

        Inventory inv = player.getOpenInventory().getTopInventory();
        // The convert slot carries the BARRIER no-rate marker, not the SUNFLOWER convert button, when no rate exists.
        assertThat(Objects.requireNonNull(inv.getItem(CONVERT_SLOT)).getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    void clickingTheSourceOpensThePickerAndPickingReopensWithThatSourceActive() {
        EconomyExchangeMenu menu = wire(ratedRegistry());
        menu.open(player, COINS, GEMS);

        fireClick(SOURCE_SLOT); // opens the shared engine picker
        Inventory picker = player.getOpenInventory().getTopInventory();
        assertThat(picker.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(picker.getSize()).isEqualTo(54); // the six-row picker

        fireClick(2); // pick RUBIES (currency order: COINS@0, GEMS@1, RUBIES@2) -> re-opens with source=RUBIES
        Inventory reopened = player.getOpenInventory().getTopInventory();
        assertThat(reopened.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(reopened.getSize()).isEqualTo(27);
        // The subject now carries RUBIES as the source, so the source icon material is its per-currency icon.
        assertThat(Objects.requireNonNull(reopened.getItem(SOURCE_SLOT)).getType())
                .isEqualTo(Material.REDSTONE);
        // The target side is unchanged.
        assertThat(Objects.requireNonNull(reopened.getItem(TARGET_SLOT)).getType())
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void clickingTheTargetOpensThePickerAndPickingReopensWithThatTargetActive() {
        EconomyExchangeMenu menu = wire(ratedRegistry());
        menu.open(player, COINS, GEMS);

        fireClick(TARGET_SLOT); // opens the shared engine picker
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(54);

        fireClick(2); // pick RUBIES -> re-opens with target=RUBIES
        Inventory reopened = player.getOpenInventory().getTopInventory();
        assertThat(reopened.getSize()).isEqualTo(27);
        assertThat(Objects.requireNonNull(reopened.getItem(TARGET_SLOT)).getType())
                .isEqualTo(Material.REDSTONE);
        assertThat(Objects.requireNonNull(reopened.getItem(SOURCE_SLOT)).getType())
                .isEqualTo(Material.GOLD_INGOT);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code ExchangeGuiView} produced for a COINS->GEMS pair with
     * a rate configured: the source GOLD_INGOT@11 and target DIAMOND@15 (their per-currency icons), the convert
     * SUNFLOWER@13, and the close BARRIER@22, each carrying its catalog key (the test's {@code KeyMessages} returns
     * each key verbatim, so a wrong key or material still mismatches). The grey-glass backdrop fills every other slot.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            baseline.put(slot, new Snapshot(FILLER, ""));
        }
        baseline.put(SOURCE_SLOT, new Snapshot(Material.GOLD_INGOT, key(EconomyMessageKey.EXCHANGE_GUI_SOURCE_NAME)));
        baseline.put(CONVERT_SLOT, new Snapshot(Material.SUNFLOWER, key(EconomyMessageKey.EXCHANGE_GUI_INFO_NAME)));
        baseline.put(TARGET_SLOT, new Snapshot(Material.DIAMOND, key(EconomyMessageKey.EXCHANGE_GUI_TARGET_NAME)));
        baseline.put(CLOSE_SLOT, new Snapshot(Material.BARRIER, key(EconomyMessageKey.BALTOP_GUI_CLOSE)));
        return baseline;
    }

    private EconomyExchangeMenu wire(ExchangeRegistry registry) {
        MenuBindings bindings = engine.bindings();
        ExchangeService exchangeService = new ExchangeService(mock(WalletRepository.class), registry, true);
        CurrencyPickerMenu picker = new CurrencyPickerMenu(engine.menus(), new KeyMessages(), scheduler);
        EconomyExchangeMenu menu = new EconomyExchangeMenu(
                engine.menus(), provider, exchangeService, scheduler, notifier, new KeyMessages(), textInput, picker);
        picker.register(bindings, specDir(), NOOP);
        menu.register(bindings, specDir(), NOOP);
        return menu;
    }

    private static ExchangeRegistry ratedRegistry() {
        return new ExchangeRegistry(
                List.of(new ExchangeRate(COINS.id(), GEMS.id(), new BigDecimal("2"), new BigDecimal("0.05"))));
    }

    private static String key(EconomyMessageKey messageKey) {
        return messageKey.key();
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the menu loads the shipped exchange spec. */
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

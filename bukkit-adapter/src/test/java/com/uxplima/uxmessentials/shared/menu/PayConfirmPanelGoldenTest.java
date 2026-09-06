package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.PayConfirmPanelMenu;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.application.PayTaxation;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
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
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The pay-confirmation golden test: the engine-rendered strip must draw the exact window the original
 * {@code PayConfirmationView} drew, and its buttons must keep the behaviour. The strip draws the confirm block
 * (GREEN_STAINED_GLASS_PANE@0-2), the recipient head (PLAYER_HEAD@3), the value (PAPER@4), and the cancel block
 * (RED_STAINED_GLASS_PANE@5-8), snapshotted as {@code (slot -> material, plain name)} and asserted equal slot for
 * slot to the baseline the old view produced (frozen here so the old class could be deleted). Clicking the confirm
 * block fires through the engine's own {@link MenuListener} and runs the payer's staged transfer through the same
 * {@link Pay} use case {@code /payconfirm} uses; clicking the cancel block aborts without consuming the staged pay.
 */
class PayConfirmPanelGoldenTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int CONFIRM_SLOT = 0;
    private static final int CANCEL_SLOT = 5;

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).symbol("$").precision(2).build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private PlayerRef target;
    private GuiText guiText;
    private Scheduler scheduler;
    private RecordingPending pending;
    private Pay pay;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        target = new PlayerRef(java.util.UUID.randomUUID(), "Bob");
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        pending = new RecordingPending();
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        pay = new Pay(new FakeProvider(), new AlwaysAccepts(), pending, notifier, PayTaxation.none(), clock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameStripAsTheOldView() {
        openEngine();

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(9);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void clickingConfirmRunsTheStagedTransfer() {
        openEngine();

        fireClick(CONFIRM_SLOT);

        // The confirm block runs Pay.confirm, which consumes the payer's staged pay through take().
        assertThat(pending.taken).containsExactly(viewer);
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING); // closed after confirm
    }

    @Test
    void clickingCancelAbortsWithoutConsumingTheStagedPay() {
        openEngine();

        fireClick(CANCEL_SLOT);

        // The cancel block never touches the Pay use case; the staged pay is left for the expiry timer.
        assertThat(pending.taken).isEmpty();
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING); // closed after cancel
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code PayConfirmationView} produced: the confirm block
     * across slots 0-2, the PLAYER_HEAD recipient at 3, the PAPER value at 4, and the cancel block across slots 5-8,
     * each carrying its catalog key (the test's {@code KeyMessages} returns each key verbatim, so a wrong key or
     * material still mismatches).
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot <= 2; slot++) {
            baseline.put(slot, new Snapshot(Material.GREEN_STAINED_GLASS_PANE, "pay.confirm-gui-confirm-name"));
        }
        baseline.put(3, new Snapshot(Material.PLAYER_HEAD, "pay.confirm-gui-recipient-name"));
        baseline.put(4, new Snapshot(Material.PAPER, "pay.confirm-gui-value-name"));
        for (int slot = 5; slot <= 8; slot++) {
            baseline.put(slot, new Snapshot(Material.RED_STAINED_GLASS_PANE, "pay.confirm-gui-cancel-name"));
        }
        return baseline;
    }

    /** Build the engine, register the pay-confirm bindings + spec, and open the strip for the player. */
    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        MenuVocabulary.registerConditions(bindings, mock(Permissions.class), mock(Logger.class));
        MenuVocabulary.registerPlaceholders(bindings);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        PayConfirmPanelMenu menu = new PayConfirmPanelMenu(menus, pay, scheduler);
        menu.register(bindings, specDir(), NOOP);
        menu.open(player, target, Money.of(COINS, new BigDecimal("500")));
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped pay-confirm spec. */
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
            if (item == null || item.getType() == FILLER) {
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

    /** Records the payers whose staged pay was consumed; {@code take} always returns empty (no real settle leg). */
    private static final class RecordingPending implements PendingPayRegistry {
        private final List<PlayerRef> taken = new java.util.ArrayList<>();

        @Override
        public void stage(PendingPay pending) {}

        @Override
        public Optional<PendingPay> peek(PlayerRef payer) {
            return Optional.empty();
        }

        @Override
        public Optional<PendingPay> take(PlayerRef payer) {
            taken.add(payer);
            return Optional.empty();
        }

        @Override
        public void clear(PlayerRef payer) {}
    }

    private static final class AlwaysAccepts implements PayPreferences {
        @Override
        public boolean acceptsPay(PlayerRef target) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    /** A minimal {@link EconomyProvider}; the confirm path under test never reaches a transfer leg. */
    private static final class FakeProvider implements EconomyProvider {
        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return Money.of(currency, BigDecimal.ZERO);
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            return Result.ok();
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException("transfer is not exercised by this test");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS);
        }
    }

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

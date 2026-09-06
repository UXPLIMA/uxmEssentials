package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.trade.application.CrossServerTrade;
import com.uxplima.uxmessentials.trade.application.TradeEscrow;
import com.uxplima.uxmessentials.trade.application.TradeSignal;
import com.uxplima.uxmessentials.trade.application.TradeSignalType;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import com.uxplima.uxmessentials.trade.application.port.TradeItemDelivery;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the cross-server trade window. The solo offer view one participant stakes into while their
 * counterpart trades from another backend. It pins the loss- and peer-safety of the window's terminal paths, which the
 * pure {@code CrossServerTrade} coordinator test cannot reach because it never opens a view: a plain close before
 * confirming returns the staked items to the player and signals the peer to abort exactly once; a confirm-driven close
 * does <em>not</em> fire a spurious abort that would tear the counterpart down; and a module-stop drain returns an open
 * window's items rather than dropping them with the transient inventory.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open, the item return, and the escrow all run inline. A
 * remote invite is delivered straight into {@link CrossServerTradeView#onSignal} to open the local player's window, and
 * a captured bus records the signals the view emits so the abort assertions read them directly.
 */
class CrossServerTradeWindowTest {

    private static final PlayerRef SENDER = new PlayerRef(UUID.randomUUID(), "Sender");
    private static final String REMOTE_SERVER = "lobby-2";

    private ServerMock server;
    private Plugin plugin;
    private CrossTradeWindow window;
    private CapturingBus bus;
    private CrossServerTradeView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        KeyMessages messages = new KeyMessages();
        TestMenuEngine engine = TestMenuEngine.create(messages, new SyncScheduler());
        window = TradeWindows.crossServer(messages, engine.menus());
        bus = new CapturingBus();
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);
        CrossServerTrade coordinator = new CrossServerTrade(
                new FakeEscrowStore(), new PermissiveEconomy(), bus, new FakeDelivery(), clock, new SilentLogger());
        view = new CrossServerTradeView(messages, new NoopSink(), new SyncScheduler(), coordinator, bus, window);
        view.register(engine.bindings());
        engine.installListener(plugin);
        server.getPluginManager().registerEvents(view.newListener(), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPlainCloseReturnsTheStakedItemsAndAbortsThePeerExactlyOnce() {
        PlayerMock target = openWindowFor("Target");
        stake(target, new ItemStack(Material.DIAMOND, 5));

        // The player closes the window without confirming, the genuine abort path.
        server.getPluginManager().callEvent(new InventoryCloseEvent(target.getOpenInventory()));

        assertThat(amount(target, Material.DIAMOND)).isEqualTo(5);
        assertThat(view.isTrading(target.getUniqueId())).isFalse();
        assertThat(bus.signalsOfType(TradeSignalType.ABORT)).hasSize(1);
    }

    @Test
    void aConfirmDrivenCloseDoesNotFireASpuriousAbort() {
        PlayerMock target = openWindowFor("Target");
        CrossTradeHolder holder = holderOf(target);

        // Confirm wins the single-winner escrow gate; the window then closes, and Bukkit fires that close into the
        // listener. That confirming close must be silent, no ABORT, or every confirm would tear the peer down.
        view.confirm(holder);
        view.onWindowClosed(holder, List.of());

        assertThat(bus.signalsOfType(TradeSignalType.ABORT)).isEmpty();
    }

    @Test
    void aModuleStopDrainReturnsAnOpenWindowsItems() {
        PlayerMock target = openWindowFor("Target");
        stake(target, new ItemStack(Material.EMERALD, 3));

        // /uxmess reload trade, disable, or server stop: the wiring drains every open cross-server window.
        view.flushAll();

        assertThat(amount(target, Material.EMERALD)).isEqualTo(3);
        assertThat(view.isTrading(target.getUniqueId())).isFalse();
    }

    /** Deliver a remote invite for {@code name}, opening that local player's cross-server window and returning them. */
    private PlayerMock openWindowFor(String name) {
        PlayerMock target = server.addPlayer(name);
        view.onSignal(new TradeSignal(
                TradeId.newId(),
                TradeSignalType.INVITE,
                SENDER,
                new PlayerRef(UUID.randomUUID(), name),
                REMOTE_SERVER));
        return target;
    }

    private void stake(PlayerMock player, ItemStack stack) {
        player.getOpenInventory().getTopInventory().setItem(window.offerSlot(0), stack);
    }

    private CrossTradeHolder holderOf(PlayerMock player) {
        return java.util.Objects.requireNonNull(view.session(player.getUniqueId()));
    }

    private static int amount(PlayerMock player, Material material) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(stack -> stack != null && stack.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    /** Records the signals the view publishes so the abort assertions can read them; the local backend is fixed. */
    private static final class CapturingBus implements TradeBus {
        private final List<TradeSignal> sent = new ArrayList<>();

        List<TradeSignal> signalsOfType(TradeSignalType type) {
            return sent.stream().filter(signal -> signal.type() == type).toList();
        }

        @Override
        public void send(TradeSignal signal) {
            sent.add(signal);
        }

        @Override
        public void subscribe(Consumer<TradeSignal> handler) {}

        @Override
        public String localServer() {
            return "survival-1";
        }

        @Override
        public boolean healthy() {
            return true;
        }
    }

    /** A minimal in-memory escrow store: the confirm path holds one side's row through the coordinator. */
    private static final class FakeEscrowStore implements TradeEscrowStore {
        private final Map<String, TradeEscrow> rows = new HashMap<>();

        private static String key(TradeId id, UUID owner) {
            return id + "/" + owner;
        }

        @Override
        public void escrow(TradeEscrow escrow) {
            rows.put(key(escrow.tradeId(), escrow.owner().uuid()), escrow);
        }

        @Override
        public Optional<TradeEscrow> find(TradeId tradeId, UUID owner) {
            return Optional.ofNullable(rows.get(key(tradeId, owner)));
        }

        @Override
        public List<TradeEscrow> findByOwner(UUID owner) {
            return rows.values().stream()
                    .filter(row -> row.owner().uuid().equals(owner))
                    .toList();
        }

        @Override
        public boolean commitBoth(TradeId tradeId, UUID a, UUID b) {
            return false;
        }

        @Override
        public boolean claim(TradeId tradeId, UUID owner) {
            return false;
        }

        @Override
        public boolean beginRefund(TradeId tradeId, UUID owner) {
            return rows.remove(key(tradeId, owner)) != null;
        }

        @Override
        public void clear(TradeId tradeId, UUID owner) {
            rows.remove(key(tradeId, owner));
        }
    }

    /** An economy that permits every debit: the window tests move items, not money. */
    private static final class PermissiveEconomy implements TradeEconomy {
        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public void deposit(PlayerRef who, BigDecimal amount, String currencyId) {}
    }

    /** Item delivery is never exercised by the window's abort paths; treat everyone as online. */
    private static final class FakeDelivery implements TradeItemDelivery {
        @Override
        public boolean isOnline(PlayerRef player) {
            return true;
        }

        @Override
        public void deliver(PlayerRef player, String itemData) {}
    }

    /** Resolves any key to its plain key string; the layout renders it as literal text. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Swallows delivery; the window tests assert on items, session state, and bus signals, not on chat. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** Runs every scheduled task inline so the open, item return, and escrow complete in-test. */
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

    private static final class SilentLogger implements Logger {
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

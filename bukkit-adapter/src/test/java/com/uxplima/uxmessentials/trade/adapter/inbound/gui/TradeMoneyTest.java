package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of Phase 3: money and the item blacklist. Money is staked directly on the shared exchange (the
 * seam the anvil prompt drives) so the tests need no real anvil; a both-confirm then settles it through a fake
 * {@link TradeEconomy}. Money moves both ways atomically with the item swap when both sides can pay; a payer who cannot
 * afford their leg blocks the whole trade with no item or money moved; and a blacklisted material is refused back out of
 * the window rather than accepted.
 */
class TradeMoneyTest {

    private ServerMock server;
    private Plugin plugin;
    private TradeWindow window;
    private TradeSessions sessions;
    private TradeView view;
    private RecordingEconomy economy;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        TradeConfig config = new TradeConfig(true, List.of("coins"), List.of("BEDROCK"), 0, 5, false, 60, false);
        KeyMessages messages = new KeyMessages();
        TestMenuEngine engine = TestMenuEngine.create(messages, new SyncScheduler());
        window = TradeWindows.sameServer(messages, engine.menus(), config.currenciesAllowed());
        sessions = new TradeSessions();
        economy = new RecordingEconomy();
        TradeExperience experience = new NoopExperience();
        view = new TradeView(
                messages,
                new NoopSink(),
                new SyncScheduler(),
                config,
                sessions,
                window,
                (p, v, c, s, x) -> {},
                (p, v, s, x) -> {},
                new TradeSettlement(economy, experience),
                experience,
                receipt -> {},
                event -> {});
        view.register(engine.bindings());
        engine.installListener(plugin);
        server.getPluginManager().registerEvents(view.newListener(), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void moneyMovesBothWaysAtomicallyWithTheItemSwapOnBothConfirm() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 1));
        place(bob, 0, new ItemStack(Material.EMERALD, 1));
        exchangeOf(alice).setMoney(TradeSide.INITIATOR, "coins", BigDecimal.valueOf(100));
        exchangeOf(alice).setMoney(TradeSide.PARTNER, "coins", BigDecimal.valueOf(50));

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        // Both money legs settled, in both directions.
        assertThat(economy.moves)
                .containsExactlyInAnyOrder(
                        new Move(alice.getName(), bob.getName(), BigDecimal.valueOf(100)),
                        new Move(bob.getName(), alice.getName(), BigDecimal.valueOf(50)));
        // And the items swapped alongside the money.
        assertThat(amount(alice, Material.EMERALD)).isEqualTo(1);
        assertThat(amount(bob, Material.DIAMOND)).isEqualTo(1);
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void aPayerWhoCannotAffordBlocksTheTradeWithNoItemOrMoneyMoved() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        economy.broke(alice.getName());
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 1));
        place(bob, 0, new ItemStack(Material.EMERALD, 1));
        exchangeOf(alice).setMoney(TradeSide.INITIATOR, "coins", BigDecimal.valueOf(100));

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        // No money moved…
        assertThat(economy.moves).isEmpty();
        // …and no items swapped: each side got its own stack back.
        assertThat(amount(alice, Material.DIAMOND)).isEqualTo(1);
        assertThat(amount(alice, Material.EMERALD)).isZero();
        assertThat(amount(bob, Material.EMERALD)).isEqualTo(1);
        assertThat(amount(bob, Material.DIAMOND)).isZero();
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void aBlacklistedItemIsRefusedIntoTheWindow() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);

        alice.setItemOnCursor(new ItemStack(Material.BEDROCK));
        InventoryClickEvent event = new InventoryClickEvent(
                alice.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                window.offerSlot(0),
                ClickType.LEFT,
                InventoryAction.PLACE_ALL);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        // The offer stays empty: the blacklisted stack never entered the domain offer.
        assertThat(exchangeOf(alice).session().offer(TradeSide.INITIATOR).items())
                .isEmpty();
    }

    private void place(PlayerMock player, int slot, ItemStack stack) {
        player.getOpenInventory().getTopInventory().setItem(window.offerSlot(slot), stack);
        view.syncOffer(holder(player));
    }

    private TradeHolder holder(PlayerMock player) {
        TradeExchange exchange = exchangeOf(player);
        return exchange.participant(TradeSide.INITIATOR).uuid().equals(player.getUniqueId())
                ? exchange.holder(TradeSide.INITIATOR)
                : exchange.holder(TradeSide.PARTNER);
    }

    private TradeExchange exchangeOf(PlayerMock player) {
        return java.util.Objects.requireNonNull(sessions.find(player.getUniqueId()));
    }

    private static int amount(PlayerMock player, Material material) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(stack -> stack != null && stack.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    /** A recorded money movement, by player name, for the assertions. */
    private record Move(String from, String to, BigDecimal amount) {}

    /** Records every transfer and fails any transfer/affordability for a player marked broke. */
    private static final class RecordingEconomy implements TradeEconomy {
        private final List<Move> moves = new ArrayList<>();
        private final List<String> broke = new ArrayList<>();

        void broke(String name) {
            broke.add(name);
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return !broke.contains(who.name());
        }

        @Override
        public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
            if (broke.contains(from.name())) {
                return false;
            }
            moves.add(new Move(from.name(), to.name(), amount));
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("same-server settlement uses transfer");
        }

        @Override
        public void deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("same-server settlement uses transfer");
        }
    }

    /** No experience is staked in these money tests, so the experience seam is a permissive stub. */
    private static final class NoopExperience implements TradeExperience {
        @Override
        public long available(PlayerRef who) {
            return 0L;
        }

        @Override
        public boolean withdraw(PlayerRef who, long points) {
            return true;
        }

        @Override
        public void deposit(PlayerRef who, long points) {}
    }

    /** Resolves any key to its plain key string. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Swallows delivery; these tests assert on items, money moves, and session state, not on chat. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** Runs every scheduled task inline so the open, settlement, and delivery complete in-test. */
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

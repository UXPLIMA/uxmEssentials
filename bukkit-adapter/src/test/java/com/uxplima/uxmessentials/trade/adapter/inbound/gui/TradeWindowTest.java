package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
 * MockBukkit coverage of the same-server trade window: two live views over one shared {@code TradeSession}. Placing a
 * stack re-reads the offer into the session and resets both confirmations (anti-scam); a change after one side has
 * confirmed un-confirms both; a both-confirm swaps the stacks between the two players; and every abort, a close, or a
 * commit into a full inventory returns or delivers every stack with none lost or duplicated.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open, the live re-render, and the settlement all run
 * inline. A placement is driven by putting the stack in the window's editable slot and calling the same {@code
 * syncOffer} re-read the listener schedules after a click resolves. This keeps the test deterministic without
 * simulating Bukkit's mid-click slot mechanics.
 */
class TradeWindowTest {

    private ServerMock server;
    private Plugin plugin;
    private TradeWindow window;
    private TradeSessions sessions;
    private TradeView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        TradeConfig config = new TradeConfig(true, List.of("coins"), List.of(), 0, 5, false, 60, false);
        KeyMessages messages = new KeyMessages();
        TestMenuEngine engine = TestMenuEngine.create(messages, new SyncScheduler());
        window = TradeWindows.sameServer(messages, engine.menus(), List.of());
        sessions = new TradeSessions();
        // This coverage is items-only: no money prompt is driven and money is off (economy disabled), and no experience
        // is staked, so the settlement moves nothing. Audit is off in this config, so the no-op sink is never
        // consulted.
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
                new TradeSettlement(new NoopEconomy(), experience),
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
    void placingAnItemUpdatesTheSessionAndResetsBothConfirms() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);

        place(alice, 0, new ItemStack(Material.DIAMOND, 3));

        TradeExchange exchange = exchange(alice);
        assertThat(exchange.session().offer(TradeSide.INITIATOR).items()).hasSize(1);
        assertThat(exchange.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(exchange.confirmed(TradeSide.PARTNER)).isFalse();
        // The counterpart's read-only mirror shows Alice's stack live.
        assertThat(bob.getOpenInventory().getTopInventory().getItem(window.mirrorSlot(0)))
                .isNotNull();
    }

    @Test
    void aChangeAfterOneSideConfirmedUnconfirmsBoth() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 1));

        view.confirm(holder(alice));
        TradeExchange exchange = exchange(alice);
        assertThat(exchange.confirmed(TradeSide.INITIATOR)).isTrue();

        // Bob changes his offer after Alice confirmed: the invariant clears BOTH confirmations.
        place(bob, 0, new ItemStack(Material.EMERALD, 1));

        assertThat(exchange.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(exchange.confirmed(TradeSide.PARTNER)).isFalse();
    }

    @Test
    void bothConfirmSwapsItemsBetweenThePlayers() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 3));
        place(bob, 0, new ItemStack(Material.EMERALD, 2));

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        assertThat(amount(alice, Material.EMERALD)).isEqualTo(2);
        assertThat(amount(bob, Material.DIAMOND)).isEqualTo(3);
        assertThat(amount(alice, Material.DIAMOND)).isZero();
        assertThat(amount(bob, Material.EMERALD)).isZero();
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void aStackPlacedInTheSameTickAsConfirmIsDeliveredNotDiscarded() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 3));
        view.confirm(holder(alice));

        // Bob drops an emerald into his window and confirms in the same tick, before the deferred re-read (syncOffer)
        // that would fold it into the offer snapshot has run, so the domain session still shows Bob's offer empty. The
        // Folia sub-tick fix reads each side's LIVE window at commit, so the emerald is delivered rather than
        // discarded.
        bob.getOpenInventory().getTopInventory().setItem(window.offerSlot(0), new ItemStack(Material.EMERALD, 2));
        view.confirm(holder(bob));

        assertThat(amount(alice, Material.EMERALD)).isEqualTo(2);
        assertThat(amount(bob, Material.DIAMOND)).isEqualTo(3);
        assertThat(amount(alice, Material.DIAMOND)).isZero();
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void closingReturnsTheOfferedItemsToTheirOwner() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 5));

        // Alice closes her window; the listener cancels the trade and returns both sides' items.
        server.getPluginManager().callEvent(new InventoryCloseEvent(alice.getOpenInventory()));

        assertThat(amount(alice, Material.DIAMOND)).isEqualTo(5);
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void committingWithAFullRecipientDropsOverflowWithoutLoss() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 5));
        fillStorage(bob, new ItemStack(Material.STONE, 64));

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        int inBobInventory = amount(bob, Material.DIAMOND);
        int onGround = server.getEntities().stream()
                .filter(Item.class::isInstance)
                .map(entity -> ((Item) entity).getItemStack())
                .filter(stack -> stack.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount)
                .sum();
        assertThat(inBobInventory + onGround).isEqualTo(5); // none deleted
    }

    @Test
    void theCounterpartsMirroredOfferCannotBeTakenOutOfTheWindow() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(bob, 0, new ItemStack(Material.DIAMOND, 3));

        // Alice tries to pick Bob's stack straight out of the read-only mirror. Were this allowed she would walk away
        // with a copy of an item Bob still owns, so the click has to die at the region rather than at the settlement.
        InventoryClickEvent event = new InventoryClickEvent(
                alice.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                window.mirrorSlot(0),
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(alice.getItemOnCursor().getType()).isEqualTo(Material.AIR);
        // Bob's stack is still in his own offer, unchanged and undivided.
        assertThat(exchange(bob).session().offer(TradeSide.PARTNER).items()).hasSize(1);
        assertThat(bob.getOpenInventory().getTopInventory().getItem(window.offerSlot(0)))
                .isNotNull();

        // The same click one region over, in Alice's own offer, goes through, so the refusal above is the mirror
        // being read-only, not the window cancelling everything.
        alice.setItemOnCursor(new ItemStack(Material.EMERALD));
        InventoryClickEvent intoOwnOffer = new InventoryClickEvent(
                alice.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                window.offerSlot(0),
                ClickType.LEFT,
                InventoryAction.PLACE_ALL);
        server.getPluginManager().callEvent(intoOwnOffer);
        assertThat(intoOwnOffer.isCancelled()).isFalse();
    }

    private void place(PlayerMock player, int slot, ItemStack stack) {
        player.getOpenInventory().getTopInventory().setItem(window.offerSlot(slot), stack);
        view.syncOffer(holder(player));
    }

    private TradeHolder holder(PlayerMock player) {
        TradeExchange exchange = exchange(player);
        return exchange.participant(TradeSide.INITIATOR).uuid().equals(player.getUniqueId())
                ? exchange.holder(TradeSide.INITIATOR)
                : exchange.holder(TradeSide.PARTNER);
    }

    private TradeExchange exchange(PlayerMock player) {
        return java.util.Objects.requireNonNull(sessions.find(player.getUniqueId()));
    }

    private static int amount(PlayerMock player, Material material) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(stack -> stack != null && stack.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private static void fillStorage(PlayerMock player, ItemStack filler) {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, filler.clone());
        }
    }

    /** An economy that is never charged here (money is off and none is staked), so every call is a permissive stub. */
    private static final class NoopEconomy implements TradeEconomy {
        @Override
        public boolean canAfford(PlayerRef who, java.math.BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean transfer(PlayerRef from, PlayerRef to, java.math.BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, java.math.BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public void deposit(PlayerRef who, java.math.BigDecimal amount, String currencyId) {}
    }

    /** An experience seam that is never staked against here, so every call is a permissive stub. */
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

    /** Resolves any key to its plain key string; the layout renders it as literal text. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Swallows delivery; the trade-window tests assert on items and session state, not on chat. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** Runs every scheduled task inline so the open, re-render, and settlement complete in-test. */
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

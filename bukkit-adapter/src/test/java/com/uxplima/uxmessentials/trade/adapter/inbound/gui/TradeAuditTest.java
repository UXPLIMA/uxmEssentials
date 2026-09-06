package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeReceipt;
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.port.TradeAudit;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import com.uxplima.uxmessentials.trade.domain.event.TradeCancelled;
import com.uxplima.uxmessentials.trade.domain.event.TradeCompleted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the completed-trade audit: a both-confirm swap emits exactly one {@link TradeReceipt}, the
 * two participants and each side's item quantity. When the module's {@code audit} knob is on, and emits nothing when it
 * is off. The scheduler is synchronous so the settlement (and its audit emission) runs inline.
 */
class TradeAuditTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCompletedTradeEmitsOneReceiptWhenAuditIsOn() {
        Fixture fixture = fixture(true);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        fixture.view.open(alice, bob);
        place(fixture, alice, new ItemStack(Material.DIAMOND, 3));
        place(fixture, bob, new ItemStack(Material.EMERALD, 2));

        fixture.view.confirm(holder(fixture, alice));
        fixture.view.confirm(holder(fixture, bob));

        assertThat(fixture.audit.receipts).hasSize(1);
        TradeReceipt receipt = fixture.audit.receipts.get(0);
        assertThat(receipt.initiator().name()).isEqualTo("Alice");
        assertThat(receipt.partner().name()).isEqualTo("Bob");
        assertThat(receipt.initiatorItems()).isEqualTo(3);
        assertThat(receipt.partnerItems()).isEqualTo(2);
    }

    @Test
    void aCompletedTradeIsSilentWhenAuditIsOff() {
        Fixture fixture = fixture(false);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        fixture.view.open(alice, bob);
        place(fixture, alice, new ItemStack(Material.DIAMOND, 3));
        place(fixture, bob, new ItemStack(Material.EMERALD, 2));

        fixture.view.confirm(holder(fixture, alice));
        fixture.view.confirm(holder(fixture, bob));

        // The swap still ran…
        assertThat(fixture.sessions.isTrading(alice.getUniqueId())).isFalse();
        // …but no audit line was emitted.
        assertThat(fixture.audit.receipts).isEmpty();
    }

    @Test
    void aCompletedTradePublishesWhatEachSideGaveEvenWithTheAuditOff() {
        Fixture fixture = fixture(false);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        fixture.view.open(alice, bob);
        place(fixture, alice, new ItemStack(Material.DIAMOND, 3));
        place(fixture, bob, new ItemStack(Material.EMERALD, 2));

        fixture.view.confirm(holder(fixture, alice));
        fixture.view.confirm(holder(fixture, bob));

        assertThat(fixture.events.published).hasSize(1);
        TradeCompleted fact = (TradeCompleted) fixture.events.published.get(0);
        assertThat(fact.initiator().name()).isEqualTo("Alice");
        assertThat(fact.partner().name()).isEqualTo("Bob");
        assertThat(fact.initiatorItems()).isEqualTo(3);
        assertThat(fact.partnerItems()).isEqualTo(2);
    }

    @Test
    void aTradeNobodyFinishedPublishesTheCancellationInstead() {
        Fixture fixture = fixture(true);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        fixture.view.open(alice, bob);
        place(fixture, alice, new ItemStack(Material.DIAMOND, 3));

        // The drain every module stop runs, which is the same cancel path a closed window takes.
        fixture.view.closeAll();

        assertThat(fixture.events.published).hasSize(1);
        TradeCancelled fact = (TradeCancelled) fixture.events.published.get(0);
        assertThat(fact.initiator().name()).isEqualTo("Alice");
        assertThat(fact.partner().name()).isEqualTo("Bob");
        assertThat(fixture.audit.receipts).isEmpty();
    }

    private Fixture fixture(boolean auditEnabled) {
        TradeSessions sessions = new TradeSessions();
        RecordingAudit audit = new RecordingAudit();
        RecordingEvents events = new RecordingEvents();
        TradeConfig config = new TradeConfig(true, List.of("coins"), List.of(), 0, 5, false, 60, auditEnabled);
        KeyMessages messages = new KeyMessages();
        TestMenuEngine engine = TestMenuEngine.create(messages, new SyncScheduler());
        TradeWindow window = TradeWindows.sameServer(messages, engine.menus(), List.of());
        TradeExperience experience = new NoopExperience();
        TradeView view = new TradeView(
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
                audit,
                events);
        view.register(engine.bindings());
        engine.installListener(plugin);
        server.getPluginManager().registerEvents(view.newListener(), plugin);
        return new Fixture(sessions, window, view, audit, events);
    }

    private void place(Fixture fixture, PlayerMock player, ItemStack stack) {
        player.getOpenInventory().getTopInventory().setItem(fixture.window.offerSlot(0), stack);
        fixture.view.syncOffer(holder(fixture, player));
    }

    private TradeHolder holder(Fixture fixture, PlayerMock player) {
        TradeExchange exchange = java.util.Objects.requireNonNull(fixture.sessions.find(player.getUniqueId()));
        return exchange.participant(TradeSide.INITIATOR).uuid().equals(player.getUniqueId())
                ? exchange.holder(TradeSide.INITIATOR)
                : exchange.holder(TradeSide.PARTNER);
    }

    /** One test's collaborators over a shared session: kept local so each test picks its own audit setting. */
    private record Fixture(
            TradeSessions sessions, TradeWindow window, TradeView view, RecordingAudit audit, RecordingEvents events) {}

    /** Collects the facts the view published, so a test can assert on them rather than on the audit line. */
    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** Captures every completed-trade receipt so the test can assert emission (or silence). */
    private static final class RecordingAudit implements TradeAudit {
        private final List<TradeReceipt> receipts = new ArrayList<>();

        @Override
        public void completed(TradeReceipt receipt) {
            receipts.add(receipt);
        }
    }

    /** No money is staked here, so the economy seam is a permissive stub. */
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

    /** No experience is staked here, so the experience seam is a permissive stub. */
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

    /** Swallows delivery. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** Runs every scheduled task inline so the settlement and its audit emission complete in-test. */
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

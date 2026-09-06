package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeOutcome;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * Drives the package-private convert apply seam of the engine-rendered exchange dashboard, the branch the anvil
 * prompt's submit callback runs. MockBukkit cannot drive a live anvil, so the golden test (in the menu's sibling
 * package) covers the render and the picker re-open while this test, in the menu's own package, proves a typed
 * amount runs the exchange use case for the source/target pair, and a malformed amount runs no exchange, exactly as
 * the old {@code ExchangeGuiView} did.
 */
class EconomyExchangeMenuApplyTest {

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
    private PlayerMock player;
    private PlayerRef viewerRef;

    private EconomyProvider provider;
    private ExchangeService exchangeService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        provider = mock(EconomyProvider.class);
        // The apply seam re-opens the panel after the exchange, which re-reads the two balances off the provider.
        when(provider.balance(any(PlayerRef.class), any(Currency.class))).thenReturn(Money.of(COINS, BigDecimal.ZERO));
        exchangeService = mock(ExchangeService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void convertApplySeamRunsTheExchangeForTheTypedAmount() {
        EconomyExchangeMenu menu = menu();
        when(exchangeService.exchange(eq(viewerRef), eq(new BigDecimal("50")), eq(COINS), eq(GEMS)))
                .thenReturn(ExchangeOutcome.success(new BigDecimal("50"), new BigDecimal("100")));

        menu.applyConvert(player, viewerRef, COINS, GEMS, "50");

        verify(exchangeService).exchange(eq(viewerRef), eq(new BigDecimal("50")), eq(COINS), eq(GEMS));
    }

    @Test
    void aMalformedAmountRunsNoExchange() {
        EconomyExchangeMenu menu = menu();

        menu.applyConvert(player, viewerRef, COINS, GEMS, "not-a-number");

        verify(exchangeService, never()).exchange(any(), any(), any(), any());
    }

    private EconomyExchangeMenu menu() {
        return new EconomyExchangeMenu(
                mock(Menus.class),
                provider,
                exchangeService,
                new SyncScheduler(),
                new EconomyNotifier(new KeyMessages(), new NoopSink()),
                new KeyMessages(),
                mock(TextInput.class),
                mock(CurrencyPickerMenu.class));
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

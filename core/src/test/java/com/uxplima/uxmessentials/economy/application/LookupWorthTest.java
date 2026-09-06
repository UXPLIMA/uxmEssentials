package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.fakes.CapturingSink;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.KeyMessages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /worth}: report the configured sell value of a held item. A priced single item renders the unit
 * worth, a priced stack renders the unit worth and the stack total, and an unpriced material renders the
 * not-sellable notice. The use case never touches a balance: it is a pure pricing read.
 */
class LookupWorthTest {

    private CapturingSink sink;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        sink = new CapturingSink();
        viewer = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    private LookupWorth lookupWith(WorthTable table) {
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), sink);
        return new LookupWorth(table, notifier, Currencies.COINS, java.util.List.of(Currencies.COINS));
    }

    @Test
    void unpricedMaterialReportsNotSellable() {
        LookupWorth lookup = lookupWith(WorthTable.empty());

        lookup.report(viewer, "emerald", 1);

        assertThat(sink.delivered("wallet.worth-not-sellable")).isTrue();
    }

    @Test
    void singleItemReportsItsUnitWorth() {
        LookupWorth lookup = lookupWith(new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))));

        lookup.report(viewer, "diamond", 1);

        assertThat(sink.delivered("wallet.worth-result")).isTrue();
    }

    @Test
    void stackReportsTheStackTotal() {
        LookupWorth lookup = lookupWith(new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))));

        lookup.report(viewer, "diamond", 4);

        assertThat(sink.delivered("wallet.worth-result-stack")).isTrue();
    }
}

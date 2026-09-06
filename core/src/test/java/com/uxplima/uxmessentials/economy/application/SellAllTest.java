package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.fakes.CapturingSink;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.KeyMessages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /sellall}: sell every priced material in the inventory snapshot, crediting each currency the items
 * price in. Unpriced materials are left in place (no spam, not reported for removal), a material is reported as
 * sold only once the credit for its currency applied, the proceeds are tracked per currency, and the seller is
 * sent exactly one summary covering every currency that paid out.
 */
class SellAllTest {

    private InMemoryWalletRepository repo;
    private CapturingSink sink;
    private PlayerRef seller;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWalletRepository();
        sink = new CapturingSink();
        seller = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    private SellAll sellAllWith(WorthTable table) {
        CurrencyRegistry registry = CurrencyRegistry.single(Currencies.COINS);
        return sellAllOver(table, registry);
    }

    private SellAll multiCurrencySellAll(WorthTable table, Currency... currencies) {
        CurrencyRegistry registry = CurrencyRegistry.of(Set.of(currencies), Currencies.COINS.id());
        return sellAllOver(table, registry);
    }

    private SellAll sellAllOver(WorthTable table, CurrencyRegistry registry) {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NativeEconomyProvider provider = new NativeEconomyProvider(repo, registry, clock);
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), sink);
        return new SellAll(provider, table, notifier, Currencies.COINS, registry.all());
    }

    @Test
    void sellsOnlyPricedMaterialsAndCreditsTheTotal() {
        SellAll sellAll = sellAllWith(new WorthTable(Map.of(
                "diamond", Worth.of(new BigDecimal("10"), "coins"),
                "iron_ingot", Worth.of(new BigDecimal("2"), "coins"))));
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("diamond", 4);
        inventory.put("iron_ingot", 5);
        inventory.put("dirt", 64);

        SellAllOutcome outcome = sellAll.sellAll(seller, inventory);

        assertThat(outcome.earned()).contains(Money.of(Currencies.COINS, 50));
        assertThat(outcome.sold()).containsOnlyKeys("diamond", "iron_ingot");
        assertThat(repo.findByOwner(seller).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 50));
        assertThat(sink.count("wallet.sellall-summary")).isEqualTo(1);
    }

    @Test
    void nothingPricedIsRefusedWithoutCredit() {
        SellAll sellAll = sellAllWith(WorthTable.empty());

        SellAllOutcome outcome = sellAll.sellAll(seller, Map.of("dirt", 64, "cobblestone", 32));

        assertThat(outcome.sold()).isEmpty();
        assertThat(outcome.earned()).isEmpty();
        assertThat(repo.findByOwner(seller)).isEmpty();
        assertThat(sink.delivered("wallet.sell-nothing")).isTrue();
    }

    @Test
    void creditsEachCurrencyAndReportsEveryMaterialSoldWithOneSummary() {
        SellAll sellAll = multiCurrencySellAll(
                new WorthTable(Map.of(
                        "diamond", Worth.of(new BigDecimal("10"), "coins"),
                        "emerald", Worth.of(new BigDecimal("3"), "gems"))),
                Currencies.COINS,
                Currencies.GEMS);
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("diamond", 4);
        inventory.put("emerald", 5);

        SellAllOutcome outcome = sellAll.sellAll(seller, inventory);

        assertThat(outcome.sold()).containsOnlyKeys("diamond", "emerald");
        assertThat(outcome.perCurrency())
                .containsEntry(Currencies.COINS, Money.of(Currencies.COINS, 40))
                .containsEntry(Currencies.GEMS, Money.of(Currencies.GEMS, 15));
        assertThat(repo.findByOwner(seller).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 40));
        assertThat(repo.findByOwner(seller).orElseThrow().balanceOf(Currencies.GEMS))
                .isEqualTo(Money.of(Currencies.GEMS, 15));
        // Exactly one summary regardless of how many currencies paid out.
        assertThat(sink.count("wallet.sellall-summary")).isEqualTo(1);
    }

    @Test
    void rejectedCurrencyLeavesItsStacksUnsoldButStillSettlesTheOthers() {
        Currency cappedCoins = Currencies.cappedCoins(20);
        SellAll sellAll = multiCurrencySellAll(
                new WorthTable(Map.of(
                        "diamond", Worth.of(new BigDecimal("10"), "coins"),
                        "emerald", Worth.of(new BigDecimal("3"), "gems"))),
                cappedCoins,
                Currencies.GEMS);
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("diamond", 4); // 40 coins. Over the cap of 20, so the coins credit is rejected
        inventory.put("emerald", 5); // 15 gems, settles

        SellAllOutcome outcome = sellAll.sellAll(seller, inventory);

        // Only the gems-priced material was actually paid for, so only it may be removed.
        assertThat(outcome.sold()).containsOnlyKeys("emerald");
        assertThat(outcome.perCurrency()).containsOnlyKeys(Currencies.GEMS);
        assertThat(repo.findByOwner(seller).orElseThrow().balanceOf(cappedCoins))
                .isEqualTo(Money.of(cappedCoins, 0));
        assertThat(repo.findByOwner(seller).orElseThrow().balanceOf(Currencies.GEMS))
                .isEqualTo(Money.of(Currencies.GEMS, 15));
    }
}

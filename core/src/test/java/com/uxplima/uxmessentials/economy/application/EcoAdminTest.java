package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.fakes.CapturingSink;
import com.uxplima.uxmessentials.economy.fakes.CapturingTransactionHistory;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.KeyMessages;
import com.uxplima.uxmessentials.economy.fakes.RecordingAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The eco-admin surface: give/take/set/reset and the bulk verbs. Give credits and take debits through the
 * provider, set writes an exact balance, reset returns it to the currency's starting figure, and a bulk verb
 * audits exactly once with an affected-count (never per-target spam). An offline never-joined target is
 * materialised so a give/set to them does not fail.
 */
class EcoAdminTest {

    private InMemoryWalletRepository repo;
    private RecordingAudit audit;
    private CapturingTransactionHistory history;
    private EcoAdmin admin;
    private PlayerRef operator;
    private PlayerRef target;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWalletRepository();
        audit = new RecordingAudit();
        history = new CapturingTransactionHistory();
        CurrencyRegistry registry = CurrencyRegistry.of(Set.of(Currencies.COINS), Currencies.COINS.id());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NativeEconomyProvider provider = new NativeEconomyProvider(repo, registry, clock);
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), new CapturingSink());
        admin = new EcoAdmin(provider, repo, audit, notifier, history, clock);
        operator = new PlayerRef(UUID.randomUUID(), "Operator");
        target = new PlayerRef(UUID.randomUUID(), "Target");
    }

    @Test
    void giveCreditsAndAuditsOnce() {
        admin.give(operator, target, Money.of(Currencies.COINS, 100));

        assertThat(repo.findByOwner(target).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 100));
        assertThat(audit.lines())
                .singleElement()
                .extracting(RecordingAudit.Line::reason)
                .isEqualTo(EconomyReason.ADMIN_GIVE);
    }

    @Test
    void takeDebitsWithinBalance() {
        repo.credit(target, Money.of(Currencies.COINS, 100));

        admin.take(operator, target, Money.of(Currencies.COINS, 40));

        assertThat(repo.findByOwner(target).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 60));
    }

    @Test
    void setWritesExactBalance() {
        repo.credit(target, Money.of(Currencies.COINS, 100));

        admin.set(operator, target, Money.of(Currencies.COINS, 7));

        assertThat(repo.findByOwner(target).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 7));
    }

    @Test
    void resetReturnsToStartingBalance() {
        Currency starting = Currency.builder(CurrencyId.of("coins"))
                .starting(java.math.BigDecimal.valueOf(50))
                .build();
        repo.credit(target, Money.of(starting, 999));

        admin.reset(operator, target, starting);

        assertThat(repo.findByOwner(target).orElseThrow().balanceOf(starting)).isEqualTo(Money.of(starting, 50));
    }

    @Test
    void giveAllAuditsOnceWithAffectedCount() {
        PlayerRef a = new PlayerRef(UUID.randomUUID(), "A");
        PlayerRef b = new PlayerRef(UUID.randomUUID(), "B");
        PlayerRef c = new PlayerRef(UUID.randomUUID(), "C");

        int affected = admin.giveAll(operator, List.of(a, b, c), Money.of(Currencies.COINS, 10));

        assertThat(affected).isEqualTo(3);
        assertThat(audit.lines()).singleElement().satisfies(line -> {
            assertThat(line.reason()).isEqualTo(EconomyReason.ADMIN_GIVEALL);
            assertThat(line.affected()).isEqualTo(3);
        });
        assertThat(repo.findByOwner(a).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 10));
    }

    @Test
    void giveRandomRangeCreditsAnExactAmountWithinTheRange() {
        Money min = Money.of(Currencies.COINS, 10);
        Money max = Money.of(Currencies.COINS, 100);

        for (int i = 0; i < 200; i++) {
            PlayerRef who = new PlayerRef(UUID.randomUUID(), "T" + i);
            Money credited = admin.giveRandomRange(operator, who, min, max).orElseThrow();

            assertThat(credited.amount()).isGreaterThanOrEqualTo(min.amount());
            assertThat(credited.amount()).isLessThanOrEqualTo(max.amount());
            // The credited figure is normalised to the currency precision (no double drift).
            assertThat(credited.amount().scale()).isEqualTo(Currencies.COINS.precision());
            assertThat(repo.findByOwner(who).orElseThrow().balanceOf(Currencies.COINS))
                    .isEqualTo(credited);
        }
    }

    @Test
    void giveRandomRangeWithEqualBoundsCreditsExactlyThatAmount() {
        Money exact = Money.of(Currencies.COINS, 42);

        Money credited = admin.giveRandomRange(operator, target, exact, exact).orElseThrow();

        assertThat(credited).isEqualTo(exact);
    }

    @Test
    void giveRecordsACreditTransaction() {
        admin.give(operator, target, Money.of(Currencies.COINS, 100));

        assertThat(history.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.kind()).isEqualTo("CREDIT");
            assertThat(entry.ownerId()).isEqualTo(target.uuid().toString());
            assertThat(entry.amount()).isEqualTo(Money.of(Currencies.COINS, 100));
            assertThat(entry.reason()).isEqualTo(EconomyReason.ADMIN_GIVE);
        });
    }

    @Test
    void takeRecordsADebitTransaction() {
        repo.credit(target, Money.of(Currencies.COINS, 100));

        admin.take(operator, target, Money.of(Currencies.COINS, 40));

        assertThat(history.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.kind()).isEqualTo("DEBIT");
            assertThat(entry.ownerId()).isEqualTo(target.uuid().toString());
            assertThat(entry.amount()).isEqualTo(Money.of(Currencies.COINS, 40));
            assertThat(entry.reason()).isEqualTo(EconomyReason.ADMIN_TAKE);
        });
    }

    @Test
    void aFailedTakeRecordsNoTransaction() {
        // The target has no funds, so the guarded debit rejects: nothing should reach the transaction trail.
        admin.take(operator, target, Money.of(Currencies.COINS, 40));

        assertThat(history.entries()).isEmpty();
    }

    @Test
    void setRecordsTheNetChangeAgainstThePriorBalance() {
        repo.credit(target, Money.of(Currencies.COINS, 100));

        admin.set(operator, target, Money.of(Currencies.COINS, 30));

        assertThat(history.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.kind()).isEqualTo("DEBIT");
            assertThat(entry.amount()).isEqualTo(Money.of(Currencies.COINS, 70));
            assertThat(entry.reason()).isEqualTo(EconomyReason.ADMIN_SET);
        });
    }

    @Test
    void aZeroDeltaSetRecordsNoTransaction() {
        repo.credit(target, Money.of(Currencies.COINS, 50));

        admin.set(operator, target, Money.of(Currencies.COINS, 50));

        assertThat(history.entries()).isEmpty();
    }

    @Test
    void giveToNeverJoinedTargetMaterialisesTheirRow() {
        PlayerRef ghost = new PlayerRef(UUID.randomUUID(), "Ghost");

        admin.give(operator, ghost, Money.of(Currencies.COINS, 25));

        assertThat(repo.findByOwner(ghost).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 25));
    }
}

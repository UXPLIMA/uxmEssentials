package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.event.LoanDisbursed;
import com.uxplima.uxmessentials.economy.domain.event.LoanRepaid;
import com.uxplima.uxmessentials.economy.fakes.CapturingEvents;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryLoanRepository;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The loan service orchestration: disburse and repay route through the atomic repository, each applied change
 * raises exactly one event, every distinct failure maps to its own {@link LoanError}, and a foreign provider
 * refuses the feature rather than risking a non-atomic move. The final installment settles the exact residual
 * and an over-offer is trimmed so the debtor is never debited more than they owe.
 */
class LoanServiceTest {

    private InMemoryWalletRepository wallets;
    private InMemoryLoanRepository loans;
    private CapturingEvents events;
    private LoanService service;
    private PlayerRef debtor;

    @BeforeEach
    void setUp() {
        wallets = new InMemoryWalletRepository();
        loans = new InMemoryLoanRepository(wallets);
        events = new CapturingEvents();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        service = new LoanService(loans, LoanPolicy.defaults(), events, clock, true);
        debtor = new PlayerRef(UUID.randomUUID(), "Debtor");
    }

    @Test
    void takeLoanCreditsThePrincipalAndRaisesOneDisburseEvent() {
        Money principal = Money.of(Currencies.COINS, 1000);

        Result<Loan, LoanError> result = service.takeLoan(debtor, principal, 4);

        assertThat(result.isOk()).isTrue();
        assertThat(wallets.findByOwner(debtor).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(principal);
        assertThat(events.published()).hasSize(1).first().isInstanceOf(LoanDisbursed.class);
    }

    @Test
    void takeLoanAboveTheCreditLimitIsRejectedWithLimitExceeded() {
        // Default score 500 * default multiplier 1000 = 500_000 limit.
        Money principal = Money.of(Currencies.COINS, 600_000);

        Result<Loan, LoanError> result = service.takeLoan(debtor, principal, 4);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(LoanError.LIMIT_EXCEEDED);
        assertThat(wallets.findByOwner(debtor)).isEmpty();
    }

    @Test
    void invalidTermsAreRejected() {
        assertThat(service.takeLoan(debtor, Money.of(Currencies.COINS, 100), 0).errorOrThrow())
                .isEqualTo(LoanError.INVALID_TERMS);
        assertThat(service.takeLoan(debtor, Money.zero(Currencies.COINS), 4).errorOrThrow())
                .isEqualTo(LoanError.INVALID_TERMS);
    }

    @Test
    void aForeignProviderRefusesLoansRatherThanRiskANonAtomicMove() {
        LoanService foreign = new LoanService(
                loans, LoanPolicy.defaults(), events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), false);

        assertThat(foreign.takeLoan(debtor, Money.of(Currencies.COINS, 100), 4).errorOrThrow())
                .isEqualTo(LoanError.PROVIDER_UNSUPPORTED);
        assertThat(foreign.payInstallment(debtor, "x", Money.of(Currencies.COINS, 10))
                        .errorOrThrow())
                .isEqualTo(LoanError.PROVIDER_UNSUPPORTED);
    }

    @Test
    void payInstallmentDebitsAndRaisesOneRepayEvent() {
        Loan loan =
                service.takeLoan(debtor, Money.of(Currencies.COINS, 1000), 4).orElseThrow();
        events = new CapturingEvents();
        service =
                new LoanService(loans, LoanPolicy.defaults(), events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), true);

        Result<?, LoanError> result = service.payInstallment(debtor, loan.id(), Money.of(Currencies.COINS, 250));

        assertThat(result.isOk()).isTrue();
        assertThat(events.published()).hasSize(1).first().isInstanceOf(LoanRepaid.class);
    }

    @Test
    void payInstallmentOnANonExistentLoanIsNotFound() {
        assertThat(service.payInstallment(debtor, "no-such-loan", Money.of(Currencies.COINS, 10))
                        .errorOrThrow())
                .isEqualTo(LoanError.NOT_FOUND);
    }

    @Test
    void payInstallmentByAStrangerIsWrongDebtor() {
        Loan loan =
                service.takeLoan(debtor, Money.of(Currencies.COINS, 1000), 4).orElseThrow();
        PlayerRef stranger = new PlayerRef(UUID.randomUUID(), "Stranger");

        assertThat(service.payInstallment(stranger, loan.id(), Money.of(Currencies.COINS, 10))
                        .errorOrThrow())
                .isEqualTo(LoanError.WRONG_DEBTOR);
    }

    @Test
    void payInstallmentShortOfFundsIsInsufficientAndMovesNothing() {
        Loan loan =
                service.takeLoan(debtor, Money.of(Currencies.COINS, 1000), 4).orElseThrow();
        // Spend the disbursed principal so the wallet cannot cover the installment.
        wallets.debit(debtor, Money.of(Currencies.COINS, 1000));

        Result<?, LoanError> result = service.payInstallment(debtor, loan.id(), Money.of(Currencies.COINS, 250));

        assertThat(result.errorOrThrow()).isEqualTo(LoanError.INSUFFICIENT_FUNDS);
        assertThat(loans.findById(loan.id()).orElseThrow().remainingAmount()).isEqualTo(loan.remainingAmount());
    }

    @Test
    void quoteReportsTheSameLimitAndInterestThePolicyDrivesALoanWith() {
        LoanPolicy policy = LoanPolicy.defaults();
        // The default score is 500: limit = 500 * 1000, interest = the policy band at score 500.
        LoanService.LoanQuote quote = service.quote(500);

        assertThat(quote.limit()).isEqualByComparingTo(policy.limitFor(500));
        assertThat(quote.interestRate()).isEqualByComparingTo(policy.interestFor(500));
    }

    @Test
    void quoteTracksATunedPolicySoTheGuiNeverDisagreesWithTheService() {
        LoanPolicy tuned = new LoanPolicy(
                new BigDecimal("250"),
                new BigDecimal("0.40"),
                new BigDecimal("0.30"),
                java.time.Duration.ofHours(12),
                25,
                10,
                -50,
                0,
                1000);
        LoanService tunedService =
                new LoanService(loans, tuned, events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), true);

        LoanService.LoanQuote quote = tunedService.quote(800);

        assertThat(quote.limit()).isEqualByComparingTo(tuned.limitFor(800));
        assertThat(quote.interestRate()).isEqualByComparingTo(tuned.interestFor(800));
    }

    @Test
    void theFinalPaymentSettlesTheExactResidualAndAnOverOfferIsTrimmed() {
        // A one-installment loan of 1000 principal at 0% would total 1000; pick terms so the residual is known.
        Loan loan =
                service.takeLoan(debtor, Money.of(Currencies.COINS, 1000), 1).orElseThrow();
        Money residual = loan.remainingAmount();
        // Top the wallet up so it can cover any over-offer, then offer far more than owed.
        wallets.credit(debtor, Money.of(Currencies.COINS, 10_000));
        Money walletBefore = wallets.findByOwner(debtor).orElseThrow().balanceOf(Currencies.COINS);

        Result<?, LoanError> result = service.payInstallment(debtor, loan.id(), Money.of(Currencies.COINS, 9_999_999));

        assertThat(result.isOk()).isTrue();
        // Only the residual was debited, never the over-offer.
        Money walletAfter = wallets.findByOwner(debtor).orElseThrow().balanceOf(Currencies.COINS);
        assertThat(walletBefore.minus(walletAfter)).isEqualTo(residual);
        // The loan is fully settled and closed.
        assertThat(loans.findById(loan.id())).isEmpty();
    }
}

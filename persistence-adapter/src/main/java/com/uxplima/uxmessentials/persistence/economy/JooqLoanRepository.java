package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyCreditScores.ECONOMY_CREDIT_SCORES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyLoans.ECONOMY_LOANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.LoanRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Loan.CreditScore;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * jOOQ-backed {@link LoanRepository} over the generated {@code ECONOMY_LOANS} and
 * {@code ECONOMY_CREDIT_SCORES} tables. Every statement is typed jOOQ DSL. JOOQ renders the per-dialect
 * upsert ({@code ON CONFLICT} on SQLite/Postgres, {@code ON DUPLICATE KEY UPDATE} on MySQL), and the debtor
 * name is resolved with a single {@code LEFT JOIN} on {@code ECONOMY_OWNERS} rather than a per-loan lookup.
 */
@NullMarked
public final class JooqLoanRepository extends JooqRepository implements LoanRepository {

    // A defensive ceiling on the auto-repayment scan; far above any realistic concurrent-loan count.
    private static final int ALL_ACTIVE_LIMIT = 10_000;

    private final CurrencyRegistry currencies;
    private final Clock clock;

    public JooqLoanRepository(DSLContext dsl, CurrencyRegistry currencies, Clock clock) {
        super(dsl);
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Loan> findById(String id) {
        Objects.requireNonNull(id, "id");
        return read(dsl ->
                selectLoans(dsl).where(ECONOMY_LOANS.ID.eq(id)).fetchOptional().map(this::mapLoan));
    }

    @Override
    public List<Loan> findByDebtor(PlayerRef debtor) {
        Objects.requireNonNull(debtor, "debtor");
        return read(dsl -> selectLoans(dsl)
                .where(ECONOMY_LOANS.PLAYER_UUID.eq(debtor.uuid().toString()))
                .fetch()
                .map(this::mapLoan));
    }

    @Override
    public List<Loan> findAllActive() {
        return read(dsl -> selectLoans(dsl).limit(ALL_ACTIVE_LIMIT).fetch().map(this::mapLoan));
    }

    @Override
    public void save(Loan loan) {
        Objects.requireNonNull(loan, "loan");
        write(dsl -> {
            upsertLoan(dsl, loan);
            return null;
        });
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id");
        write(dsl -> {
            dsl.deleteFrom(ECONOMY_LOANS).where(ECONOMY_LOANS.ID.eq(id)).execute();
            return null;
        });
    }

    @Override
    public Result<Unit, TransferError> disburse(Loan loan) {
        Objects.requireNonNull(loan, "loan");
        // The principal credit and the loan-row insert commit together: a credit the clamp rejects writes no
        // loan row, so a loan can never exist without the matching disbursement.
        return write(dsl -> {
            Money principal = loan.principal();
            if (!WalletLegs.creditFitsClamp(dsl, loan.debtor(), principal)) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            WalletLegs.creditRow(dsl, loan.debtor(), principal, clock.millis());
            upsertLoan(dsl, loan);
            return Result.ok();
        });
    }

    @Override
    public Result<Unit, TransferError> applyRepayment(
            PlayerRef debtor, Money paid, Loan updatedLoan, boolean fullyPaid) {
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(updatedLoan, "updatedLoan");
        // The guarded debit and the loan-row change commit together: a debit short of funds changes no rows and
        // leaves the loan untouched, so a payment is never recorded against money that was not actually taken.
        return write(dsl -> {
            if (WalletLegs.guardedDebit(dsl, debtor, paid) == 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            if (fullyPaid) {
                dsl.deleteFrom(ECONOMY_LOANS)
                        .where(ECONOMY_LOANS.ID.eq(updatedLoan.id()))
                        .execute();
            } else {
                upsertLoan(dsl, updatedLoan);
            }
            return Result.ok();
        });
    }

    private static void upsertLoan(DSLContext dsl, Loan loan) {
        dsl.insertInto(ECONOMY_LOANS)
                .set(ECONOMY_LOANS.ID, loan.id())
                .set(ECONOMY_LOANS.PLAYER_UUID, loan.debtor().uuid().toString())
                .set(ECONOMY_LOANS.PRINCIPAL, loan.principal().amount())
                .set(ECONOMY_LOANS.AMOUNT, loan.remainingAmount().amount())
                .set(ECONOMY_LOANS.CURRENCY, loan.principal().currency().id().value())
                .set(ECONOMY_LOANS.INTEREST_RATE, loan.interestRate())
                .set(ECONOMY_LOANS.REMAINING_INSTALLMENTS, loan.remainingInstallments())
                .set(ECONOMY_LOANS.INSTALLMENT_AMOUNT, loan.installmentAmount().amount())
                .set(ECONOMY_LOANS.TAKEN_AT, loan.takenAt())
                .set(ECONOMY_LOANS.NEXT_PAYMENT_AT, loan.nextPaymentAt())
                .onConflict(ECONOMY_LOANS.ID)
                .doUpdate()
                .set(ECONOMY_LOANS.AMOUNT, loan.remainingAmount().amount())
                .set(ECONOMY_LOANS.REMAINING_INSTALLMENTS, loan.remainingInstallments())
                .set(ECONOMY_LOANS.NEXT_PAYMENT_AT, loan.nextPaymentAt())
                .execute();
    }

    @Override
    public CreditScore getCreditScore(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(ECONOMY_CREDIT_SCORES)
                .where(ECONOMY_CREDIT_SCORES.PLAYER_UUID.eq(player.uuid().toString()))
                .fetchOptional()
                .map(r -> new CreditScore(player, r.getScore(), r.getLastUpdated()))
                // A player with no record yet starts at 100, matching the economy_credit_scores.score column
                // DEFAULT and the LoanPolicy score floor, so a fresh debtor's limit is the policy's lowest tier.
                .orElseGet(() -> new CreditScore(player, 100, System.currentTimeMillis())));
    }

    @Override
    public void saveCreditScore(CreditScore creditScore) {
        Objects.requireNonNull(creditScore, "creditScore");
        write(dsl -> {
            dsl.insertInto(ECONOMY_CREDIT_SCORES)
                    .set(
                            ECONOMY_CREDIT_SCORES.PLAYER_UUID,
                            creditScore.player().uuid().toString())
                    .set(ECONOMY_CREDIT_SCORES.SCORE, creditScore.score())
                    .set(ECONOMY_CREDIT_SCORES.LAST_UPDATED, creditScore.lastUpdated())
                    .onConflict(ECONOMY_CREDIT_SCORES.PLAYER_UUID)
                    .doUpdate()
                    .set(ECONOMY_CREDIT_SCORES.SCORE, creditScore.score())
                    .set(ECONOMY_CREDIT_SCORES.LAST_UPDATED, creditScore.lastUpdated())
                    .execute();
            return null;
        });
    }

    // Build the select over a Field[] so jOOQ returns the untyped Record overload; the per-loan name comes
    // from the single LEFT JOIN on ECONOMY_OWNERS, never a per-row lookup.
    private static SelectOnConditionStep<Record> selectLoans(DSLContext dsl) {
        Field<?>[] columns = {
            ECONOMY_LOANS.ID,
            ECONOMY_LOANS.PLAYER_UUID,
            ECONOMY_LOANS.PRINCIPAL,
            ECONOMY_LOANS.AMOUNT,
            ECONOMY_LOANS.CURRENCY,
            ECONOMY_LOANS.INTEREST_RATE,
            ECONOMY_LOANS.REMAINING_INSTALLMENTS,
            ECONOMY_LOANS.INSTALLMENT_AMOUNT,
            ECONOMY_LOANS.TAKEN_AT,
            ECONOMY_LOANS.NEXT_PAYMENT_AT,
            ECONOMY_OWNERS.NAME
        };
        return dsl.select(columns)
                .from(ECONOMY_LOANS)
                .leftJoin(ECONOMY_OWNERS)
                .on(ECONOMY_LOANS.PLAYER_UUID.eq(ECONOMY_OWNERS.OWNER));
    }

    private Loan mapLoan(Record row) {
        String currencyId = row.get(ECONOMY_LOANS.CURRENCY);
        Currency currency = currencies
                .find(CurrencyId.of(currencyId))
                .orElseThrow(() -> new IllegalStateException("unknown currency: " + currencyId));

        PlayerRef debtor = playerRef(row.get(ECONOMY_LOANS.PLAYER_UUID), row.get(ECONOMY_OWNERS.NAME));
        // The amount column is the shrinking remaining balance; the principal is its own stored column, so the
        // original loan size survives a restart while the remaining figure drops with each installment.
        Money principal = Money.of(currency, row.get(ECONOMY_LOANS.PRINCIPAL));
        Money remaining = Money.of(currency, row.get(ECONOMY_LOANS.AMOUNT));
        Money installment = Money.of(currency, row.get(ECONOMY_LOANS.INSTALLMENT_AMOUNT));

        return new Loan(
                row.get(ECONOMY_LOANS.ID),
                debtor,
                principal,
                remaining,
                row.get(ECONOMY_LOANS.INTEREST_RATE),
                row.get(ECONOMY_LOANS.REMAINING_INSTALLMENTS),
                installment,
                row.get(ECONOMY_LOANS.TAKEN_AT),
                row.get(ECONOMY_LOANS.NEXT_PAYMENT_AT));
    }

    private static PlayerRef playerRef(String uuid, @Nullable String name) {
        String resolved = name == null || name.isBlank() ? uuid : name;
        return new PlayerRef(UUID.fromString(uuid), resolved);
    }
}

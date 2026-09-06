package com.uxplima.uxmessentials.economy.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player loan: the principal disbursed, the remaining repayable balance (principal plus interest), the fixed
 * interest rate, the count of installments still owed, the per-installment figure, and the debtor. The
 * aggregate is immutable. A repayment produces a new {@code Loan} through {@link #afterPayment(Money)} rather
 * than mutating in place, so a persistence failure can never leave a half-applied loan in memory. Inputs are
 * validated at construction: the principal, remaining amount, and installment all share one currency, the
 * interest rate is non-negative, and the installment count is positive while installments remain.
 */
public final class Loan {

    private final String id;
    private final PlayerRef debtor;
    private final Money principal;
    private final Money remainingAmount;
    private final BigDecimal interestRate;
    private final int remainingInstallments;
    private final Money installmentAmount;
    private final long takenAt;
    private final long nextPaymentAt;

    public Loan(
            String id,
            PlayerRef debtor,
            Money principal,
            Money remainingAmount,
            BigDecimal interestRate,
            int remainingInstallments,
            Money installmentAmount,
            long takenAt,
            long nextPaymentAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.debtor = Objects.requireNonNull(debtor, "debtor");
        this.principal = Objects.requireNonNull(principal, "principal");
        this.remainingAmount = Objects.requireNonNull(remainingAmount, "remainingAmount");
        this.interestRate = Objects.requireNonNull(interestRate, "interestRate");
        this.installmentAmount = Objects.requireNonNull(installmentAmount, "installmentAmount");
        if (interestRate.signum() < 0) {
            throw new IllegalArgumentException("interest rate must not be negative: " + interestRate);
        }
        if (remainingInstallments < 0) {
            throw new IllegalArgumentException("remaining installments must not be negative: " + remainingInstallments);
        }
        requireSameCurrency(principal, remainingAmount, "principal", "remainingAmount");
        requireSameCurrency(principal, installmentAmount, "principal", "installmentAmount");
        this.remainingInstallments = remainingInstallments;
        this.takenAt = takenAt;
        this.nextPaymentAt = nextPaymentAt;
    }

    /** A freshly disbursed loan with a collision-free generated id. */
    public static Loan open(
            PlayerRef debtor,
            Money principal,
            Money totalRepayable,
            BigDecimal interestRate,
            int installments,
            Money installmentAmount,
            long takenAt,
            long nextPaymentAt) {
        return new Loan(
                generateId(),
                debtor,
                principal,
                totalRepayable,
                interestRate,
                installments,
                installmentAmount,
                takenAt,
                nextPaymentAt);
    }

    /**
     * A loan id that cannot collide with another taken in the same millisecond, a full random UUID, where the
     * old {@code uuid.substring(0,8) + millis} scheme could collide for two loans a player took in one tick. A
     * bare 36-character UUID also fits the {@code economy_loans.id VARCHAR(36)} column on every backend.
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    public String id() {
        return id;
    }

    public PlayerRef debtor() {
        return debtor;
    }

    public Money principal() {
        return principal;
    }

    public Money remainingAmount() {
        return remainingAmount;
    }

    public BigDecimal interestRate() {
        return interestRate;
    }

    public int remainingInstallments() {
        return remainingInstallments;
    }

    public Money installmentAmount() {
        return installmentAmount;
    }

    public long takenAt() {
        return takenAt;
    }

    public long nextPaymentAt() {
        return nextPaymentAt;
    }

    /**
     * The figure that actually settles {@code requested}: never more than the remaining balance. When the
     * caller offers at least the residual on the final installment (or any payment that would clear the loan),
     * the loan is paid in full and the settled amount is exactly the residual. Overpayment is refused here so
     * the caller never debits the debtor more than they owe.
     */
    public Money settlementFor(Money requested) {
        requireSameCurrency(remainingAmount, requested, "remainingAmount", "requested");
        if (requested.isNegative() || requested.isZero()) {
            throw new IllegalArgumentException("a loan payment must be positive");
        }
        return requested.isGreaterThan(remainingAmount) ? remainingAmount : requested;
    }

    /**
     * The loan after {@code paid} is applied: the remaining balance drops by the settled amount and one
     * installment is consumed (the last payment that clears the balance also zeroes the installment count).
     * {@code paid} must be the {@link #settlementFor(Money)} figure, never larger than the residual.
     */
    public Loan afterPayment(Money paid) {
        Objects.requireNonNull(paid, "paid");
        requireSameCurrency(remainingAmount, paid, "remainingAmount", "paid");
        if (paid.isGreaterThan(remainingAmount)) {
            throw new IllegalArgumentException("a loan payment must not exceed the remaining balance");
        }
        Money nextRemaining = remainingAmount.minus(paid);
        int nextInstallments = nextRemaining.isZero() ? 0 : Math.max(0, remainingInstallments - 1);
        return new Loan(
                id,
                debtor,
                principal,
                nextRemaining,
                interestRate,
                nextInstallments,
                installmentAmount,
                takenAt,
                nextPaymentAt);
    }

    /** The loan with its next-payment timestamp moved to {@code nextPaymentAt}. */
    public Loan withNextPayment(long nextPaymentAt) {
        return new Loan(
                id,
                debtor,
                principal,
                remainingAmount,
                interestRate,
                remainingInstallments,
                installmentAmount,
                takenAt,
                nextPaymentAt);
    }

    /** True once the remaining balance is fully repaid: the loan can be closed. */
    public boolean isFullyPaid() {
        return remainingAmount.isZero();
    }

    private static void requireSameCurrency(Money a, Money b, String aName, String bName) {
        if (!a.currency().equals(b.currency())) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + aName + " is " + a.currency() + " but " + bName + " is " + b.currency());
        }
    }

    /** Record to store a player's credit rating/score (0 to 1000). */
    public record CreditScore(PlayerRef player, int score, long lastUpdated) {
        public CreditScore {
            Objects.requireNonNull(player, "player");
            if (score < 0 || score > 1000) {
                throw new IllegalArgumentException("credit score must be between 0 and 1000");
            }
        }
    }
}

package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * The operator-tunable economics of the loan system, loaded once from the economy config and swapped
 * atomically on reload (like every other config value). The {@link LoanService} reads its limit, interest band,
 * installment cycle, and credit-score deltas from here instead of hardcoding them, so a server can make loans
 * cheaper or stricter without a code change. Every {@link BigDecimal} is built from a string literal at the
 * config boundary, never {@code BigDecimal.valueOf(double)}, so no figure ever carries floating-point error.
 *
 * @param limitMultiplier the loan limit is {@code creditScore * limitMultiplier}
 * @param interestMax the interest rate at the lowest credit score (a credit score of {@link #scoreFloor})
 * @param interestSpan how far the rate drops between the lowest and highest score; the rate at score
 *     {@link #scoreCeiling} is {@code interestMax - interestSpan}
 * @param installmentCycle how long between installments (the auto-repayment due interval)
 * @param onTimeManualDelta credit-score gain for a manual on-time payment
 * @param onTimeAutoDelta credit-score gain for a successful automatic repayment
 * @param missedDelta credit-score penalty for a missed automatic repayment (a negative number)
 * @param scoreFloor the lowest a credit score can fall to
 * @param scoreCeiling the highest a credit score can rise to
 */
public record LoanPolicy(
        BigDecimal limitMultiplier,
        BigDecimal interestMax,
        BigDecimal interestSpan,
        Duration installmentCycle,
        int onTimeManualDelta,
        int onTimeAutoDelta,
        int missedDelta,
        int scoreFloor,
        int scoreCeiling) {

    public LoanPolicy {
        Objects.requireNonNull(limitMultiplier, "limitMultiplier");
        Objects.requireNonNull(interestMax, "interestMax");
        Objects.requireNonNull(interestSpan, "interestSpan");
        Objects.requireNonNull(installmentCycle, "installmentCycle");
        if (limitMultiplier.signum() < 0) {
            throw new IllegalArgumentException("limit multiplier must not be negative");
        }
        if (interestMax.signum() < 0 || interestSpan.signum() < 0) {
            throw new IllegalArgumentException("interest figures must not be negative");
        }
        if (installmentCycle.isZero() || installmentCycle.isNegative()) {
            throw new IllegalArgumentException("installment cycle must be positive");
        }
        if (scoreFloor < 0 || scoreCeiling < scoreFloor) {
            throw new IllegalArgumentException(
                    "score bounds invalid: floor=" + scoreFloor + " ceiling=" + scoreCeiling);
        }
    }

    /** The shipped defaults, matching the historical hardcoded economics (2% to 22% band, 24h cycle). */
    public static LoanPolicy defaults() {
        return new LoanPolicy(
                new BigDecimal("1000"),
                new BigDecimal("0.22"),
                new BigDecimal("0.20"),
                Duration.ofHours(24),
                25,
                10,
                -50,
                100,
                1000);
    }

    /** The interest rate for {@code creditScore}: {@code interestMax} less the score-fraction of {@code interestSpan}. */
    public BigDecimal interestFor(int creditScore) {
        BigDecimal span = new BigDecimal(scoreCeiling - scoreFloor);
        if (span.signum() == 0) {
            return interestMax;
        }
        BigDecimal clamped = new BigDecimal(Math.max(scoreFloor, Math.min(scoreCeiling, creditScore)) - scoreFloor);
        BigDecimal fraction = clamped.divide(span, 6, java.math.RoundingMode.HALF_UP);
        return interestMax.subtract(fraction.multiply(interestSpan));
    }

    /** The maximum principal {@code creditScore} permits. */
    public BigDecimal limitFor(int creditScore) {
        return new BigDecimal(creditScore).multiply(limitMultiplier);
    }
}

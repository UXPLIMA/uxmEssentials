package com.uxplima.uxmessentials.economy.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A loan installment was debited from the debtor's wallet, the repayment leg of {@code /loan pay} or an
 * automatic repayment. Carries the loan id, the debtor, the amount settled, and the balance still owed after
 * the payment, so audit and any consuming context observe each repayment exactly once. Maps to one
 * {@code event=loan_repay} audit line.
 *
 * @param loanId the loan this payment settled against
 * @param debtor the wallet the installment was debited from
 * @param paid the {@link Money} debited, carrying its currency
 * @param remaining the loan balance still owed after this payment
 */
public record LoanRepaid(String loanId, PlayerRef debtor, Money paid, Money remaining) implements EconomyEvent {

    public LoanRepaid {
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(remaining, "remaining");
    }
}

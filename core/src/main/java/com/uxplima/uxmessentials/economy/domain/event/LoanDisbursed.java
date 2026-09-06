package com.uxplima.uxmessentials.economy.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A loan's principal was credited to the debtor's wallet, the disbursement leg of {@code /loan take}. Carries
 * the loan id, the debtor, and the principal moved, so audit and any consuming context observe the
 * money-creation event exactly once. Maps to one {@code event=loan_disburse} audit line.
 *
 * @param loanId the loan this disbursement opened
 * @param debtor the wallet that received the principal
 * @param principal the {@link Money} credited, carrying its currency
 */
public record LoanDisbursed(String loanId, PlayerRef debtor, Money principal) implements EconomyEvent {

    public LoanDisbursed {
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(principal, "principal");
    }
}

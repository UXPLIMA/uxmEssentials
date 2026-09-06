package com.uxplima.uxmessentials.economy.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player moved money from their wallet into a shared bank, the deposit leg of {@code /bank deposit}. Carries
 * the bank id, the depositing player, the amount moved, and the bank balance after the deposit, so audit and
 * any consuming context observe each deposit exactly once. Maps to one {@code event=bank_deposit} audit line.
 *
 * @param bankId the shared bank that received the money
 * @param player the wallet the money was debited from
 * @param amount the {@link Money} moved, carrying its currency
 * @param bankBalance the bank's balance after the deposit
 */
public record BankDeposited(String bankId, PlayerRef player, Money amount, Money bankBalance) implements EconomyEvent {

    public BankDeposited {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(bankBalance, "bankBalance");
    }
}

package com.uxplima.uxmessentials.economy.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player moved money from a shared bank into their wallet, the withdraw leg of {@code /bank withdraw}.
 * Carries the bank id, the withdrawing player, the amount moved, and the bank balance after the withdrawal, so
 * audit and any consuming context observe each withdrawal exactly once. Maps to one
 * {@code event=bank_withdraw} audit line.
 *
 * @param bankId the shared bank the money came from
 * @param player the wallet the money was credited to
 * @param amount the {@link Money} moved, carrying its currency
 * @param bankBalance the bank's balance after the withdrawal
 */
public record BankWithdrawn(String bankId, PlayerRef player, Money amount, Money bankBalance) implements EconomyEvent {

    public BankWithdrawn {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(bankBalance, "bankBalance");
    }
}

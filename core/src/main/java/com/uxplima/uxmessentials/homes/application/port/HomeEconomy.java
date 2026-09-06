package com.uxplima.uxmessentials.homes.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the homes context owns so a per-action cost can be charged <em>without</em> a
 * hard dependency on the economy context. This is the entire economy surface homes needs, a balance check
 * and a withdrawal. Expressed in homes' own terms; the economy context supplies an adapter that bridges
 * this to its {@code EconomyProvider}/{@code Wallet}, and the homes context never imports an economy type.
 *
 * <p>Soft coupling: this port is injected as an {@link java.util.Optional} into {@code HomeCharge}. When
 * no provider is present, a configured cost is simply ignored at use time (the action proceeds for free,
 * as if no cost were configured). When a provider is present, {@code HomeCharge} charges through it before
 * the operation is committed. A {@code withdraw} that the player cannot afford fails the action, and the
 * charge happens only once all other guards have passed.
 */
public interface HomeEconomy {

    /** True when {@code who} can afford {@code amount} in the specified currency. */
    boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Withdraw {@code amount} of {@code currencyId} from {@code who}'s balance, returning {@code true} on
     * success and {@code false} when the balance was insufficient.
     */
    boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId);
}

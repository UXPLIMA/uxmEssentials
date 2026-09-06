package com.uxplima.uxmessentials.kits.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the kits context owns so a per-kit cost can be charged <em>without</em> a hard
 * dependency on the economy context. This is the entire economy surface kits needs, a balance check and a
 * withdrawal. Expressed in kits' own terms; the economy context (P3) supplies an adapter that bridges this
 * to its {@code EconomyProvider}/{@code Wallet}, and the kits context never imports an economy type.
 *
 * <p>Soft coupling: this port is injected as an {@link java.util.Optional} into the claim gate. When no
 * provider is present, a kit's recorded cost is simply ignored at claim time (the kit is still defined and
 * claimable for free). When a provider is present, the cost is charged through this seam before the items are
 * granted. A {@code withdraw} the player cannot afford fails the claim, so a kit they cannot pay for never
 * grants its items.
 */
public interface KitEconomy {

    /** True when {@code who} can afford {@code amount} in the specified currency. */
    boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Withdraw {@code amount} of {@code currencyId} from {@code who}'s balance, returning {@code true} on success and
     * {@code false} when the balance was insufficient.
     */
    boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Credit {@code amount} of {@code currencyId} to {@code who}'s balance, returning {@code true} on success and
     * {@code false} when the deposit fails.
     */
    boolean deposit(PlayerRef who, BigDecimal amount, String currencyId);
}

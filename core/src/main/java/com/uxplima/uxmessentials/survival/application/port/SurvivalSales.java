package com.uxplima.uxmessentials.survival.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam autosell owns so a break's drops can be sold into the server economy <em>without</em> a hard
 * dependency on the economy context. This is the entire economy surface autosell needs, a single credit of the sale
 * proceeds to the seller. Expressed in survival's own terms ({@link BigDecimal} in the default currency, never an
 * economy type); the economy context supplies an adapter that bridges this to its {@code EconomyProvider}/{@code
 * Wallet}, and the survival context never imports an economy type (mirrors the kits {@code KitEconomy} and the shared
 * {@code ClickActionEconomy} seams).
 *
 * <p>Soft coupling: this port is injected as an {@link java.util.Optional} into the autosell path. When no provider is
 * present the whole mechanic is inert (a drop is never removed for a sale that cannot be paid) so autosell degrades
 * to doing nothing rather than deleting items on a server without an economy. When a provider is present the proceeds
 * are credited once through {@link #credit}.
 */
public interface SurvivalSales {

    /**
     * Credit {@code amount} of the default currency to {@code who} as the proceeds of an autosold drop, returning
     * {@code true} when the money reached the account and {@code false} when the deposit was refused (for instance a
     * balance cap). The credit is guarded at the source, so a {@code true} return means the proceeds were banked
     * exactly once.
     *
     * @param who the seller to pay
     * @param amount the sale proceeds, non-negative
     * @return whether the proceeds were credited
     */
    boolean credit(PlayerRef who, BigDecimal amount);

    /**
     * Render {@code amount} the way the economy shows money, for the {@code {amount}} placeholder of the sale notice.
     * The economy owns how a figure reads (grouping, decimals, the currency symbol), so the adapter that implements
     * {@link #credit} answers this too and the survival context never has to know the currency.
     *
     * <p>The default is the bare figure with trailing zeros trimmed, which is what a server with no economy provider
     * would show if it ever rendered one; it exists so a test double or a future provider only has to implement the
     * credit.
     *
     * @param amount the figure to render, non-negative
     * @return the figure as it should appear to a player
     */
    default String format(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}

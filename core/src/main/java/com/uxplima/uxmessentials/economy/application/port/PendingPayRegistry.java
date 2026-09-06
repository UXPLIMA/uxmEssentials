package com.uxplima.uxmessentials.economy.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port holding the one outstanding {@code /pay} confirmation per payer, a per-payer
 * {@code AtomicReference<PendingPay>} ({@code docs/11-economy-integration.md} §9.2). A second large
 * {@code /pay} replaces the first and re-prompts; an expiry timer discards a stale one. Staging never
 * debits, so a dropped pending pay loses no money.
 *
 * <p>The adapter behind this owns the {@code Scheduler.asyncAfter} expiry and the
 * {@code event=economy_pay_confirm_expired} audit line; the use case only stages, takes, and clears.
 */
public interface PendingPayRegistry {

    /** Stage {@code pending} for its payer, replacing and re-prompting any earlier outstanding confirmation. */
    void stage(PendingPay pending);

    /** The payer's outstanding confirmation, if one is still live. */
    Optional<PendingPay> peek(PlayerRef payer);

    /** Take and clear the payer's outstanding confirmation, if any, the {@code /payconfirm} consume step. */
    Optional<PendingPay> take(PlayerRef payer);

    /** Drop the payer's outstanding confirmation without consuming it, the expiry path. */
    void clear(PlayerRef payer);
}

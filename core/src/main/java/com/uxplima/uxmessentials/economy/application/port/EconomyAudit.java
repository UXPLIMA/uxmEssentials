package com.uxplima.uxmessentials.economy.application.port;

import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the economy audit trail. The {@code com.uxplima.uxmessentials.audit} channel,
 * <em>separate</em> from the ledger (do not conflate the two; the GLOSSARY warns against it). Every mutation
 * through the port carries an {@link EconomyReason} so a ledger review can {@code GROUP BY reason}, and bulk
 * verbs emit exactly one line with an affected-count rather than per-target spam
 * ({@code docs/11-economy-integration.md} §8, §9.4 to §9.5).
 *
 * <p>Each method maps to one {@code event=...} line documented in {@code docs/09-deployment.md}. The
 * implementation lands with the bukkit-adapter; the use cases depend only on this contract.
 */
public interface EconomyAudit {

    /** {@code event=economy_credit}: a credit applied to {@code owner}. */
    void credited(PlayerRef owner, Money amount, EconomyReason reason);

    /** {@code event=economy_debit}: a debit applied to {@code owner}. */
    void debited(PlayerRef owner, Money amount, EconomyReason reason);

    /** {@code event=economy_rejected}: a change refused for {@code owner} (insufficient funds / max). */
    void rejected(PlayerRef owner, Money requested, EconomyReason reason);

    /** {@code event=economy_transfer}, an atomic {@code Allow} from {@code from} to {@code to}. */
    void transferred(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason);

    /** An eco-admin mutation, {@code event=economy_admin_set|give|take|reset}, by {@code actor} on {@code target}. */
    void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason);

    /** A bulk eco-admin mutation: one line with the affected count, never per-target spam. */
    void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason);

    /**
     * {@code event=economy_setworth}, {@code actor} set the per-item worth override of {@code material} to
     * {@code price}. A pricing-policy change, not a balance mutation, so it carries no {@link EconomyReason}.
     */
    void worthSet(PlayerRef actor, String material, Money price);

    /** {@code event=economy_setworth_clear}: {@code actor} cleared the worth override of {@code material}. */
    void worthCleared(PlayerRef actor, String material);
}

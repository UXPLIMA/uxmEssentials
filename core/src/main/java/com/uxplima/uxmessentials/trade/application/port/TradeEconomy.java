package com.uxplima.uxmessentials.trade.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the trade context owns so a trade can move staked money <em>without</em> a hard dependency on
 * the economy context (mirrors {@code KitEconomy} / {@code PlayerWarpEconomy}). This is the entire economy surface trade
 * needs: a balance probe for the window, and one guarded, atomic two-sided move per money leg. The economy context
 * supplies an adapter that bridges this to its {@code EconomyProvider}, and the trade context never imports an economy
 * type.
 *
 * <p>Soft coupling: the port is injected as an {@link java.util.Optional} into the trade view. With no provider present
 * no money button is shown and a trade moves items only; with a provider present, each staked money leg is settled
 * through {@link #transfer} at commit. {@link #transfer} is the double-spend-safe point, a single atomic move that
 * either applies both legs or neither, so an affordability probe is never the gate: a payer who cannot cover the leg is
 * reported by a {@code false} return, not a check-then-charge race. A leg that has to be undone is reversed by the
 * caller with the opposite {@link #transfer}.
 */
public interface TradeEconomy {

    /** True when {@code who} could currently afford {@code amount} of {@code currencyId}, window preview only. */
    boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Atomically move {@code amount} of {@code currencyId} from {@code from} to {@code to}, returning {@code true} when
     * both legs committed and {@code false} when the payer could not cover it (nothing applied). Never partially
     * applies: the guard is at the source, so two concurrent moves can never both overdraw.
     */
    boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId);

    /**
     * Escrow debit: guardedly remove {@code amount} of {@code currencyId} from {@code who}, returning {@code true} when
     * the debit took and {@code false} when they could not cover it (nothing removed). This is the double-spend-safe
     * point of a cross-server trade. The money leaves the payer here and lives in the escrow row until the trade
     * commits (delivered to the counterparty via {@link #deposit}) or is refunded (deposited back to the payer).
     */
    boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Credit {@code amount} of {@code currencyId} to {@code who}, the delivery half of a cross-server trade (the
     * counterparty's escrowed money reaching the recipient) or a refund (the payer's own escrowed money returning). A
     * DB-backed credit, so it applies whether or not the player is online.
     */
    void deposit(PlayerRef who, BigDecimal amount, String currencyId);
}

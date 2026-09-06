package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * The modelled ways a player-warp charge can fail. The access gate maps any of these to a single
 * {@link PlayerWarpError#CANNOT_AFFORD} refusal. The payer only needs to know they were not admitted and no
 * money moved, while the outbound economy adapter (P4-T4) distinguishes them so it can log and, under a
 * foreign provider, decide whether a compensating credit is owed.
 *
 * <ul>
 *   <li>{@link #INSUFFICIENT_FUNDS}: the payer's balance could not cover the price; nothing was debited.
 *   <li>{@link #ACCRUAL_FAILED}. The payer was debited but crediting the owner's bank failed, so the adapter
 *       issued a compensating refund and the visit was refused; the payer ends whole.
 *   <li>{@link #PROVIDER_ERROR}: the economy provider raised an unexpected fault; treated as no charge taken.
 * </ul>
 */
public enum ChargeError {
    INSUFFICIENT_FUNDS,
    ACCRUAL_FAILED,
    PROVIDER_ERROR
}

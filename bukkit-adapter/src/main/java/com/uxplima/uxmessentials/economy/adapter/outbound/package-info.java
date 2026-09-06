/**
 * The economy context's provider-agnostic outbound adapters: the {@code ServicesManager} register-or-defer
 * registrar, the operator-log audit emitter, the persisted {@code /paytoggle} preferences (jOOQ-backed, wired
 * from the persistence factory), the scheduler-driven pending-pay registry with self-cancelling expiry, the
 * permission-driven baltop exemption, the per-currency baltop snapshot machinery, and the {@code WarpEconomy}
 * bridge that lets the warps context charge a per-warp cost through the resolved provider.
 *
 * <p>None of these import a Vault or Treasury SDK. Those live solely in the sibling {@code treasury} and
 * {@code vault} packages, fenced off by {@code economyDomainHasNoProviderSdk}. This package is the seam where
 * the native ledger and a foreign economy are made indistinguishable to every consumer.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.adapter.outbound;

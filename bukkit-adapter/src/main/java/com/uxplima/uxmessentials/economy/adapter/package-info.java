/**
 * The economy context's adapter wiring: the one place the canonical worked DDD example is assembled. It
 * builds the native ledger (cached jOOQ repository + debounced settle writer + batched transaction telemetry),
 * wraps it in the native {@code EconomyProvider}, runs register-or-defer through the {@code ServicesManager}
 * (registering the native provider unless a foreign Treasury/Vault economy is already present, in which case it
 * consumes the incumbent), constructs the use cases and Brigadier commands over the resolved provider, and
 * produces the {@code WarpEconomy} bridge the warps context charges a per-warp cost through.
 *
 * <p>The provider-SDK imports are confined to the {@code treasury} and {@code vault} sub-packages, fenced by
 * {@code economyDomainHasNoProviderSdk}; this wiring touches only the foreign registrations through the
 * {@code ServicesManager}, never the SDK types directly.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.adapter;

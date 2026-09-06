/**
 * The Vault outbound adapter, the legacy, single-currency compatibility bridge from a Vault economy into
 * this plugin's {@code EconomyProvider} port. This package and the sibling {@code treasury} package are the
 * <strong>only</strong> places allowed to import a provider SDK ({@code net.milkbowl.vault..}); the ArchUnit
 * fence {@code economyDomainHasNoProviderSdk} forbids it anywhere above the adapter. Vault has no notion of
 * multiple currencies, so the adapter serves exactly the configured default currency and refuses any other
 * with {@code CURRENCY_UNSUPPORTED}: there is no implicit cross-currency conversion.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.adapter.vault;

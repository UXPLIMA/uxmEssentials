/**
 * The optional-plugin hook SPI: the foundation every later soft-depend integration plugs into. A
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.hooks.PluginHook} names a target plugin, reports
 * whether it is present and enabled, and resolves to a typed capability that always has a safe absent default;
 * the {@link com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks} façade resolves every registered
 * hook once at bootstrap (presence is stable for a server run) and hands callers the capability by class, so a
 * feature consumes an integration without ever null-checking or try/catching a missing plugin.
 *
 * <p>Every hook keeps the target plugin's SDK types behind {@code whenPresent}, and the no-op default carries
 * none of them: the same lazy-structure the claim-provider adapters use. Because {@code Hooks} calls
 * {@code whenPresent} only past the present-guard, a server without the soft-depend never constructs the real
 * implementation and never loads its external classes: there is no {@link java.lang.NoClassDefFoundError} path
 * when a soft-depend is absent.
 *
 * <p>{@code PlaceholderApiHook} is the worked example that proves the pattern; the real integrations (Vault
 * economy/permission, the multi-currency providers, HeadDatabase, the item providers) follow it in later
 * features, and the {@code HookHarness} in the matching test package is the reusable present/absent harness.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import org.jspecify.annotations.NullMarked;

/**
 * The live-import sources: {@link com.uxplima.uxmessentials.migration.convert.Convert} impls that read
 * their data from a running provider rather than an on-disk data tree. The economy providers, Vault and
 * PlayerPoints. Expose balances through a service API, not a {@code userdata/} directory, so the source
 * reads them through a platform-neutral {@code BalanceFeed} seam the bukkit adapter backs at runtime.
 * Everything else (conflict policy, balance policy, idempotency, audit) is the same machinery every other
 * source funnels through.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.migration.convert.live;

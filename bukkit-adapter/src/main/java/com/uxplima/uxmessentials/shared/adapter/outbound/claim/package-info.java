/**
 * Outbound adapters binding the {@link com.uxplima.uxmessentials.shared.application.port.ClaimProvider}
 * port to whichever land-claim plugin is installed, plus the
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProviders} discoverer that picks
 * the active one and the {@link com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimServiceImpl}
 * that drives the shared {@code ClaimPolicy} from it.
 *
 * <p>Two integration styles coexist. <b>Typed</b> providers, Lands, GriefPrevention, SimpleClaimSystem and
 * RClaim, depend on a {@code compileOnly} API jar, so their SDK types are absent from the runtime and test
 * classpaths; each reaches its SDK only past a plugin-present guard, and the discoverer constructs a typed
 * provider only once that plugin is known to be loaded, so a server (or test) without the plugin never loads
 * the SDK classes. <b>Reflective</b> providers. UxmClaims, GriefDefender, ExcellentClaims, XClaim, Homestead
 * and Towny, have no compile dependency at all and reach their API purely by reflection: GriefDefender
 * publishes only a GC-prone commit-pinned JitPack build, ExcellentClaims is premium with no public
 * coordinate, XClaim's JitPack build fails, Homestead ships only to authenticated GitHub Packages, and Towny
 * is bound through its stable {@code TownyAPI} entry point rather than a pinned jar, so a pinned
 * canon-verifiable {@code compileOnly} dependency is not available or not warranted for any of them, while each
 * exposes a stable static entry point that reflection can bind to.
 *
 * <p>Every lookup is pure-datastore: claims are resolved from in-memory state by world plus block/chunk
 * coordinate without loading or reading a chunk, so the proximity scan stays safe on the region thread and
 * on Folia.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import org.jspecify.annotations.NullMarked;

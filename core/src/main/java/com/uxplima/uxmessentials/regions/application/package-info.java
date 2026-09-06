/**
 * The regions context's application layer: the {@link com.uxplima.uxmessentials.regions.application.RegionsModule}
 * feature-module identity, the typed {@link com.uxplima.uxmessentials.regions.application.RegionsConfig}, the
 * {@link com.uxplima.uxmessentials.regions.application.RegionsMessageKey} catalog, and the
 * {@link com.uxplima.uxmessentials.regions.application.port.RegionService} port. The single seam over WorldGuard's
 * region API. Orchestration and contracts only; the WorldGuard types live solely in the outbound adapter. Pure
 * Java: no Bukkit, Paper, Kyori, SLF4J, or WorldGuard.
 */
@NullMarked
package com.uxplima.uxmessentials.regions.application;

import org.jspecify.annotations.NullMarked;

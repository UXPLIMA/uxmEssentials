/**
 * The warps context's outbound persistence adapter: the jOOQ {@code WarpRepository} over the generated DSL
 * and its Caffeine read-cache decorator. Every warp fact is a first-class column in the {@code warps}
 * table (name, world uid + name, coordinates, owner, creation time, optional cost, optional required
 * permission), there is no opaque JSON blob, so a {@code Warp} is rebuilt from queryable rows. Warps are
 * server-wide and keyed by name alone, and the small set is cached whole. SQL is issued only through the
 * typed jOOQ DSL, never string concatenation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.warps;

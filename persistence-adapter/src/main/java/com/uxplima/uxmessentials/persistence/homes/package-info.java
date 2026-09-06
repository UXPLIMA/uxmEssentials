/**
 * The homes context's outbound persistence adapter: the jOOQ {@code HomeRepository} over the generated
 * DSL and its Caffeine read-cache decorator. Every home fact is a first-class column in the {@code homes}
 * table (owner, name, world uid + name, coordinates, creation time), there is no opaque JSON blob, so an
 * owner's {@code HomeSet} is rebuilt from queryable rows and the quota count is a {@code COUNT(*)}. SQL is
 * issued only through the typed jOOQ DSL, never string concatenation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.homes;

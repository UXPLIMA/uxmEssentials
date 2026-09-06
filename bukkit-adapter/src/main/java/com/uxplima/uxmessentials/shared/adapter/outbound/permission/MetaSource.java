package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.OptionalLong;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * Reads an optional numeric meta value for a value-bearing quota family. This is the seam that keeps
 * LuckPerms a soft dependency: the default {@link #none()} source reports no meta, so the reducer runs
 * on numbered nodes alone, and {@code LuckPermsMetaSource} binds only when LuckPerms is on the
 * classpath and registered (resolved in bootstrap).
 *
 * <p>The {@code familyNode} is the node prefix without a value segment
 * ({@code uxmessentials.home.limit}); the implementation derives the meta key from it
 * ({@code home-limit}) so the meta convention in docs/permissions.md is honoured without the caller
 * hard-coding per-family meta names.
 */
@NullMarked
public interface MetaSource {

    /** The numeric meta value for {@code who} under {@code familyNode}, or empty when absent. */
    OptionalLong metaValue(UUID who, String familyNode);

    /** A source that never reports meta: the default when no permission plugin exposes meta. */
    static MetaSource none() {
        return (who, familyNode) -> OptionalLong.empty();
    }
}

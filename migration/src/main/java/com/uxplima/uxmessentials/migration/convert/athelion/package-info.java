/**
 * The Athelion PlayerWarps import source (docs/12-migration §5): reads player-owned warps from Athelion's serialised
 * {@code plugins/PlayerWarps/data.yml}, maps each into the competitor-neutral {@code ImportedPlayerWarp}, and streams them
 * as {@code PlayerWarpRecord}s for the shared player-warp write path. {@code parse/} confines the foreign YAML codec;
 * {@code map/} performs the ACL translation. Access from the password/status gate, ratings reconstructed from the star
 * total and reviewer set, blocked-players as bans. Parsing stays strictly in this source: no YAML node escapes it.
 */
@NullMarked
package com.uxplima.uxmessentials.migration.convert.athelion;

import org.jspecify.annotations.NullMarked;

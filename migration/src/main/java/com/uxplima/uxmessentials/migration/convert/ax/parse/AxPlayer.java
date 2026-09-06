package com.uxplima.uxmessentials.migration.convert.ax.parse;

import java.util.Objects;

/**
 * One row of AxPlayerWarps' {@code axplayerwarps_players} table: the surrogate {@code id} the warp / rating /
 * whitelist / blacklist / favourite tables reference, plus the player's {@code uuid} and last-seen {@code name}. The
 * source keys everything on the integer id, so the plan loads this table into an {@code id -> AxPlayer} map once and
 * resolves every foreign id through it, the new schema keys on the uuid, so the id never leaves the adapter.
 *
 * @param id the AxPlayerWarps surrogate player id
 * @param uuid the player's uuid, as its canonical 36-character text
 * @param name the player's last-seen name
 */
public record AxPlayer(int id, String uuid, String name) {

    public AxPlayer {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }
}

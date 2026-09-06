package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The pair the player-warps editor is generic over: the {@link PlayerRef owner} the warp belongs to and the
 * {@link PlayerWarp} snapshot taken when the list row was clicked. A player-warp is keyed {@code (owner, name)},
 * so a property cannot be resolved or written without the owner. The snapshot's {@link PlayerWarp#owner()}
 * carries it, but bundling it explicitly keeps the editor's fresh-state reads and writes owner-scoped without
 * re-deriving it. The warp field is only the open-time snapshot; every property re-reads the live row from the
 * repository so an edit never works against a stale copy.
 *
 * @param owner the player whose warp this is (the editor writes only against this owner)
 * @param warp the warp snapshot taken when the list row was clicked
 */
@NullMarked
public record OwnedWarp(PlayerRef owner, PlayerWarp warp) {

    public OwnedWarp {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(warp, "warp");
    }
}

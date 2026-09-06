package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NametagVanish} implementation, soft-coupled to the presence context through Bukkit's own
 * {@code Player#canSee} visibility graph. The same seam messaging's {@code CanSeeVanishVisibility} and teleport's
 * {@code /tpa} listing use. When the presence module is active it hides a vanished player from those who lack the
 * vanish-see node by removing the entity from their view, which {@code canSee} reflects, so the wearer's nametag is
 * not shown to them; when presence is disabled nothing is hidden, {@code canSee} is always true, and the nametag is
 * shown to everyone. This is why the binding degrades to "everyone can see everyone" with presence off without any
 * nametag-side branch.
 *
 * <p>A viewer or wearer who is not online cannot be resolved to a live {@code Player}; the renderer only ever asks
 * about online players (it builds the eligible set from {@code Bukkit.getOnlinePlayers()}), but the guard answers
 * {@code false} (not visible) for a resolution miss so a since-departed player is never added back to the viewer set.
 */
@NullMarked
public final class CanSeeNametagVanish implements NametagVanish {

    @Override
    public boolean canSee(PlayerRef viewer, PlayerRef wearer) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(wearer, "wearer");
        Player viewerPlayer = Bukkit.getPlayer(viewer.uuid());
        Player wearerPlayer = Bukkit.getPlayer(wearer.uuid());
        if (viewerPlayer == null || wearerPlayer == null) {
            return false;
        }
        return viewerPlayer.canSee(wearerPlayer);
    }
}

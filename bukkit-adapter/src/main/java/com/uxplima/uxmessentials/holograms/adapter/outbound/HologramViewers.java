package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import org.jspecify.annotations.NullMarked;

/**
 * Owns the visibility side of hologram rendering for {@link HologramRenderer}: which online players may currently
 * see a hologram under its {@link Visibility}, and the show/hide calls that apply that to a live shared entity.
 * The renderer keeps the spawn/refresh/remove lifecycle and the per-viewer text dispatch; this collaborator
 * answers "who sees it" and applies it, so the two concerns live apart and each stays small.
 *
 * <p>An {@code ALL} hologram is the cheap default. The shared entity is visible to everyone, so it is never
 * restricted and never enumerated. A {@code PERMISSION} hologram is restricted to the online holders of its
 * gating node, recomputed on every spawn; a {@code MANUAL} hologram is restricted to its persisted shown-viewer
 * set, queried per hologram through the injected {@code manualViewers} lookup. The per-player decision itself is
 * {@link HologramRenderer#maySee}, kept on the renderer so it stays unit-testable there; this class wraps it with
 * the online scan and the show/hide application against a {@link RenderedHologram}.
 */
@NullMarked
public final class HologramViewers {

    private final Plugin plugin;
    private final Permissions permissions;
    private final Function<HologramName, Set<UUID>> manualViewers;
    private final Function<HologramName, Set<UUID>> blacklist;

    public HologramViewers(
            Plugin plugin,
            Permissions permissions,
            Function<HologramName, Set<UUID>> manualViewers,
            Function<HologramName, Set<UUID>> blacklist) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.manualViewers = Objects.requireNonNull(manualViewers, "manualViewers");
        this.blacklist = Objects.requireNonNull(blacklist, "blacklist");
    }

    /** The online players who may currently see {@code hologram} under its visibility, the override audience. */
    List<Player> eligible(Hologram hologram) {
        Visibility visibility = hologram.visibility();
        Set<UUID> shown = shownViewersFor(hologram);
        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (HologramRenderer.maySee(permissions, visibility, BukkitRefs.toRef(online), shown)) {
                eligible.add(online);
            }
        }
        return eligible;
    }

    /**
     * Apply {@code visibility} to a freshly spawned entity on its own region thread. {@code ALL} keeps the entity
     * visible by default (no work). {@code PERMISSION} and {@code MANUAL} restrict it to an explicit viewer set,
     * then show it to each online player who qualifies (a node-holder, or a member of the manual shown set), so a
     * non-qualifier never sees it and a qualifier sees it at once.
     */
    void applyOnSpawn(RenderedHologram spawned, Hologram hologram) {
        Visibility visibility = hologram.visibility();
        if (visibility.isPermissionGated() || visibility.isManual()) {
            Set<UUID> shown = shownViewersFor(hologram);
            spawned.restrictToViewers();
            for (Player online : Bukkit.getOnlinePlayers()) {
                applyViewer(spawned, visibility, online, shown);
            }
        }
        hideBlacklisted(spawned, hologram);
    }

    /**
     * Hide the shared entity from each online blacklisted player. Applied across every mode and last, so it
     * overrides an {@code ALL} hologram's default visibility and a mode-based show for a blacklisted qualifier.
     * A hologram with no blacklist (the common case) does no work, so it stays the cheap default.
     */
    private void hideBlacklisted(RenderedHologram spawned, Hologram hologram) {
        Set<UUID> blacklisted = blacklist.apply(hologram.name());
        if (blacklisted.isEmpty()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (blacklisted.contains(online.getUniqueId())) {
                spawned.hide(plugin, online);
            }
        }
    }

    /** Whether {@code viewer} is on {@code hologram}'s blacklist: hidden from it regardless of visibility mode. */
    boolean isBlacklisted(Hologram hologram, Player viewer) {
        return blacklist.apply(hologram.name()).contains(viewer.getUniqueId());
    }

    /** Show or hide the shared entity to a single {@code viewer} according to whether they may see {@code visibility}. */
    void applyViewer(RenderedHologram spawned, Visibility visibility, Player viewer, Set<UUID> shown) {
        if (HologramRenderer.maySee(permissions, visibility, BukkitRefs.toRef(viewer), shown)) {
            spawned.show(plugin, viewer);
        } else {
            spawned.hide(plugin, viewer);
        }
    }

    /** Whether {@code viewer} may currently see {@code hologram} under its visibility and shown-viewer set. */
    boolean maySee(Hologram hologram, Player viewer, Set<UUID> shown) {
        return HologramRenderer.maySee(permissions, hologram.visibility(), BukkitRefs.toRef(viewer), shown);
    }

    /** The manual shown-viewer set for a MANUAL hologram, queried lazily; empty for any other mode. */
    Set<UUID> shownViewersFor(Hologram hologram) {
        if (!hologram.visibility().isManual()) {
            return Set.of();
        }
        return manualViewers.apply(hologram.name());
    }
}

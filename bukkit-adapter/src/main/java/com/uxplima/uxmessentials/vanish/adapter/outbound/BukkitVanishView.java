package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link VanishView} implementation. It reconciles a vanished player's visibility across every viewer, hiding
 * them from a viewer whose see level is below the player's use level, and revealing them to one who clears it, by
 * driving Bukkit's {@code hidePlayer} / {@code showPlayer} graph, which drops both the player <em>entity</em> and their
 * <em>tablist</em> entry for that viewer in one call, so a hidden player disappears from the world and the tab list
 * together. This is the acceptable Bukkit-level fallback for the packet hide the design prefers; it has the bonus that
 * the {@code canSee}-reading surfaces observe the same result, so no consumer can disagree with the store about who is
 * visible.
 *
 * <p>Every mutation hops to the affected viewer's owning region/entity thread through the injected {@link Scheduler}
 * port. {@code hidePlayer}/{@code showPlayer} are per-viewer entity operations valid only on the <em>viewer's</em>
 * owning thread on Folia. The online roster is enumerated on the global region thread (iterating
 * {@code Bukkit.getOnlinePlayers()} off it is illegal on Folia), and each {@code hidePlayer}/{@code showPlayer} then
 * runs on that viewer's own entity thread. An offline player on either side is a silent no-op. The viewer's see level
 * is resolved per viewer through the {@link VanishLevelResolver}, so a permitted viewer keeps seeing the hidden player
 * and a level raise or drop settles in one reconciliation pass.
 */
@NullMarked
public final class BukkitVanishView implements VanishView {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final VanishLevelResolver levels;

    public BukkitVanishView(Plugin plugin, Scheduler scheduler, VanishLevelResolver levels) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    @Override
    public void hide(PlayerRef who, VanishLevel level) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(level, "level");
        forEachViewer((viewer, viewerRef) -> {
            if (viewerRef.equals(who)) {
                return;
            }
            @Nullable Player target = liveTarget(who);
            if (target == null) {
                return;
            }
            if (VanishLevels.sees(levels.seeLevel(viewerRef), level)) {
                viewer.showPlayer(plugin, target); // this viewer clears the bar, make sure they see the player
            } else {
                viewer.hidePlayer(plugin, target); // below the bar, hide the player from them
            }
        });
    }

    @Override
    public void reveal(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        forEachViewer((viewer, viewerRef) -> {
            if (!viewerRef.equals(who)) {
                @Nullable Player target = liveTarget(who);
                if (target != null) {
                    viewer.showPlayer(plugin, target);
                }
            }
        });
    }

    /**
     * Enumerate the online roster on the global region thread, then run {@code action} for each viewer on that viewer's
     * own entity thread, where its {@code hidePlayer}/{@code showPlayer} is valid under Folia. The live viewer is
     * re-resolved inside the hop so an offline viewer is a silent no-op.
     */
    private void forEachViewer(ViewerAction action) {
        scheduler.onGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                PlayerRef viewerRef = BukkitRefs.toRef(online);
                scheduler.onEntity(viewerRef, () -> {
                    @Nullable Player viewer = Bukkit.getPlayer(viewerRef.uuid());
                    if (viewer != null && viewer.isOnline()) {
                        action.run(viewer, viewerRef);
                    }
                });
            }
        });
    }

    private @Nullable Player liveTarget(PlayerRef who) {
        Player target = Bukkit.getPlayer(who.uuid());
        return target != null && target.isOnline() ? target : null;
    }

    @FunctionalInterface
    private interface ViewerAction {
        void run(Player viewer, PlayerRef viewerRef);
    }
}

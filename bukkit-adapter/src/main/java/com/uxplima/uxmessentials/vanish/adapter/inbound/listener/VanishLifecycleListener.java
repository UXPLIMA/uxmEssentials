package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.JoinVanishReconciler;
import com.uxplima.uxmessentials.vanish.application.SetVanishLevel;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the vanish view coherent across join and quit, and owns the join-vanished behaviour.
 *
 * <ul>
 *   <li><b>Join</b>. The joiner's cross-server vanish is reconciled first through the {@link JoinVanishReconciler}: a
 *       player vanished on another backend is seeded into this server's store so they arrive already hidden. Then a
 *       joiner who holds {@code uxmessentials.vanish.persist} (and with {@code join-vanished} on) is re-vanished before
 *       anyone sees them, so a staff member rejoins already hidden; otherwise the joiner's own use level is re-derived
 *       through {@link SetVanishLevel} so a layered permission change takes effect on (re)join and a player still marked
 *       vanished (a reload, or a cross-server hop) is re-hidden. Either way, once the joiner ends up vanished their real
 *       join line is suppressed. Every currently-vanished <em>other</em> player is then re-hidden from the joiner, so a
 *       hidden staff member does not flash into view the moment someone connects.
 *   <li><b>Quit</b>. Suppress the quit line for a vanished player (they already appeared offline to those who could
 *       not see them), then drop them from the vanish store so a disconnected player holds no vanish state; a later
 *       reconnect re-derives it from the persist permission.
 * </ul>
 *
 * <p>The events fire on the player's region thread; every hide still routes through the {@link VanishView}, which hops
 * to the owning entity thread via the {@code Scheduler} port, valid on Folia.
 */
@NullMarked
public final class VanishLifecycleListener implements Listener {

    private static final String PERSIST_PERMISSION = "uxmessentials.vanish.persist";

    private final VanishStore store;
    private final VanishView view;
    private final SetVanishLevel setVanishLevel;
    private final ToggleVanish toggleVanish;
    private final JoinVanishReconciler reconciler;
    private final VanishConfig config;
    private final Server server;

    public VanishLifecycleListener(
            VanishStore store,
            VanishView view,
            SetVanishLevel setVanishLevel,
            ToggleVanish toggleVanish,
            JoinVanishReconciler reconciler,
            VanishConfig config,
            Server server) {
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.setVanishLevel = Objects.requireNonNull(setVanishLevel, "setVanishLevel");
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.config = Objects.requireNonNull(config, "config");
        this.server = Objects.requireNonNull(server, "server");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        PlayerRef ref = BukkitRefs.toRef(joiner);
        // Cross-server: seed a player vanished on another backend into this server's store before the hide flow runs.
        reconciler.reconcile(joiner.getUniqueId());
        if (shouldJoinVanished(joiner)) {
            toggleVanish.setVanished(ref, true);
        } else {
            setVanishLevel.reapply(ref);
        }
        if (store.isVanished(joiner.getUniqueId())) {
            event.joinMessage(null);
        }
        rehideOthersFrom(joiner.getUniqueId());
    }

    /** A player rejoins vanished when the feature is on, they are not already marked vanished, and they may persist. */
    private boolean shouldJoinVanished(Player joiner) {
        return config.joinVanished()
                && !store.isVanished(joiner.getUniqueId())
                && joiner.hasPermission(PERSIST_PERMISSION);
    }

    private void rehideOthersFrom(UUID joiner) {
        for (UUID vanished : store.vanished()) {
            if (vanished.equals(joiner)) {
                continue;
            }
            @Nullable Player other = server.getPlayer(vanished);
            if (other != null && other.isOnline()) {
                VanishLevel level = store.levelOf(vanished).orElse(VanishLevel.DEFAULT);
                view.hide(BukkitRefs.toRef(other), level);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID who = event.getPlayer().getUniqueId();
        if (store.isVanished(who)) {
            event.quitMessage(null);
        }
        store.reveal(who);
    }
}

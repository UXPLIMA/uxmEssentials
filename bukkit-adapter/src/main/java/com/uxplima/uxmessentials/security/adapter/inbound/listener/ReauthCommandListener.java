package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.security.adapter.ReauthController;
import com.uxplima.uxmessentials.security.adapter.ReauthState;
import com.uxplima.uxmessentials.security.domain.ReauthPolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * Gates the op/dangerous commands an operator lists behind a fresh second-factor proof. When a player runs a protected
 * command they have not verified for within the re-auth window, the dispatch is cancelled before the command runs and
 * the {@link ReauthController} prompts them on the PIN keypad; a successful proof stamps the window and retries the
 * command. A holder of {@code uxmessentials.security.bypass} is exempt, and the console is never gated (this listens
 * only to the player-command event).
 *
 * <p>Runs at {@link EventPriority#HIGH} so cooperating plugins at NORMAL still see an uncancelled event but the
 * dispatch is stopped before the vanilla handler. The recent-verify check is an in-memory {@link ReauthState} read, so
 * the decision to cancel is made synchronously on the tick thread with no I/O; the enrolment lookup and the factor
 * verification happen off-thread inside the controller. When the protected list is empty (or the feature is disabled)
 * the listener short-circuits before any work.
 */
@NullMarked
public final class ReauthCommandListener implements Listener {

    private static final String BYPASS_PERMISSION = "uxmessentials.security.bypass";

    private final boolean enabled;
    private final ReauthPolicy policy;
    private final ReauthState reauthState;
    private final ReauthController controller;
    private final Clock clock;

    public ReauthCommandListener(
            boolean enabled, ReauthPolicy policy, ReauthState reauthState, ReauthController controller, Clock clock) {
        this.enabled = enabled;
        this.policy = Objects.requireNonNull(policy, "policy");
        this.reauthState = Objects.requireNonNull(reauthState, "reauthState");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled || policy.isEmpty()) {
            return;
        }
        String message = event.getMessage();
        if (!policy.isProtected(message)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        Instant now = clock.instant();
        if (policy.decide(message, reauthState.lastVerified(player.getUniqueId()), now)
                == ReauthPolicy.Decision.ALLOW) {
            return; // verified recently enough. Let the protected command run
        }
        event.setCancelled(true);
        controller.require(player, message);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        controller.onQuit(BukkitRefs.toRef(event.getPlayer()));
    }
}

package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.servertweaks.application.SignedDirectiveQueue;
import com.uxplima.uxmessentials.servertweaks.domain.SignedChatDirective;
import com.uxplima.uxmessentials.servertweaks.domain.SignedSource;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the proxy's command ruling to the backend's {@link PlayerCommandPreprocessEvent}, the command counterpart of
 * {@link SignedVelocityChatListener}: a signed command vetoed or rewritten at the Velocity proxy is handled identically
 * here so the two sides stay consistent.
 *
 * <p>Runs at {@link EventPriority#LOWEST}. This event fires on the server thread, so the directive is taken from the
 * shared {@link SignedDirectiveQueue} without blocking. The queue's non-blocking poll keeps the tick thread free,
 * relying on the proxy delivering its ruling ahead of the forwarded command. With no ruling queued (the no-proxy case)
 * the command runs unchanged.
 */
@NullMarked
public final class SignedVelocityCommandListener implements Listener {

    private final SignedDirectiveQueue queue;

    public SignedVelocityCommandListener(SignedDirectiveQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /** Apply the proxy's command ruling for this sender, if one has arrived. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        queue.poll(event.getPlayer().getUniqueId(), SignedSource.COMMAND)
                .ifPresent(directive -> apply(event, directive));
    }

    private void apply(PlayerCommandPreprocessEvent event, SignedChatDirective directive) {
        if (directive.cancelled()) {
            event.setCancelled(true);
            return;
        }
        directive.modifiedMessage().ifPresent(event::setMessage);
    }
}

package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import org.jspecify.annotations.NullMarked;

/**
 * Enforces the global chat lock {@code /togglechat} flips: while the {@link ChatLock} is set, a public chat
 * message from a player who lacks {@code uxmessentials.communication.chat.bypass} is cancelled and the speaker
 * is told chat is locked. Staff with the bypass node (and console, which never raises this event) speak
 * through a frozen chat so they can still coordinate.
 *
 * <p>{@code AsyncChatEvent} fires off the main thread; this listener only reads the atomic flag and checks a
 * permission (both thread-safe) and cancels the event: it touches no other Bukkit API on the async thread.
 * The blocked-notice delivery hops back to the speaker's thread inside the {@link Notifier}'s
 * sink, so the async-listener-no-Bukkit-API rule holds.
 */
@NullMarked
public final class ChatLockListener implements Listener {

    private static final String BYPASS_PERMISSION = "uxmessentials.communication.chat.bypass";

    private final ChatLock lock;
    private final Notifier notifier;

    public ChatLockListener(ChatLock lock, Notifier notifier) {
        this.lock = Objects.requireNonNull(lock, "lock");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Cancel a non-bypass player's public chat while the lock is on, and tell them chat is locked. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!lock.isLocked()) {
            return;
        }
        Player speaker = event.getPlayer();
        if (speaker.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        event.setCancelled(true);
        notifier.send(BukkitRefs.toRef(speaker), CommunicationMessageKey.CHAT_LOCKED);
    }
}

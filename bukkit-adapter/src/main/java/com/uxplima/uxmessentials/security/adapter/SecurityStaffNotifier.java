package com.uxplima.uxmessentials.security.adapter;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Fans a security-guard notice (a same-IP alt on join, a flagged client brand) out to every online player holding
 * {@code uxmessentials.security.alts.notify}, each in their own locale, and mirrors a plain line to the operator
 * log so a console-only staffer still sees it. The online set is enumerated on the global region thread (it is not
 * safe to iterate off-thread under Folia); per-player delivery then hops to each recipient's own entity thread
 * inside the {@link MessageSink}. A join is infrequent, so the per-notice scan of the online set is acceptable.
 *
 * <p>No raw IP or secret is ever passed in. The alt notice carries player names and a count, the client notice a
 * brand, so the log line is safe.
 */
@NullMarked
public final class SecurityStaffNotifier {

    /** The node a player must hold to receive the alt/flagged-client staff notice. */
    public static final String NOTIFY_NODE = "uxmessentials.security.alts.notify";

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final Logger log;

    public SecurityStaffNotifier(Server server, Scheduler scheduler, Messages messages, MessageSink sink, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Notify staff of {@code key} rendered with {@code placeholders}, and log the same in plain form. */
    public void notifyStaff(SecurityMessageKey key, Map<String, String> placeholders, String logLine) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(logLine, "logLine");
        Map<String, String> safe = Map.copyOf(placeholders);
        log.info(logLine);
        scheduler.onGlobal(() -> fanOut(key, safe));
    }

    private void fanOut(SecurityMessageKey key, Map<String, String> placeholders) {
        for (Player player : server.getOnlinePlayers()) {
            if (player.hasPermission(NOTIFY_NODE)) {
                PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
                sink.deliver(who, messages.resolve(who, key, placeholders));
            }
        }
    }
}

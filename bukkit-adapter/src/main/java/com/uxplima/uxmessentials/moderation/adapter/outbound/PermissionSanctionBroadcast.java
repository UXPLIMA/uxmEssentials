package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SanctionBroadcast} implementation: fans a non-silent sanction's one-line announcement out to every
 * online player holding {@code uxmessentials.moderation.broadcast.receive} plus the server console. Each
 * recipient is sent the message resolved in their own locale. The player path delegates to the shared
 * {@link MessageSink}, which parses the MiniMessage source once and hops to the recipient's region thread; the
 * console path renders against {@code en} and prints on the global thread.
 *
 * <h2>Concurrency</h2>
 * The online set is enumerated on the global region thread ({@code Server.getOnlinePlayers()} is not safe to
 * iterate off-thread under Folia), and the console line is printed there too; per-player delivery then hops to
 * each recipient's own entity thread inside the {@link MessageSink}. A sanction is an infrequent action, so the
 * per-call scan of the online set is acceptable.
 */
@NullMarked
public final class PermissionSanctionBroadcast implements SanctionBroadcast {

    /** The node a player must hold to receive the staff/public sanction broadcast. */
    private static final String RECEIVE_NODE = "uxmessentials.moderation.broadcast.receive";

    /** The locale dimension the console line is resolved against: the console has no per-viewer locale. */
    private static final PlayerRef CONSOLE = PlayerRef.system("console");

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final String prefixTemplate;
    private final MiniMessage miniMessage;

    public PermissionSanctionBroadcast(
            Server server, Scheduler scheduler, Messages messages, MessageSink sink, String prefixTemplate) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.prefixTemplate = Objects.requireNonNull(prefixTemplate, "prefixTemplate");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void announce(MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        Map<String, String> safe = Map.copyOf(placeholders);
        scheduler.onGlobal(() -> fanOut(key, safe));
    }

    private void fanOut(MessageKey key, Map<String, String> placeholders) {
        for (Player player : server.getOnlinePlayers()) {
            if (player.hasPermission(RECEIVE_NODE)) {
                PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
                sink.deliver(who, messages.resolve(who, key, placeholders));
            }
        }
        sendToConsole(key, placeholders);
    }

    private void sendToConsole(MessageKey key, Map<String, String> placeholders) {
        TagResolver prefix = Placeholder.parsed("prefix", prefixTemplate);
        String rendered = messages.resolve(CONSOLE, key, placeholders);
        server.getConsoleSender().sendMessage(miniMessage.deserialize(rendered, prefix, StyleTags.resolver()));
    }
}

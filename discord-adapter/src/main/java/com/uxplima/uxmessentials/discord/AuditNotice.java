package com.uxplima.uxmessentials.discord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The bridge-side representation of one host notification, an audit event or an economy notification the host
 * plugin raised on the {@code com.uxplima.uxmessentials.audit} channel / in-process {@code DomainEventPublisher}
 * (docs/09-deployment.md §Audit logging). The host's notification source produces these and feeds them to the
 * {@link AuditNoticeSubscriber}; the {@link NotificationFormatter} turns one into the formatted
 * {@link Notification} that is forwarded to Discord.
 *
 * <p>It carries the event name (e.g. {@code player_jail}, {@code eco_set}), the issuing {@code actor} and the
 * affected {@code target} (both already rendered to display strings by the host adapter, never live Bukkit
 * handles), the remaining greppable {@code key=value} fields in declaration order, the optional economy
 * {@code amount} used for the {@code min-eco-notify} threshold, and the {@code originServer} that raised it.
 *
 * <h2>The loop sentinel</h2>
 * {@link #originServer()} carries the id of the backend that produced the event. A notification whose origin is
 * {@link #DISCORD_ORIGIN} was synthesized by the bridge itself (or re-broadcast on the cluster bus on its
 * behalf); forwarding it would mirror the same action twice, so {@link #fromBridge()} flags it and the
 * formatter drops it. This is the {@code originServer = "discord"} sentinel that prevents replication loops
 * (docs/09-deployment.md Path C, CLAUDE.md).
 *
 * @param category which Discord channel category this notice routes to
 * @param event the audit/event name (the {@code event=} value), without the {@code event=} prefix
 * @param actor the issuing staff member or {@code "system"} for bootstrap events
 * @param target the affected player/subject, or empty for events with no single target
 * @param fields the remaining {@code key -> value} fields, preserving insertion order for a stable line
 * @param amount the economy transaction amount for threshold filtering; empty for non-economy notices
 * @param originServer the {@code server-id} of the backend that raised the event ({@link #DISCORD_ORIGIN} loops)
 */
public record AuditNotice(
        EventCategory category,
        String event,
        String actor,
        Optional<String> target,
        Map<String, String> fields,
        Optional<Long> amount,
        String originServer) {

    /** The sentinel origin the bridge stamps on anything it produced: never re-forwarded (the loop guard). */
    public static final String DISCORD_ORIGIN = "discord";

    public AuditNotice {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        // Preserve insertion order so the rendered line keeps the host's field ordering: Map.copyOf would not.
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(originServer, "originServer");
    }

    /**
     * Whether this notice originated from the bridge itself and must not be forwarded again. Comparison is
     * case-insensitive so a backend configured with {@code server-id = "Discord"} is still recognised as the
     * sentinel rather than treated as a real server and mirrored into a loop.
     */
    public boolean fromBridge() {
        return originServer.equalsIgnoreCase(DISCORD_ORIGIN);
    }
}

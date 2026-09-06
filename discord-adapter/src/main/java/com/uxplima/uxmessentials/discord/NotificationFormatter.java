package com.uxplima.uxmessentials.discord;

import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Turns a host {@link AuditNotice} into the formatted {@link Notification} that gets mirrored to Discord, one
 * concise line per event category (the task's "format per event category"). Audit notices render as the
 * greppable {@code event=<name> actor=<actor> [target=<target>] key=value …} form the operator already knows
 * from the {@code com.uxplima.uxmessentials.audit} channel (docs/09-deployment.md §Audit logging); economy
 * notices render the same way but carry the transaction amount so the forwarder can apply {@code min-eco-notify}.
 *
 * <p>This class also enforces the <strong>loop sentinel</strong>: a notice whose {@code originServer} is the
 * {@link AuditNotice#DISCORD_ORIGIN} sentinel was produced by the bridge itself and is dropped here, before it
 * can reach the gateway, so a Discord-mirrored action is never mirrored a second time (CLAUDE.md, Path C). The
 * formatter holds no JDA or Bukkit state, so it is fully unit-testable.
 */
public final class NotificationFormatter {

    private NotificationFormatter() {}

    /**
     * Format the notice for forwarding, or {@link Optional#empty()} when it must not be forwarded, currently
     * only the loop-sentinel case (a bridge-originated notice). All other notices format to a {@link Notification}
     * tagged with the notice's own {@link AuditNotice#category() category}; the per-category channel mapping and
     * enable flags are applied later by the {@link NotificationForwarder} against {@code discord.conf}.
     */
    public static Optional<Notification> format(AuditNotice notice) {
        if (notice.fromBridge()) {
            return Optional.empty();
        }
        String line = render(notice);
        return Optional.of(
                switch (notice.category()) {
                    case AUDIT -> Notification.audit(line);
                    case ECONOMY -> Notification.economy(line, notice.amount().orElse(0L));
                });
    }

    private static String render(AuditNotice notice) {
        StringJoiner line = new StringJoiner(" ");
        line.add("event=" + notice.event());
        line.add("actor=" + notice.actor());
        notice.target().ifPresent(target -> line.add("target=" + target));
        for (Map.Entry<String, String> field : notice.fields().entrySet()) {
            line.add(field.getKey() + "=" + field.getValue());
        }
        return line.toString();
    }
}

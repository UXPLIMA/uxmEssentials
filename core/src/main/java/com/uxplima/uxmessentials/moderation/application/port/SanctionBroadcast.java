package com.uxplima.uxmessentials.moderation.application.port;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * Outbound port for the staff/public sanction broadcast. The one-line "{@code <actor> banned <target>}"
 * announcement a sanction emits to everyone who should see moderation activity. Distinct from both the
 * {@link com.uxplima.uxmessentials.shared.application.message.Notifier} (the single actor/target
 * recipient) and the {@link ModerationAudit} (the operator log file): this fans one rendered message out to a
 * live audience.
 *
 * <p>The adapter renders {@code key} with {@code placeholders} per recipient and delivers it to every online
 * player holding {@code uxmessentials.moderation.broadcast.receive} plus the console. A use case calls this
 * only for a non-silent sanction; a silent ({@code -s}) sanction suppresses the broadcast while the actor and
 * target notices still go out.
 */
public interface SanctionBroadcast {

    /** A no-op broadcast: announces nothing. The default a use case runs against when broadcasts are off. */
    SanctionBroadcast NONE = (key, placeholders) -> {};

    /**
     * Announce {@code key}, resolved per recipient with {@code placeholders}, to the moderation broadcast
     * audience and the console.
     */
    void announce(MessageKey key, Map<String, String> placeholders);
}

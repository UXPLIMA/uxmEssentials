package com.uxplima.uxmessentials.moderation.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A named punishment preset an operator configures under {@code templates} in {@code moderation.conf} and a
 * staff member applies with {@code /punish <player> <template>}: a fixed reason plus an optional duration. A
 * template that carries a duration issues a timed ban ({@code /tempban}); one without issues a permanent ban
 * ({@code /ban}). The duration is the parsed span, {@link Optional#empty()} is a permanent template, so the
 * malformed-duration decision is made once, at load time, and never reaches the use case.
 *
 * @param name the template name as referenced on the command line (never blank)
 * @param reason the reason applied to the sanction (never blank)
 * @param duration the timed span, or empty for a permanent ban
 */
public record PunishmentTemplate(String name, String reason, Optional<Duration> duration) {

    public PunishmentTemplate {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(duration, "duration");
        if (name.isBlank()) {
            throw new IllegalArgumentException("template name must not be blank");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("template reason must not be blank");
        }
    }

    /** A permanent template: the reason with no duration. */
    public static PunishmentTemplate permanent(String name, String reason) {
        return new PunishmentTemplate(name, reason, Optional.empty());
    }

    /** A timed template: the reason bounded to {@code span}. */
    public static PunishmentTemplate timed(String name, String reason, Duration span) {
        return new PunishmentTemplate(name, reason, Optional.of(span));
    }
}

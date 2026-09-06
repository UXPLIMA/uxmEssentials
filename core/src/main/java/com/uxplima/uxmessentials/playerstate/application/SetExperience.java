package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects.ExperienceReport;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /exp} (alias {@code /xp}) {@code get | set <amount> | give <amount> | take <amount> | reset [player]}:
 * read or change a player's experience. A live-only effect through the {@link PlayerEffects} port, the adapter
 * reads the current total, applies the {@link ExperienceChange} (clamped non-negative in the domain), and
 * reports the resulting level and point total back so this use case can confirm. A read-only {@code get}
 * reports without changing anything; a mutating form confirms the new total to the actor and, for a staff
 * target, to the subject.
 */
public final class SetExperience {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public SetExperience(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Apply {@code change} to {@code who} themselves. */
    public void apply(PlayerRef who, ExperienceChange change) {
        applyFor(who, who, change);
    }

    /** Apply {@code change} to {@code subject} on behalf of {@code actor}. */
    public void applyFor(PlayerRef actor, PlayerRef subject, ExperienceChange change) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(change, "change");
        effects.applyExperience(subject, change, report -> confirm(actor, subject, change, report));
    }

    private void confirm(PlayerRef actor, PlayerRef subject, ExperienceChange change, ExperienceReport report) {
        Map<String, String> data = Map.of(
                "level", Integer.toString(report.level()),
                "points", Integer.toString(report.totalPoints()),
                "player", subject.name());
        boolean show = !change.mutates();
        if (actor.equals(subject)) {
            notifier.send(actor, show ? PlayerstateMessageKey.EXP_SHOW : PlayerstateMessageKey.EXP_SET, data);
            return;
        }
        notifier.send(actor, show ? PlayerstateMessageKey.EXP_SHOW_OTHER : PlayerstateMessageKey.EXP_SET_OTHER, data);
        if (change.mutates()) {
            notifier.send(subject, PlayerstateMessageKey.EXP_SET, data);
        }
    }
}

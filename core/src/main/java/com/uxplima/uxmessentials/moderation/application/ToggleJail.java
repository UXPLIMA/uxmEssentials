package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /togglejail <player> [jail] [reason]}: a convenience wrapper that releases a jailed target or jails a
 * free one in a single command. It owns no jail logic of its own. The current jail row decides the direction
 * and the existing {@link Jail} / {@link Unjail} use cases do the work (exempt gating, unknown-jail rejection,
 * audit lines, teleport and the cross-context {@code JailGate} update all stay there). A jailed target is
 * released; a free target is confined to the named jail, or the first configured jail when the name is omitted.
 */
public final class ToggleJail {

    private final ModerationRepository repository;
    private final JailDirectory jails;
    private final Jail jail;
    private final Unjail unjail;

    public ToggleJail(ModerationRepository repository, JailDirectory jails, Jail jail, Unjail unjail) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.jails = Objects.requireNonNull(jails, "jails");
        this.jail = Objects.requireNonNull(jail, "jail");
        this.unjail = Objects.requireNonNull(unjail, "unjail");
    }

    /** Release {@code target} when currently jailed, otherwise confine them to {@code jailName} (or the default). */
    public void toggle(PlayerRef actor, PlayerRef target, String jailName, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jailName, "jailName");
        Objects.requireNonNull(reason, "reason");
        if (repository.loadJail(target) instanceof JailState.Active) {
            unjail.unjail(actor, target);
            return;
        }
        // A blank name falls through to Jail with the empty token it already rejects as UNKNOWN_JAIL, so the
        // actor still gets feedback when no jail is configured to default to.
        jail.jail(actor, target, jailName.isBlank() ? defaultJail() : jailName, "", reason);
    }

    private String defaultJail() {
        List<String> names = jails.peekNames();
        return names.isEmpty() ? "" : names.get(0);
    }
}

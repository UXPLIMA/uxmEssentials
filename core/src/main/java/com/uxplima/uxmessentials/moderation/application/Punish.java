package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.PunishmentTemplate;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /punish <player> <template>}: apply a configured punishment template to a target. It resolves the
 * template name through {@link ResolveTemplate}, then dispatches to the existing sanction use case: a timed
 * template lands a {@link TempBan}, a permanent one a {@link Ban}, so the exempt gate, the kick, the audit
 * line, the history row, the duration cap and the broadcast are all the underlying use case's job, never
 * duplicated here. An unknown template name is refused with {@link ModerationError#UNKNOWN_TEMPLATE} rendered
 * to the actor.
 */
public final class Punish {

    private final ResolveTemplate templates;
    private final Ban ban;
    private final TempBan tempBan;
    private final Notifier notifier;

    public Punish(ResolveTemplate templates, Ban ban, TempBan tempBan, Notifier notifier) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.ban = Objects.requireNonNull(ban, "ban");
        this.tempBan = Objects.requireNonNull(tempBan, "tempBan");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Apply the named template to {@code target}. Returns the resolved template on success (already applied),
     * or the {@link ModerationError} when the name is unknown; the actor is told either way.
     */
    public Result<PunishmentTemplate, ModerationError> punish(
            PlayerRef actor, PlayerRef target, String templateName, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(templateName, "templateName");
        Result<PunishmentTemplate, ModerationError> resolved = templates.resolve(templateName);
        if (resolved.isErr()) {
            notifier.send(actor, resolved.errorOrThrow().messageKey(), Map.of("template", templateName));
            return resolved;
        }
        apply(actor, target, resolved.orElseThrow(), silent);
        return resolved;
    }

    /** The configured template names, for command tab-completion. */
    public List<String> templateNames() {
        return templates.names();
    }

    private void apply(PlayerRef actor, PlayerRef target, PunishmentTemplate template, boolean silent) {
        Optional<String> reason = Optional.of(template.reason());
        if (template.duration().isPresent()) {
            tempBan.tempban(
                    actor, target, SanctionDuration.format(template.duration().get()), reason, silent);
        } else {
            ban.ban(actor, target, reason, silent);
        }
    }
}

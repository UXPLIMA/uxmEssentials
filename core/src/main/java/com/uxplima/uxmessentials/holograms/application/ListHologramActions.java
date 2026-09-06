package com.uxplima.uxmessentials.holograms.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;

/**
 * {@code /hologram action <name> list}: show a hologram's action chain in run order, 1-based, with each action's
 * trigger, type and value. No new query is needed: the hologram is loaded and its {@code actions()} read. A name
 * no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; a hologram with no actions gets the empty
 * notice. The header / per-entry / empty feedback is pushed through the notifier so all text resolves from
 * {@link HologramsMessageKey}.
 */
public final class ListHologramActions {

    private final HologramRepository repository;
    private final Notifier notifier;

    public ListHologramActions(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Push the action chain of hologram {@code name} (header + entries, or the empty notice), or reject. */
    public Result<Unit, HologramError> list(PlayerRef viewer, HologramName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(viewer, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        List<ClickAction> actions = existing.get().actions();
        if (actions.isEmpty()) {
            notifier.send(viewer, HologramsMessageKey.HOLOGRAM_ACTION_LIST_EMPTY, Map.of("name", name.value()));
            return Result.ok();
        }
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_ACTION_LIST_HEADER,
                Map.of("name", name.value(), "count", Integer.toString(actions.size())));
        for (int index = 0; index < actions.size(); index++) {
            ClickAction action = actions.get(index);
            notifier.send(
                    viewer,
                    HologramsMessageKey.HOLOGRAM_ACTION_LIST_ENTRY,
                    Map.of(
                            "index",
                            Integer.toString(index + 1),
                            "trigger",
                            action.trigger().name(),
                            "type",
                            action.type().name(),
                            "value",
                            action.value()));
        }
        return Result.ok();
    }
}

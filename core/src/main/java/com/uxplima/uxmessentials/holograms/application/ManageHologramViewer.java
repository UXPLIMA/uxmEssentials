package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The commands behind {@code /hologram show|hide <name> <player>}: grow or shrink a MANUAL-visibility
 * hologram's explicit per-player viewer set, persist the change, and apply it to the live entity at once so the
 * named player sees (or stops seeing) the hologram without waiting for a refresh tick. The viewer set is a
 * persisted association keyed by hologram name (it survives a restart), separate from the hologram snapshot so a
 * show/hide never re-writes the whole aggregate.
 *
 * <p>The set may be managed regardless of the hologram's current mode. Adding a viewer before switching to
 * MANUAL is allowed and simply has no visible effect until {@code /hologram visibility <name> manual} runs. A
 * name no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; a show of a player already in the
 * set (or a hide of one not in it) is reported as a no-op rather than re-applying the live change. The
 * operator-only permission is enforced at the command gate.
 */
public final class ManageHologramViewer {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public ManageHologramViewer(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Add {@code target} to the hologram {@code name}'s manual viewer set, or reject an unknown name. */
    public Result<Unit, HologramError> show(PlayerRef actor, HologramName name, PlayerRef target) {
        return mutate(actor, name, target, true);
    }

    /** Remove {@code target} from the hologram {@code name}'s manual viewer set, or reject an unknown name. */
    public Result<Unit, HologramError> hide(PlayerRef actor, HologramName name, PlayerRef target) {
        return mutate(actor, name, target, false);
    }

    private Result<Unit, HologramError> mutate(PlayerRef actor, HologramName name, PlayerRef target, boolean show) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(target, "target");
        if (!repository.exists(name)) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        boolean alreadyInSet = repository.manualViewers(name).contains(target.uuid());
        if (show) {
            return applyShow(actor, name, target, alreadyInSet);
        }
        return applyHide(actor, name, target, alreadyInSet);
    }

    private Result<Unit, HologramError> applyShow(
            PlayerRef actor, HologramName name, PlayerRef target, boolean alreadyShown) {
        if (alreadyShown) {
            notifier.send(actor, HologramsMessageKey.HOLOGRAM_ALREADY_SHOWN, placeholders(name, target));
            return Result.ok();
        }
        repository.showTo(name, target.uuid());
        view.applyManualViewer(name, target.uuid(), true);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_SHOWN_TO, placeholders(name, target));
        return Result.ok();
    }

    private Result<Unit, HologramError> applyHide(
            PlayerRef actor, HologramName name, PlayerRef target, boolean wasShown) {
        if (!wasShown) {
            notifier.send(actor, HologramsMessageKey.HOLOGRAM_NOT_SHOWN, placeholders(name, target));
            return Result.ok();
        }
        repository.hideFrom(name, target.uuid());
        view.applyManualViewer(name, target.uuid(), false);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_HIDDEN_FROM, placeholders(name, target));
        return Result.ok();
    }

    private static Map<String, String> placeholders(HologramName name, PlayerRef target) {
        return Map.of("name", name.value(), "player", target.name());
    }
}

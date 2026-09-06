package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The commands behind {@code /hologram blacklist|unblacklist <name> <player>}: grow or shrink a hologram's
 * viewer blacklist (the players it is hidden from regardless of its visibility mode) persist the change, and
 * re-render the live entity so the change takes effect at once. The blacklist is a persisted association keyed
 * by hologram name (it survives a restart), separate from the hologram snapshot so it never re-writes the whole
 * aggregate. A re-render re-applies the current blacklist on spawn (hiding the listed players and showing a
 * just-removed one again), so the same path serves both directions without bespoke show/hide logic.
 *
 * <p>A name no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; blacklisting a player already
 * on the list (or removing one not on it) is reported as a no-op rather than re-rendering. The operator-only
 * permission is enforced at the command gate.
 */
public final class ManageHologramBlacklist {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public ManageHologramBlacklist(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Add {@code target} to the hologram {@code name}'s viewer blacklist, or reject an unknown name. */
    public Result<Unit, HologramError> blacklist(PlayerRef actor, HologramName name, PlayerRef target) {
        return mutate(actor, name, target, true);
    }

    /** Remove {@code target} from the hologram {@code name}'s viewer blacklist, or reject an unknown name. */
    public Result<Unit, HologramError> unblacklist(PlayerRef actor, HologramName name, PlayerRef target) {
        return mutate(actor, name, target, false);
    }

    private Result<Unit, HologramError> mutate(PlayerRef actor, HologramName name, PlayerRef target, boolean add) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(target, "target");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        boolean alreadyListed = repository.blacklisted(name).contains(target.uuid());
        if (add) {
            return applyAdd(actor, name, target, existing.get(), alreadyListed);
        }
        return applyRemove(actor, name, target, existing.get(), alreadyListed);
    }

    private Result<Unit, HologramError> applyAdd(
            PlayerRef actor, HologramName name, PlayerRef target, Hologram hologram, boolean alreadyListed) {
        if (alreadyListed) {
            notifier.send(actor, HologramsMessageKey.HOLOGRAM_ALREADY_BLACKLISTED, placeholders(name, target));
            return Result.ok();
        }
        repository.addToBlacklist(name, target.uuid());
        view.render(hologram);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_BLACKLISTED, placeholders(name, target));
        return Result.ok();
    }

    private Result<Unit, HologramError> applyRemove(
            PlayerRef actor, HologramName name, PlayerRef target, Hologram hologram, boolean alreadyListed) {
        if (!alreadyListed) {
            notifier.send(actor, HologramsMessageKey.HOLOGRAM_NOT_BLACKLISTED, placeholders(name, target));
            return Result.ok();
        }
        repository.removeFromBlacklist(name, target.uuid());
        view.render(hologram);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_UNBLACKLISTED, placeholders(name, target));
        return Result.ok();
    }

    private static Map<String, String> placeholders(HologramName name, PlayerRef target) {
        return Map.of("name", name.value(), "player", target.name());
    }
}

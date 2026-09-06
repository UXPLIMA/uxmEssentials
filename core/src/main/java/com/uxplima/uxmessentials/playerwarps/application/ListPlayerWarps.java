package com.uxplima.uxmessentials.playerwarps.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /pwarps [player]}: list player-warps. With no argument a player lists their own warps (public and
 * private, in creation order); with a player argument they list only that owner's <em>public</em> warps, so a
 * private warp never leaks to another player. The visible warps are returned for the adapter while the
 * header / per-entry / empty feedback is pushed through the notifier so all text resolves from
 * {@link PlayerwarpsMessageKey}.
 *
 * <p>The own-list filter is exposed side-effect-free as {@link #ownedList(PlayerRef)} for symmetry with the
 * server warps list; {@link #own} delegates to it and then notifies.
 */
public final class ListPlayerWarps {

    private final PlayerWarpRepository repository;
    private final Notifier notifier;

    public ListPlayerWarps(PlayerWarpRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** The warps {@code viewer} owns, in stored creation order, with no side effect. */
    public List<PlayerWarp> ownedList(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return repository.ownedBy(viewer);
    }

    /**
     * Resolve the warp {@code name} by its global name and push a one-line summary. Owner, access, price, visits,
     * and average rating, to {@code viewer}, or the not-found notice when no warp bears the name. This is a pure
     * read (the {@code /pwarp info} verb): it never mutates the warp, and the returned optional lets a caller reuse
     * the resolved aggregate without a second lookup.
     */
    public Optional<PlayerWarp> info(PlayerRef viewer, PlayerWarpName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(viewer, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return found;
        }
        notifier.send(viewer, PlayerwarpsMessageKey.PWARP_INFO, summary(found.get()));
        return found;
    }

    /** The placeholder set the {@code pwarp.info} line renders: every value is a plain, pre-formatted string. */
    private static Map<String, String> summary(PlayerWarp warp) {
        return Map.of(
                "warp", warp.name().value(),
                "owner", warp.ownerName(),
                "access", warp.access().name(),
                "price", warp.price().amount().toPlainString(),
                "visits", Long.toString(warp.visits().count()),
                "rating", averageStars(warp.ratings()));
    }

    /** The average star rating rounded to one decimal, formatted without floating-point noise. */
    private static String averageStars(RatingSummary ratings) {
        return new BigDecimal(Double.toString(ratings.average()))
                .setScale(1, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** The warps {@code viewer} owns, also pushing the header/entries (or the empty notice) to them. */
    public List<PlayerWarp> own(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<PlayerWarp> owned = ownedList(viewer);
        if (owned.isEmpty()) {
            notifier.send(viewer, PlayerwarpsMessageKey.PWARP_LIST_EMPTY);
            return owned;
        }
        notifier.send(viewer, PlayerwarpsMessageKey.PWARP_LIST_HEADER, Map.of("count", Integer.toString(owned.size())));
        for (PlayerWarp warp : owned) {
            notifier.send(
                    viewer,
                    PlayerwarpsMessageKey.PWARP_LIST_ENTRY,
                    Map.of("warp", warp.name().value()));
        }
        return owned;
    }

    /**
     * The public warps {@code owner} owns, rendered to {@code viewer} under the other-owner header (or the
     * other-owner empty notice). {@code ownerName} is the owner's display name shown in the header.
     */
    public List<PlayerWarp> publicOf(PlayerRef viewer, PlayerRef owner, String ownerName) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerName, "ownerName");
        List<PlayerWarp> shown = repository.publicOwnedBy(owner);
        if (shown.isEmpty()) {
            notifier.send(viewer, PlayerwarpsMessageKey.PWARP_LIST_OTHER_EMPTY, Map.of("player", ownerName));
            return shown;
        }
        notifier.send(
                viewer,
                PlayerwarpsMessageKey.PWARP_LIST_OTHER_HEADER,
                Map.of("player", ownerName, "count", Integer.toString(shown.size())));
        for (PlayerWarp warp : shown) {
            notifier.send(
                    viewer,
                    PlayerwarpsMessageKey.PWARP_LIST_OTHER_ENTRY,
                    Map.of("warp", warp.name().value(), "player", ownerName));
        }
        return shown;
    }
}

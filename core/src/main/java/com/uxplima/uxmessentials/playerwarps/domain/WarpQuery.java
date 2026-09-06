package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The immutable description of one browse request: which warps to include, how to order them, and which page to
 * return. Every filter is optional, an absent filter widens the result, while {@code onlyActive}, the
 * {@link WarpSort}, the page coordinates, and the {@code viewer} are always present.
 *
 * <p>This query only <em>describes</em> the listing; it does not enforce access policy. The read-model applies
 * exactly the filters it is handed and never adds a hidden access check, because a card appearing in a browse
 * never grants access: the teleport gate re-checks the real warp when a player picks one. The menu is therefore
 * responsible for passing safe filters; {@link #publicBrowse} is the safe default it should start from.
 *
 * @param category include only warps in this category id
 * @param access include only warps with this access mode
 * @param server include only warps on this server id
 * @param owner include only warps owned by this player
 * @param search include only warps whose name or display name contains this text (case-insensitive)
 * @param favouritesOf include only warps this player has favourited
 * @param onlyActive when true, exclude suspended and archived warps
 * @param sort the ordering to apply
 * @param page the zero-based page index to return
 * @param pageSize how many cards a page holds (1..100)
 * @param viewer the player viewing the browse. Drives the {@code viewerFavourited} flag on each card
 * @param viewerPosition the viewer's location, used only by {@link WarpSort#DISTANCE}
 */
public record WarpQuery(
        Optional<String> category,
        Optional<WarpAccess> access,
        Optional<String> server,
        Optional<UUID> owner,
        Optional<String> search,
        Optional<UUID> favouritesOf,
        boolean onlyActive,
        WarpSort sort,
        int page,
        int pageSize,
        UUID viewer,
        Optional<Position> viewerPosition) {

    /** The largest page a single browse read may request, a guard against an unbounded fetch dressed as one page. */
    public static final int MAX_PAGE_SIZE = 100;

    public WarpQuery {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(favouritesOf, "favouritesOf");
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerPosition, "viewerPosition");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be in 1.." + MAX_PAGE_SIZE + ": " + pageSize);
        }
    }

    /**
     * The safe default public browse: only {@link WarpStatus#ACTIVE active}, {@link WarpAccess#PUBLIC public}
     * warps, no other filter, ordered by {@code sort}. This is the query the menu opens with, a private,
     * suspended, or archived warp can never leak through it.
     */
    public static WarpQuery publicBrowse(UUID viewer, WarpSort sort, int page, int pageSize) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(sort, "sort");
        return new WarpQuery(
                Optional.empty(),
                Optional.of(WarpAccess.PUBLIC),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                sort,
                page,
                pageSize,
                viewer,
                Optional.empty());
    }
}

package com.uxplima.uxmessentials.warps.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;

/**
 * {@code /warps}: list the warps a player may use. The list is filtered to the warps whose per-warp
 * permission node ({@code uxmessentials.warp.use.<warp>}) and optional extra permission the player holds,
 * so a player never sees a warp they cannot teleport to. The visible warps (in creation order) are returned
 * for the adapter to render as a clickable MiniMessage list; the header / per-entry / empty feedback is
 * pushed through the notifier so all text resolves from {@link WarpsMessageKey}.
 *
 * <p>The same filter is exposed side-effect-free as {@link #available(PlayerRef)} so the {@code /warps} browse
 * menu can list exactly the warps the chat list shows without re-sending the chat feedback; {@link #list} now
 * delegates to it and then notifies.
 */
public final class ListWarps {

    private final WarpRepository repository;
    private final Permissions permissions;
    private final Notifier notifier;

    public ListWarps(WarpRepository repository, Permissions permissions, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * The warps {@code viewer} may use, in stored creation order, with no side effect, a warp whose per-warp
     * permission they lack or whose optional extra permission they do not hold is dropped. This is the same
     * filter the chat {@link #list} renders; the {@code /warps} browse menu calls this directly so the GUI and
     * the text list never disagree on what a player may warp to.
     */
    public List<Warp> available(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return repository.all().stream().filter(warp -> canUse(viewer, warp)).toList();
    }

    /** The warps {@code viewer} may use, also pushing the header/entries (or the empty notice) to them. */
    public List<Warp> list(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<Warp> visible = available(viewer);
        if (visible.isEmpty()) {
            notifier.send(viewer, WarpsMessageKey.WARP_LIST_EMPTY);
            return visible;
        }
        notifier.send(viewer, WarpsMessageKey.WARP_LIST_HEADER, Map.of("count", Integer.toString(visible.size())));
        for (Warp warp : visible) {
            notifier.send(
                    viewer,
                    WarpsMessageKey.WARP_LIST_ENTRY,
                    Map.of("warp", warp.name().value()));
        }
        return visible;
    }

    private boolean canUse(PlayerRef viewer, Warp warp) {
        if (!permissions.has(viewer, warp.name().useNode())) {
            return false;
        }
        return warp.requiredPermission()
                .map(node -> permissions.has(viewer, node))
                .orElse(true);
    }
}

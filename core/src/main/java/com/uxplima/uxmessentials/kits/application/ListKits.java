package com.uxplima.uxmessentials.kits.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /kit list}: list the kits a player may claim. The list is filtered to the kits the player can actually
 * obtain. Those whose per-kit permission they hold (a kit not gated behind a permission is always shown) and
 * which they have not already consumed if the kit is one-time. A one-time kit the player has claimed is
 * omitted entirely (§15.5), so the list never advertises a kit the player can no longer take.
 *
 * <p>The visible kits (in catalog order) are returned for the adapter to render as a clickable MiniMessage
 * list; the header / per-entry / empty feedback is pushed through the notifier so all text resolves from
 * {@link KitsMessageKey}. Cooldown state does <em>not</em> hide a kit. A kit on cooldown is still shown
 * (the player will claim it when it is ready); only a consumed one-time kit drops out.
 */
public final class ListKits {

    private final KitRepository repository;
    private final Permissions permissions;
    private final KitClaimStore claims;
    private final Notifier notifier;

    public ListKits(KitRepository repository, Permissions permissions, KitClaimStore claims, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * The kits {@code viewer} may claim, in catalog order, with no side effect. A one-time kit they have
     * already consumed and a permission-gated kit they lack the node for are dropped. This is the same filter
     * the chat {@link #list} renders; the {@code /kit list} browse menu calls this directly so the GUI and the
     * text list never disagree on what a player may take.
     */
    public List<KitDefinition> available(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return repository.all().stream().filter(kit -> claimable(viewer, kit)).toList();
    }

    /** The kits {@code viewer} may claim, also pushing the header/entries (or the empty notice) to them. */
    public List<KitDefinition> list(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<KitDefinition> visible = available(viewer);
        if (visible.isEmpty()) {
            notifier.send(viewer, KitsMessageKey.KIT_LIST_EMPTY);
            return visible;
        }
        notifier.send(viewer, KitsMessageKey.KIT_LIST_HEADER, Map.of("count", Integer.toString(visible.size())));
        for (KitDefinition kit : visible) {
            notifier.send(
                    viewer,
                    KitsMessageKey.KIT_LIST_ENTRY,
                    Map.of("kit", kit.id().value()));
        }
        return visible;
    }

    private boolean claimable(PlayerRef viewer, KitDefinition kit) {
        if (kit.requiresPermission() && !permissions.has(viewer, kit.permissionNode())) {
            return false;
        }
        return !(kit.isOneTime() && claims.hasClaimed(viewer, kit.id()));
    }
}

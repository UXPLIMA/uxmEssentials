package com.uxplima.uxmessentials.worlds.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.PortalDestination;
import com.uxplima.uxmessentials.worlds.domain.PortalKind;
import com.uxplima.uxmessentials.worlds.domain.PortalScaling;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;

/**
 * Resolves the exit a player should arrive at when stepping through a nether or end portal in a given source
 * world. The destination world is whatever the source world's per-kind link property names; the horizontal
 * coordinates are scaled by the vanilla rule between the two environments and the vertical coordinate is carried
 * across unchanged. Resolution yields nothing, and the caller falls back to vanilla behaviour, whenever the
 * source is unmanaged, the link is unset, the linked world is unregistered, or the link is syntactically invalid.
 */
public final class ResolvePortalDestination {

    private final WorldRepository repository;

    public ResolvePortalDestination(WorldRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<PortalDestination> resolve(WorldName source, PortalKind kind, double x, double y, double z) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(kind, "kind");
        Optional<ManagedWorld> from = repository.find(source);
        if (from.isEmpty()) {
            return Optional.empty();
        }
        String raw = from.get().settings().get(linkProperty(kind));
        if (raw.isBlank()) {
            return Optional.empty();
        }
        WorldName target;
        try {
            target = WorldName.of(raw);
        } catch (IllegalArgumentException badName) {
            return Optional.empty();
        }
        Optional<ManagedWorld> to = repository.find(target);
        if (to.isEmpty()) {
            return Optional.empty();
        }
        double s = PortalScaling.scale(
                from.get().spec().environment(), to.get().spec().environment());
        return Optional.of(new PortalDestination(target, x * s, y, z * s));
    }

    private static WorldProperty<String> linkProperty(PortalKind kind) {
        return kind == PortalKind.NETHER ? WorldProperties.PORTAL_NETHER_LINK : WorldProperties.PORTAL_END_LINK;
    }
}

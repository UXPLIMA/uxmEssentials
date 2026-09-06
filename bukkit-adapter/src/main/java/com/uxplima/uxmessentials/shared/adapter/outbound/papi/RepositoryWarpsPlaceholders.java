package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.domain.Warp;
import org.jspecify.annotations.NullMarked;

/**
 * {@link WarpsPlaceholders} over the warps context's {@link ListWarps} access filter. Every read goes through
 * the same {@link ListWarps#available(PlayerRef)} filter the {@code /warps} list and browse menu render, the
 * warps whose per-warp permission (and optional extra permission) the viewer holds, so a placeholder never
 * surfaces a warp the player cannot reach. The accessible set is small (server warps are operator-curated), so
 * filtering it on the placeholder path is cheap.
 */
@NullMarked
public final class RepositoryWarpsPlaceholders implements WarpsPlaceholders {

    private final ListWarps listWarps;

    public RepositoryWarpsPlaceholders(ListWarps listWarps) {
        this.listWarps = Objects.requireNonNull(listWarps, "listWarps");
    }

    @Override
    public int count(PlayerRef who) {
        return listWarps.available(Objects.requireNonNull(who, "who")).size();
    }

    @Override
    public List<String> accessibleNames(PlayerRef who) {
        return listWarps.available(Objects.requireNonNull(who, "who")).stream()
                .map(warp -> warp.name().value())
                .toList();
    }

    @Override
    public Optional<WarpView> find(PlayerRef who, String name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        return listWarps.available(who).stream()
                .filter(warp -> warp.name().value().equalsIgnoreCase(name))
                .findFirst()
                .map(RepositoryWarpsPlaceholders::view);
    }

    private static WarpView view(Warp warp) {
        Position position = warp.location();
        return new WarpView(
                position.world().name(),
                position.blockX(),
                position.blockY(),
                position.blockZ(),
                warp.visitors(),
                warp.owner().name(),
                warp.cost().amount());
    }
}

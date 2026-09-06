package com.uxplima.uxmessentials.homes.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListMenu;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeApiWrites;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.InviteToHome;
import com.uxplima.uxmessentials.homes.application.UninviteFromHome;
import com.uxplima.uxmessentials.homes.application.VisitHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed homes use cases and views the Brigadier commands share, built once per module start by
 * {@code HomesWiring} from the kernel ports, the jOOQ repository, and the teleport-delegating teleporter.
 * {@code /home} opens the slot grid; {@code /homeadmin} drives the admin use case; {@code /visit},
 * {@code /invite}, and {@code /uninvite} drive the public/visit use cases against another player resolved
 * through {@link PlayerLookup}. The grid and its child menus own every other home operation, so the only
 * per-command use cases held here are admin plus the visit/invite trio. The homes context keeps no other
 * adapter-side runtime state.
 *
 * @param homeList the {@code /home} slot grid the player opens
 * @param homeAdmin the {@code /homeadmin} management use case
 * @param visitHome the {@code /visit} use case
 * @param inviteToHome the {@code /invite} use case
 * @param uninviteFromHome the {@code /uninvite} use case
 * @param players name → ref resolution for the admin form and the visit/invite targets (offline-capable)
 * @param repository the home store, held only so the PlaceholderAPI seam can read it without blocking
 * @param apiWrites the free-of-charge use cases the published developer API runs
 */
@NullMarked
public record HomeServices(
        HomeListMenu homeList,
        HomeAdmin homeAdmin,
        VisitHome visitHome,
        InviteToHome inviteToHome,
        UninviteFromHome uninviteFromHome,
        PlayerLookup players,
        HomeRepository repository,
        HomeApiWrites apiWrites) {

    public HomeServices {
        Objects.requireNonNull(homeList, "homeList");
        Objects.requireNonNull(homeAdmin, "homeAdmin");
        Objects.requireNonNull(visitHome, "visitHome");
        Objects.requireNonNull(inviteToHome, "inviteToHome");
        Objects.requireNonNull(uninviteFromHome, "uninviteFromHome");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(apiWrites, "apiWrites");
    }
}

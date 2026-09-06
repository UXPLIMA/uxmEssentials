package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;
import java.util.Set;

/**
 * The warp-name tokens the {@code /pwarp} subcommand tree claims as literals. A warp named {@code set},
 * {@code admin}, or {@code rate} would be shadowed by its verb literal and become unreachable through
 * {@code /pwarp <name>}, so these names are refused at every point a name is created, the {@code /setpwarp}
 * command, a rename, and any importer, not merely documented.
 *
 * <p>The set is a canonical, lowercase superset of the current subcommand literals: it also holds the verbs a
 * menu or a future subcommand is likely to add ({@code create}, {@code delete}, {@code search}, {@code near},
 * {@code manage}, {@code sponsor}), so reserving them now keeps a later addition from silently colliding with an
 * existing warp. A {@code bukkit-adapter} drift guard pins the other direction, every literal the command builder
 * registers must be present here, so the two can never drift apart.
 */
public final class ReservedWarpNames {

    private static final Set<String> RESERVED = Set.of(
            "set",
            "create",
            "delete",
            "del",
            "rename",
            "displayname",
            "description",
            "icon",
            "category",
            "access",
            "password",
            "price",
            "move",
            "info",
            "list",
            "search",
            "near",
            "favourite",
            "unfavourite",
            "rate",
            "manage",
            "members",
            "ban",
            "unban",
            "whitelist",
            "transfer",
            "sponsor",
            "withdraw",
            "admin",
            "restore",
            "purge",
            "setowner",
            "addwarps",
            "reload",
            "visibility",
            "edit");

    private ReservedWarpNames() {}

    /** Whether {@code name} collides with a {@code /pwarp} verb literal and so may not be used as a warp name. */
    public static boolean isReserved(PlayerWarpName name) {
        Objects.requireNonNull(name, "name");
        return RESERVED.contains(name.value());
    }

    /** The reserved token set, unmodifiable, for the drift guard to check the command literals against. */
    public static Set<String> tokens() {
        return RESERVED;
    }
}

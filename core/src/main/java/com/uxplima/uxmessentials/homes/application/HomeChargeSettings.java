package com.uxplima.uxmessentials.homes.application;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeCost;

/**
 * The per-action cost configuration for the homes context, bundled into one record so the config layer
 * constructs it once on enable (swapped atomically on reload) and the application layer reads it through
 * {@link #forKind(HomeChargeKind)} without spreading cost-lookup logic across use cases.
 *
 * @param create cost charged when creating a new home slot
 * @param relocate cost charged when relocating an existing home to a new position
 * @param teleport cost charged each time a player teleports to a home
 */
public record HomeChargeSettings(HomeCost create, HomeCost relocate, HomeCost teleport) {

    public HomeChargeSettings {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(relocate, "relocate");
        Objects.requireNonNull(teleport, "teleport");
    }

    /** Returns the cost for {@code kind}. */
    public HomeCost forKind(HomeChargeKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case CREATE -> create;
            case RELOCATE -> relocate;
            case TELEPORT -> teleport;
        };
    }

    /** A settings instance with all actions free: useful when economy is disabled or not yet wired. */
    public static HomeChargeSettings allFree() {
        HomeCost free = HomeCost.free();
        return new HomeChargeSettings(free, free, free);
    }
}

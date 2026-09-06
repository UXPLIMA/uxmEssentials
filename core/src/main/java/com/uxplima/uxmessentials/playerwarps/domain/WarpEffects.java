package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The optional sound and particle flourishes a warp plays on departure and arrival, grouped so the aggregate
 * carries one {@code WarpEffects} field instead of four loose {@link Optional}s. Each token is left opaque to the
 * domain, a sound or particle name the presentation adapter resolves, so adding a new effect scheme later
 * touches the adapter alone. An absent effect is an empty {@link Optional}, never a blank string, keeping
 * "no effect" a single unambiguous state.
 *
 * @param departureSound the sound played where the player leaves from, if any
 * @param arrivalSound the sound played where the player lands, if any
 * @param departureParticle the particle shown at the departure point, if any
 * @param arrivalParticle the particle shown at the arrival point, if any
 */
public record WarpEffects(
        Optional<String> departureSound,
        Optional<String> arrivalSound,
        Optional<String> departureParticle,
        Optional<String> arrivalParticle) {

    public WarpEffects {
        Objects.requireNonNull(departureSound, "departureSound");
        Objects.requireNonNull(arrivalSound, "arrivalSound");
        Objects.requireNonNull(departureParticle, "departureParticle");
        Objects.requireNonNull(arrivalParticle, "arrivalParticle");
    }

    /** A warp with no effects configured, every flourish absent. */
    public static WarpEffects none() {
        return new WarpEffects(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}

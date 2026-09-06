package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A thin verb-keyed façade over {@link EcoAdmin} the admin GUI views call, so a view dispatches a Give / Take /
 * Set with one {@link #dispatch} call rather than a switch of its own, and a test can capture the verb and the
 * {@link Money} a button raised without driving the whole use case. The single-target verbs route to the
 * matching {@code EcoAdmin} method; {@link #reset} and the bulk ops pass straight through. Every call assumes it
 * runs off the tick thread, the same contract {@code EcoAdmin} states, so the view hops first.
 */
@NullMarked
public final class EcoAdminOps {

    /** The single-target balance verbs an amount-anvil resolves to. */
    public enum Verb {
        GIVE,
        TAKE,
        SET
    }

    private final EcoAdmin ecoAdmin;

    public EcoAdminOps(EcoAdmin ecoAdmin) {
        this.ecoAdmin = Objects.requireNonNull(ecoAdmin, "ecoAdmin");
    }

    /** Run {@code verb} crediting/debiting/setting {@code target} by {@code amount} as {@code actor}. */
    public void dispatch(Verb verb, PlayerRef actor, PlayerRef target, Money amount) {
        Objects.requireNonNull(verb, "verb");
        switch (verb) {
            case GIVE -> ecoAdmin.give(actor, target, amount);
            case TAKE -> ecoAdmin.take(actor, target, amount);
            case SET -> ecoAdmin.set(actor, target, amount);
        }
    }

    /** Zero {@code target}'s {@code currency} balance to its starting figure. */
    public void reset(PlayerRef actor, PlayerRef target, Currency currency) {
        ecoAdmin.reset(actor, target, currency);
    }

    /** Credit every wallet in {@code targets} by {@code amount}. */
    public void giveAll(PlayerRef actor, List<PlayerRef> targets, Money amount) {
        ecoAdmin.giveAll(actor, targets, amount);
    }

    /** Reset every wallet in {@code targets} to {@code currency}'s starting figure. */
    public void resetAll(PlayerRef actor, List<PlayerRef> targets, Currency currency) {
        ecoAdmin.resetAll(actor, targets, currency);
    }
}

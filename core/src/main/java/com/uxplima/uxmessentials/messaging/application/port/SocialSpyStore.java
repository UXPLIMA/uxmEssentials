package com.uxplima.uxmessentials.messaging.application.port;

import java.util.List;
import java.util.Set;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the staff {@code /socialspy} toggle, which staff are currently observing other
 * players' private messages (audit-relevant). When a private message is delivered, every spying staff
 * member who is not a party to the conversation receives a spy line. The flag is per-holder session/PDC
 * state, dropped on relog by default; the store also enumerates the observers of a given conversation so
 * the send path can fan out without scanning every online player.
 *
 * <p>A spy is in one of three states. <strong>Not spying:</strong> no toggle on, no targets. <strong>ALL:
 * </strong> the global {@code /socialspy} flag is on, so they observe every private message. <strong>
 * Targeted:</strong> the global flag is off but one or more players are on their target set, so they observe
 * only conversations one of those players is a party to. The two are independent: a spy may hold the ALL flag
 * and a target set at once, and ALL wins (they see everything). {@link #observersOf} folds both states into
 * the set the send path delivers to.
 */
public interface SocialSpyStore {

    /** True when {@code who} currently has the global {@code /socialspy} (ALL) flag on. */
    boolean isSpying(PlayerRef who);

    /** Flip {@code who}'s global {@code /socialspy} (ALL) flag, returning the new "is spying all" value. */
    boolean toggle(PlayerRef who);

    /**
     * Add or remove {@code target} from {@code spy}'s target set, returning {@code true} when {@code spy} is
     * now watching {@code target} (added) and {@code false} when the target was removed. Watching a target
     * puts the spy in the targeted state; it is independent of the global ALL flag.
     */
    boolean toggleTarget(PlayerRef spy, PlayerRef target);

    /** Every staff member currently spying with the global ALL flag, for callers that need the ALL set. */
    List<PlayerRef> activeSpies();

    /**
     * The spies who should see a private message between {@code sender} and {@code target}: every ALL-flag
     * holder, plus every targeted spy whose target set contains {@code sender} or {@code target}. The result
     * resolves to currently-online observers only; an offline spy is skipped. A spy who is a party to the
     * conversation may still be returned here: the send path drops them when fanning out. Resolved to a
     * {@link PlayerRef} per observer (the send path's {@code deliverSpy} addresses each by ref), de-duplicated
     * by identity so a spy who is both an ALL holder and a target-matcher is delivered to once.
     */
    Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target);
}

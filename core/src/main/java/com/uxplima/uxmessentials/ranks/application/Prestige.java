package com.uxplima.uxmessentials.ranks.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.ranks.application.RanksConfig.PrestigeSettings;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerPrestiged;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /prestige}: a player at the very top of the ladder resets back to the first rank in exchange for a
 * permanent prestige level and its reward multiplier. It mirrors {@link Rankup}'s pipeline shape, eligibility →
 * requirements → charge → mutate → actions, so a refused prestige leaves no trace, and reuses the same
 * requirement evaluator, action runner and economy seam a rankup goes through. This class holds no Bukkit type
 * and sends no message; it returns a typed {@link PrestigeResult} the command boundary renders.
 *
 * <h2>Eligibility and ordering</h2>
 * <ol>
 *   <li><b>At the top rank.</b> The current standing (via {@link CurrentRank}) must resolve to a rank with no
 *       {@link RankLadder#next(com.uxplima.uxmessentials.ranks.domain.RankId) next} rung, the top. Anything else
 *       (including an empty ladder) yields {@link PrestigeResult#notAtTop()}.</li>
 *   <li><b>Below the cap.</b> The player's prestige level must be
 *       {@link com.uxplima.uxmessentials.ranks.domain.Prestige#belowCap(int) below} the configured
 *       {@code max-level}; at the cap it yields {@link PrestigeResult#maxLevel()}.</li>
 *   <li><b>Requirements.</b> The configured prestige requirements are evaluated; a single failure yields
 *       {@link PrestigeResult#requirementsNotMet()} with nothing charged and the level unmoved.</li>
 *   <li><b>Charge.</b> Only once eligible and past the requirements is the prestige cost charged through the
 *       {@link RankEconomy} seam. A refused debit yields {@link PrestigeResult#cannotAfford()}; with no economy
 *       provider wired, or a zero cost, the charge is skipped and the prestige is free.</li>
 *   <li><b>Mutate.</b> The pointer is reset to {@link RankLadder#first()} and the prestige level incremented in
 *       one {@code save}.</li>
 *   <li><b>Actions.</b> The configured prestige actions run last, only after the reset is durable.</li>
 * </ol>
 * Charging strictly before the reset is what guarantees a failed charge never prestiges, the same rollback stance
 * (and the same residual save-fault gap) {@link Rankup} documents.
 */
public final class Prestige {

    /** Prestige costs are charged in the economy's default currency, exactly as a rank's rankup cost is. */
    private static final String DEFAULT_CURRENCY = "default";

    private final CurrentRank currentRank;
    private final PlayerRankRepository repository;
    private final RankLadder ladder;
    private final RankRequirementEvaluator requirements;
    private final RankActionRunner actions;
    private final Optional<RankEconomy> economy;
    private final PrestigeSettings settings;
    private final DomainEventPublisher events;

    public Prestige(
            CurrentRank currentRank,
            PlayerRankRepository repository,
            RankLadder ladder,
            RankRequirementEvaluator requirements,
            RankActionRunner actions,
            Optional<RankEconomy> economy,
            PrestigeSettings settings,
            DomainEventPublisher events) {
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Attempt to prestige {@code who}, resetting them to the first rank, and return the typed outcome. */
    public PrestigeResult prestige(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Optional<RankStanding> standing = currentRank.of(who.uuid());
        if (standing.isEmpty() || ladder.next(standing.get().rank().id()).isPresent()) {
            return PrestigeResult.notAtTop();
        }
        return advance(who, standing.get());
    }

    private PrestigeResult advance(PlayerRef who, RankStanding standing) {
        if (!standing.prestige().belowCap(settings.maxLevel())) {
            return PrestigeResult.maxLevel();
        }
        if (!requirements.passesAll(who, parseRequirements())) {
            return PrestigeResult.requirementsNotMet();
        }
        if (!charge(who)) {
            return PrestigeResult.cannotAfford();
        }
        return reset(who, standing);
    }

    /** Reset the pointer to the first rank at the next prestige level and run the configured prestige actions. */
    private PrestigeResult reset(PlayerRef who, RankStanding standing) {
        Rank first = ladder.first().orElseThrow(); // non-empty: the standing already resolved a ladder rank
        var next = standing.prestige().increment();
        repository.save(who.uuid(), first.id(), next);
        actions.run(who, settings.actions());
        double multiplier = next.rewardMultiplier(settings.rewardMultiplier());
        events.publish(new PlayerPrestiged(who, next.level(), multiplier));
        return PrestigeResult.prestiged(next, multiplier);
    }

    /** Charge the prestige cost, or pass through free when the cost is zero or no economy provider is wired. */
    private boolean charge(PlayerRef who) {
        if (settings.cost() <= 0L || economy.isEmpty()) {
            return true;
        }
        return economy.get().withdraw(who, BigDecimal.valueOf(settings.cost()), DEFAULT_CURRENCY);
    }

    /** Parse the prestige requirement lines, dropping any the codec cannot recognise (not enforced). */
    private List<RankRequirement> parseRequirements() {
        return settings.requirements().stream()
                .map(RankRequirement::parse)
                .flatMap(Optional::stream)
                .toList();
    }
}

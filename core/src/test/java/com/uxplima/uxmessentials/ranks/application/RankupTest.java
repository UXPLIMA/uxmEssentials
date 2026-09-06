package com.uxplima.uxmessentials.ranks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.RankupResult.Status;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerRankedUp;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link Rankup}'s ordering, requirements → charge → advance → actions, and its four terminal outcomes: a
 * met rankup advances the pointer, charges the cost and fires the actions; a failed requirement and a short
 * balance both refuse without advancing, charging or acting; and a player at the top rank has nothing to advance
 * to. The economy, requirement evaluator and action runner are fakes, so the decision logic is exercised in pure
 * isolation from Bukkit, PlaceholderAPI and the database.
 */
class RankupTest {

    private static final Rank FIRST = new Rank(RankId.of("first"), 10, "First", 0L, List.of(), List.of());
    private static final Rank CITIZEN =
            new Rank(RankId.of("citizen"), 20, "Citizen", 1000L, List.of("money 1000"), List.of("message hello"));
    private static final Rank VIP = new Rank(RankId.of("vip"), 30, "VIP", 5000L, List.of(), List.of());
    private static final RankLadder LADDER = RankLadder.of(List.of(FIRST, CITIZEN, VIP));
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Ada");

    private final RecordingEvents events = new RecordingEvents();

    @Test
    void advancesChargesAndFiresActionsWhenRequirementsAndCostAreMet() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("first"), new Prestige(2))));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();
        Rankup rankup = rankup(repo, allowAll(), actions, Optional.of(economy));

        RankupResult result = rankup.rankUp(WHO);

        assertThat(result.status()).isEqualTo(Status.RANKED_UP);
        assertThat(result.newRank()).contains(CITIZEN);
        assertThat(repo.saved).containsExactly(new Saved(RankId.of("citizen"), new Prestige(2)));
        assertThat(economy.withdrawn).containsExactly(BigDecimal.valueOf(1000L));
        assertThat(actions.ran).containsExactly(CITIZEN.actions());
    }

    @Test
    void publishesTheMoveWithBothEndsOfItWhenARankupLands() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("first"), new Prestige(0))));
        Rankup rankup = rankup(repo, allowAll(), new FakeActionRunner(), Optional.empty());

        rankup.rankUp(WHO);

        assertThat(events.published).containsExactly(new PlayerRankedUp(WHO, RankId.of("first"), RankId.of("citizen")));
    }

    @Test
    void publishesNothingWhenTheRankupIsRefused() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("first"), new Prestige(0))));
        Rankup rankup = rankup(repo, denyAll(), new FakeActionRunner(), Optional.empty());

        rankup.rankUp(WHO);

        assertThat(events.published).isEmpty();
    }

    @Test
    void rankingUpIsFreeAndStillAdvancesWhenNoEconomyIsWired() {
        FakeRepository repo = new FakeRepository(Optional.empty());
        FakeActionRunner actions = new FakeActionRunner();
        Rankup rankup = rankup(repo, allowAll(), actions, Optional.empty());

        RankupResult result = rankup.rankUp(WHO);

        assertThat(result.status()).isEqualTo(Status.RANKED_UP);
        assertThat(repo.saved).containsExactly(new Saved(RankId.of("citizen"), Prestige.INITIAL));
        assertThat(actions.ran).containsExactly(CITIZEN.actions());
    }

    @Test
    void autorankChargesTheCostWhenTheChargeFlagIsSet() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("first"), Prestige.INITIAL)));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();
        Rankup rankup = rankup(repo, allowAll(), actions, Optional.of(economy));

        RankupResult result = rankup.rankUp(WHO, true);

        assertThat(result.status()).isEqualTo(Status.RANKED_UP);
        assertThat(repo.saved).containsExactly(new Saved(RankId.of("citizen"), Prestige.INITIAL));
        assertThat(economy.withdrawn).containsExactly(BigDecimal.valueOf(1000L));
        assertThat(actions.ran).containsExactly(CITIZEN.actions());
    }

    @Test
    void autorankPromotesForFreeWithoutChargingWhenTheChargeFlagIsClear() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("first"), Prestige.INITIAL)));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();
        Rankup rankup = rankup(repo, allowAll(), actions, Optional.of(economy));

        RankupResult result = rankup.rankUp(WHO, false);

        assertThat(result.status()).isEqualTo(Status.RANKED_UP);
        assertThat(repo.saved).containsExactly(new Saved(RankId.of("citizen"), Prestige.INITIAL));
        assertThat(economy.withdrawn).isEmpty();
        assertThat(actions.ran).containsExactly(CITIZEN.actions());
    }

    @Test
    void refusesWithoutAdvancingOrChargingWhenARequirementFails() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("first"), Prestige.INITIAL)));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();
        Rankup rankup = rankup(repo, denyAll(), actions, Optional.of(economy));

        RankupResult result = rankup.rankUp(WHO);

        assertThat(result.status()).isEqualTo(Status.REQUIREMENTS_NOT_MET);
        assertThat(repo.saved).isEmpty();
        assertThat(economy.withdrawn).isEmpty();
        assertThat(actions.ran).isEmpty();
    }

    @Test
    void refusesWithoutAdvancingOrActingWhenTheBalanceIsShort() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("first"), Prestige.INITIAL)));
        FakeEconomy economy = new FakeEconomy(false);
        FakeActionRunner actions = new FakeActionRunner();
        Rankup rankup = rankup(repo, allowAll(), actions, Optional.of(economy));

        RankupResult result = rankup.rankUp(WHO);

        assertThat(result.status()).isEqualTo(Status.CANNOT_AFFORD);
        assertThat(repo.saved).isEmpty();
        assertThat(actions.ran).isEmpty();
    }

    @Test
    void yieldsAlreadyMaxWhenThePlayerIsAtTheTopRank() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("vip"), new Prestige(1))));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();
        Rankup rankup = rankup(repo, allowAll(), actions, Optional.of(economy));

        RankupResult result = rankup.rankUp(WHO);

        assertThat(result.status()).isEqualTo(Status.ALREADY_MAX);
        assertThat(repo.saved).isEmpty();
        assertThat(economy.withdrawn).isEmpty();
        assertThat(actions.ran).isEmpty();
    }

    private Rankup rankup(
            FakeRepository repo,
            RankRequirementEvaluator requirements,
            FakeActionRunner actions,
            Optional<RankEconomy> economy) {
        return new Rankup(new CurrentRank(repo, LADDER), repo, LADDER, requirements, actions, economy, events);
    }

    /** Collects what the use case published, so a test can assert on the fact and not only on the return value. */
    private static final class RecordingEvents implements DomainEventPublisher {

        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static RankRequirementEvaluator allowAll() {
        return (who, requirement) -> true;
    }

    private static RankRequirementEvaluator denyAll() {
        return (who, requirement) -> false;
    }

    /** A repository that returns a fixed stored pointer and records every save the use case issues. */
    private static final class FakeRepository implements PlayerRankRepository {
        private final Optional<PlayerRank> stored;
        private final List<Saved> saved = new ArrayList<>();

        FakeRepository(Optional<PlayerRank> stored) {
            this.stored = stored;
        }

        @Override
        public Optional<PlayerRank> find(UUID playerId) {
            return stored;
        }

        @Override
        public void save(UUID playerId, RankId rankId, Prestige prestige) {
            saved.add(new Saved(rankId, prestige));
        }
    }

    /** A charge that succeeds or fails per its flag, recording every amount it was asked to withdraw. */
    private static final class FakeEconomy implements RankEconomy {
        private final boolean affordable;
        private final List<BigDecimal> withdrawn = new ArrayList<>();

        FakeEconomy(boolean affordable) {
            this.affordable = affordable;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return affordable;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (!affordable) {
                return false;
            }
            withdrawn.add(amount);
            return true;
        }
    }

    /** Records each list of action lines it is handed, so the test can assert the actions fired on a rankup. */
    private static final class FakeActionRunner implements RankActionRunner {
        private final List<List<String>> ran = new ArrayList<>();

        @Override
        public void run(PlayerRef who, List<String> actionLines) {
            ran.add(List.copyOf(actionLines));
        }
    }

    /** The captured shape of one {@link PlayerRankRepository#save} call. */
    private record Saved(RankId rankId, Prestige prestige) {}

    @Test
    void parsesTheMoneyRequirementFromTheExampleRank() {
        // Anchors the requirement grammar the codec hands the evaluator: "money 1000" is a MONEY condition.
        RankRequirement parsed = RankRequirement.parse("money 1000").orElseThrow();
        assertThat(parsed.value()).isEqualTo("1000");
    }
}

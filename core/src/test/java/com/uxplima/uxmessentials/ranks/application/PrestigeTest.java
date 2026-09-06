package com.uxplima.uxmessentials.ranks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.PrestigeResult.Status;
import com.uxplima.uxmessentials.ranks.application.RanksConfig.PrestigeSettings;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerPrestiged;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link com.uxplima.uxmessentials.ranks.application.Prestige} use case's eligibility and ordering. A
 * player at the very top rank prestiges: the pointer resets to the first rank, the level increments, the cost is
 * charged and the actions fire, and the earned reward multiplier is carried back. Off the top rank, at the level
 * cap, on a failed requirement or a short balance the attempt refuses atomically. Nothing charged, no reset, no
 * actions. The economy, requirement evaluator and action runner are fakes, so the decision logic is exercised in
 * pure isolation from Bukkit, PlaceholderAPI and the database. The domain {@code Prestige} value is imported
 * here, so the use case is referenced by its fully qualified name.
 */
class PrestigeTest {

    private static final Rank FIRST = new Rank(RankId.of("first"), 10, "First", 0L, List.of(), List.of());
    private static final Rank CITIZEN = new Rank(RankId.of("citizen"), 20, "Citizen", 0L, List.of(), List.of());
    private static final Rank VIP = new Rank(RankId.of("vip"), 30, "VIP", 0L, List.of(), List.of());
    private static final RankLadder LADDER = RankLadder.of(List.of(FIRST, CITIZEN, VIP));
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Ada");
    private static final List<String> ACTIONS = List.of("message you prestiged");

    private final RecordingEvents events = new RecordingEvents();

    @Test
    void resetsChargesAndFiresActionsWhenAtTheTopRankAndEligible() {
        FakeRepository repo = atTop(new Prestige(2));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();
        PrestigeSettings settings = settings(0, 1000L, List.of("money 1000"), 1.5);

        PrestigeResult result = prestige(repo, allowAll(), actions, Optional.of(economy), settings)
                .prestige(WHO);

        assertThat(result.status()).isEqualTo(Status.PRESTIGED);
        assertThat(result.newLevel()).isEqualTo(3);
        assertThat(result.rewardMultiplier()).isEqualTo(2.5);
        assertThat(repo.saved).containsExactly(new Saved(RankId.of("first"), new Prestige(3)));
        assertThat(economy.withdrawn).containsExactly(BigDecimal.valueOf(1000L));
        assertThat(actions.ran).containsExactly(ACTIONS);
    }

    @Test
    void publishesTheNewLevelAndItsEarnedMultiplierWhenAPrestigeLands() {
        FakeRepository repo = atTop(new Prestige(2));
        PrestigeSettings settings = settings(0, 0L, List.of(), 1.5);

        prestige(repo, allowAll(), new FakeActionRunner(), Optional.empty(), settings)
                .prestige(WHO);

        assertThat(events.published).containsExactly(new PlayerPrestiged(WHO, 3, 2.5));
    }

    @Test
    void publishesNothingWhenThePrestigeIsRefused() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("citizen"), new Prestige(1))));
        PrestigeSettings settings = settings(0, 0L, List.of(), 1.0);

        prestige(repo, allowAll(), new FakeActionRunner(), Optional.empty(), settings)
                .prestige(WHO);

        assertThat(events.published).isEmpty();
    }

    @Test
    void prestigeIsFreeAndStillResetsWhenNoEconomyIsWired() {
        FakeRepository repo = atTop(Prestige.INITIAL);
        FakeActionRunner actions = new FakeActionRunner();
        PrestigeSettings settings = settings(0, 1000L, List.of(), 1.0);

        PrestigeResult result =
                prestige(repo, allowAll(), actions, Optional.empty(), settings).prestige(WHO);

        assertThat(result.status()).isEqualTo(Status.PRESTIGED);
        assertThat(repo.saved).containsExactly(new Saved(RankId.of("first"), new Prestige(1)));
        assertThat(actions.ran).containsExactly(ACTIONS);
    }

    @Test
    void refusesAsNotAtTopWhenThePlayerIsBelowTheTopRank() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("citizen"), new Prestige(1))));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();

        PrestigeResult result = prestige(
                        repo, allowAll(), actions, Optional.of(economy), settings(0, 1000L, List.of(), 1.0))
                .prestige(WHO);

        assertThat(result.status()).isEqualTo(Status.NOT_AT_TOP);
        assertThat(repo.saved).isEmpty();
        assertThat(economy.withdrawn).isEmpty();
        assertThat(actions.ran).isEmpty();
    }

    @Test
    void refusesAtTheConfiguredMaxLevel() {
        FakeRepository repo = atTop(new Prestige(5));
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();

        PrestigeResult result = prestige(
                        repo, allowAll(), actions, Optional.of(economy), settings(5, 0L, List.of(), 1.0))
                .prestige(WHO);

        assertThat(result.status()).isEqualTo(Status.MAX_LEVEL);
        assertThat(repo.saved).isEmpty();
        assertThat(economy.withdrawn).isEmpty();
        assertThat(actions.ran).isEmpty();
    }

    @Test
    void refusesWithoutResettingOrChargingWhenARequirementFails() {
        FakeRepository repo = atTop(Prestige.INITIAL);
        FakeEconomy economy = new FakeEconomy(true);
        FakeActionRunner actions = new FakeActionRunner();

        PrestigeResult result = prestige(
                        repo, denyAll(), actions, Optional.of(economy), settings(0, 1000L, List.of("money 1"), 1.0))
                .prestige(WHO);

        assertThat(result.status()).isEqualTo(Status.REQUIREMENTS_NOT_MET);
        assertThat(repo.saved).isEmpty();
        assertThat(economy.withdrawn).isEmpty();
        assertThat(actions.ran).isEmpty();
    }

    @Test
    void refusesWithoutResettingOrActingWhenTheBalanceIsShort() {
        FakeRepository repo = atTop(Prestige.INITIAL);
        FakeEconomy economy = new FakeEconomy(false);
        FakeActionRunner actions = new FakeActionRunner();

        PrestigeResult result = prestige(
                        repo, allowAll(), actions, Optional.of(economy), settings(0, 1000L, List.of(), 1.0))
                .prestige(WHO);

        assertThat(result.status()).isEqualTo(Status.CANNOT_AFFORD);
        assertThat(repo.saved).isEmpty();
        assertThat(actions.ran).isEmpty();
    }

    private static FakeRepository atTop(Prestige prestige) {
        return new FakeRepository(Optional.of(new PlayerRank(RankId.of("vip"), prestige)));
    }

    private static PrestigeSettings settings(int maxLevel, long cost, List<String> requirements, double multiplier) {
        return new PrestigeSettings(true, maxLevel, cost, requirements, ACTIONS, multiplier);
    }

    private com.uxplima.uxmessentials.ranks.application.Prestige prestige(
            FakeRepository repo,
            RankRequirementEvaluator requirements,
            FakeActionRunner actions,
            Optional<RankEconomy> economy,
            PrestigeSettings settings) {
        return new com.uxplima.uxmessentials.ranks.application.Prestige(
                new CurrentRank(repo, LADDER), repo, LADDER, requirements, actions, economy, settings, events);
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

    /** Records each list of action lines it is handed, so the test can assert the actions fired on a prestige. */
    private static final class FakeActionRunner implements RankActionRunner {
        private final List<List<String>> ran = new ArrayList<>();

        @Override
        public void run(PlayerRef who, List<String> actionLines) {
            ran.add(List.copyOf(actionLines));
        }
    }

    /** The captured shape of one {@link PlayerRankRepository#save} call. */
    private record Saved(RankId rankId, Prestige prestige) {}
}

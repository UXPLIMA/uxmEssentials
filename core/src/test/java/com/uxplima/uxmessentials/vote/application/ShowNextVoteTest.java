package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShowNextVoteTest {

    private static final Instant NOW = Instant.ofEpochSecond(2_000_000);
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private StubRepository repository;
    private CapturingNotifier notifier;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        notifier = new CapturingNotifier();
    }

    @Test
    void emptyCatalogSendsNoneMessage() {
        ShowNextVote use = new ShowNextVote(repository, VoteSiteCatalog.empty(), notifier.voteNotifier());
        use.show(ALICE, NOW);
        assertThat(notifier.keys).containsExactly(VoteMessageKey.VOTE_NEXT_NONE);
    }

    @Test
    void availableSiteSendsAvailableEntry() {
        VoteSiteCatalog catalog = catalogOf(site("TopVoter", Duration.ofHours(12)));
        // No last vote recorded → always votable.
        ShowNextVote use = new ShowNextVote(repository, catalog, notifier.voteNotifier());
        use.show(ALICE, NOW);

        assertThat(notifier.keys).contains(VoteMessageKey.VOTE_NEXT_HEADER, VoteMessageKey.VOTE_NEXT_AVAILABLE);
        assertThat(notifier.placeholderFor(VoteMessageKey.VOTE_NEXT_AVAILABLE, "site"))
                .isEqualTo("TopVoter");
    }

    @Test
    void siteOnCooldownSendsCooldownEntry() {
        VoteSiteCatalog catalog = catalogOf(site("TopVoter", Duration.ofHours(12)));
        // Voted 6 hours ago → still 6h remaining.
        repository.lastVote = Optional.of(NOW.minus(Duration.ofHours(6)));

        ShowNextVote use = new ShowNextVote(repository, catalog, notifier.voteNotifier());
        use.show(ALICE, NOW);

        assertThat(notifier.keys).contains(VoteMessageKey.VOTE_NEXT_HEADER, VoteMessageKey.VOTE_NEXT_ENTRY);
        assertThat(notifier.placeholderFor(VoteMessageKey.VOTE_NEXT_ENTRY, "time"))
                .isEqualTo("6h");
    }

    @Test
    void cooldownIsKeyedOnServiceWhileDisplayUsesName() {
        // Display name differs from the Votifier service key; the recorded cooldown is under the service.
        VoteSiteSpec spec = new VoteSiteSpec("PlanetMinecraft", "PMC", Optional.empty(), Duration.ofHours(12));
        VoteSiteCatalog catalog = new VoteSiteCatalog(List.of(spec));
        // Recorded under the SERVICE key "PMC", 6h ago: must be found and shown on cooldown.
        repository.lastVotesBySite = Map.of("PMC", NOW.minus(Duration.ofHours(6)));

        ShowNextVote use = new ShowNextVote(repository, catalog, notifier.voteNotifier());
        use.show(ALICE, NOW);

        assertThat(notifier.keys).contains(VoteMessageKey.VOTE_NEXT_ENTRY);
        // The display placeholder is the friendly name, not the service key.
        assertThat(notifier.placeholderFor(VoteMessageKey.VOTE_NEXT_ENTRY, "site"))
                .isEqualTo("PlanetMinecraft");
        assertThat(notifier.placeholderFor(VoteMessageKey.VOTE_NEXT_ENTRY, "time"))
                .isEqualTo("6h");
    }

    @Test
    void mixedSitesSendsBothEntryTypes() {
        VoteSiteCatalog catalog =
                catalogOf(site("ReadySite", Duration.ofHours(12)), site("CooldownSite", Duration.ofHours(12)));
        // CooldownSite was voted 2h ago.
        repository.lastVotesBySite = Map.of("CooldownSite", NOW.minus(Duration.ofHours(2)));

        ShowNextVote use = new ShowNextVote(repository, catalog, notifier.voteNotifier());
        use.show(ALICE, NOW);

        assertThat(notifier.keys).contains(VoteMessageKey.VOTE_NEXT_AVAILABLE, VoteMessageKey.VOTE_NEXT_ENTRY);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static VoteSiteSpec site(String name, Duration cooldown) {
        return new VoteSiteSpec(name, Optional.empty(), cooldown);
    }

    private static VoteSiteCatalog catalogOf(VoteSiteSpec... specs) {
        return new VoteSiteCatalog(List.of(specs));
    }

    /** Minimal fake repository: only per-site timestamp lookup matters here. */
    private static final class StubRepository extends FakeVoteRepositoryBase {
        Optional<Instant> lastVote = Optional.empty();
        Map<String, Instant> lastVotesBySite = Map.of();

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            if (lastVotesBySite.containsKey(site)) {
                return Optional.of(lastVotesBySite.get(site));
            }
            return lastVote;
        }
    }

    /** Captures keys and per-key placeholder maps from Notifier deliveries. */
    static final class CapturingNotifier {
        final List<VoteMessageKey> keys = new ArrayList<>();
        final List<Map<String, String>> placeholdersList = new ArrayList<>();

        Notifier voteNotifier() {
            Messages messages = (viewer, key, placeholders) -> {
                if (key instanceof VoteMessageKey vk) {
                    keys.add(vk);
                    placeholdersList.add(Map.copyOf(placeholders));
                }
                return key.key();
            };
            MessageSink sink = (viewer, text) -> {};
            return new Notifier(messages, sink);
        }

        @Nullable String placeholderFor(VoteMessageKey key, String placeholder) {
            for (int i = 0; i < keys.size(); i++) {
                if (keys.get(i) == key) {
                    return placeholdersList.get(i).get(placeholder);
                }
            }
            return null;
        }
    }
}

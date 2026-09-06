package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.NullMarked;

/**
 * {@link VotePlaceholders} over the vote context's read ports: the {@link VoteRepository} tally and
 * party counter. Built during vote wiring from the same repository the {@code HandleVote} use case
 * holds, so the placeholder counts match what the leaderboard and total commands display.
 *
 * <p>The threshold placeholders reflect the <em>effective</em> threshold. The stored override when
 * one is active, the configured base otherwise, so they stay accurate under escalation.
 *
 * <p>Indexed leaderboard placeholders ({@link #topAt}, {@link #positionOf}) fetch the top-{@value #LEADERBOARD_LIMIT}
 * rows per query. The result is small and bounded, so no extra cache is added at this layer; the
 * repository's own cache (when present) absorbs repeated calls.
 *
 * <p>The repository stores each ranked player's name as their UUID string (the persistence layer has no
 * profile table), so {@link #topAt} resolves the UUID to a display name through the supplied
 * {@code nameResolver}, the same lookup the {@code /vote top} command uses, before handing the row
 * back. Without this, a {@code votes_top_<period>_<n>_name} placeholder on a hologram or scoreboard
 * would render a raw UUID. The resolver falls back to the UUID string when the profile is unknown.
 */
@NullMarked
public final class RepositoryVotePlaceholders implements VotePlaceholders {

    /** The maximum number of rows fetched for indexed leaderboard placeholders. */
    private static final int LEADERBOARD_LIMIT = 100;

    private final VoteRepository repository;
    private final int baseThreshold;
    private final Function<UUID, String> nameResolver;

    public RepositoryVotePlaceholders(
            VoteRepository repository, int baseThreshold, Function<UUID, String> nameResolver) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (baseThreshold < 1) {
            throw new IllegalArgumentException("baseThreshold must be at least one: " + baseThreshold);
        }
        this.baseThreshold = baseThreshold;
        this.nameResolver = Objects.requireNonNull(nameResolver, "nameResolver");
    }

    @Override
    public long countFor(PlayerRef who, VotePeriod period) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(period, "period");
        return repository.totalsOf(who).countFor(period);
    }

    @Override
    public long currentStreak(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.totalsOf(who).currentStreak();
    }

    @Override
    public long bestStreak(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.totalsOf(who).bestStreak();
    }

    @Override
    public int partyCount() {
        return repository.partyCount();
    }

    @Override
    public int partyThreshold() {
        int override = repository.thresholdOverride();
        return override > 0 ? override : baseThreshold;
    }

    @Override
    public Optional<VoteRanking> topAt(VotePeriod period, int rank) {
        Objects.requireNonNull(period, "period");
        if (rank < 1) {
            return Optional.empty();
        }
        List<VoteRanking> rows = repository.topVoters(period, LEADERBOARD_LIMIT);
        int index = rank - 1; // convert 1-based rank to 0-based index
        if (index >= rows.size()) {
            return Optional.empty();
        }
        return Optional.of(withResolvedName(rows.get(index)));
    }

    /**
     * Replace the row's UUID-string name with the resolver's display name. The repository fills the
     * {@link PlayerRef} name with the UUID string, so {@code votes_top_*_name} would otherwise show a
     * UUID; the resolver maps it to the offline/online display name (or the UUID string if unknown).
     */
    private VoteRanking withResolvedName(VoteRanking row) {
        UUID uuid = row.player().uuid();
        String resolved = nameResolver.apply(uuid);
        return new VoteRanking(new PlayerRef(uuid, resolved), row.votes());
    }

    @Override
    public OptionalInt positionOf(PlayerRef who, VotePeriod period) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(period, "period");
        List<VoteRanking> rows = repository.topVoters(period, LEADERBOARD_LIMIT);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).player().uuid().equals(who.uuid())) {
                return OptionalInt.of(i + 1); // 1-based rank
            }
        }
        return OptionalInt.empty();
    }
}

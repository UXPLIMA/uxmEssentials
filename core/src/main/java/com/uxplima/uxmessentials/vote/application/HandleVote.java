package com.uxplima.uxmessentials.vote.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.BroadcastThrottle;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;

/**
 * The core vote use case. On a received {@link Vote} it records the voter's tally first, then runs the
 * configured reward sets through the {@link RewardEngine}: the per-vote, per-site, first-vote, and
 * milestone specs whose chance/permission/world gates pass become {@link RewardGrant}s, each applied via
 * the {@link RewardApplier}. An online voter has the grants applied at once (commands run, messages and
 * items delivered) with {@link VoteReceived} published; an offline voter has the grants queued for their
 * next join (the applier queues the commands). Whether the credited vote is then announced is decided by
 * {@link BroadcastDecision} against the configured {@link BroadcastSettings}. When it is, the thank-you
 * fans out through the {@link com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster} across the
 * configured channels. Then the global party counter advances. When it reaches the configured threshold a
 * party fires (the party reward runs for every eligible online player,
 * {@link com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered} is published, and the counter
 * resets to zero), otherwise the incremented count is persisted.
 *
 * <p>The {@link Outcome} it returns names what happened (rewarded vs queued, and whether a party fired)
 * so the test paths and the {@code /vote testreward} confirmation can assert on it without reading
 * adapter side effects.
 */
public final class HandleVote {

    private final VoteRepository repository;
    private final RewardEngine engine;
    private final RewardApplier applier;
    private final VoteContext context;
    private final BroadcastSettings broadcastSettings;
    private final VoteBroadcaster broadcaster;
    private final BroadcastThrottle throttle;
    private final DomainEventPublisher events;
    private final PartyConfig party;
    private final PartyService partyService;
    private final int streakGraceDays;
    private final ZoneId zone;

    public HandleVote(
            VoteRepository repository,
            RewardEngine engine,
            RewardApplier applier,
            VoteContext context,
            VoteAudience audience,
            BroadcastSettings broadcastSettings,
            VoteBroadcaster broadcaster,
            BroadcastThrottle throttle,
            DomainEventPublisher events,
            PartyConfig party,
            int streakGraceDays,
            ZoneId zone) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.context = Objects.requireNonNull(context, "context");
        Objects.requireNonNull(audience, "audience");
        this.broadcastSettings = Objects.requireNonNull(broadcastSettings, "broadcastSettings");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.throttle = Objects.requireNonNull(throttle, "throttle");
        this.events = Objects.requireNonNull(events, "events");
        this.party = Objects.requireNonNull(party, "party");
        this.streakGraceDays = streakGraceDays;
        this.zone = Objects.requireNonNull(zone, "zone");
        this.partyService = new PartyService(
                repository, applier, audience, broadcaster, broadcastSettings.channels(), events, party);
    }

    /** Apply {@code vote}: record the tally, resolve and apply the reward sets, advance the counter, fire a party when due. */
    public Outcome handle(Vote vote) {
        Objects.requireNonNull(vote, "vote");
        PlayerRef voter = vote.voter();
        VoteTally before = repository.totalsOf(voter);
        VoteTally after = before.recordVote(vote.at(), zone, streakGraceDays);
        repository.saveTotals(voter, after);
        repository.recordLastVoteAtSite(voter, vote.serviceName(), vote.at());
        boolean streakAdvanced = before.streakDayKey() != after.streakDayKey();
        boolean voterOnline = context.isOnline(voter);
        boolean rewarded = creditVoter(vote, after, streakAdvanced, voterOnline);
        maybeBroadcastThanks(vote, after, voterOnline);
        repository.markPartyParticipant(voter);
        boolean partyFired = advanceCounter(vote);
        return new Outcome(rewarded, partyFired);
    }

    private boolean creditVoter(Vote vote, VoteTally after, boolean streakAdvanced, boolean online) {
        PlayerRef voter = vote.voter();
        RewardContext ctx = new RewardContext(
                voter,
                online,
                online ? context.worldOf(voter) : "",
                vote.serviceName(),
                after,
                streakAdvanced,
                node -> context.hasPermission(voter, node),
                context::roll);
        for (RewardGrant grant : engine.resolve(ctx)) {
            applier.apply(voter, online, grant);
        }
        if (online) {
            events.publish(new VoteReceived(voter, vote.serviceName()));
        }
        return online;
    }

    private boolean advanceCounter(Vote vote) {
        maybeResetForNewPeriod(vote);
        int newCount = repository.incrementAndGetPartyCount();
        int threshold = partyService.effectiveThreshold();
        maybeAnnounce(newCount, threshold);
        if (newCount >= threshold && repository.claimPartyFire(threshold)) {
            // claimPartyFire atomically reset the counter to 0; only this thread proceeds to fire.
            partyService.fire(threshold);
            return true;
        }
        return false;
    }

    private void maybeResetForNewPeriod(Vote vote) {
        PartyResetSchedule schedule = party.resetSchedule();
        if (schedule == PartyResetSchedule.NONE) {
            return;
        }
        LocalDate date = LocalDate.ofInstant(vote.at(), zone);
        long currentKey = periodKey(schedule, date);
        if (repository.partyPeriodKey() != currentKey) {
            repository.setPartyCount(0);
            repository.setPartyPeriodKey(currentKey);
        }
    }

    private void maybeAnnounce(int newCount, int threshold) {
        if (!party.announceAt().contains(newCount)) {
            return;
        }
        int remaining = Math.max(0, threshold - newCount);
        Map<String, String> placeholders = Map.of("remaining", Integer.toString(remaining));
        broadcaster.broadcast(VoteMessageKey.VOTEPARTY_ANNOUNCE, placeholders, broadcastSettings.channels());
    }

    private static long periodKey(PartyResetSchedule schedule, LocalDate date) {
        return switch (schedule) {
            case DAILY -> date.toEpochDay();
            case WEEKLY ->
                (long) date.get(IsoFields.WEEK_BASED_YEAR) * 100 + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case NONE -> 0L;
        };
    }

    private void maybeBroadcastThanks(Vote vote, VoteTally after, boolean voterOnline) {
        PlayerRef voter = vote.voter();
        boolean blacklisted =
                broadcastSettings.blacklist().contains(voter.name().toLowerCase(Locale.ROOT));
        if (!BroadcastDecision.shouldBroadcast(
                broadcastSettings.type(),
                voterOnline,
                after.daily(),
                blacklisted,
                throttle.lastBroadcastAt(voter),
                vote.at(),
                broadcastSettings.cooldown())) {
            return;
        }
        broadcaster.broadcast(VoteMessageKey.VOTE_THANKS, Map.of("player", voter.name()), broadcastSettings.channels());
        if (broadcastSettings.type() == BroadcastType.COOLDOWN_PER_PLAYER) {
            throttle.recordBroadcast(voter, vote.at());
        }
    }

    /** What {@link #handle} did: whether the voter was rewarded now (vs queued) and whether a party fired. */
    public record Outcome(boolean rewardedNow, boolean partyTriggered) {}
}

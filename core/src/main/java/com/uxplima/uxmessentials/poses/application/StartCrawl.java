package com.uxplima.uxmessentials.poses.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseStarted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Starts a crawl: {@code /crawl}, the prone one-block-high pose the player keeps <em>while walking around</em>.
 * Unlike the sit and free poses it anchors the player on no seat. The whole crawl (the swimming pose and the low
 * ceiling that stops the client standing back up) lives behind the {@link CrawlView}, so this use case only says
 * where to hold the player; the move-follow re-states the same hold as they walk, and {@code StopPose} releases it.
 *
 * <p>Like the other starters it gates on {@code features.crawl}, the {@link PoseRegionGate}, and the
 * one-session-per-player invariant (a player already posing (sitting, laying, or crawling) is turned away rather
 * than double-posed). {@code /crawl} is a toggle, but the toggle-off is the plain {@code StopPose}, so this use
 * case only ever starts a crawl; a second {@code /crawl} on an already-crawling player is routed to {@code StopPose}
 * by the command, not here.
 */
public final class StartCrawl {

    private final PoseSessions sessions;
    private final CrawlView crawlView;
    private final PoseRegionGate regionGate;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final boolean crawlEnabled;
    private final PoseCooldown cooldown;

    public StartCrawl(
            PoseSessions sessions,
            CrawlView crawlView,
            PoseRegionGate regionGate,
            DomainEventPublisher events,
            Clock clock,
            boolean crawlEnabled,
            PoseCooldown cooldown) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.crawlView = Objects.requireNonNull(crawlView, "crawlView");
        this.regionGate = Objects.requireNonNull(regionGate, "regionGate");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.crawlEnabled = crawlEnabled;
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Start {@code who} crawling from where they stand ({@code feet}). Returns the outcome the adapter renders
     * feedback from; a successful start holds the player prone through the {@link CrawlView} and records the
     * session.
     */
    public CrawlOutcome start(PlayerRef who, Position feet) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(feet, "feet");
        if (!crawlEnabled) {
            return CrawlOutcome.DISABLED;
        }
        if (!regionGate.canPose(who, feet, PoseType.CRAWL)) {
            return CrawlOutcome.DENIED_REGION;
        }
        if (sessions.isPosing(who)) {
            return CrawlOutcome.ALREADY_POSING;
        }
        if (cooldown.remaining(who).isPresent()) {
            return CrawlOutcome.ON_COOLDOWN;
        }
        beginCrawl(who, feet);
        cooldown.stamp(who);
        return CrawlOutcome.STARTED;
    }

    private void beginCrawl(PlayerRef who, Position feet) {
        crawlView.hold(who, feet);
        // A crawl rides no seat entity, so the seat handle is a sentinel and the return location is where the crawl
        // began; StopPose skips both for a crawl (no seat to remove, no return-to-start for a pose you walk around in).
        PoseSession session = new PoseSession(who, PoseType.CRAWL, feet, "crawl", null, clock.instant());
        sessions.start(session);
        events.publish(new PoseStarted(session));
    }

    /** How a {@code /crawl} attempt resolved, so the adapter can send the matching feedback. */
    public enum CrawlOutcome {

        /** The crawl began; the player is held prone and the session recorded. */
        STARTED,

        /** {@code features.crawl} is off: crawling is not offered. */
        DISABLED,

        /** The region gate refused the crawl here (a claim or WorldGuard veto). */
        DENIED_REGION,

        /** The player already holds a pose; the one-session invariant turns the crawl away. */
        ALREADY_POSING,

        /** The player is still inside the pose cooldown window; the crawl is refused and nothing is stamped. */
        ON_COOLDOWN
    }
}

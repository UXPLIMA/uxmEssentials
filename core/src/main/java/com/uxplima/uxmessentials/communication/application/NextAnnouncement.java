package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.AnnouncerCursor;

/**
 * Picks the next announcement each timer tick, honouring the config's min-players gate and the no-immediate-repeat
 * rule. The adapter's {@code Scheduler}-port timer calls {@link #pick(int)} with the current online count; this
 * use case reads the live {@link AnnouncerConfig} from the supplied holder (so a reload that swaps the config
 * takes effect on the next tick), checks the gate, advances the {@link AnnouncerCursor}, and returns the chosen
 * {@link Announcement} to broadcast, or empty when the tick is skipped.
 *
 * <p>The cursor is the only mutable state: it is held in an {@code AtomicReference} and advanced with {@code
 * updateAndGet} so two ticks can never read the same position, keeping the no-repeat rule honest under the
 * fire-and-forget scheduler. Randomness flows through the injected {@link RandomSource}, leaving the domain cursor
 * pure. The returned announcement carries operator-authored lines for the adapter to render through MiniMessage,
 * gate per viewer by the announcement's condition, and deliver across its channels, never a {@code MessageKey}.
 */
public final class NextAnnouncement {

    private final Supplier<AnnouncerConfig> config;
    private final RandomSource random;
    private final AtomicReference<AnnouncerCursor> cursor = new AtomicReference<>(AnnouncerCursor.start());

    public NextAnnouncement(Supplier<AnnouncerConfig> config, RandomSource random) {
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * The announcement to broadcast this tick given {@code onlinePlayers}, or empty when the config has no
     * announcements or the online count is below the min-players gate. Advances the rotation cursor only when an
     * announcement is actually chosen, so a skipped tick does not consume a position. The timer is a single serial
     * task on the scheduler port, so the read-advance-store sequence on the cursor reference is never interleaved
     * with itself.
     */
    public Optional<Announcement> pick(int onlinePlayers) {
        AnnouncerConfig current = config.get();
        if (!current.shouldFire(onlinePlayers)) {
            return Optional.empty();
        }
        AnnouncerCursor position = Objects.requireNonNull(cursor.get(), "cursor");
        AnnouncerCursor.Selection selection =
                position.advance(current.announcementCount(), current.ordering(), random::nextBounded);
        cursor.set(selection.cursor());
        return Optional.of(current.announcementAt(selection.index()));
    }

    /** Reset the rotation to its starting position; called when a reload swaps a new config in. */
    public void reset() {
        cursor.set(AnnouncerCursor.start());
    }
}

package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /banhistory}, {@code /mutehistory}, {@code /history} and {@code /staffhistory}: read-only reviews of
 * the append-only sanction history. An empty history renders the empty notice; a populated one renders a header
 * with the count then one entry per row, scoped to the right slice. The family reviews stay ban- or
 * mute-family, the unified {@code /history} folds every kind for one target newest-first, and
 * {@code /staffhistory} folds every kind issued by one staff member.
 */
class SanctionHistoryReviewTest {

    private static final Instant T0 = Instant.parse("2026-06-02T00:00:00Z");

    private FakeSanctionHistory history;
    private CapturingNotifier notifier;
    private PlayerRef staff;
    private PlayerRef target;

    @BeforeEach
    void setUp() {
        history = new FakeSanctionHistory();
        notifier = new CapturingNotifier();
        staff = new PlayerRef(UUID.randomUUID(), "Staff");
        target = new PlayerRef(UUID.randomUUID(), "Griefer");
    }

    @Test
    void banHistoryWithNoRowsRendersEmpty() {
        new ReviewBanHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys).containsExactly("moderation.banhistory.empty");
    }

    @Test
    void banHistoryRendersHeaderAndEntryPerRowNewestFirst() {
        append(SanctionAction.BAN, T0, Optional.of("grief"));
        append(SanctionAction.UNBAN, T0.plusSeconds(60), Optional.empty());
        // A mute-family row for the same target must not bleed into the ban review.
        append(SanctionAction.MUTE, T0.plusSeconds(30), Optional.of("spam"));

        new ReviewBanHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.banhistory.header", "moderation.banhistory.entry", "moderation.banhistory.entry");
    }

    @Test
    void muteHistoryWithNoRowsRendersEmpty() {
        new ReviewMuteHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys).containsExactly("moderation.mutehistory.empty");
    }

    @Test
    void muteHistoryRendersHeaderAndEntryPerRow() {
        append(SanctionAction.MUTE, T0, Optional.of("spam"));
        append(SanctionAction.UNMUTE, T0.plusSeconds(60), Optional.empty());
        // A ban-family row for the same target must not bleed into the mute review.
        append(SanctionAction.BAN, T0.plusSeconds(30), Optional.of("grief"));

        new ReviewMuteHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.mutehistory.header",
                        "moderation.mutehistory.entry",
                        "moderation.mutehistory.entry");
    }

    @Test
    void unifiedHistoryWithNoRowsRendersEmpty() {
        new ReviewSanctionHistory(history, notifier.notifier()).show(staff, target);

        assertThat(notifier.keys).containsExactly("moderation.history.empty");
    }

    @Test
    void unifiedHistoryFoldsEveryKindNewestFirst() {
        append(SanctionAction.WARN, T0, Optional.of("first warning"));
        append(SanctionAction.KICK, T0.plusSeconds(30), Optional.empty());
        append(SanctionAction.BAN, T0.plusSeconds(60), Optional.of("grief"));
        append(SanctionAction.MUTE, T0.plusSeconds(90), Optional.of("spam"));

        new ReviewSanctionHistory(history, notifier.notifier()).show(staff, target);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.history.header",
                        "moderation.history.entry",
                        "moderation.history.entry",
                        "moderation.history.entry",
                        "moderation.history.entry");
    }

    @Test
    void staffHistoryWithNoRowsRendersEmpty() {
        new ReviewStaffHistory(history, players(), notifier.notifier()).show(staff, staff);

        assertThat(notifier.keys).containsExactly("moderation.staffhistory.empty");
    }

    @Test
    void staffHistoryFoldsEveryKindIssuedByTheActor() {
        appendBy(staff, SanctionAction.WARN, T0, Optional.of("by staff"));
        appendBy(staff, SanctionAction.KICK, T0.plusSeconds(30), Optional.empty());
        // A sanction issued by a different staff member must not bleed into this review.
        appendBy(
                new PlayerRef(UUID.randomUUID(), "OtherStaff"),
                SanctionAction.BAN,
                T0.plusSeconds(60),
                Optional.empty());

        new ReviewStaffHistory(history, players(), notifier.notifier()).show(staff, staff);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.staffhistory.header",
                        "moderation.staffhistory.entry",
                        "moderation.staffhistory.entry");
    }

    private void append(SanctionAction action, Instant at, Optional<String> reason) {
        history.append(new SanctionHistoryEntry(
                action, target.uuid(), Issuer.console("Staff"), reason, at, Optional.empty(), Optional.empty()));
    }

    private void appendBy(PlayerRef actor, SanctionAction action, Instant at, Optional<String> reason) {
        history.append(new SanctionHistoryEntry(
                action, target.uuid(), Issuer.of(actor), reason, at, Optional.empty(), Optional.empty()));
    }

    private PlayerLookup players() {
        return new ModerationFakes.FixedPlayers(java.util.Map.of(target.uuid(), target), java.util.Set.of());
    }

    private static final class CapturingNotifier {
        private final List<String> keys = new ArrayList<>();

        Notifier notifier() {
            return new Notifier(new RecordingMessages(keys), new NoopSink());
        }
    }

    private static final class RecordingMessages implements Messages {
        private final List<String> keys;

        RecordingMessages(List<String> keys) {
            this.keys = keys;
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}

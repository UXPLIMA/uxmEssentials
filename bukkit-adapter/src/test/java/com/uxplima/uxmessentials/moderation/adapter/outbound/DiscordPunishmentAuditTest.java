package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The Discord-notification decorator over {@link ModerationAudit}: it always delegates the operator audit line
 * unchanged, and, only when {@code discord-notify} is on, additionally emits a name-based
 * {@code event=punishment_notify} line on the shared audit channel for each <em>successful</em> punishment. A
 * failed action or a disabled toggle emits no notice. The delegate is verified with Mockito; the notice sink is
 * a recording {@link Logger}.
 */
class DiscordPunishmentAuditTest {

    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Bob");

    private final ModerationAudit delegate = mock(ModerationAudit.class);
    private final RecordingLogger notices = new RecordingLogger();

    @Test
    void enabledEmitsANameBasedNoticeForASuccessfulBan() {
        ModerationAudit audit = new DiscordPunishmentAudit(delegate, notices, true);

        audit.tempbanned(STAFF, TARGET, "permanent", true, Optional.of("Griefing"));

        verify(delegate).tempbanned(STAFF, TARGET, "permanent", true, Optional.of("Griefing"));
        assertThat(notices.lines).singleElement().satisfies(line -> {
            assertThat(line.format).contains("event=punishment_notify");
            assertThat(line.args).containsSequence("ban", "Alice", "Bob", "permanent");
        });
    }

    @Test
    void enabledEmitsANoticeForASuccessfulTimedMute() {
        ModerationAudit audit = new DiscordPunishmentAudit(delegate, notices, true);

        audit.muted(STAFF, TARGET, Optional.of("1h"), true, Optional.empty());

        assertThat(notices.lines).singleElement().satisfies(line -> {
            assertThat(line.format).contains("event=punishment_notify");
            assertThat(line.args).containsSequence("mute", "Alice", "Bob", "1h");
        });
    }

    @Test
    void aFailedPunishmentEmitsNoNotice() {
        ModerationAudit audit = new DiscordPunishmentAudit(delegate, notices, true);

        audit.tempbanned(STAFF, TARGET, "permanent", false, Optional.of("Griefing"));

        verify(delegate).tempbanned(STAFF, TARGET, "permanent", false, Optional.of("Griefing"));
        assertThat(notices.lines).isEmpty();
    }

    @Test
    void disabledDelegatesButEmitsNoNotice() {
        ModerationAudit audit = new DiscordPunishmentAudit(delegate, notices, false);

        audit.tempbanned(STAFF, TARGET, "7d", true, Optional.of("Griefing"));
        audit.muted(STAFF, TARGET, Optional.empty(), true, Optional.empty());

        verify(delegate).tempbanned(STAFF, TARGET, "7d", true, Optional.of("Griefing"));
        verify(delegate).muted(STAFF, TARGET, Optional.empty(), true, Optional.empty());
        assertThat(notices.lines).isEmpty();
    }

    @Test
    void aLiftEmitsNoNoticeEvenWhenEnabled() {
        ModerationAudit audit = new DiscordPunishmentAudit(delegate, notices, true);

        audit.unbanned(STAFF, TARGET, true);
        audit.unmuted(STAFF, TARGET, true, Optional.empty());

        assertThat(notices.lines).isEmpty();
    }

    /** A {@link Logger} recording each {@code info} call's format and args so the notice is assertable. */
    private static final class RecordingLogger implements Logger {
        private final List<Line> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            lines.add(new Line(message, List.of(args)));
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private record Line(String format, List<Object> args) {}
    }
}

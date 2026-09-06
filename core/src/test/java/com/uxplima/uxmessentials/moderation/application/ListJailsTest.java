package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /jails}: the read-only listing of the named jails an operator configured. Mirrors the
 * {@link ReviewWarns} shape, a header with the count then one entry per name when jails exist, an empty
 * notice when none are configured. The directory hands names back sorted, so the entries arrive sorted.
 */
class ListJailsTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "op");

    @Test
    void listsAHeaderWithTheCountThenOneSortedEntryPerJail() {
        RecordingSink sink = new RecordingSink();
        JailDirectory directory = new FixedNames(List.of("spawnjail", "mines"));
        ListJails listJails = new ListJails(directory, notifier(sink));

        listJails.list(ACTOR);

        assertThat(sink.keys())
                .containsExactly(
                        ModerationMessageKey.JAILS_HEADER.key(),
                        ModerationMessageKey.JAILS_ENTRY.key(),
                        ModerationMessageKey.JAILS_ENTRY.key());
        assertThat(sink.placeholdersAt(0)).containsEntry("count", "2");
        assertThat(sink.placeholdersAt(1)).containsEntry("jail", "mines");
        assertThat(sink.placeholdersAt(2)).containsEntry("jail", "spawnjail");
    }

    @Test
    void suggestionNamesReturnsTheConfigNamesForTabCompletion() {
        JailDirectory directory = new FixedNames(List.of("spawnjail", "mines"));
        ListJails listJails = new ListJails(directory, notifier(new RecordingSink()));

        assertThat(listJails.suggestionNames()).containsExactly("mines", "spawnjail");
    }

    @Test
    void anEmptyDirectorySendsTheEmptyNoticeAndNoEntries() {
        RecordingSink sink = new RecordingSink();
        JailDirectory directory = new FixedNames(List.of());
        ListJails listJails = new ListJails(directory, notifier(sink));

        listJails.list(ACTOR);

        assertThat(sink.keys()).containsExactly(ModerationMessageKey.JAILS_EMPTY.key());
    }

    private static Notifier notifier(RecordingSink sink) {
        return new Notifier(new RecordingMessages(sink), sink);
    }

    /** A directory that returns its configured names already sorted, the way the production port contracts. */
    private record FixedNames(List<String> names) implements JailDirectory {
        @Override
        public boolean exists(String jail) {
            return names.contains(jail);
        }

        @Override
        public boolean isWallClock(String jail) {
            return false;
        }

        @Override
        public List<String> names() {
            return names.stream().sorted().toList();
        }
    }

    /** Echoes the key so the sink can assert on which message went out, and stashes the placeholders. */
    private record RecordingMessages(RecordingSink sink) implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.record(key.key(), placeholders);
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> keys = new ArrayList<>();
        private final List<Map<String, String>> placeholders = new ArrayList<>();

        void record(String key, Map<String, String> values) {
            keys.add(key);
            placeholders.add(values);
        }

        List<String> keys() {
            return keys;
        }

        Map<String, String> placeholdersAt(int index) {
            return placeholders.get(index);
        }

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}

package com.uxplima.uxmessentials.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /ignorelist}: the read-only companion to {@code /ignore} that reports who the owner ignores. It
 * proves the three shapes the existing catalog keys describe. An empty list reports only the empty key, a
 * populated list opens with the header carrying the count and then one entry per ignored player in the stable
 * insertion order {@link IgnoreList#entries()} preserves.
 */
class ListIgnoresTest {

    private final PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
    private final PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");

    private FakeIgnoreStore ignores;
    private RecordingNotifier recorder;
    private ListIgnores listIgnores;

    @BeforeEach
    void setUp() {
        ignores = new FakeIgnoreStore();
        recorder = new RecordingNotifier();
        listIgnores = new ListIgnores(ignores, new Notifier(recorder, new NoopSink()));
    }

    @Test
    void anEmptyListReportsOnlyTheEmptyKey() {
        listIgnores.list(owner);

        assertThat(recorder.sent).containsExactly(MessagingMessageKey.IGNORE_LIST_EMPTY);
        assertThat(recorder.placeholders).containsExactly(Map.of());
    }

    @Test
    void aPopulatedListReportsTheHeaderThenOneEntryPerPlayerInInsertionOrder() {
        ignores.ignore(owner, alice, IgnoreScope.ALL);
        ignores.ignore(owner, bob, IgnoreScope.ALL);

        listIgnores.list(owner);

        assertThat(recorder.sent)
                .containsExactly(
                        MessagingMessageKey.IGNORE_LIST_HEADER,
                        MessagingMessageKey.IGNORE_LIST_ENTRY,
                        MessagingMessageKey.IGNORE_LIST_ENTRY);
        assertThat(recorder.placeholders)
                .containsExactly(Map.of("count", "2"), Map.of("player", "Alice"), Map.of("player", "Bob"));
    }

    // --- fakes -----------------------------------------------------------------------------------------------

    private static final class FakeIgnoreStore implements IgnoreStore {
        final ConcurrentHashMap<UUID, IgnoreList> lists = new ConcurrentHashMap<>();

        @Override
        public IgnoreList load(PlayerRef who) {
            return lists.getOrDefault(who.uuid(), IgnoreList.empty(who));
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
            lists.compute(
                    owner.uuid(), (id, list) -> (list == null ? IgnoreList.empty(owner) : list).ignore(ignored, scope));
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {
            lists.computeIfPresent(owner.uuid(), (id, list) -> list.unignore(ignored));
        }
    }

    /** Records the resolved key and placeholders of every notifier send, in order. */
    private static final class RecordingNotifier implements Messages {
        final List<MessageKey> sent = new ArrayList<>();
        final List<Map<String, String>> placeholders = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sent.add(key);
            this.placeholders.add(Map.copyOf(placeholders));
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}

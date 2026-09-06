package com.uxplima.uxmessentials.persistence.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqMailRepository} and {@link JooqIgnoreStore} against the default embedded
 * SQLite backend with the Flyway V4 messaging tables applied. It proves mail survives a round-trip and is
 * read newest-first, the unread count, mark-all-read, clear, and the by-send-time expiry sweep; and that the
 * ignore store upserts the {@code (owner, ignored)} key idempotently, persists the scope, and removes on
 * unignore: the durable facts behind ignore-aware delivery and the persistent mailbox.
 */
class JooqMessagingRepositoriesTest {

    private Persistence persistence;
    private JooqMailRepository mail;
    private JooqIgnoreStore ignores;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        mail = new JooqMailRepository(persistence.dsl());
        ignores = new JooqIgnoreStore(persistence.dsl());
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void mailRoundTripsAndReadsNewestFirst() {
        mail.append(item("older", Instant.ofEpochMilli(1_000)));
        mail.append(item("newer", Instant.ofEpochMilli(2_000)));

        MailBox box = mail.load(alice);

        assertThat(box.size()).isEqualTo(2);
        assertThat(box.items().stream().map(i -> i.body().value())).containsExactly("newer", "older");
        assertThat(box.items().get(0).sender().name()).isEqualTo("Bob");
    }

    @Test
    void appendAssignsAscendingIds() {
        MailItem first = mail.append(item("a", Instant.ofEpochMilli(1_000)));
        MailItem second = mail.append(item("b", Instant.ofEpochMilli(2_000)));

        assertThat(first.id().value()).isPositive();
        assertThat(second.id().value()).isGreaterThan(first.id().value());
    }

    @Test
    void unreadCountAndMarkAllReadTrackReadState() {
        mail.append(item("a", Instant.ofEpochMilli(1_000)));
        mail.append(item("b", Instant.ofEpochMilli(2_000)));

        assertThat(mail.unreadCount(alice)).isEqualTo(2);

        mail.markAllRead(alice);

        assertThat(mail.unreadCount(alice)).isZero();
        assertThat(mail.load(alice).size()).isEqualTo(2); // mark-read does not delete
    }

    @Test
    void clearEmptiesTheBox() {
        mail.append(item("a", Instant.ofEpochMilli(1_000)));

        mail.clear(alice);

        assertThat(mail.load(alice).isEmpty()).isTrue();
    }

    @Test
    void expirySweepDeletesMailOlderThanTheCutoff() {
        mail.append(item("old", Instant.ofEpochMilli(1_000)));
        mail.append(item("new", Instant.ofEpochMilli(10_000)));

        int removed = mail.deleteSentBefore(Instant.ofEpochMilli(5_000));

        assertThat(removed).isEqualTo(1);
        assertThat(mail.load(alice).items().stream().map(i -> i.body().value())).containsExactly("new");
    }

    @Test
    void ignoreUpsertsIdempotentlyOnTheOwnerIgnoredKey() {
        ignores.ignore(alice, bob, IgnoreScope.MESSAGES);
        ignores.ignore(alice, bob, IgnoreScope.ALL); // same (owner, ignored), a re-scope, not a duplicate

        IgnoreList list = ignores.load(alice);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.scopeFor(bob)).contains(IgnoreScope.ALL);
    }

    @Test
    void unignoreRemovesThePersistedEntry() {
        ignores.ignore(alice, bob, IgnoreScope.ALL);

        ignores.unignore(alice, bob);

        assertThat(ignores.load(alice).ignores(bob)).isFalse();
    }

    private MailItem item(String body, Instant sentAt) {
        return MailItem.compose(alice, MailSender.player(bob), MessageBody.of(body), sentAt);
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

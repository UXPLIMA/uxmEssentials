package com.uxplima.uxmessentials.persistence.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqAnnouncementStore} against the default embedded SQLite backend with the Flyway
 * ladder applied through V65. It proves the round-trip (save → find) preserves every editable field, lines,
 * channels, the enabled flag, the raw condition string, the sound, and the interval override: that a re-save
 * upserts in place on the id key, that the enabled filter excludes a disabled row, and that delete returns true
 * once then false.
 */
class JooqAnnouncementStoreTest {

    private Persistence persistence;
    private JooqAnnouncementStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqAnnouncementStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsEveryEditableFieldRoundTrip() {
        StoredAnnouncement announcement = new StoredAnnouncement(
                "tips",
                List.of("First line", "Second line"),
                Set.of(BroadcastChannel.CHAT, BroadcastChannel.TITLE),
                true,
                "permission:uxmessentials.vip && world:hub",
                Optional.of("entity.player.levelup"),
                OptionalLong.of(600));

        store.save(announcement);

        assertThat(store.find("tips")).hasValueSatisfying(loaded -> {
            assertThat(loaded.lines()).containsExactly("First line", "Second line");
            assertThat(loaded.channels()).containsExactlyInAnyOrder(BroadcastChannel.CHAT, BroadcastChannel.TITLE);
            assertThat(loaded.enabled()).isTrue();
            assertThat(loaded.condition()).isEqualTo("permission:uxmessentials.vip && world:hub");
            assertThat(loaded.sound()).contains("entity.player.levelup");
            assertThat(loaded.intervalSeconds()).hasValue(600);
        });
    }

    @Test
    void theConditionStringPersistsVerbatim() {
        store.save(StoredAnnouncement.fresh("notice", "hello").withCondition("world:nether"));

        assertThat(store.find("notice"))
                .hasValueSatisfying(loaded -> assertThat(loaded.condition()).isEqualTo("world:nether"));
    }

    @Test
    void saveUpsertsOnTheIdKeyRatherThanInserting() {
        store.save(StoredAnnouncement.fresh("tips", "v1"));
        store.save(StoredAnnouncement.fresh("tips", "v2").withCondition("permission:vip"));

        assertThat(store.all()).singleElement().satisfies(loaded -> {
            assertThat(loaded.lines()).containsExactly("v2");
            assertThat(loaded.condition()).isEqualTo("permission:vip");
        });
    }

    @Test
    void enabledExcludesADisabledRow() {
        store.save(StoredAnnouncement.fresh("on", "shown"));
        store.save(StoredAnnouncement.fresh("off", "hidden").withEnabled(false));

        assertThat(store.enabled()).extracting(StoredAnnouncement::id).containsExactly("on");
        assertThat(store.all()).extracting(StoredAnnouncement::id).containsExactly("off", "on");
    }

    @Test
    void togglingEnabledPersistsWithoutLosingOtherFields() {
        store.save(new StoredAnnouncement(
                "tips",
                List.of("kept line"),
                Set.of(BroadcastChannel.ACTION_BAR),
                true,
                "world:hub",
                Optional.empty(),
                OptionalLong.empty()));

        StoredAnnouncement stored = store.find("tips").orElseThrow();
        store.save(stored.withEnabled(false));

        assertThat(store.find("tips")).hasValueSatisfying(loaded -> {
            assertThat(loaded.enabled()).isFalse();
            assertThat(loaded.lines()).containsExactly("kept line");
            assertThat(loaded.channels()).containsExactly(BroadcastChannel.ACTION_BAR);
            assertThat(loaded.condition()).isEqualTo("world:hub");
        });
    }

    @Test
    void deleteReturnsTrueOnceThenFalse() {
        store.save(StoredAnnouncement.fresh("tips", "line"));

        assertThat(store.delete("tips")).isTrue();
        assertThat(store.delete("tips")).isFalse();
        assertThat(store.exists("tips")).isFalse();
    }

    @Test
    void existsReflectsWhetherAnAnnouncementIsStored() {
        assertThat(store.exists("tips")).isFalse();

        store.save(StoredAnnouncement.fresh("tips", "line"));

        assertThat(store.exists("tips")).isTrue();
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

    /** A logger that drops everything: the store test asserts on rows, not log output. */
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

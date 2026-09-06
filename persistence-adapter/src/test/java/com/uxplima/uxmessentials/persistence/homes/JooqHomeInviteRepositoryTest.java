package com.uxplima.uxmessentials.persistence.homes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqHomeInviteRepository} against the default embedded SQLite backend
 * with all Flyway migrations applied. Verifies the full CRUD surface: list, add (idempotent), remove one,
 * remove a slot, and remove an owner's entire invite set.
 */
class JooqHomeInviteRepositoryTest {

    private Persistence persistence;
    private JooqHomeInviteRepository repository;
    private PlayerRef owner;
    private HomeSlot slot0;
    private HomeSlot slot1;
    private UUID guestA;
    private UUID guestB;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqHomeInviteRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Alice");
        slot0 = HomeSlot.of(0);
        slot1 = HomeSlot.of(1);
        guestA = UUID.randomUUID();
        guestB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void invitesIsEmptyWhenNoneAdded() {
        assertThat(repository.invites(owner, slot0)).isEmpty();
    }

    @Test
    void addInviteAndInvitesReturnsBothGuests() {
        repository.addInvite(owner, slot0, guestA);
        repository.addInvite(owner, slot0, guestB);

        Set<UUID> result = repository.invites(owner, slot0);
        assertThat(result).containsExactlyInAnyOrder(guestA, guestB);
    }

    @Test
    void addInviteIsIdempotentWhenCalledTwice() {
        repository.addInvite(owner, slot0, guestA);
        repository.addInvite(owner, slot0, guestA); // duplicate, PK deduplication

        assertThat(repository.invites(owner, slot0)).containsExactly(guestA);
    }

    @Test
    void removeInviteRemovesOnlyTheTargetGuest() {
        repository.addInvite(owner, slot0, guestA);
        repository.addInvite(owner, slot0, guestB);

        repository.removeInvite(owner, slot0, guestA);

        Set<UUID> remaining = repository.invites(owner, slot0);
        assertThat(remaining).containsExactly(guestB);
    }

    @Test
    void removeInviteIsNoOpWhenNotInvited() {
        repository.addInvite(owner, slot0, guestA);

        repository.removeInvite(owner, slot0, guestB); // guestB was never added

        assertThat(repository.invites(owner, slot0)).containsExactly(guestA);
    }

    @Test
    void removeAllClearsOnlyTheTargetSlot() {
        repository.addInvite(owner, slot0, guestA);
        repository.addInvite(owner, slot0, guestB);
        repository.addInvite(owner, slot1, guestA);

        repository.removeAll(owner, slot0);

        assertThat(repository.invites(owner, slot0)).isEmpty();
        assertThat(repository.invites(owner, slot1)).containsExactly(guestA);
    }

    @Test
    void removeAllForOwnerClearsEverySlot() {
        repository.addInvite(owner, slot0, guestA);
        repository.addInvite(owner, slot1, guestB);

        repository.removeAllForOwner(owner);

        assertThat(repository.invites(owner, slot0)).isEmpty();
        assertThat(repository.invites(owner, slot1)).isEmpty();
    }

    @Test
    void invitesAreIsolatedBetweenOwners() {
        PlayerRef other = new PlayerRef(UUID.randomUUID(), "Bob");
        repository.addInvite(owner, slot0, guestA);
        repository.addInvite(other, slot0, guestB);

        assertThat(repository.invites(owner, slot0)).containsExactly(guestA);
        assertThat(repository.invites(other, slot0)).containsExactly(guestB);
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

package com.uxplima.uxmessentials.discordlink;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.discordlink.adapter.outbound.ConfirmLinkService;
import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.ConfirmLink;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.discordlink.domain.event.AccountLinked;
import com.uxplima.uxmessentials.discordlink.domain.event.AccountUnlinked;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The discordlink command and seam paths through the real use cases against an in-memory store, the same
 * wiring the Brigadier handlers and the {@code ServicesManager}-exposed confirmation drive, minus Bukkit. It
 * proves {@code /discordlink} issues and persists a code, {@code /discordlink status} reflects the binding,
 * {@code /discordunlink} clears it, and the bridge-facing {@link ConfirmLinkService} redeems a code end to end
 *. Mapping a malformed input, an unknown code, and an already-linked Discord account to the right outcomes
 * without ever throwing across the seam.
 */
class DiscordLinkCommandPathTest {

    private static final Clock CLOCK = Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private FakeStore store;
    private BeginLink beginLink;
    private ConfirmLinkService confirmation;
    private Unlink unlink;
    private LinkStatus linkStatus;
    private PlayerRef alice;
    private List<DomainEvent> published;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        published = new ArrayList<>();
        beginLink = new BeginLink(store, CLOCK, new java.util.Random(1L), Duration.ofMinutes(10));
        confirmation = new ConfirmLinkService(new ConfirmLink(store, CLOCK), new StubLookup(), published::add);
        unlink = new Unlink(store, published::add);
        linkStatus = new LinkStatus(store);
        alice = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "Alice");
    }

    @Test
    void discordLinkIssuesAndPersistsACodeThatTheBridgeRedeems() {
        LinkCode code = beginLink.begin(alice); // /discordlink

        assertThat(store.findPendingByCode(code)).isPresent();

        DiscordLinkConfirmation.Outcome outcome = confirmation.confirm(code.value(), "123456789012345678"); // /link

        assertThat(outcome.result()).isEqualTo(DiscordLinkConfirmation.Result.LINKED);
        assertThat(outcome.playerName()).isEqualTo("Alice");
        assertThat(linkStatus.status(alice)).isPresent(); // /discordlink status now linked
        assertThat(store.findPendingByCode(code)).isEmpty(); // the code was consumed
    }

    @Test
    void discordUnlinkClearsTheBindingAfterLinking() {
        LinkCode code = beginLink.begin(alice);
        confirmation.confirm(code.value(), "123456789012345678");

        assertThat(unlink.unlink(alice).isOk()).isTrue(); // /discordunlink
        assertThat(linkStatus.status(alice)).isEmpty();
        assertThat(unlink.unlink(alice).isErr()).isTrue(); // nothing left to unbind
    }

    @Test
    void bothEndsOfTheBindingAreAnnouncedWithTheNameResolved() {
        LinkCode code = beginLink.begin(alice);
        confirmation.confirm(code.value(), "123456789012345678");
        unlink.unlink(alice);

        assertThat(published)
                .containsExactly(
                        new AccountLinked(alice, DiscordId.of("123456789012345678"), CLOCK.instant()),
                        new AccountUnlinked(alice, DiscordId.of("123456789012345678")));
    }

    @Test
    void theBridgeReportsModelledFailuresInsteadOfThrowing() {
        assertThat(confirmation.confirm("not a code", "123456789012345678").result())
                .isEqualTo(DiscordLinkConfirmation.Result.INVALID);
        assertThat(confirmation.confirm("ABC234", "123456789012345678").result())
                .isEqualTo(DiscordLinkConfirmation.Result.NO_CODE);

        // A Discord account already bound to a different player rejects a fresh code.
        store.confirm(new ConfirmedLink(
                UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                DiscordId.of("123456789012345678"),
                CLOCK.instant()));
        LinkCode code = beginLink.begin(alice);
        assertThat(confirmation.confirm(code.value(), "123456789012345678").result())
                .isEqualTo(DiscordLinkConfirmation.Result.ALREADY_LINKED);
    }

    /** A map-backed {@link DiscordLinkStore} mirroring the jOOQ store's per-player and uniqueness invariants. */
    private static final class FakeStore implements DiscordLinkStore {
        private final Map<UUID, PendingLink> pending = new HashMap<>();
        private final Map<UUID, ConfirmedLink> confirmed = new HashMap<>();

        @Override
        public void savePending(PendingLink row) {
            pending.put(row.player(), row);
        }

        @Override
        public Optional<PendingLink> findPendingByCode(LinkCode code) {
            return pending.values().stream().filter(r -> r.code().equals(code)).findFirst();
        }

        @Override
        public void deletePending(UUID player) {
            pending.remove(player);
        }

        @Override
        public void confirm(ConfirmedLink link) {
            confirmed.put(link.player(), link);
            pending.remove(link.player());
        }

        @Override
        public Optional<ConfirmedLink> findByPlayer(UUID player) {
            return Optional.ofNullable(confirmed.get(player));
        }

        @Override
        public Optional<ConfirmedLink> findByDiscordId(DiscordId discordId) {
            return confirmed.values().stream()
                    .filter(l -> l.discordId().equals(discordId))
                    .findFirst();
        }

        @Override
        public boolean unlink(UUID player) {
            return confirmed.remove(player) != null;
        }
    }

    /** Resolves the bound player's name so the seam outcome carries it. */
    private static final class StubLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(new PlayerRef(uuid, "Alice"));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }
}

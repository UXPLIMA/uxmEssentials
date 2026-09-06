package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ResidenceClaimProvider.ResidenceClaimLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ResidenceClaimProvider.ResidenceView;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The Residence provider with Residence absent, the case on the test classpath, where no {@code com.bekvon}
 * class resolves. {@link ResidenceClaimProvider#active()} must report inactive without naming a Residence type
 * and {@link ResidenceClaimProvider#claimAt} must degrade to empty, proving the present-guard keeps the
 * reflective Residence chain from loading on a server without Residence.
 *
 * <p>The Residence API chain cannot be stood up under MockBukkit, so the decisions that do not need a live
 * Residence. The case-insensitive owner match, the build-flag trust widening, the empty owner, the always-false
 * ban, and above all the UUID-to-name resolution and its {@code null} guard (a UUID the server has never seen)
 * are exercised against the pure {@link ResidenceView} seam instead. The unclaimed-space branch (a live
 * Residence returning {@code null} from {@code getByLoc}) needs a running Residence and is not reproducible here.
 */
class ResidenceClaimProviderTest {

    private static final String OWNER_NAME = "Alice";
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID BUILDER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID UNKNOWN = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void active_isFalse_whenResidenceAbsent() {
        ResidenceClaimProvider provider = new ResidenceClaimProvider(plugin, server, noOpLog());
        assertThat(provider.active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenResidenceAbsent() {
        ResidenceClaimProvider provider = new ResidenceClaimProvider(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThatCode(() -> assertThat(provider.claimAt(world, 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void owner_matchedByNameCaseInsensitively_isOwnerAndTrusted() {
        // The resolved name differs in case from the residence owner name; Residence compares owner names
        // case-insensitively, and so must we.
        ClaimLookup lookup = lookupOf(new FakeResidence(OWNER_NAME, Set.of(), Map.of(OWNER, "alice")));

        assertThat(lookup.isOwner(OWNER)).isTrue();
        assertThat(lookup.isTrusted(OWNER)).isTrue();
    }

    @Test
    void buildFlagHolder_isTrustedButNotOwner_andStrangerIsNeither() {
        ClaimLookup lookup =
                lookupOf(new FakeResidence(OWNER_NAME, Set.of("Bob"), Map.of(BUILDER, "Bob", STRANGER, "Carol")));

        assertThat(lookup.isTrusted(BUILDER)).isTrue();
        assertThat(lookup.isOwner(BUILDER)).isFalse();
        assertThat(lookup.isTrusted(STRANGER)).isFalse();
        assertThat(lookup.isOwner(STRANGER)).isFalse();
    }

    @Test
    void unknownUuid_withNullResolvedName_isNeitherOwnerNorTrusted() {
        // The name-resolution null guard: a UUID the server has never seen resolves to null, and a null name
        // must never match the owner or a flag holder: even here, where the flag set would otherwise grant it.
        ClaimLookup lookup = lookupOf(new FakeResidence(OWNER_NAME, Set.of("Ghost"), Map.of()));

        assertThat(lookup.isOwner(UNKNOWN)).isFalse();
        assertThat(lookup.isTrusted(UNKNOWN)).isFalse();
    }

    @Test
    void owner_isEmpty_andBannedIsAlwaysFalse() {
        ClaimLookup lookup = lookupOf(new FakeResidence(OWNER_NAME, Set.of(), Map.of(OWNER, OWNER_NAME)));

        assertThat(lookup.owner()).isEmpty();
        assertThat(lookup.isBanned(OWNER)).isFalse();
        assertThat(lookup.isBanned(STRANGER)).isFalse();
        assertThat(lookup.isBanned(UNKNOWN)).isFalse();
    }

    private static ClaimLookup lookupOf(ResidenceView residence) {
        return new ResidenceClaimLookup(residence);
    }

    /**
     * A {@link ResidenceView} whose answers come from a plain owner name, a set of build-flag holder names and a
     * UUID-to-name map, standing in for a real Residence. A UUID absent from the map resolves to {@code null},
     * modelling a player the server has never seen.
     */
    private record FakeResidence(String ownerName, Set<String> buildFlagHolders, Map<UUID, String> names)
            implements ResidenceView {

        @Override
        public boolean playerHas(String playerName, String flag) {
            return "build".equals(flag) && buildFlagHolders.contains(playerName);
        }

        @Override
        public Optional<String> resolveName(UUID player) {
            return Optional.ofNullable(names.get(player));
        }
    }

    private static Logger noOpLog() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }
}

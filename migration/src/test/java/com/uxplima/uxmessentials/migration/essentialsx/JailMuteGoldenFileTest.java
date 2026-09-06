package com.uxplima.uxmessentials.migration.essentialsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.essentialsx.map.JailMuteMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXLocation;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXUserdata;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.JailMuteParser;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.UserdataParser;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Golden-file round-trip for the EssentialsX jail/mute path (docs/12-migration §8.2, §5.1): parse the
 * checked-in userdata fixtures and assert the resulting {@code ModerationProfile} equals the expected
 * mute/jail state. This is the moderation leg of the three-way drift equality, code ⇄ doc ⇄ fixture,
 * and it catches the failure mode the drift guard cannot: the parser silently mis-reading a sanction
 * layout variant.
 */
class JailMuteGoldenFileTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GRIEFER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final JailMuteMapper mapper = new JailMuteMapper();

    @Test
    void aMutedPlayerMapsToATimedMuteState() throws IOException {
        ImportedModeration result = mapModeration(ALEX, "userdata/00000000-0000-0000-0000-000000000002.yml");

        assertThat(result.hasMute()).isTrue();
        assertThat(result.hasJail()).isFalse();
        assertThat(result.profile().mute()).isInstanceOf(MuteState.Timed.class);
        // muteTimeout: 1700100000000: the legacy top-level expiry key is read as a fallback.
        assertThat(result.profile().mute().expiry()).contains(Instant.ofEpochMilli(1_700_100_000_000L));
    }

    @Test
    void aJailedAndPermanentlyMutedPlayerMapsToBothSanctions() throws IOException {
        ImportedModeration result = mapModeration(GRIEFER, "userdata/00000000-0000-0000-0000-000000000003.yml");

        assertThat(result.hasJail()).isTrue();
        assertThat(result.hasMute()).isTrue();
        assertThat(result.profile().jail()).isInstanceOf(JailState.Active.class);
        JailState.Active jail = (JailState.Active) result.profile().jail();
        assertThat(jail.jail()).isEqualTo("cell");
        assertThat(jail.until()).contains(Instant.ofEpochMilli(1_700_200_000_000L));
        // muteReason present, no expiry (timestamps.mute = 0) -> a permanent mute.
        assertThat(result.profile().mute()).isInstanceOf(MuteState.Permanent.class);
        assertThat(result.profile().mute().expiry()).isEmpty();
    }

    @Test
    void aCleanPlayerMapsToNoModerationRecord() throws IOException {
        EssXUserdata steve = parse(STEVE, "userdata/00000000-0000-0000-0000-000000000001.yml");

        Optional<ImportedModeration> result = mapper.map(ref(steve), steve.moderation());

        assertThat(result).isEmpty();
    }

    @Test
    void jailDefinitionsParseToTheNamedLocations() throws IOException {
        Map<String, EssXLocation> jails;
        try (Reader reader = open("jail.yml")) {
            jails = new JailMuteParser().parseDefinitions(reader);
        }

        assertThat(jails).containsOnlyKeys("cell");
        assertThat(jails.get("cell").world()).isEqualTo("world");
        assertThat(jails.get("cell").x()).isEqualTo(50.0);
    }

    private ImportedModeration mapModeration(UUID uuid, String resource) throws IOException {
        EssXUserdata parsed = parse(uuid, resource);
        return mapper.map(ref(parsed), parsed.moderation()).orElseThrow();
    }

    private static PlayerRef ref(EssXUserdata parsed) {
        return new PlayerRef(parsed.uuid(), parsed.name());
    }

    private EssXUserdata parse(UUID uuid, String resource) throws IOException {
        try (Reader reader = open(resource)) {
            return new UserdataParser().parse(uuid, reader);
        }
    }

    private static Reader open(String resource) {
        InputStream stream =
                JailMuteGoldenFileTest.class.getClassLoader().getResourceAsStream("essentialsx/" + resource);
        if (stream == null) {
            throw new IllegalStateException("missing fixture: essentialsx/" + resource);
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}

package com.uxplima.uxmessentials.migration.essentialsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.UserdataMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXUserdata;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.UserdataParser;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Golden-file round-trip for the EssentialsX userdata path (docs/12-migration §8.2): parse a checked-in
 * fixture and assert the resulting domain aggregates equal the expected values. This catches the failure
 * mode the drift guard cannot: the parser silently mis-reading a layout variant. The world resolver is a
 * fake that knows the fixture's worlds, so the mapper is exercised without a live server.
 */
class UserdataGoldenFileTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final UserdataMapper mapper = new UserdataMapper(fakeWorlds());

    @Test
    void userdataWithThreeHomesAndABalanceMapsToExpectedAggregates() throws IOException {
        ImportedUser result = mapper.map(parse(STEVE, "userdata/00000000-0000-0000-0000-000000000001.yml"));

        assertThat(result.owner().uuid()).isEqualTo(STEVE);
        assertThat(result.owner().name()).isEqualTo("steve");
        // Homes are mapped in alphabetical name order for determinism: base→slot 0, farm→slot 1, mine→slot 2.
        assertThat(result.homes()).hasSize(3);
        assertThat(result.homes())
                .extracting(Home::slot)
                .containsExactly(HomeSlot.of(0), HomeSlot.of(1), HomeSlot.of(2));
        assertThat(result.homes())
                .extracting(h -> h.label().map(HomeLabel::value).orElse(""))
                .containsExactly("base", "farm", "mine");
        assertThat(result.balance()).contains(new BigDecimal("1500.00"));
        assertThat(result.mail()).hasSize(2);
    }

    @Test
    void userdataWithNoBalanceAndNoHomesMapsToEmptyDefaultsNotAFailure() throws IOException {
        ImportedUser result = mapper.map(parse(ALEX, "userdata/00000000-0000-0000-0000-000000000002.yml"));

        assertThat(result.owner().name()).isEqualTo("alex");
        assertThat(result.homes()).isEmpty();
        assertThat(result.balance()).isEmpty();
        assertThat(result.mail()).hasSize(1);
    }

    @Test
    void homeInAnUnknownWorldIsDroppedNotFatal() throws IOException {
        // A resolver that knows no world drops every home but still produces a valid user record.
        UserdataMapper noWorlds = new UserdataMapper(name -> Optional.empty());
        ImportedUser result = noWorlds.map(parse(STEVE, "userdata/00000000-0000-0000-0000-000000000001.yml"));

        assertThat(result.homes()).isEmpty();
        assertThat(result.balance()).contains(new BigDecimal("1500.00"));
    }

    private EssXUserdata parse(UUID uuid, String resource) throws IOException {
        try (Reader reader = open(resource)) {
            return new UserdataParser().parse(uuid, reader);
        }
    }

    private static Reader open(String resource) {
        InputStream stream =
                UserdataGoldenFileTest.class.getClassLoader().getResourceAsStream("essentialsx/" + resource);
        if (stream == null) {
            throw new IllegalStateException("missing fixture: essentialsx/" + resource);
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static WorldNameResolver fakeWorlds() {
        return name -> switch (name) {
            case "world" ->
                Optional.of(new WorldRef(UUID.nameUUIDFromBytes("world".getBytes(StandardCharsets.UTF_8)), "world"));
            case "world_nether" ->
                Optional.of(new WorldRef(
                        UUID.nameUUIDFromBytes("world_nether".getBytes(StandardCharsets.UTF_8)), "world_nether"));
            default -> Optional.empty();
        };
    }
}

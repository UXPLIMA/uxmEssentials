package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.SpawnCodec;
import com.uxplima.uxmessentials.worlds.domain.WeatherLock;
import com.uxplima.uxmessentials.worlds.domain.WorldDifficulty;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

// The static GameRule constants and getName() are removal-flagged, but they are the stable handles the
// applier resolves against; asserting through them keeps the test environment-agnostic across MockBukkit
// (snake_case names) and real Paper (camelCase).
@SuppressWarnings({"deprecation", "removal"})
class BukkitWorldSettingApplierTest {

    private ServerMock server;
    private BukkitWorldSettingApplier applier;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        server.addSimpleWorld("w");
        applier = new BukkitWorldSettingApplier(server, new BukkitGameRuleCatalog(), new NoOpLogger());
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void appliesPvpDifficultyAndGamerule() {
        World world = server.getWorld("w");
        var settings = WorldSettings.defaults()
                .with(WorldProperties.PVP, false)
                .with(WorldProperties.DIFFICULTY, WorldDifficulty.HARD)
                // store under the constant's reported name so the assertion is environment-agnostic
                // (MockBukkit reports snake_case; real Paper camelCase).
                .withRaw(WorldSettings.gameruleKey(GameRule.KEEP_INVENTORY.getName()), "true");

        applier.apply(WorldName.of("w"), settings);

        assertThat(world).isNotNull();
        assertThat(world.getPVP()).isFalse();
        assertThat(world.getDifficulty()).isEqualTo(Difficulty.HARD);
        assertThat(world.getGameRuleValue(GameRule.KEEP_INVENTORY)).isTrue();
    }

    @Test
    void appliesMonsterSpawningAndIntegerGamerule() {
        World world = server.getWorld("w");
        var settings = WorldSettings.defaults()
                .with(WorldProperties.SPAWN_MONSTERS, false)
                .withRaw(WorldSettings.gameruleKey(GameRule.RANDOM_TICK_SPEED.getName()), "7");

        applier.apply(WorldName.of("w"), settings);

        // The animal half of the old spawn flags is gone from the server API; AnimalSpawnListener holds it.
        assertThat(world.getAllowMonsters()).isFalse();
        assertThat(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)).isEqualTo(7);
    }

    @Test
    void lockedTimeFreezesDaylightCycleAndSetsTime() {
        World world = server.getWorld("w");
        var settings = WorldSettings.defaults().with(WorldProperties.TIME, 6000L);

        applier.apply(WorldName.of("w"), settings);

        assertThat(world.getTime()).isEqualTo(6000L);
        assertThat(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)).isFalse();
    }

    @Test
    void absentTimeLeavesDaylightCycleRunning() {
        World world = server.getWorld("w");

        applier.apply(WorldName.of("w"), WorldSettings.defaults());

        assertThat(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)).isTrue();
    }

    @Test
    void thunderLockSetsStormThunderAndFreezesWeatherCycle() {
        World world = server.getWorld("w");
        var settings = WorldSettings.defaults().with(WorldProperties.WEATHER, WeatherLock.THUNDER);

        applier.apply(WorldName.of("w"), settings);

        assertThat(world.hasStorm()).isTrue();
        assertThat(world.isThundering()).isTrue();
        assertThat(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE)).isFalse();
    }

    @Test
    void clearWeatherLockClearsStormAndFreezesWeatherCycle() {
        World world = server.getWorld("w");
        world.setStorm(true);
        world.setThundering(true);
        var settings = WorldSettings.defaults().with(WorldProperties.WEATHER, WeatherLock.CLEAR);

        applier.apply(WorldName.of("w"), settings);

        assertThat(world.hasStorm()).isFalse();
        assertThat(world.isThundering()).isFalse();
        assertThat(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE)).isFalse();
    }

    @Test
    void noneWeatherLeavesWeatherCycleRunning() {
        World world = server.getWorld("w");

        applier.apply(WorldName.of("w"), WorldSettings.defaults());

        assertThat(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE)).isTrue();
    }

    // MockBukkit's WorldMock#setSpawnLocation(int,int,int,float) throws UnimplementedOperationException
    // (a TestAbortedException), so the applier's spawn path, which carries yaw, can only be verified
    // end-to-end on a real server. We assert the parser yields exactly the components the applier feeds
    // setSpawnLocation; the apply call below either lands them (real server) or aborts on the known gap.
    @Test
    void appliesSpawnLocation() {
        World world = server.getWorld("w");
        var settings = WorldSettings.defaults().withRaw(WorldSettings.spawnKey(), "10.0;64.0;-20.0;90.0;0.0");
        double[] components =
                SpawnCodec.parseComponents("10.0;64.0;-20.0;90.0;0.0").orElseThrow();
        assertThat(components).containsExactly(10.0, 64.0, -20.0, 90.0, 0.0);

        applier.apply(WorldName.of("w"), settings); // aborts (skips) under MockBukkit; lands the spawn on Paper
        assertThat(world.getSpawnLocation().getBlockX()).isEqualTo(10);
        assertThat(world.getSpawnLocation().getBlockY()).isEqualTo(64);
        assertThat(world.getSpawnLocation().getBlockZ()).isEqualTo(-20);
    }

    @Test
    void unknownGameruleIsSkipped() {
        World world = server.getWorld("w");
        var settings = WorldSettings.defaults().withRaw(WorldSettings.gameruleKey("nopeNotARule"), "true");

        assertThatCode(() -> applier.apply(WorldName.of("w"), settings)).doesNotThrowAnyException();
        assertThat(world).isNotNull();
    }

    @Test
    void noOpsForUnloadedWorld() {
        assertThatCode(() -> applier.apply(WorldName.of("missing"), WorldSettings.defaults()))
                .doesNotThrowAnyException();
    }

    private static final class NoOpLogger implements Logger {
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

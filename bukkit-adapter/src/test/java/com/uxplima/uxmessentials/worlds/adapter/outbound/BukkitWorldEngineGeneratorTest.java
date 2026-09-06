package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pins the engine's generator-routing decision: a {@code uxmEssentials:void|flat} ref takes the object
 * overload (the resolver's own {@link ChunkGenerator} is set on the {@link WorldCreator}), while any
 * other token (an external {@code plugin[:args]} ref) takes Bukkit's String overload unchanged. The
 * resolver is built once against a running mock server (its generators resolve biomes via the registry).
 */
class BukkitWorldEngineGeneratorTest {

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static BukkitWorldEngine engine(WorldGeneratorResolver resolver) {
        return new BukkitWorldEngine(MockBukkit.getMock(), new NoopLogger(), resolver);
    }

    private static WorldGeneratorResolver resolver() {
        return new WorldGeneratorResolver(
                FlatLayerPlan.defaults(), BiomeId.of("plains"), BiomeId.of("plains"), new NoopLogger());
    }

    @Test
    void aBuiltInVoidRefPicksTheResolversObjectGenerator() {
        WorldGeneratorResolver resolver = resolver();
        WorldCreator creator = new WorldCreator("routing-void");

        engine(resolver).applyGenerator(creator, BuiltInGenerators.ref(BuiltInGenerators.VOID));

        ChunkGenerator expected = resolver.resolve(BuiltInGenerators.VOID).orElseThrow();
        assertThat(creator.generator()).isSameAs(expected);
    }

    @Test
    void aBuiltInFlatRefPicksTheResolversObjectGenerator() {
        WorldGeneratorResolver resolver = resolver();
        WorldCreator creator = new WorldCreator("routing-flat");

        engine(resolver).applyGenerator(creator, BuiltInGenerators.ref(BuiltInGenerators.FLAT));

        ChunkGenerator expected = resolver.resolve(BuiltInGenerators.FLAT).orElseThrow();
        assertThat(creator.generator()).isSameAs(expected);
    }

    @Test
    void anExternalRefDoesNotUseAnyOfTheResolversGenerators() {
        WorldGeneratorResolver resolver = resolver();
        WorldCreator creator = new WorldCreator("routing-external");

        // A foreign plugin ref goes through the String overload; the mock has no such plugin, so the
        // creator's generator is left null, and is certainly never one of our resolver's instances.
        engine(resolver).applyGenerator(creator, GeneratorRef.of("Multiverse:flat"));

        assertThat(creator.generator())
                .isNotSameAs(resolver.resolve(BuiltInGenerators.VOID).orElseThrow())
                .isNotSameAs(resolver.resolve(BuiltInGenerators.FLAT).orElseThrow());
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

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadQuery;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The six custom-item icon providers (ItemsAdder, Oraxen, Nexo, CraftEngine, MMOItems, ExecutableItems) in the only
 * states a
 * test without those SDKs on the classpath can reach. Each is built purely by reflection behind a plugin-present guard, so the proved
 * contract is the load-safe one, exactly as the currency reflection providers prove:
 *
 * <ul>
 *   <li><b>Absent</b>. With the plugin not installed, a matching spec resolves to empty silently (the present-guard
 *       short-circuits before any reflection, so no warning fires and no SDK class loads), and structurally no field
 *       or method signature on the provider (or the shared {@link ReflectiveItemProvider} base) names the plugin's
 *       SDK package, so loading the class on a plugin-less server pulls in none of it.
 *   <li><b>Prefix routing</b>. A provider claims only its own {@code <plugin>:} prefix, returning empty for a bare
 *       material or another provider's prefix, and the {@link IconProviders#full} chain therefore routes each prefix
 *       to exactly one provider while leaving plain materials and the existing skull source untouched.
 *   <li><b>Present but shape-shifted</b>. With the plugin enabled but its API class genuinely missing (the SDK is
 *       not on the test classpath), the lookup runs, fails, is caught, and degrades to empty after exactly one
 *       warning, proving the prefix reached the reflective lookup and that a version bump never throws into a render.
 * </ul>
 *
 * <p>The happy path (a real custom item resolved) cannot be faked without the SDKs on the classpath, the absent and
 * routing paths are the contract, exactly like the currency reflection providers.
 */
class CustomItemIconProvidersTest {

    private ServerMock server;
    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        ctx = MenuContext.of(new PlayerRef(player.getUniqueId(), player.getName()), null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void absentPlugin_matchingSpecIsSilentlyEmpty() {
        assertAbsentIsSilentEmpty(log -> new ItemsAdderIconProvider(server, log), "itemsadder:ruby_sword");
        assertAbsentIsSilentEmpty(log -> new OraxenIconProvider(server, log), "oraxen:ruby_sword");
        assertAbsentIsSilentEmpty(log -> new NexoIconProvider(server, log), "nexo:ruby_sword");
        assertAbsentIsSilentEmpty(log -> new CraftEngineIconProvider(server, log), "craftengine:default:ruby_sword");
        assertAbsentIsSilentEmpty(log -> new MMOItemsIconProvider(server, log), "mmoitems:SWORD:ruby");
        assertAbsentIsSilentEmpty(log -> new ExecutableItemsIconProvider(server, log), "ei:ruby_sword");
    }

    @Test
    void absentProviderAndBaseDeclareNoSdkType() {
        // Loading any provider (and the base it inherits) on a plugin-less server must pull in zero SDK class, the
        // structural guarantee that the present-guard, not a classload, is what gates the reflection.
        assertThat(referencesPackage(ItemsAdderIconProvider.class, "dev.lone")).isFalse();
        assertThat(referencesPackage(OraxenIconProvider.class, "io.th0rgal")).isFalse();
        assertThat(referencesPackage(NexoIconProvider.class, "com.nexomc")).isFalse();
        assertThat(referencesPackage(CraftEngineIconProvider.class, "net.momirealms"))
                .isFalse();
        assertThat(referencesPackage(MMOItemsIconProvider.class, "net.Indyuce")).isFalse();
        assertThat(referencesPackage(ExecutableItemsIconProvider.class, "com.ssomar"))
                .isFalse();
        // The shared base itself names none of the four SDK packages.
        assertThat(referencesPackage(ReflectiveItemProvider.class, "dev.lone")).isFalse();
        assertThat(referencesPackage(ReflectiveItemProvider.class, "io.th0rgal"))
                .isFalse();
        assertThat(referencesPackage(ReflectiveItemProvider.class, "com.nexomc"))
                .isFalse();
        assertThat(referencesPackage(ReflectiveItemProvider.class, "net.momirealms"))
                .isFalse();
        assertThat(referencesPackage(ReflectiveItemProvider.class, "net.Indyuce"))
                .isFalse();
        assertThat(referencesPackage(ReflectiveItemProvider.class, "com.ssomar"))
                .isFalse();
    }

    @Test
    void eachProviderClaimsOnlyItsOwnPrefix() {
        IconProvider itemsAdder = new ItemsAdderIconProvider(server, SILENT);
        // A bare material and a sibling's prefix both fall through, so the spec is left for the material fallback.
        assertThat(itemsAdder.icon("DIAMOND", ctx)).isEmpty();
        assertThat(itemsAdder.icon("oraxen:ruby", ctx)).isEmpty();
        assertThat(itemsAdder.icon("nexo:ruby", ctx)).isEmpty();
        assertThat(itemsAdder.icon("craftengine:ruby", ctx)).isEmpty();
        assertThat(itemsAdder.icon("mmoitems:SWORD:ruby", ctx)).isEmpty();
        assertThat(new OraxenIconProvider(server, SILENT).icon("itemsadder:ruby", ctx))
                .isEmpty();
        assertThat(new NexoIconProvider(server, SILENT).icon("oraxen:ruby", ctx))
                .isEmpty();
        assertThat(new CraftEngineIconProvider(server, SILENT).icon("nexo:ruby", ctx))
                .isEmpty();
        assertThat(new MMOItemsIconProvider(server, SILENT).icon("DIAMOND", ctx))
                .isEmpty();
        assertThat(new ExecutableItemsIconProvider(server, SILENT).icon("nexo:ruby", ctx))
                .isEmpty();
    }

    @Test
    void mmoItemsMalformedIdIsEmpty() {
        // The bare id must be <TYPE>:<ID>; a single token cannot be a type/id pair, so it never resolves an item.
        assertThat(new MMOItemsIconProvider(server, SILENT).icon("mmoitems:NOTYPE", ctx))
                .isEmpty();
    }

    @Test
    void fullChainRoutesEachPrefixDisjointlyAndKeepsSkullsAndPlainMaterials() {
        IconProviders chain = IconProviders.full(server, SILENT, HeadQuery.ABSENT);

        // The custom-item plugins are absent, so each prefix degrades to empty (the renderer's material fallback).
        assertThatCode(() -> {
                    assertThat(chain.resolve("itemsadder:ruby", ctx)).isEmpty();
                    assertThat(chain.resolve("oraxen:ruby", ctx)).isEmpty();
                    assertThat(chain.resolve("nexo:ruby", ctx)).isEmpty();
                    assertThat(chain.resolve("craftengine:default:ruby", ctx)).isEmpty();
                    assertThat(chain.resolve("mmoitems:SWORD:ruby", ctx)).isEmpty();
                    assertThat(chain.resolve("mmoitems:NOTYPE", ctx)).isEmpty();
                    assertThat(chain.resolve("ei:ruby", ctx)).isEmpty();
                    // A plain material is claimed by no provider and left for the material lookup.
                    assertThat(chain.resolve("DIAMOND", ctx)).isEmpty();
                })
                .doesNotThrowAnyException();
        // The custom providers slot in without disturbing the no-dependency skull source already in the chain.
        assertThat(chain.resolve("skull:Notch", ctx))
                .get()
                .extracting(item -> item.getType())
                .isEqualTo(Material.PLAYER_HEAD);
    }

    @Test
    void presentPluginButMissingSdk_reachesLookupAndWarnsExactlyOnceThenEmpty() {
        assertReachesLookupAndWarnsOnce("ItemsAdder", log -> new ItemsAdderIconProvider(server, log), "itemsadder:x");
        assertReachesLookupAndWarnsOnce("Oraxen", log -> new OraxenIconProvider(server, log), "oraxen:x");
        assertReachesLookupAndWarnsOnce("Nexo", log -> new NexoIconProvider(server, log), "nexo:x");
        assertReachesLookupAndWarnsOnce(
                "CraftEngine", log -> new CraftEngineIconProvider(server, log), "craftengine:default:x");
        assertReachesLookupAndWarnsOnce("MMOItems", log -> new MMOItemsIconProvider(server, log), "mmoitems:SWORD:x");
        assertReachesLookupAndWarnsOnce("ExecutableItems", log -> new ExecutableItemsIconProvider(server, log), "ei:x");
    }

    /** With its plugin absent, a matching spec is empty and silent, the present-guard runs no reflection. */
    private void assertAbsentIsSilentEmpty(Function<Logger, IconProvider> factory, String spec) {
        CountingLogger log = new CountingLogger();
        IconProvider provider = factory.apply(log);
        assertThatCode(() -> assertThat(provider.icon(spec, ctx)).isEmpty()).doesNotThrowAnyException();
        assertThat(log.warns()).isZero();
    }

    /**
     * With a fake plugin of {@code pluginName} enabled but its SDK class genuinely absent from the test classpath,
     * the matching spec passes the present-guard, attempts the reflective lookup, fails, and degrades to empty after
     * exactly one warning: even across repeat calls (warn-once). That the warning fires at all proves the prefix
     * reached the lookup; that nothing is thrown proves a shape shift never aborts a render.
     */
    private void assertReachesLookupAndWarnsOnce(
            String pluginName, Function<Logger, IconProvider> factory, String spec) {
        MockBukkit.createMockPlugin(pluginName);
        CountingLogger log = new CountingLogger();
        IconProvider provider = factory.apply(log);
        assertThatCode(() -> {
                    assertThat(provider.icon(spec, ctx)).isEmpty();
                    assertThat(provider.icon(spec, ctx)).isEmpty();
                })
                .doesNotThrowAnyException();
        assertThat(log.warns()).isEqualTo(1);
    }

    /** Whether {@code type} (walking up to {@code Object}) declares any field or method signature in {@code prefix}. */
    private static boolean referencesPackage(Class<?> type, String prefix) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (inPackage(method.getReturnType(), prefix)) {
                    return true;
                }
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (inPackage(parameter, prefix)) {
                        return true;
                    }
                }
            }
            for (Field field : c.getDeclaredFields()) {
                if (inPackage(field.getType(), prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inPackage(Class<?> type, String prefix) {
        return type.getName().startsWith(prefix);
    }

    /** A {@link Logger} that counts warnings: these tests assert how often a reflective failure is reported. */
    private static final class CountingLogger implements Logger {
        private final AtomicInteger warns = new AtomicInteger();

        int warns() {
            return warns.get();
        }

        @Override
        public void warn(String message, Object... args) {
            warns.incrementAndGet();
        }

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A {@link Logger} that drops every line: for the routing tests, which assert behaviour, not log output. */
    private static final Logger SILENT = new Logger() {
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

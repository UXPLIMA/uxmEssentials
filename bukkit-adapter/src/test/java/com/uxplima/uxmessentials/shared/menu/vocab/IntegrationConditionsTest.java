package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.IntegrationConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.EconomyBackends;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.FakeCurrencyBackend;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.PermissionQuery;
import com.uxplima.uxmessentials.shared.application.port.ClientProtocol;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the integration condition pack. The substrate-backed gates run over real collaborators: a
 * fake {@link PermissionQuery} for {@code has-group}, a real {@link Currencies} over an in-memory economy for
 * {@code has-points}, the {@link PlayerMock}'s own live world weather for {@code weather}, and a fake {@link Cooldowns}
 * for {@code cooldown} / {@code set-cooldown}. JobsReborn, WorldGuard and mcMMO are not on the test classpath, so their
 * gates are asserted on the contract a plugin-less test can reach (the fail-closed absent path) plus the structural
 * guarantee that the pack, its reflective nested helpers and the mcMMO reader beside it name none of their SDK
 * packages, so loading them on a plugin-less server pulls in zero SDK class. Every condition is asserted both ways where possible, and the
 * fail-closed contract is pinned by the offline-viewer and malformed-argument cases.
 */
class IntegrationConditionsTest {

    private ServerMock server;
    private World world;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private FakeCurrencyBackend economy;
    private FakePermissions permissions;
    private FakeCooldowns cooldowns;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        viewer = server.addPlayer("Viewer");
        viewer.teleport(new Location(world, 0, 64, 0));
        bindings = new MenuBindings();
        economy = new FakeCurrencyBackend("vault");
        permissions = new FakePermissions("vip");
        cooldowns = new FakeCooldowns();
        IntegrationConditions.register(
                bindings,
                permissions,
                currenciesOver(economy),
                cooldowns,
                ClientProtocol.UNAVAILABLE,
                server,
                new RecordingLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- has-group -----------------------------------------------------------------------------------------------

    @Test
    void hasGroupPassesForAMemberOfTheGroup() {
        assertThat(test("has-group", "vip")).isTrue();
        assertThat(test("has-group", "admin")).isFalse();
    }

    @Test
    void hasGroupIsReachableUnderEveryAlias() {
        assertThat(test("luckperm-group", "vip")).isTrue();
        assertThat(test("group", "vip")).isTrue();
    }

    @Test
    void hasGroupFailsClosedOnABlankGroupOrAbsentVault() {
        assertThat(test("has-group", "")).as("blank group").isFalse();
        // With the Vault permission seam absent (its no-op default), every group check answers false.
        MenuBindings absentBindings = new MenuBindings();
        IntegrationConditions.register(
                absentBindings,
                PermissionQuery.ABSENT,
                currenciesOver(economy),
                cooldowns,
                ClientProtocol.UNAVAILABLE,
                server,
                new RecordingLogger());
        assertThat(fire(absentBindings, "has-group", "vip", online())).isFalse();
    }

    // --- has-points ----------------------------------------------------------------------------------------------

    @Test
    void hasPointsRoutesToPlayerPointsNotTheVaultDefault() {
        // PlayerPoints is absent in the mock, so its provider never reports a balance; the vault balance would have
        // passed at 150, so a false here proves has-points routed to playerpoints rather than the default currency.
        economy.balances.put(viewer.getUniqueId(), 150.0);

        assertThat(test("has-points", "50")).isFalse();
    }

    @Test
    void hasPointsFailsClosedOnANonNumericAmount() {
        assertThat(test("has-points", "lots")).isFalse();
        assertThat(test("has-points", "")).isFalse();
    }

    // --- weather -------------------------------------------------------------------------------------------------

    @Test
    void weatherMatchesClearBothWays() {
        world.setStorm(false);
        world.setThundering(false);

        assertThat(test("weather", "clear")).isTrue();
        assertThat(test("weather", "rain")).isFalse();
        assertThat(test("weather", "thunder")).isFalse();
    }

    @Test
    void weatherMatchesRainBothWays() {
        world.setStorm(true);
        world.setThundering(false);

        assertThat(test("weather", "rain")).isTrue();
        assertThat(test("weather", "clear")).isFalse();
        assertThat(test("weather", "thunder")).isFalse();
    }

    @Test
    void weatherMatchesThunderBothWays() {
        world.setStorm(true);
        world.setThundering(true);

        assertThat(test("weather", "thunder")).isTrue();
        assertThat(test("weather", "rain")).as("thunder is not plain rain").isFalse();
        assertThat(test("weather", "clear")).isFalse();
    }

    @Test
    void weatherFailsClosedOnAnUnknownTokenOrOfflineViewer() {
        world.setStorm(false);
        world.setThundering(false);

        assertThat(test("weather", "hail")).as("unknown token").isFalse();
        assertThat(testOffline("weather", "clear")).as("offline viewer").isFalse();
    }

    // --- cooldown / set-cooldown ---------------------------------------------------------------------------------

    @Test
    void cooldownIsReadyUntilTheLabelIsStamped() {
        // A fresh, never-stamped label is ready (not on cooldown), so the gate passes.
        assertThat(test("cooldown", "daily")).isTrue();

        cooldowns.stampLabel(online(), "daily");

        // Now the label is on cooldown, so the gate fails: the ready/expired semantics the condition documents.
        assertThat(test("cooldown", "daily")).isFalse();
    }

    @Test
    void setCooldownActionStampsTheLabel() {
        assertThat(test("cooldown", "kit")).as("ready before").isTrue();

        runAction("set-cooldown", "kit");

        assertThat(cooldowns.stamped).contains("kit");
        assertThat(test("cooldown", "kit")).as("on cooldown after set-cooldown").isFalse();
    }

    @Test
    void setCooldownIgnoresTheOptionalSecondsToken() {
        // The [seconds] token is accepted for grammar but is not the label; the label is the first token only.
        runAction("set-cooldown", "weekly 3600");

        assertThat(cooldowns.stamped).contains("weekly");
        assertThat(cooldowns.stamped).doesNotContain("3600");
        assertThat(test("cooldown", "weekly")).isFalse();
    }

    @Test
    void cooldownFailsClosedOnABlankLabel() {
        assertThat(test("cooldown", "")).isFalse();
    }

    // --- job / worldguard-region (absent path) -------------------------------------------------------------------

    @Test
    void jobAndRegionFailClosedWhenTheirPluginsAreAbsent() {
        assertThatCode(() -> {
                    assertThat(test("job", "miner")).isFalse();
                    assertThat(test("worldguard-region", "spawn")).isFalse();
                    assertThat(test("wg-region", "spawn")).isFalse();
                    assertThat(test("region", "spawn")).isFalse();
                })
                .as("an absent integration never throws")
                .doesNotThrowAnyException();
    }

    // --- mcmmo (absent path) -------------------------------------------------------------------------------------

    @Test
    void mcmmoGatesFailClosedWhenMcmmoIsAbsent() {
        assertThatCode(() -> {
                    assertThat(test("mcmmo-level", "mining:50")).isFalse();
                    assertThat(test("mcmmo-power", "1000")).isFalse();
                })
                .as("an absent integration never throws")
                .doesNotThrowAnyException();
    }

    @Test
    void mcmmoLevelFailsClosedOnAMalformedArgument() {
        // A skill with no level, a level that is not a number, and nothing at all are all authoring mistakes, and
        // an unreadable gate must deny rather than open every item it guards.
        assertThat(test("mcmmo-level", "mining")).as("no level").isFalse();
        assertThat(test("mcmmo-level", "mining:soon"))
                .as("level is not a number")
                .isFalse();
        assertThat(test("mcmmo-level", "")).as("blank").isFalse();
        assertThat(test("mcmmo-power", "lots")).as("power is not a number").isFalse();
    }

    @Test
    void mcmmoGatesFailClosedForAnOfflineViewer() {
        assertThat(testOffline("mcmmo-level", "mining:1")).isFalse();
        assertThat(testOffline("mcmmo-power", "1")).isFalse();
    }

    @Test
    void theMcmmoReaderDeclaresNoSdkType() throws ClassNotFoundException {
        // Same structural guarantee as the pack itself: the mcMMO reader names its SDK only by string class-name,
        // so loading it on a server without mcMMO pulls in zero com.gmail.nossr50 class.
        Class<?> reader =
                Class.forName("com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.McMmoIntegration");

        assertThat(declaresPackage(reader, "com.gmail.nossr50"))
                .as("mcMMO SDK type in a signature")
                .isFalse();
    }

    @Test
    void integrationConditionsDeclareNoSdkTypeAnywhere() {
        // Loading the pack (and its reflective nested helpers) on a plugin-less server must pull in zero SDK class
        // the structural guarantee that the present-guard, not a classload, is what gates the reflection. Every SDK
        // reference is a string class-name, so no field or method signature names the JobsReborn / WorldGuard package.
        assertThat(declaresPackage(IntegrationConditions.class, "com.gamingmesh"))
                .as("JobsReborn SDK type in a signature")
                .isFalse();
        assertThat(declaresPackage(IntegrationConditions.class, "com.sk89q"))
                .as("WorldGuard/WorldEdit SDK type in a signature")
                .isFalse();
    }

    // --- harness -------------------------------------------------------------------------------------------------

    private boolean test(String id, String arg) {
        return fire(bindings, id, arg, online());
    }

    private boolean testOffline(String id, String arg) {
        return fire(bindings, id, arg, new PlayerRef(UUID.randomUUID(), "Ghost"));
    }

    private PlayerRef online() {
        return new PlayerRef(viewer.getUniqueId(), viewer.getName());
    }

    private static boolean fire(MenuBindings bindings, String id, String arg, PlayerRef ref) {
        BiPredicate<MenuContext, Map<String, String>> condition =
                bindings.condition(id).orElseThrow(() -> new AssertionError("condition not registered: " + id));
        return condition.test(MenuContext.of(ref, null, 0), Map.of("value", arg));
    }

    private void runAction(String id, String arg) {
        Consumer<MenuActionContext> action =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(online(), null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
        action.accept(ctx);
    }

    /** A real {@link Currencies} whose default {@code vault} spec resolves a synthetic currency over {@code backend}. */
    private Currencies currenciesOver(FakeCurrencyBackend backend) {
        return new Currencies(
                () -> new EconomyBackends(
                        CurrencyBackendRegistry.of(List.of(backend)),
                        CurrencyRegistry.single(
                                Currency.builder(CurrencyId.of("coins")).build())),
                new RecordingLogger(),
                "vault");
    }

    /** Whether {@code type} (or a nested class it declares) names {@code prefix} in any field or method signature. */
    private static boolean declaresPackage(Class<?> type, String prefix) {
        if (referencesPackage(type, prefix)) {
            return true;
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (referencesPackage(nested, prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code type} (walking up to {@code Object}) declares any field or method signature in {@code prefix}. */
    private static boolean referencesPackage(Class<?> type, String prefix) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getReturnType().getName().startsWith(prefix)) {
                    return true;
                }
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (parameter.getName().startsWith(prefix)) {
                        return true;
                    }
                }
            }
            for (Field field : c.getDeclaredFields()) {
                if (field.getType().getName().startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A {@link PermissionQuery} whose only member is {@code allowedGroup}; every other op is a no-op. */
    private static final class FakePermissions implements PermissionQuery {

        private final String allowedGroup;

        FakePermissions(String allowedGroup) {
            this.allowedGroup = allowedGroup;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean inGroup(UUID player, String group) {
            return allowedGroup.equals(group);
        }

        @Override
        public boolean has(UUID player, String node) {
            return false;
        }

        @Override
        public boolean add(UUID player, String node) {
            return false;
        }

        @Override
        public boolean remove(UUID player, String node) {
            return false;
        }

        @Override
        public String primaryGroup(UUID player) {
            return "";
        }
    }

    /** An in-memory {@link Cooldowns} keyed by label: a stamped label is on cooldown (err), an unstamped one is ready. */
    private static final class FakeCooldowns implements Cooldowns {

        private final Set<String> stamped = new HashSet<>();

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return stamped.contains(label) ? Result.err(Duration.ofSeconds(5)) : Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            stamped.add(label);
        }
    }

    /** Discards every log line; these tests assert behaviour, not log output. */
    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

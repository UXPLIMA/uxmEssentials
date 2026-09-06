package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.EconomyActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.EconomyBackends;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.FakeCurrencyBackend;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.PermissionQuery;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the economy action pack. The money actions run over a real {@link Currencies} whose default
 * {@code vault} spec resolves a synthetic currency over an in-memory {@link FakeCurrencyBackend}, so a deposit /
 * withdraw / set is asserted concretely against the fake balance; the {@code playerpoints} back-end is absent in the
 * mock, so a {@code playerpoints}-routed action is proved not to touch the default vault balance. The experience and
 * level actions ride Paper's native {@code giveExp} / {@code giveExpLevels}, which MockBukkit models over its
 * level/experience fields, so those are asserted concretely, including the {@code take} clamps at zero. The
 * permission actions record their grant/revoke against a fake {@link PermissionQuery}, and the absent-Vault path
 * ({@link PermissionQuery#ABSENT}) is proved to be a silent, throw-free no-op.
 */
class EconomyActionsTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private RecordingLogger log;
    private FakeCurrencyBackend economy;
    private Currencies currencies;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        log = new RecordingLogger();
        economy = new FakeCurrencyBackend("vault");
        currencies = currenciesOver(economy);
        EconomyActions.register(bindings, currencies, PermissionQuery.ABSENT, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- money ---------------------------------------------------------------------------------------------------

    @Test
    void giveMoneyDepositsIntoTheDefaultCurrency() {
        invoke("give-money", "100");

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(100.0);
    }

    @Test
    void giveMoneyAliasIsRegistered() {
        invoke("givemoney", "25");

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(25.0);
    }

    @Test
    void takeMoneyWithdrawsFromTheDefaultCurrency() {
        economy.balances.put(viewer.getUniqueId(), 100.0);

        invoke("take-money", "40");

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(60.0);
    }

    @Test
    void setMoneyRaisesTheBalanceToTheTarget() {
        economy.balances.put(viewer.getUniqueId(), 100.0);

        invoke("set-money", "250");

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(250.0);
    }

    @Test
    void setMoneyLowersTheBalanceToTheTarget() {
        economy.balances.put(viewer.getUniqueId(), 400.0);

        invoke("set-money", "250");

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(250.0);
    }

    @Test
    void setMoneyLeavesAnAlreadyMatchingBalanceAlone() {
        economy.balances.put(viewer.getUniqueId(), 250.0);

        invoke("set-money", "250");

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(250.0);
        // No move happened at all, not a deposit-then-withdraw round-trip.
        assertThat(economy.deposits).isZero();
        assertThat(economy.withdrawals).isZero();
    }

    @Test
    void giveMoneyWithANamedCurrencyRoutesAwayFromTheDefault() {
        economy.balances.put(viewer.getUniqueId(), 10.0);

        // PlayerPoints is absent in the mock, so its provider no-ops; the point is that the default vault balance is
        // untouched: the amount did not land in the default currency.
        invoke("give-money", "50 playerpoints");

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(10.0);
    }

    @Test
    void givePointsDoesNotTouchTheDefaultCurrency() {
        economy.balances.put(viewer.getUniqueId(), 10.0);

        assertThatCode(() -> invoke("give-points", "5")).doesNotThrowAnyException();

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(10.0);
    }

    @Test
    void malformedMoneyAmountIsAFailSoftNoOpThatWarns() {
        economy.balances.put(viewer.getUniqueId(), 10.0);

        assertThatCode(() -> invoke("give-money", "lots")).doesNotThrowAnyException();

        assertThat(economy.balances.get(viewer.getUniqueId())).isEqualTo(10.0);
        assertThat(log.warnings).anyMatch(line -> line.contains("malformed_amount"));
    }

    // --- experience / levels -------------------------------------------------------------------------------------

    @Test
    void giveExpRaisesTheViewersExperience() {
        viewer.setLevel(0);
        viewer.setExp(0f);

        invoke("give-exp", "100");

        assertThat(viewer.getTotalExperience()).isEqualTo(100);
        assertThat(viewer.getLevel()).isGreaterThan(0);
    }

    @Test
    void takeExpNeverDrivesExperienceNegative() {
        viewer.setLevel(0);
        viewer.setExp(0f);
        invoke("give-exp", "100");

        invoke("take-exp", "40");
        assertThat(viewer.getTotalExperience()).isEqualTo(60);

        // Take far more than the viewer holds: it empties out and never underflows or throws.
        assertThatCode(() -> invoke("take-exp", "1000")).doesNotThrowAnyException();
        assertThat(viewer.getTotalExperience()).isZero();
    }

    @Test
    void giveLevelsRaisesTheViewersLevel() {
        viewer.setLevel(0);

        invoke("give-levels", "5");

        assertThat(viewer.getLevel()).isEqualTo(5);
    }

    @Test
    void takeLevelsSubtractsAndClampsAtZero() {
        viewer.setLevel(5);

        invoke("take-levels", "3");
        assertThat(viewer.getLevel()).isEqualTo(2);

        // Take more levels than the viewer holds: the level clamps at zero rather than going negative.
        invoke("take-levels", "10");
        assertThat(viewer.getLevel()).isZero();
    }

    @Test
    void experienceAliasesAreRegistered() {
        assertThat(bindings.action("give-xp")).isPresent();
        assertThat(bindings.action("take-xp")).isPresent();
        assertThat(bindings.action("give-level")).isPresent();
        assertThat(bindings.action("take-level")).isPresent();
    }

    // --- permission ----------------------------------------------------------------------------------------------

    @Test
    void givePermissionGrantsTheNodeThroughVault() {
        FakePermissionQuery permissions = new FakePermissionQuery();
        MenuBindings permBindings = new MenuBindings();
        EconomyActions.register(permBindings, currencies, permissions, log);

        invoke(permBindings, "give-permission", "essentials.fly");

        assertThat(permissions.added).containsExactly(entry(viewer.getUniqueId(), "essentials.fly"));
    }

    @Test
    void takePermissionRevokesTheNodeThroughVault() {
        FakePermissionQuery permissions = new FakePermissionQuery();
        MenuBindings permBindings = new MenuBindings();
        EconomyActions.register(permBindings, currencies, permissions, log);

        invoke(permBindings, "take-perm", "essentials.fly");

        assertThat(permissions.removed).containsExactly(entry(viewer.getUniqueId(), "essentials.fly"));
    }

    @Test
    void permissionActionsAreSilentNoOpsWhenVaultIsAbsent() {
        // The setUp bindings were registered over PermissionQuery.ABSENT; granting through them must not throw.
        assertThatCode(() -> {
                    invoke("give-permission", "essentials.fly");
                    invoke("take-permission", "essentials.fly");
                })
                .doesNotThrowAnyException();
    }

    // --- pure parsing --------------------------------------------------------------------------------------------

    @Test
    void parsingAnAmountOnlyLeavesABlankCurrency() {
        EconomyActions.MoneyArg arg = EconomyActions.MoneyArg.parse("100").orElseThrow();

        assertThat(arg.amount()).isEqualTo(100.0);
        assertThat(arg.currency()).isEmpty();
    }

    @Test
    void parsingAnAmountAndCurrencyReadsBoth() {
        EconomyActions.MoneyArg arg =
                EconomyActions.MoneyArg.parse("50 coinsengine:gold").orElseThrow();

        assertThat(arg.amount()).isEqualTo(50.0);
        assertThat(arg.currency()).isEqualTo("coinsengine:gold");
    }

    @Test
    void parsingANegativeAmountIsEmpty() {
        assertThat(EconomyActions.MoneyArg.parse("-5")).isEmpty();
    }

    @Test
    void parsingANonNumericAmountIsEmpty() {
        assertThat(EconomyActions.MoneyArg.parse("gold")).isEmpty();
        assertThat(EconomyActions.MoneyArg.parse("   ")).isEmpty();
    }

    // --- harness -------------------------------------------------------------------------------------------------

    private void invoke(String id, String arg) {
        invoke(bindings, id, arg);
    }

    /** Builds the context the click listener would build, then fires the handler {@code id} in {@code from}. */
    private void invoke(MenuBindings from, String id, String arg) {
        Consumer<MenuActionContext> handler =
                from.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    /** A real {@link Currencies} whose default {@code vault} spec resolves a synthetic currency over {@code backend}. */
    private Currencies currenciesOver(FakeCurrencyBackend backend) {
        return new Currencies(
                () -> new EconomyBackends(
                        CurrencyBackendRegistry.of(List.of(backend)),
                        CurrencyRegistry.single(
                                Currency.builder(CurrencyId.of("coins")).build())),
                log,
                "vault");
    }

    private static Map.Entry<UUID, String> entry(UUID player, String node) {
        return Map.entry(player, node);
    }

    /** A {@link PermissionQuery} that records every grant and revoke, standing in for the Vault permission service. */
    private static final class FakePermissionQuery implements PermissionQuery {

        private final List<Map.Entry<UUID, String>> added = new ArrayList<>();
        private final List<Map.Entry<UUID, String>> removed = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean inGroup(UUID player, String group) {
            return false;
        }

        @Override
        public boolean has(UUID player, String node) {
            return false;
        }

        @Override
        public boolean add(UUID player, String node) {
            added.add(Map.entry(player, node));
            return true;
        }

        @Override
        public boolean remove(UUID player, String node) {
            removed.add(Map.entry(player, node));
            return true;
        }

        @Override
        public String primaryGroup(UUID player) {
            return "";
        }
    }

    /** Captures expanded info/warn lines so a test can assert what the console operator logger recorded. */
    private static final class RecordingLogger implements Logger {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(expand(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(expand(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String expand(String message, Object... args) {
            String expanded = message;
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            return expanded;
        }
    }
}

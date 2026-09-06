package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.EconomyBackends;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.FakeCurrencyBackend;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the requirement condition pack. The {@code has-money} condition runs over a real
 * {@link Currencies} whose default {@code vault} spec resolves a synthetic currency over an in-memory
 * {@link FakeCurrencyBackend}, so a balance threshold is asserted concretely and a {@code playerpoints}-routed check is proved to leave the
 * vault balance untouched. The experience, level, item, empty-slot, slot and PDC-meta conditions read the
 * {@link PlayerMock}'s own live state through a real {@link PlayerMeta}. Every condition is asserted both ways, and
 * the fail-closed contract is pinned by the offline-viewer and malformed-argument cases.
 */
class RequirementConditionsTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private FakeCurrencyBackend economy;
    private PlayerMeta playerMeta;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        economy = new FakeCurrencyBackend("vault");
        playerMeta = new PlayerMeta(MockBukkit.createMockPlugin());
        RequirementConditions.register(bindings, currenciesOver(economy), playerMeta, new RecordingLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- has-money -----------------------------------------------------------------------------------------------

    @Test
    void hasMoneyPassesWhenTheBalanceMeetsTheThreshold() {
        economy.balances.put(viewer.getUniqueId(), 150.0);

        assertThat(test("has-money", "100")).isTrue();
        assertThat(test("has-money", "150")).isTrue();
        assertThat(test("has-money", "200")).isFalse();
    }

    @Test
    void hasMoneyFailsWhenTheBalanceFallsShort() {
        economy.balances.put(viewer.getUniqueId(), 50.0);

        assertThat(test("has-money", "100")).isFalse();
    }

    @Test
    void hasMoneyWithANamedCurrencyRoutesAwayFromTheDefault() {
        economy.balances.put(viewer.getUniqueId(), 150.0);

        // PlayerPoints is absent in the mock, so its provider is never available and answers false; the vault balance
        // would have passed at 150, so a false here proves the check routed to playerpoints rather than the default.
        assertThat(test("has-money", "50 playerpoints")).isFalse();
    }

    // --- has-exp / has-level -------------------------------------------------------------------------------------

    @Test
    void hasExpComparesTotalExperiencePoints() {
        viewer.setLevel(0);
        viewer.setExp(0f);
        viewer.giveExp(100);
        assertThat(viewer.getTotalExperience()).isEqualTo(100);

        assertThat(test("has-exp", "100")).isTrue();
        assertThat(test("has-exp", "40")).isTrue();
        assertThat(test("has-exp", "101")).isFalse();
    }

    @Test
    void hasLevelComparesTheExperienceLevel() {
        viewer.setLevel(5);

        assertThat(test("has-level", "5")).isTrue();
        assertThat(test("has-level", "3")).isTrue();
        assertThat(test("has-level", "6")).isFalse();
    }

    // --- has-item ------------------------------------------------------------------------------------------------

    @Test
    void hasItemCountsMatchingStacks() {
        viewer.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        assertThat(test("has-item", "DIAMOND 3")).isTrue();
        assertThat(test("has-item", "DIAMOND 5")).isTrue();
        assertThat(test("has-item", "DIAMOND 9")).isFalse();
        assertThat(test("has-item", "DIAMOND"))
                .as("a bare material needs at least one")
                .isTrue();
    }

    @Test
    void hasItemFailsClosedOnAnUnknownMaterial() {
        viewer.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        assertThat(test("has-item", "NOT_A_REAL_ITEM 1")).isFalse();
    }

    @Test
    void hasItemWithANameMatchRequiresTheDisplayName() {
        viewer.getInventory().addItem(named(Material.DIAMOND, "Cool Sword"));
        // A plain, unnamed diamond as well: it must not satisfy the named match.
        viewer.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));

        assertThat(test("has-item", "DIAMOND 1 name:Cool Sword")).isTrue();
        assertThat(test("has-item", "DIAMOND 1 name:Wrong Name")).isFalse();
        assertThat(test("has-item", "DIAMOND 2 name:Cool Sword"))
                .as("only the one named stack counts toward a named match")
                .isFalse();
    }

    // --- has-meta ------------------------------------------------------------------------------------------------

    @Test
    void hasMetaReadsThePlayersPdc() {
        playerMeta.set(viewer, "rank", "vip");

        assertThat(test("has-meta", "rank")).isTrue();
        assertThat(test("has-meta", "rank vip")).isTrue();
        assertThat(test("has-meta", "rank admin")).isFalse();
        assertThat(test("has-meta", "missing")).isFalse();
    }

    // --- has-empty-slots -----------------------------------------------------------------------------------------

    @Test
    void hasEmptySlotsCountsFreeStorageSlots() {
        // A fresh viewer's main storage is entirely empty.
        assertThat(test("has-empty-slots", "1")).isTrue();
        assertThat(test("has-empty-slots", "36")).isTrue();
        assertThat(test("has-empty-slots", "37")).isFalse();
    }

    @Test
    void hasEmptySlotsFailsWhenTheInventoryIsFull() {
        for (int slot = 0; slot < 36; slot++) {
            viewer.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }

        assertThat(test("has-empty-slots", "1")).isFalse();
    }

    // --- check-inventory -----------------------------------------------------------------------------------------

    @Test
    void checkInventoryMatchesTheMaterialInASlot() {
        viewer.getInventory().setItem(0, new ItemStack(Material.DIAMOND));

        assertThat(test("check-inventory", "0 DIAMOND")).isTrue();
        assertThat(test("check-inventory", "0 DIRT")).isFalse();
        assertThat(test("check-inventory", "1 DIAMOND")).as("slot 1 is empty").isFalse();
    }

    @Test
    void checkInventoryFailsClosedOutOfRangeOrMalformed() {
        viewer.getInventory().setItem(0, new ItemStack(Material.DIAMOND));

        assertThat(test("check-inventory", "999 DIAMOND")).as("out of range").isFalse();
        assertThat(test("check-inventory", "0")).as("missing material operand").isFalse();
        assertThat(test("check-inventory", "abc DIAMOND"))
                .as("non-numeric slot")
                .isFalse();
    }

    // --- fail-closed ---------------------------------------------------------------------------------------------

    @Test
    void everyConditionFailsClosedForAnOfflineViewer() {
        economy.balances.put(viewer.getUniqueId(), 1000.0);
        playerMeta.set(viewer, "rank", "vip");

        assertThat(testOffline("has-money", "1")).isFalse();
        assertThat(testOffline("has-exp", "0")).isFalse();
        assertThat(testOffline("has-level", "0")).isFalse();
        assertThat(testOffline("has-item", "DIAMOND")).isFalse();
        assertThat(testOffline("has-meta", "rank")).isFalse();
        assertThat(testOffline("has-empty-slots", "1")).isFalse();
        assertThat(testOffline("check-inventory", "0 DIAMOND")).isFalse();
    }

    @Test
    void malformedArgumentsFailClosed() {
        assertThat(test("has-money", "lots")).isFalse();
        assertThat(test("has-exp", "lots")).isFalse();
        assertThat(test("has-level", "")).isFalse();
        assertThat(test("has-item", "")).isFalse();
        assertThat(test("has-meta", "")).isFalse();
        assertThat(test("has-empty-slots", "")).isFalse();
        assertThat(test("check-inventory", "")).isFalse();
    }

    // --- harness -------------------------------------------------------------------------------------------------

    /** Fire condition {@code id} with {@code arg} as the split-off value, for the online viewer. */
    private boolean test(String id, String arg) {
        return test(id, arg, new PlayerRef(viewer.getUniqueId(), viewer.getName()));
    }

    /** Fire condition {@code id} for a viewer UUID with no live player: the fail-closed offline case. */
    private boolean testOffline(String id, String arg) {
        return test(id, arg, new PlayerRef(UUID.randomUUID(), "Ghost"));
    }

    private boolean test(String id, String arg, PlayerRef ref) {
        BiPredicate<MenuContext, Map<String, String>> condition =
                bindings.condition(id).orElseThrow(() -> new AssertionError("condition not registered: " + id));
        return condition.test(MenuContext.of(ref, null, 0), Map.of("value", arg));
    }

    private static ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        stack.setItemMeta(meta);
        return stack;
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

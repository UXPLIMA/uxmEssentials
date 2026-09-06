package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.OfflinePlayerMock;

/**
 * The Vault economy and permission hooks in both states. The absent path proves the NoClassDefFoundError-safe
 * contract structurally. Unlike PlaceholderAPI, Vault's SDK is on the test classpath (the migration feed tests
 * need it), so a missing class cannot surface here; instead the test confirms the no-op defaults and their
 * interfaces declare no {@code net.milkbowl} type, so resolving an absent hook never touches the real service.
 * The present path registers stub Vault services in the {@code ServicesManager} and asserts each capability
 * call delegates to them, including the realistic "Vault installed, no economy/permission service" case.
 */
class VaultHooksTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void economyHook_absent_resolvesToNoOpAndEveryCallIsSafe() {
        VaultEconomyHook hook = new VaultEconomyHook();

        EconomyQuery query = HookHarness.absent(hook);

        assertThat(query).isSameAs(EconomyQuery.ABSENT);
        assertThatCode(() -> {
                    assertThat(query.available()).isFalse();
                    assertThat(query.balance(ALICE)).isZero();
                    assertThat(query.has(ALICE, 10)).isFalse();
                    assertThat(query.withdraw(ALICE, 10)).isFalse();
                    assertThat(query.deposit(ALICE, 10)).isFalse();
                    assertThat(query.format(10)).isEqualTo("10.0");
                })
                .doesNotThrowAnyException();
    }

    @Test
    void permissionHook_absent_resolvesToNoOpAndEveryCallIsSafe() {
        VaultPermissionHook hook = new VaultPermissionHook();

        PermissionQuery query = HookHarness.absent(hook);

        assertThat(query).isSameAs(PermissionQuery.ABSENT);
        assertThatCode(() -> {
                    assertThat(query.available()).isFalse();
                    assertThat(query.inGroup(ALICE, "vip")).isFalse();
                    assertThat(query.has(ALICE, "node")).isFalse();
                    assertThat(query.add(ALICE, "node")).isFalse();
                    assertThat(query.remove(ALICE, "node")).isFalse();
                    assertThat(query.primaryGroup(ALICE)).isEmpty();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void absentDefaultsAndInterfacesDeclareNoVaultSdkType() {
        // The structural confirmation that the no-op default carries no SDK type, so loading the interface (and
        // its ABSENT field initializer) on a Vault-less server cannot pull in net.milkbowl. Only the two
        // VaultXService classes, reached past Hooks' present-guard, are allowed to mention it.
        assertThat(referencesVaultSdk(EconomyQuery.class)).isFalse();
        assertThat(referencesVaultSdk(EconomyQuery.ABSENT.getClass())).isFalse();
        assertThat(referencesVaultSdk(PermissionQuery.class)).isFalse();
        assertThat(referencesVaultSdk(PermissionQuery.ABSENT.getClass())).isFalse();
    }

    @Test
    void economyHook_present_delegatesToTheRegisteredEconomy() {
        offline(ALICE, "Alice");
        StubEconomy eco = new StubEconomy();
        eco.balances.put("Alice", 100.0);
        registerVaultPlugin(net.milkbowl.vault.economy.Economy.class, eco);

        EconomyQuery query = resolve(new VaultEconomyHook());

        assertThat(query.available()).isTrue();
        assertThat(query.balance(ALICE)).isEqualTo(100.0);
        assertThat(query.has(ALICE, 50)).isTrue();
        assertThat(query.has(ALICE, 150)).isFalse();
        assertThat(query.format(100.0)).isEqualTo("coins:100.0");
        assertThat(query.withdraw(ALICE, 40)).isTrue();
        assertThat(eco.balances.get("Alice")).isEqualTo(60.0);
        assertThat(query.deposit(ALICE, 15)).isTrue();
        assertThat(eco.balances.get("Alice")).isEqualTo(75.0);
    }

    @Test
    void permissionHook_present_delegatesToTheRegisteredPermission() {
        offline(ALICE, "Alice");
        StubPermission perms = new StubPermission();
        perms.groups.put("Alice", "vip");
        perms.nodes.computeIfAbsent("Alice", k -> new HashSet<>()).add("menu.open");
        registerVaultPlugin(net.milkbowl.vault.permission.Permission.class, perms);

        PermissionQuery query = resolve(new VaultPermissionHook());

        assertThat(query.available()).isTrue();
        assertThat(query.inGroup(ALICE, "vip")).isTrue();
        assertThat(query.inGroup(ALICE, "admin")).isFalse();
        assertThat(query.primaryGroup(ALICE)).isEqualTo("vip");
        assertThat(query.has(ALICE, "menu.open")).isTrue();
        assertThat(query.has(ALICE, "menu.admin")).isFalse();
        assertThat(query.add(ALICE, "menu.admin")).isTrue();
        assertThat(perms.nodes.get("Alice")).contains("menu.admin");
        assertThat(query.remove(ALICE, "menu.open")).isTrue();
        assertThat(perms.nodes.get("Alice")).doesNotContain("menu.open");
    }

    @Test
    void economyHook_presentPluginButNoService_reportsUnavailableAndNoOps() {
        offline(ALICE, "Alice");

        EconomyQuery query = HookHarness.present(new VaultEconomyHook());

        assertThat(query.available()).isFalse();
        assertThat(query.balance(ALICE)).isZero();
        assertThat(query.withdraw(ALICE, 10)).isFalse();
        assertThat(query.deposit(ALICE, 10)).isFalse();
        assertThat(query.format(10)).isEqualTo("10.0");
    }

    @Test
    void permissionHook_presentPluginButNoService_reportsUnavailableAndNoOps() {
        offline(ALICE, "Alice");

        PermissionQuery query = HookHarness.present(new VaultPermissionHook());

        assertThat(query.available()).isFalse();
        assertThat(query.inGroup(ALICE, "vip")).isFalse();
        assertThat(query.has(ALICE, "node")).isFalse();
        assertThat(query.add(ALICE, "node")).isFalse();
        assertThat(query.primaryGroup(ALICE)).isEmpty();
    }

    private <T> T resolve(PluginHook<T> hook) {
        return Hooks.resolve(server, HookHarness.SILENT, List.of(hook)).capability(hook.capability());
    }

    private <S> void registerVaultPlugin(Class<S> service, S provider) {
        Plugin vault = MockBukkit.createMockPlugin("Vault");
        server.getServicesManager().register(service, provider, vault, ServicePriority.Normal);
    }

    private void offline(UUID uuid, String name) {
        server.getPlayerList().addOfflinePlayer(new OfflinePlayerMock(uuid, name));
    }

    private static boolean referencesVaultSdk(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (mentionsVault(method.getReturnType())) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (mentionsVault(parameter)) {
                    return true;
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (mentionsVault(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsVault(Class<?> type) {
        return type.getName().startsWith("net.milkbowl");
    }

    /** A Vault economy keyed by player name; {@link AbstractEconomy} routes the OfflinePlayer overloads here. */
    // The legacy Vault Economy interface is wholly deprecated; a test stub must still implement it verbatim.
    @SuppressWarnings("deprecation")
    private static final class StubEconomy extends AbstractEconomy {

        private final Map<String, Double> balances = new HashMap<>();

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getName() {
            return "StubEconomy";
        }

        @Override
        public boolean hasBankSupport() {
            return false;
        }

        @Override
        public int fractionalDigits() {
            return 2;
        }

        @Override
        public String format(double amount) {
            return "coins:" + amount;
        }

        @Override
        public String currencyNamePlural() {
            return "coins";
        }

        @Override
        public String currencyNameSingular() {
            return "coin";
        }

        @Override
        public boolean hasAccount(String playerName) {
            return balances.containsKey(playerName);
        }

        @Override
        public boolean hasAccount(String playerName, String worldName) {
            return hasAccount(playerName);
        }

        @Override
        public double getBalance(String playerName) {
            return balances.getOrDefault(playerName, 0.0);
        }

        @Override
        public double getBalance(String playerName, String world) {
            return getBalance(playerName);
        }

        @Override
        public boolean has(String playerName, double amount) {
            return getBalance(playerName) >= amount;
        }

        @Override
        public boolean has(String playerName, String worldName, double amount) {
            return has(playerName, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            balances.merge(playerName, -amount, Double::sum);
            return ok(balances.getOrDefault(playerName, 0.0));
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
            return withdrawPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            balances.merge(playerName, amount, Double::sum);
            return ok(balances.getOrDefault(playerName, 0.0));
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
            return depositPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse createBank(String name, String player) {
            return unsupported();
        }

        @Override
        public EconomyResponse deleteBank(String name) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankBalance(String name) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankHas(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankWithdraw(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankDeposit(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse isBankOwner(String name, String playerName) {
            return unsupported();
        }

        @Override
        public EconomyResponse isBankMember(String name, String playerName) {
            return unsupported();
        }

        @Override
        public List<String> getBanks() {
            return List.of();
        }

        @Override
        public boolean createPlayerAccount(String playerName) {
            return balances.putIfAbsent(playerName, 0.0) == null;
        }

        @Override
        public boolean createPlayerAccount(String playerName, String worldName) {
            return createPlayerAccount(playerName);
        }

        private EconomyResponse ok(double balance) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }

        private EconomyResponse unsupported() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "stub");
        }
    }

    /** A Vault permission keyed by player name; the OfflinePlayer overloads route through {@code getName()}. */
    // Vault's group/name-keyed Permission methods are deprecated; a test stub must still implement them verbatim.
    @SuppressWarnings("deprecation")
    private static final class StubPermission extends Permission {

        private final Map<String, String> groups = new HashMap<>();
        private final Map<String, Set<String>> nodes = new HashMap<>();

        @Override
        public String getName() {
            return "StubPermission";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean hasSuperPermsCompat() {
            return true;
        }

        @Override
        public boolean playerHas(String world, String playerName, String permission) {
            return nodes.getOrDefault(playerName, Set.of()).contains(permission);
        }

        @Override
        public boolean playerAdd(String world, String playerName, String permission) {
            return nodes.computeIfAbsent(playerName, k -> new HashSet<>()).add(permission);
        }

        @Override
        public boolean playerRemove(String world, String playerName, String permission) {
            return nodes.getOrDefault(playerName, new HashSet<>()).remove(permission);
        }

        @Override
        public boolean groupHas(String world, String group, String permission) {
            return false;
        }

        @Override
        public boolean groupAdd(String world, String group, String permission) {
            return false;
        }

        @Override
        public boolean groupRemove(String world, String group, String permission) {
            return false;
        }

        @Override
        public boolean playerInGroup(String world, String playerName, String group) {
            return group.equals(groups.get(playerName));
        }

        @Override
        public boolean playerAddGroup(String world, String playerName, String group) {
            return false;
        }

        @Override
        public boolean playerRemoveGroup(String world, String playerName, String group) {
            return false;
        }

        @Override
        public String[] getPlayerGroups(String world, String playerName) {
            String group = groups.get(playerName);
            return group == null ? new String[0] : new String[] {group};
        }

        @Override
        public String getPrimaryGroup(String world, String playerName) {
            return groups.getOrDefault(playerName, "");
        }

        @Override
        public String[] getGroups() {
            return new String[0];
        }

        @Override
        public boolean hasGroupSupport() {
            return true;
        }
    }
}

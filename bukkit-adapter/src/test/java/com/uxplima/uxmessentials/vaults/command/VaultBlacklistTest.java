package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmlib.gui.Guis;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the vault item-blacklist filter that runs when a vault window closes. A vault holding a
 * blacklisted item is closed; the blocked item is stripped from the saved contents and returned to the player
 * (with the {@code VAULT_ITEM_BLOCKED} notice), while a non-blocked item is left untouched. No item is lost or
 * duplicated. A player holding {@code uxmessentials.vault.bypass-blacklist} keeps every item and gets no notice.
 *
 * <p>The {@link VaultView} is opened directly (the same path {@code /vault} drives) and the close is dispatched
 * as a real {@link InventoryCloseEvent} through the installed uxmLib menu listener, so the GUI's own close
 * handler runs the filter exactly as it would in production. The scheduler is a synchronous double so the
 * return and the async save run inline.
 */
class VaultBlacklistTest {

    private static final String BYPASS = "uxmessentials.vault.bypass-blacklist";

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingSink sink;
    private RecordingRepository repository;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        sink = new RecordingSink();
        repository = new RecordingRepository();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void aBlockedItemIsStrippedReturnedAndNotifiedWhileANormalItemIsKept() {
        VaultView view = view(new GrantNone(), policyBlocking("bedrock"));
        view.open(player, ref(), ref(), emptyVault());
        Inventory vault = player.getOpenInventory().getTopInventory();
        vault.setItem(0, new ItemStack(Material.BEDROCK, 3));
        vault.setItem(1, new ItemStack(Material.DIAMOND, 5));

        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        // The blocked stack is gone from the saved contents but the diamond stays.
        ItemStack[] saved = repository.lastSaved;
        assertThat(saved).isNotNull();
        assertThat(materialsIn(saved)).contains(Material.DIAMOND).doesNotContain(Material.BEDROCK);
        // The bedrock came back to the player: nothing was lost or duplicated.
        assertThat(countOf(player.getInventory().getContents(), Material.BEDROCK))
                .isEqualTo(3);
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_ITEM_BLOCKED);
        assertThat(sink.lastBlockedCount).isEqualTo("3");
    }

    @Test
    void aBypassingPlayerKeepsTheBlockedItemAndGetsNoNotice() {
        VaultView view = view(new GrantNode(BYPASS), policyBlocking("bedrock"));
        view.open(player, ref(), ref(), emptyVault());
        Inventory vault = player.getOpenInventory().getTopInventory();
        vault.setItem(0, new ItemStack(Material.BEDROCK, 3));

        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        assertThat(materialsIn(repository.lastSaved)).contains(Material.BEDROCK);
        assertThat(countOf(player.getInventory().getContents(), Material.BEDROCK))
                .isZero();
        assertThat(sink.keys).doesNotContain(VaultsMessageKey.VAULT_ITEM_BLOCKED);
    }

    @Test
    void anEmptyPolicyLeavesEveryItemUntouched() {
        VaultView view = view(new GrantNone(), VaultItemPolicy.allowAll());
        view.open(player, ref(), ref(), emptyVault());
        Inventory vault = player.getOpenInventory().getTopInventory();
        vault.setItem(0, new ItemStack(Material.BEDROCK, 3));

        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        assertThat(materialsIn(repository.lastSaved)).contains(Material.BEDROCK);
        assertThat(sink.keys).doesNotContain(VaultsMessageKey.VAULT_ITEM_BLOCKED);
    }

    private VaultView view(Permissions permissions, VaultItemPolicy policy) {
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        return new VaultView(new KeyMessages(), sink, saveVault, new SyncScheduler(), permissions, policy, null);
    }

    private static VaultItemPolicy policyBlocking(String material) {
        return new VaultItemPolicy(Set.of(material));
    }

    private Vault emptyVault() {
        return Vault.allocate(VaultId.of(ref(), 1), VaultSize.ofClamped(6), java.time.Instant.now());
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static List<Material> materialsIn(@Nullable ItemStack[] items) {
        List<Material> materials = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null) {
                    materials.add(item.getType());
                }
            }
        }
        return materials;
    }

    private static int countOf(@Nullable ItemStack[] items, Material material) {
        int total = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Captures the last contents written through so the filter's effect on the saved data is assertable. */
    private static final class RecordingRepository implements VaultRepository {
        private @Nullable ItemStack[] lastSaved;

        @Override
        public Optional<Vault> find(VaultId id) {
            return Optional.empty();
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            return List.of();
        }

        @Override
        public List<com.uxplima.uxmessentials.vaults.application.VaultSummary> summaries(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return 0;
        }

        @Override
        public void save(Vault vault) {
            // VaultItemCodec.decode round-trips the bytes back into a slot array for the assertion.
            lastSaved = com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec.decode(
                    vault.contents(), vault.size().slots());
        }

        @Override
        public void delete(VaultId id) {}

        @Override
        public int deleteUntouchedBefore(java.time.Instant cutoff) {
            return 0;
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key == VaultsMessageKey.VAULT_ITEM_BLOCKED) {
                sink.lastBlockedCount = placeholders.get("count");
            }
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();
        private @Nullable String lastBlockedCount;

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // renderedText is the key() string (see KeyMessages); the recorded keys drive the asserts
            for (VaultsMessageKey key : VaultsMessageKey.values()) {
                if (key.key().equals(renderedText)) {
                    keys.add(key);
                }
            }
        }
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Grants no node: the blacklist applies. */
    private static final class GrantNone implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** Grants exactly one node: used to grant the bypass. */
    private static final class GrantNode implements Permissions {
        private final String granted;

        private GrantNode(String granted) {
            this.granted = granted;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.equals(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoEvents
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        @Override
        public void publish(com.uxplima.uxmessentials.shared.domain.DomainEvent event) {}
    }
}

package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
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
 * MockBukkit coverage of the overflow-rescue-on-open path. A vault is saved with items in slots beyond what the
 * current size quota now allows (the quota shrank since the last save); when the owner opens it the
 * out-of-range items are handed back to them (added to their inventory, remainder dropped at their feet), the
 * {@code VAULT_OVERFLOW_RETURNED} notice is sent with the rescued count, the GUI exposes only the in-range
 * slots, and the next close persists only the in-range contents, so the overflow is gone from the vault and
 * safely with the player, with no item lost or duplicated. A vault whose contents fit the live size triggers no
 * rescue and no notice.
 */
class VaultOverflowTest {

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
    void shrunkSizeReturnsTheOverflowNotifiesAndPersistsOnlyTheInRangeContents() {
        // A vault saved at six rows (54 slots) with a diamond in slot 0 (in range for a one-row vault) and an
        // emerald in slot 20 (overflow for a one-row vault). It is opened at one row (9 slots), the quota
        // shrank, so slot 20 is out of range and must be rescued.
        ItemStack[] stored = new ItemStack[54];
        stored[0] = new ItemStack(Material.DIAMOND, 4);
        stored[20] = new ItemStack(Material.EMERALD, 7);
        Vault vault = vaultOf(VaultSize.ofClamped(1), VaultItemCodec.encode(stored));

        VaultView view = view(VaultItemPolicy.allowAll());
        view.open(player, ref(), ref(), vault);

        // The GUI is sized to the live one-row quota and exposes only the in-range slot.
        Inventory gui = player.getOpenInventory().getTopInventory();
        assertThat(gui.getSize()).isEqualTo(9);
        assertThat(gui.getItem(0)).isNotNull();
        assertThat(gui.getItem(0).getType()).isEqualTo(Material.DIAMOND);

        // The overflow emerald came back to the player, nothing lost or duplicated, and the notice fired.
        assertThat(countOf(player.getInventory().getContents(), Material.EMERALD))
                .isEqualTo(7);
        assertThat(countOf(player.getInventory().getContents(), Material.DIAMOND))
                .isZero();
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_OVERFLOW_RETURNED);
        assertThat(sink.lastOverflowCount).isEqualTo("7");

        // The rescue persisted the truncated contents immediately, before any close, so a crash here cannot
        // leave the overflow in the DB row for the next open to rescue and hand out again. The saved snapshot is
        // already the in-range-only contents: the diamond stays, the emerald is gone.
        assertThat(repository.saves).isEqualTo(1);
        ItemStack[] savedOnOpen = repository.lastSaved;
        assertThat(savedOnOpen).isNotNull();
        assertThat(materialsIn(savedOnOpen)).contains(Material.DIAMOND).doesNotContain(Material.EMERALD);

        // Closing the window persists again, idempotently: the live GUI by now equals the truncated contents.
        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));
        ItemStack[] saved = repository.lastSaved;
        assertThat(saved).isNotNull();
        assertThat(materialsIn(saved)).contains(Material.DIAMOND).doesNotContain(Material.EMERALD);
    }

    @Test
    void aNormalOpenWithNoOverflowDoesNotPersistUntilClose() {
        // Contents that fit the live size: nothing to rescue, so the open path must not save: the existing
        // close-only persistence is unchanged for the common case.
        ItemStack[] stored = new ItemStack[9];
        stored[0] = new ItemStack(Material.DIAMOND, 4);
        Vault vault = vaultOf(VaultSize.ofClamped(1), VaultItemCodec.encode(stored));

        VaultView view = view(VaultItemPolicy.allowAll());
        view.open(player, ref(), ref(), vault);

        assertThat(repository.saves).isZero();

        // The close still persists exactly once, as before.
        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));
        assertThat(repository.saves).isEqualTo(1);
    }

    @Test
    void reOpeningAfterARescueWithNoCloseSaveDoesNotReRescueOrReDupe() {
        // First open rescues the overflow and persists the truncated row immediately. Simulate a crash before
        // close (no close-save) by re-opening straight from the persisted vault: the row no longer carries the
        // overflow, so the second open must rescue nothing and hand the player no further items.
        ItemStack[] stored = new ItemStack[54];
        stored[0] = new ItemStack(Material.DIAMOND, 4);
        stored[20] = new ItemStack(Material.EMERALD, 7);
        Vault vault = vaultOf(VaultSize.ofClamped(1), VaultItemCodec.encode(stored));

        VaultView view = view(VaultItemPolicy.allowAll());
        view.open(player, ref(), ref(), vault);

        assertThat(countOf(player.getInventory().getContents(), Material.EMERALD))
                .isEqualTo(7);
        Vault persisted = repository.lastSavedVault;
        assertThat(persisted).isNotNull();
        sink.keys.clear();

        // Re-open from the already-truncated persisted row (the close-save was "lost").
        view.open(player, ref(), ref(), persisted);

        // No second rescue: the player still holds only the single emerald stack, and no notice fired again.
        // (Re-opening auto-closes the first still-open window, which persists it once more, idempotent, same
        // truncated contents, so the no-dupe guarantee is the unchanged emerald count and the absent notice.)
        assertThat(countOf(player.getInventory().getContents(), Material.EMERALD))
                .isEqualTo(7);
        assertThat(sink.keys).doesNotContain(VaultsMessageKey.VAULT_OVERFLOW_RETURNED);
    }

    @Test
    void aConfiguredOpenSoundPlaysWithoutThrowingAndAnUnknownNameIsTolerated() {
        // A resolved sound opens cleanly; an unknown name resolves to null at wire time, so the view simply
        // plays nothing: neither path throws.
        org.bukkit.Sound resolved =
                com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys.resolveSound("block.chest.open");
        assertThat(resolved).isNotNull();
        assertThat(com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys.resolveSound(
                        "not.a.real.sound"))
                .isNull();

        ItemStack[] stored = new ItemStack[9];
        stored[0] = new ItemStack(Material.DIAMOND, 1);
        Vault vault = vaultOf(VaultSize.ofClamped(1), VaultItemCodec.encode(stored));
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        VaultView withSound = new VaultView(
                new KeyMessages(),
                sink,
                saveVault,
                new SyncScheduler(),
                new GrantNone(),
                VaultItemPolicy.allowAll(),
                resolved);

        withSound.open(player, ref(), ref(), vault);

        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(9);
    }

    @Test
    void contentsThatFitTheLiveSizeTriggerNoRescueAndNoNotice() {
        ItemStack[] stored = new ItemStack[9];
        stored[0] = new ItemStack(Material.DIAMOND, 4);
        Vault vault = vaultOf(VaultSize.ofClamped(1), VaultItemCodec.encode(stored));

        VaultView view = view(VaultItemPolicy.allowAll());
        view.open(player, ref(), ref(), vault);

        assertThat(countOf(player.getInventory().getContents(), Material.DIAMOND))
                .isZero();
        assertThat(sink.keys).doesNotContain(VaultsMessageKey.VAULT_OVERFLOW_RETURNED);
        Inventory gui = player.getOpenInventory().getTopInventory();
        assertThat(gui.getItem(0)).isNotNull();
        assertThat(gui.getItem(0).getType()).isEqualTo(Material.DIAMOND);
    }

    private VaultView view(VaultItemPolicy policy) {
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        return new VaultView(new KeyMessages(), sink, saveVault, new SyncScheduler(), new GrantNone(), policy, null);
    }

    private Vault vaultOf(VaultSize size, VaultContents contents) {
        return Vault.of(VaultId.of(ref(), 1), size, contents, null, null, Instant.now());
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

    /**
     * Captures the last contents written through so the next-save consistency is assertable, counts the saves so
     * an open that persists immediately is distinguishable from one that defers to close, and retains the last
     * saved {@link Vault} so a re-open from the persisted (already-truncated) row can be simulated.
     */
    private static final class RecordingRepository implements VaultRepository {
        private @Nullable ItemStack[] lastSaved;
        private @Nullable Vault lastSavedVault;
        private int saves;

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
            saves++;
            lastSavedVault = vault;
            lastSaved = VaultItemCodec.decode(vault.contents(), vault.size().slots());
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
            if (key == VaultsMessageKey.VAULT_OVERFLOW_RETURNED) {
                sink.lastOverflowCount = placeholders.get("count");
            }
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();
        private @Nullable String lastOverflowCount;

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
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

    private static final class NoEvents
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        @Override
        public void publish(com.uxplima.uxmessentials.shared.domain.DomainEvent event) {}
    }
}

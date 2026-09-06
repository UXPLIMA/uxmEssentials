package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankActionsMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankListMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankMembersMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation;
import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The bank-members golden test: the engine-rendered members menu must draw the exact grid the original
 * {@code BankMembersView} drew. The fresh bank holds two members (the viewer "Alice" as LEADER, "Bob" as MEMBER), so
 * the list draws two PLAYER_HEAD icons (content slots 0 and 1. Each member's name surfaces through the {@code
 * bank_member} token), the prev ARROW (slot 45), the ARROW back button (slot 47), the EMERALD_BLOCK add button (slot
 * 49. Shown because the LEADER viewer may add through the {@code economy:can-add-member} condition), and the next
 * ARROW (slot 53). The engine window is snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot
 * for slot, to the baseline the old view produced for this fixture, frozen here as the contract so the old class could
 * be deleted.
 *
 * <p>A right click on Bob's head through the engine's own {@link MenuListener} proves the migrated path removes that
 * member through the same {@link BankService} the {@code /bank removemember} command takes, captured by the mock the
 * remove use case calls. The {@code KeyMessages} catalog surfaces the entry name's {@code bank_member} token; every
 * other key renders verbatim, so a real rendering difference still shows up as a snapshot mismatch.
 */
class BankMembersListGoldenTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private static final UUID BOB = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private PlayerRef bob;
    private GuiText guiText;
    private Scheduler scheduler;
    private BankService bankService;
    private TextInput textInput;
    private final AtomicReference<BankNavigation> navigationHolder = new AtomicReference<>();

    private final Path dataFolder = Path.of("nonexistent");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), "Alice");
        bob = new PlayerRef(BOB, "Bob");
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        bankService = mock(BankService.class);
        textInput = mock(TextInput.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHeadGridBackAddAndNavAsTheOldView() {
        seedBank(bankWith("eEa12523", "Vault"));

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void rightClickingAMemberThroughTheEngineRemovesThemThroughTheBankService() {
        seedBank(bankWith("eEa12523", "Vault"));
        when(bankService.removeMember(eq(viewer), eq("eEa12523"), eq(bob)))
                .thenReturn(com.uxplima.uxmessentials.shared.domain.Result.ok());
        openEngine();

        fireClick(1, ClickType.RIGHT); // content slot 1 holds Bob; a right click must remove them

        verify(bankService).removeMember(eq(viewer), eq("eEa12523"), eq(bob));
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code BankMembersView} produced for this fixture (the LEADER
     * viewer "Alice" and the MEMBER "Bob"), captured while both paths rendered it identically and frozen here: two
     * PLAYER_HEAD icons (content slots 0 and 1. The names surface through the {@code bank_member} token), the prev
     * ARROW (slot 45), the ARROW back button (slot 47), the EMERALD_BLOCK add button (slot 49), and the next ARROW
     * (slot 53).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PLAYER_HEAD, "Alice"));
        baseline.put(1, new Snapshot(Material.PLAYER_HEAD, "Bob"));
        baseline.put(45, new Snapshot(Material.ARROW, "bank.members-gui-prev"));
        baseline.put(47, new Snapshot(Material.ARROW, "bank.members-gui-back"));
        baseline.put(49, new Snapshot(Material.EMERALD_BLOCK, "bank.members-gui-add"));
        baseline.put(53, new Snapshot(Material.ARROW, "bank.members-gui-next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        MenuVocabulary.registerConditions(bindings, mock(Permissions.class), mock(Logger.class));
        MenuVocabulary.registerPlaceholders(bindings);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        BankMembersMenu menu = membersMenu(menus);
        menu.register(bindings, dataFolder, NOOP);
        menu.open(player, navigationBank());
    }

    /** A {@link BankMembersMenu} wired off the same collaborators as the old view, over the engine façade. */
    private BankMembersMenu membersMenu(Menus menus) {
        Messages messages = new KeyMessages();
        Supplier<BankNavigation> navigation = () -> Objects.requireNonNull(navigationHolder.get(), "navigation");
        BankMembersMenu menu =
                new BankMembersMenu(menus, bankService, textInput, scheduler, new FakeLookup(), messages, navigation);
        navigationHolder.set(new BankNavigation(mock(BankListMenu.class), mock(BankActionsMenu.class), menu));
        return menu;
    }

    /** The bank the open re-fetches; the actual rows come from the mocked {@code getBank}. */
    private SharedBank navigationBank() {
        return bankWith("eEa12523", "Vault");
    }

    private void seedBank(SharedBank bank) {
        when(bankService.getBank(anyString())).thenReturn(Optional.empty());
        when(bankService.getBank(eq(bank.id()))).thenReturn(Optional.of(bank));
    }

    private SharedBank bankWith(String id, String name) {
        List<BankMember> members =
                List.of(new BankMember(viewer, BankRole.LEADER), new BankMember(bob, BankRole.MEMBER));
        return new SharedBank(id, name, Money.of(COINS, new BigDecimal(100)), viewer, members, 0L);
    }

    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    private static final class FakeLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /**
     * Surfaces the entry name's {@code bank_member} token so a member's name appears; else the bare key. The engine
     * resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is
     * by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(EconomyMessageKey.BANK_MEMBERS_GUI_MEMBER_NAME.key())) {
                return placeholders.getOrDefault("bank_member", "");
            }
            return key.key();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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
}

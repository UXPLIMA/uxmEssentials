package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.CurrencyPickerMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The bank-list golden test: the engine-rendered {@code /bank} list must draw the exact grid the original
 * {@code BankGuiView} drew. The service returns two banks the viewer belongs to ("Vault" and "Reserve"), so the list
 * draws two CHEST icons (content slots 0 and 1. The bank name surfaces through the {@code bank_name} token), the
 * EMERALD_BLOCK create button (slot 49), the prev ARROW (slot 45), and the next ARROW (slot 53), the geometry the
 * original list used. The engine window is snapshotted as {@code (slot -> material, plain name)} and asserted equal,
 * slot for slot, to the baseline the old view produced for this fixture, frozen here as the contract so the old class
 * could be deleted.
 *
 * <p>A left click on the first chest through the engine's own {@link MenuListener} proves the migrated path opens that
 * bank's engine {@link BankActionsMenu} hub, and the create button still drives the name prompt into the
 * currency picker and reaches {@code BankService.createBank}, so the move is faithful in both appearance and the
 * create flow. The {@code KeyMessages} catalog surfaces the entry name's {@code bank_name} token; every other key
 * renders verbatim, so a real rendering difference still shows up as a snapshot mismatch.
 */
class BankListGoldenTest {

    private static final int CREATE_SLOT = 49;

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
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
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        bankService = mock(BankService.class);
        textInput = mock(TextInput.class);
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameChestGridCreateAndNavAsTheOldView() {
        seedBanks(bankFor("eEa12523", "Vault"), bankFor("eEb98765", "Reserve"));

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingABankThroughTheEngineOpensThatBanksActionsHub() {
        seedBanks(bankFor("eEa12523", "Vault"));
        openEngine();

        fireClick(0, ClickType.LEFT); // content slot 0 holds Vault; a left click opens its actions hub

        // The bank-actions hub is now an engine-rendered MenuHolder window: a 3-row window with the deposit GOLD_INGOT
        // at slot 10 and the ARROW back button at slot 22, distinct from the 6-row list this click left.
        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(top.getSize()).isEqualTo(27);
        assertThat(Objects.requireNonNull(top.getItem(10)).getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(Objects.requireNonNull(top.getItem(22)).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void theCreateButtonStillDrivesTheCurrencyPickerAndCreatesABank() {
        seedBanks();
        feedName("Vault");
        SharedBank created = bankFor("eEa12523", "Vault");
        when(bankService.createBank(eq("Vault"), eq(COINS), any(PlayerRef.class)))
                .thenReturn(Result.ok(created));
        openEngine();

        fireClick(CREATE_SLOT, ClickType.LEFT); // create -> name prompt (fires inline) -> currency picker opens
        // The picker grids one icon per currency in its content slots; coins sits at slot 0.
        fireClick(0, ClickType.LEFT); // pick coins -> createBank(name, currency, viewer)

        verify(bankService).createBank(eq("Vault"), eq(COINS), any(PlayerRef.class));
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code BankGuiView} produced for this fixture (two banks
     * "Vault" and "Reserve"), captured while both paths rendered it identically and frozen here: two CHEST icons
     * (content slots 0 and 1. The names surface through the {@code bank_name} token), the EMERALD_BLOCK create button
     * (slot 49), the prev ARROW (slot 45), and the next ARROW (slot 53).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.CHEST, "Vault"));
        baseline.put(1, new Snapshot(Material.CHEST, "Reserve"));
        baseline.put(45, new Snapshot(Material.ARROW, "bank.list-gui-prev"));
        baseline.put(49, new Snapshot(Material.EMERALD_BLOCK, "bank.list-gui-create"));
        baseline.put(53, new Snapshot(Material.ARROW, "bank.list-gui-next"));
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
        // The list spec opens nothing through the shared vocabulary, but registering it keeps the engine wiring whole.
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        MenuVocabulary.registerConditions(bindings, mock(Permissions.class), mock(Logger.class));
        MenuVocabulary.registerPlaceholders(bindings);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        BankListMenu menu = listMenu(menus, bindings);
        menu.register(bindings, dataFolder, NOOP);
        menu.open(player);
    }

    /** A {@link BankListMenu} wired off the same collaborators as the old view, over the engine façade. */
    private BankListMenu listMenu(Menus menus, MenuBindings bindings) {
        Messages messages = new KeyMessages();
        CurrencyPickerMenu picker = new CurrencyPickerMenu(menus, messages, scheduler);
        picker.register(bindings, specDir(), NOOP);
        Supplier<BankNavigation> navigation = () -> Objects.requireNonNull(navigationHolder.get(), "navigation");
        BankListMenu listMenu = new BankListMenu(
                menus, bankService, CurrencyRegistry.single(COINS), textInput, picker, scheduler, messages, navigation);
        BankActionsMenu actions = new BankActionsMenu(
                menus, bankService, textInput, scheduler, messages, mock(TransactionsHistoryMenu.class), navigation);
        // The bank click now opens the engine actions panel, so its spec must be registered for the open to draw.
        actions.register(bindings, specDir(), NOOP);
        navigationHolder.set(new BankNavigation(listMenu, actions, mock(BankMembersMenu.class)));
        return listMenu;
    }

    /** The bundled spec directory under the source tree, so the actions panel loads the shipped spec. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    private void feedName(String name) {
        doAnswer(invocation -> {
                    Consumer<String> onSubmit = invocation.getArgument(3);
                    onSubmit.accept(name);
                    return null;
                })
                .when(textInput)
                .prompt(any(), any(PlayerRef.class), any(InputRequest.class), any(), any());
    }

    private void seedBanks(SharedBank... banks) {
        List<String> ids = java.util.Arrays.stream(banks).map(SharedBank::id).toList();
        when(bankService.getBankIdsForPlayer(any())).thenReturn(ids);
        when(bankService.getBank(anyString())).thenReturn(Optional.empty());
        for (SharedBank bank : banks) {
            when(bankService.getBank(eq(bank.id()))).thenReturn(Optional.of(bank));
        }
    }

    private SharedBank bankFor(String id, String name) {
        List<BankMember> members = List.of(new BankMember(viewer, BankRole.LEADER));
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

    /**
     * Surfaces the entry name's {@code bank_name} token so a bank's name appears; else the bare key. The engine
     * resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is
     * by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(EconomyMessageKey.BANK_LIST_GUI_ICON_NAME.key())) {
                return placeholders.getOrDefault("bank_name", "");
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

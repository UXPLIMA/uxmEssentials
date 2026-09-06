package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitBrowseMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The kit-browse golden test: the engine-rendered {@code /kit} browse menu must draw the exact grid the original
 * {@code KitMenuView} drew, in both its modes, and click the same way.
 *
 * <p>Legacy (no categories): two free, repeatable, ungated, itemless kits "alpha" and "beta" fall back to the
 * CHEST icon, so page 0 holds two CHEST tiles (content slots 0 and 1, the names surfacing through the kit-name
 * token) and the two ARROW nav buttons (slots 48 and 50), with no back button at the root. The engine's window is
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old
 * view produced, frozen here as the contract so the old class could be deleted. A priced kit is also rendered to
 * prove its lore expands through the single multi-line placeholder: a free kit expands to two lines
 * (cooldown + claim hint) where a priced one expands to three (cooldown + cost + claim hint).
 *
 * <p>Category mode: a "tools" category pinned to content slot 0 (a BOOK tile) sits alongside the uncategorised
 * kits at the root, fixed to its slot on every page while the kits flow through the remaining slots and spill onto
 * page 1: proving the pinned-slot pagination the migration relies on. A left click on the category drills in, the
 * engine re-opens at that level, where the kit filed under it appears; a left click on the back button steps back
 * to the root. And, through the engine's own {@code MenuListener}, a left click on a kit tile claims it through the
 * same {@link ClaimKit} use case the {@code /kit} command drives (recorded by the granter the use case delegates
 * the grant to), while a right click opens the kit's bespoke {@link KitPreviewView}.
 */
class KitBrowseGoldenTest {

    /** Content slots span 0..44, so a kit beyond the 44 free flow slots (slot 0 is the pinned category) spills. */
    private static final int FLOW_SLOTS_WITH_PIN = 44;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private SyncScheduler scheduler;
    private RecordingRepository repository;
    private StubCategoryRepository categories;
    private RecordingGranter granter;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        repository = new RecordingRepository();
        categories = new StubCategoryRepository();
        granter = new RecordingGranter();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void legacyModeRendersTheSameIconGridAndNavAsTheOldView() {
        seedKit(freeKit("alpha"));
        seedKit(freeKit("beta"));

        Map<Integer, Snapshot> engineGrid = snapshot(openLegacy());

        assertThat(engineGrid).isEqualTo(legacyBaseline());
    }

    @Test
    void aPricedKitExpandsToItsThreeLoreLinesWhereAFreeOneExpandsToTwo() {
        seedKit(freeKit("free"));
        seedKit(freeKit("vip").withCost(KitCost.of(new BigDecimal("100"))));
        Inventory inv = openLegacy();

        // "free": cooldown + claim hint = 2 lines; "vip": cooldown + cost + claim hint = 3.
        assertThat(loreLines(inv.getItem(0))).isEqualTo(2);
        assertThat(loreLines(inv.getItem(1))).isEqualTo(3);
    }

    @Test
    void categoryModePinsTheCategoryToItsSlotAndRepeatsItWhileKitsAdvance() {
        seedCategory("tools", Optional.empty(), 0);
        // One kit filed under "tools" (seen only after drilling in) and enough root kits to spill onto page 1.
        seedKit(freeKit("drill").withCategoryId(Optional.of("tools")));
        for (int i = 0; i < FLOW_SLOTS_WITH_PIN + 1; i++) {
            seedKit(freeKit("root" + i));
        }
        openCategoryRoot();

        // Page 0: the BOOK category pinned to slot 0, with root kits flowing through slots 1..44.
        Inventory page0 = top();
        assertThat(snapshot(page0).get(0)).isEqualTo(new Snapshot(Material.BOOK, "tools"));
        assertThat(contentIcons(page0)).isEqualTo(FLOW_SLOTS_WITH_PIN + 1); // 44 root kits + the pinned category

        // Advancing to page 1 keeps the category pinned at slot 0 while the spilled root kit flows in below it.
        fireClick(50, ClickType.LEFT); // the NEXT arrow
        Inventory page1 = top();
        assertThat(snapshot(page1).get(0)).isEqualTo(new Snapshot(Material.BOOK, "tools"));
        assertThat(snapshot(page1).get(1)).isEqualTo(new Snapshot(Material.CHEST, "root" + FLOW_SLOTS_WITH_PIN));
        assertThat(contentIcons(page1)).isEqualTo(2); // the pinned category + the one spilled kit
    }

    @Test
    void clickingACategoryDrillsIntoItAndBackReturnsToTheRoot() {
        seedCategory("tools", Optional.empty(), 0);
        seedKit(freeKit("drill").withCategoryId(Optional.of("tools")));
        seedKit(freeKit("beta"));
        openCategoryRoot();

        // Slot 0 is the pinned "tools" category tile; a left click drills into it, where its kit "drill" appears.
        fireClick(0, ClickType.LEFT);
        assertThat(snapshot(top()).get(0)).isEqualTo(new Snapshot(Material.CHEST, "drill"));
        assertThat(snapshot(top())).containsKey(49); // the back button shows below the root

        // Slot 49 is the back button; a left click steps back to the root grid with the pinned category at slot 0.
        fireClick(49, ClickType.LEFT);
        assertThat(snapshot(top()).get(0)).isEqualTo(new Snapshot(Material.BOOK, "tools"));
        assertThat(snapshot(top())).doesNotContainKey(49); // no back button at the root
    }

    @Test
    void leftClickingAKitThroughTheEngineClaimsItThroughClaimKit() {
        seedKit(freeKit("alpha"));
        openLegacy();

        // Content slot 0 is the "alpha" kit tile; a left click must claim it through ClaimKit, which grants it.
        fireClick(0, ClickType.LEFT);

        assertThat(granter.granted).containsExactly("alpha");
    }

    @Test
    void rightClickingAKitThroughTheEngineOpensTheBespokePreview() {
        seedKit(freeKit("alpha").withPreview(true));
        openLegacy();

        // Content slot 0 is the "alpha" kit tile; a right click opens the bespoke read-only preview window, whose
        // holder is the package-private KitPreviewHolder: recognised here by its class name rather than its type.
        fireClick(0, ClickType.RIGHT);

        assertThat(top().getHolder()).isNotNull();
        assertThat(top().getHolder().getClass().getSimpleName()).isEqualTo("KitPreviewHolder");
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code KitMenuView} produced for the two-kit legacy
     * fixture, frozen as the contract: two CHEST tiles (content slots 0 and 1, the names surfacing through the
     * kit-name token) and the two nav ARROWs (slots 48 and 50). The root level shows no back button.
     */
    private static Map<Integer, Snapshot> legacyBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.CHEST, "alpha"));
        baseline.put(1, new Snapshot(Material.CHEST, "beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "kit.menu.prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "kit.menu.next"));
        return baseline;
    }

    /** Open the browse menu over a real engine, returning the top inventory the window populated. */
    private Inventory openLegacy() {
        browseMenu().open(player, viewer, repository.all());
        return top();
    }

    /** Open the category-mode root (categories present); the window is read off the player afterwards. */
    private void openCategoryRoot() {
        browseMenu().open(player, viewer, repository.all());
    }

    /** A {@link KitBrowseMenu} wired off the same collaborators the old view used, over a real engine façade. */
    private KitBrowseMenu browseMenu() {
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        Messages messages = new KeyMessages();
        Permissions permissions = new AllowAllPermissions();
        KitClaimStore claims = new NoClaims();
        Notifier notifier = new Notifier(messages, (v, text) -> {});
        KitAccess access = new KitAccess(permissions, new NoCooldowns(), claims, Optional.<KitEconomy>empty());
        ClaimKit claimKit = new ClaimKit(
                repository, access, granter, notifier, new NoEvents(), Clock.systemUTC(), Optional.empty());
        KitPreviewView preview =
                new KitPreviewView(messages, scheduler, GuiLayout.paginatedDefault(Material.GRAY_STAINED_GLASS_PANE));
        KitBrowseMenu menu = new KitBrowseMenu(
                engine.menus(),
                scheduler,
                claimKit,
                notifier,
                categories,
                access,
                preview,
                messages,
                GuiLayout.paginatedDefault(Material.CHEST),
                Clock.systemUTC());
        menu.register(engine.bindings(), dataFolder, NOOP);
        return menu;
    }

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    /** Click the given slot of the open menu with the given gesture through the production click path. */
    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    /** Non-air icons in the content rows (slots 0..44), excluding the reserved bottom-row nav buttons. */
    private static int contentIcons(Inventory inv) {
        int count = 0;
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    /** The number of lore lines on a rendered tile, proving the multi-line placeholder expanded variable lore. */
    private static int loreLines(ItemStack item) {
        // The body under the title line: the count the variable-lore placeholder is responsible for.
        return TileText.body(item).size();
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private void seedKit(KitDefinition kit) {
        repository.save(kit);
    }

    private void seedCategory(String id, Optional<String> parent, int slot) {
        categories.save(new KitCategory(id, id, Optional.empty(), List.of(), slot, parent));
    }

    /**
     * A free, repeatable, ungated, itemless kit with preview off. Its icon falls back to CHEST and its default
     * lore is exactly the cooldown and claim-hint lines, so a cost line is the only variable lore the test adds.
     */
    private static KitDefinition freeKit(String id) {
        return KitDefinition.repeatable(KitId.of(id), List.<KitItem>of(), Duration.ofSeconds(60))
                .withPreview(false);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    // --- fakes ---

    /** A repository that records stored kits in insertion order, mirroring the per-kit catalog. */
    private static final class RecordingRepository implements KitRepository {
        private final List<KitDefinition> kits = new CopyOnWriteArrayList<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return kits.stream().filter(kit -> kit.id().equals(id)).findFirst();
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(kits);
        }

        @Override
        public boolean exists(KitId id) {
            return find(id).isPresent();
        }

        @Override
        public void save(KitDefinition definition) {
            kits.removeIf(existing -> existing.id().equals(definition.id()));
            kits.add(definition);
        }

        @Override
        public void delete(KitId id) {
            kits.removeIf(kit -> kit.id().equals(id));
        }
    }

    /** Records stored categories in insertion order so the browse menu groups by them. */
    private static final class StubCategoryRepository implements KitCategoryRepository {
        private final List<KitCategory> categories = new ArrayList<>();

        @Override
        public Optional<KitCategory> find(String id) {
            return categories.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<KitCategory> all() {
            return List.copyOf(categories);
        }

        @Override
        public void save(KitCategory category) {
            categories.removeIf(existing -> existing.id().equals(category.id()));
            categories.add(category);
        }

        @Override
        public void delete(String id) {
            categories.removeIf(c -> c.id().equals(id));
        }
    }

    /** Records every grant so a kit click is asserted by the use case delegating the grant here. */
    private static final class RecordingGranter implements KitGranter {
        private final List<String> granted = new ArrayList<>();

        @Override
        public Grant grant(PlayerRef who, KitDefinition kit) {
            granted.add(kit.id().value());
            return Grant.complete();
        }
    }

    private static final class NoClaims implements KitClaimStore {
        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return false;
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {}

        @Override
        public void reset(PlayerRef who, KitId kit) {}

        @Override
        public void resetAll(PlayerRef who) {}
    }

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.unlimited();
        }
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    /**
     * Surfaces the kit-name and category-name tokens so a tile's name appears; every other key (the lore lines, the
     * nav and back labels) renders as its bare key, which is enough for the slot-and-lore-count assertions.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(KitsMessageKey.KIT_MENU_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("kit", "");
            }
            if (key.key().equals(KitsMessageKey.KIT_MENU_CATEGORY_NAME.key())) {
                return placeholders.getOrDefault("category", "");
            }
            return key.key();
        }
    }

    private static final com.uxplima.uxmessentials.shared.application.port.Logger NOOP =
            new com.uxplima.uxmessentials.shared.application.port.Logger() {
                @Override
                public void info(String m, Object... a) {}

                @Override
                public void warn(String m, Object... a) {}

                @Override
                public void error(String m, Throwable t) {}

                @Override
                public void debug(String m, Object... a) {}
            };

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
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
}

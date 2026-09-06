package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpBrowseMenu;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.Page;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpSort;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.ListControlActions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The player-warp browse menu, the first real consumer of the engine's paged-list SPI. Proves the SPI end to end:
 * the shipped {@code pwarp-browse} spec loads and renders one page of cards over a real engine; the bound source is
 * asked for exactly one bounded page per request (never the whole table) with the right total; a page flip and the
 * sort-cycle control each re-query one page off-thread with the changed coordinates; and a card click teleports the
 * viewer through the same {@link UsePlayerWarp} gate the command drives. Drives the façade through a synchronous
 * scheduler so the async resolve and the entity render both run inline.
 */
class PwarpBrowseMenuTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);
    private static final int PAGE_SIZE = 45;
    private static final int TOTAL = 50;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private SyncScheduler scheduler;
    private SpyBrowse browse;
    private PlayerWarpRepository repository;
    private PlayerWarpTeleporter teleporter;
    private UsePlayerWarp usePlayerWarp;

    private PlayerWarpBrowseMenu menu;
    private MenuBindings bindings;
    private List<PlayerWarpName> viewOpened;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        browse = new SpyBrowse();
        viewOpened = new java.util.ArrayList<>();
        repository = mock(PlayerWarpRepository.class);
        teleporter = mock(PlayerWarpTeleporter.class);
        WarpSafetyChecker safety = mock(WarpSafetyChecker.class);
        when(safety.isSafe(any())).thenReturn(true);
        usePlayerWarp = new UsePlayerWarp(
                repository,
                teleporter,
                new Notifier(new KeyMessages(), noopSink()),
                safety,
                mock(Permissions.class),
                mock(WarpBanStore.class),
                mock(WarpMemberStore.class),
                mock(WarpWhitelistStore.class),
                mock(PlayerWarpPasswordStore.class),
                mock(Cooldowns.class),
                Optional.empty(),
                "local",
                Optional.empty(),
                CLOCK);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theSpecLoadsAndRendersOnePageOfCardsWithItsControls() {
        browse.seed(cards(TOTAL));
        Inventory inv = open();

        // The top five rows are the paged grid: 45 card tiles, each an ENDER_PEARL fallback icon named for its warp.
        assertThat(filledContentSlots(inv)).isEqualTo(PAGE_SIZE);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.ENDER_PEARL);
        assertThat(plainName(inv.getItem(0))).isEqualTo("warp0");
        // The bottom row is the control bar: sort / search / scope buttons and the two page arrows.
        assertThat(inv.getItem(45).getType()).isEqualTo(Material.COMPARATOR);
        assertThat(inv.getItem(46).getType()).isEqualTo(Material.SPYGLASS);
        assertThat(inv.getItem(47).getType()).isEqualTo(Material.WRITABLE_BOOK);
        assertThat(inv.getItem(48).getType()).isEqualTo(Material.ARROW);
        assertThat(inv.getItem(49).getType()).isEqualTo(Material.BOOKSHELF);
        assertThat(inv.getItem(50).getType()).isEqualTo(Material.ARROW);
        assertThat(inv.getItem(51).getType()).isEqualTo(Material.NETHER_STAR);
    }

    @Test
    void theSourceAnswersOneBoundedPageWithTheRightTotalAndNeverTheWholeTable() {
        browse.seed(cards(TOTAL));
        wireEngine();
        var source = bindings.pagedList(PlayerWarpBrowseMenu.LIST_SOURCE).orElseThrow();

        PagedResult<?> firstPage = source.apply(subjectContext(), new PageRequest(0, PAGE_SIZE, "rating", Map.of()));
        PagedResult<?> secondPage = source.apply(subjectContext(), new PageRequest(1, PAGE_SIZE, "rating", Map.of()));

        // Each request returns at most one page, and reports the whole-corpus total in the same round trip.
        assertThat(firstPage.rows()).hasSize(PAGE_SIZE);
        assertThat(firstPage.totalCount()).isEqualTo(TOTAL);
        assertThat(secondPage.rows()).hasSize(TOTAL - PAGE_SIZE);
        // A later page is a different page: the source paged the corpus rather than reading it whole and slicing.
        assertThat(secondPage.rows()).isNotEqualTo(firstPage.rows());
        // The query the read model saw was the safe public browse, bounded to the requested window.
        assertThat(browse.queries).allSatisfy(query -> {
            assertThat(query.pageSize()).isEqualTo(PAGE_SIZE);
            assertThat(query.access()).contains(WarpAccess.PUBLIC);
            assertThat(query.onlyActive()).isTrue();
            assertThat(query.viewer()).isEqualTo(viewer.uuid());
        });
    }

    @Test
    void theSourcePinsActiveSponsorsAndDropsThemFromTheScrollingFlow() {
        browse.seed(cards(TOTAL)); // the flow includes warp0
        browse.seedSponsors(List.of(sponsorCard())); // ...which is also the active sponsor
        wireEngine(new com.uxplima.uxmessentials.playerwarps.application.SponsorConfig(
                true, 5, 7, BigDecimal.valueOf(1000), "default", 1, java.time.Duration.ofDays(3)));
        var source = bindings.pagedList(PlayerWarpBrowseMenu.LIST_SOURCE).orElseThrow();

        PagedResult<?> page = source.apply(subjectContext(), new PageRequest(0, PAGE_SIZE, "rating", Map.of()));

        // The active sponsor is pinned to the top content slot, flagged sponsored, on the page.
        assertThat(page.pinned()).hasSize(1);
        PlayerWarpBrowseMenu.Tile pinned =
                (PlayerWarpBrowseMenu.Tile) page.pinned().get(0);
        assertThat(pinned.name()).isEqualTo("warp0");
        assertThat(pinned.pinnedSlot()).isZero();
        assertThat(pinned.sponsored()).isTrue();
        // ...and it is never drawn a second time in the scrolling flow.
        assertThat(page.rows())
                .noneSatisfy(row ->
                        assertThat(((PlayerWarpBrowseMenu.Tile) row).name()).isEqualTo("warp0"));
    }

    @Test
    void anEmptyCorpusPinsTheCentredPlaceholderInsteadOfABareGrid() {
        browse.seed(List.of()); // a fresh server: no public warps at all
        wireEngine();
        var source = bindings.pagedList(PlayerWarpBrowseMenu.LIST_SOURCE).orElseThrow();

        PagedResult<?> page = source.apply(subjectContext(), new PageRequest(0, PAGE_SIZE, "rating", Map.of()));

        // The empty corpus scrolls no rows and reports a zero total, pinning one placeholder to the grid centre.
        assertThat(page.rows()).isEmpty();
        assertThat(page.totalCount()).isZero();
        assertThat(page.pinned()).hasSize(1);
        PlayerWarpBrowseMenu.Tile placeholder =
                (PlayerWarpBrowseMenu.Tile) page.pinned().get(0);
        assertThat(placeholder.pinnedSlot()).isEqualTo(22);
    }

    @Test
    void theBrowseRendersTheEmptyPlaceholderWhenThereAreNoWarps() {
        browse.seed(List.of());
        Inventory inv = open();

        // The centred placeholder is the only content tile; the rest of the grid stays empty.
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.BARRIER);
        assertThat(filledContentSlots(inv)).isEqualTo(1);
    }

    @Test
    void theOwnerAndFavouritesPresetFiltersNarrowTheQueryToThatSubject() {
        browse.seed(cards(TOTAL));
        wireEngine();
        var source = bindings.pagedList(PlayerWarpBrowseMenu.LIST_SOURCE).orElseThrow();
        UUID subject = UUID.randomUUID();

        // The my-warps entry pre-applies owner=<uuid>; the query narrows to that owner and touches favourites not.
        var ownerPage = source.apply(
                presetContext(Map.of("owner", subject.toString())), new PageRequest(0, PAGE_SIZE, "rating", Map.of()));
        assertThat(ownerPage).isNotNull();
        assertThat(browse.last().owner()).contains(subject);
        assertThat(browse.last().favouritesOf()).isEmpty();

        // The favourites entry pre-applies favouritesOf=<uuid>; the query narrows to that favouriter, owner unset.
        var favouritesPage = source.apply(
                presetContext(Map.of("favouritesOf", subject.toString())),
                new PageRequest(0, PAGE_SIZE, "rating", Map.of()));
        assertThat(favouritesPage).isNotNull();
        assertThat(browse.last().favouritesOf()).contains(subject);
        assertThat(browse.last().owner()).isEmpty();
    }

    @Test
    void pagingToTheNextPageRequeriesOnePageThatDiffersFromTheFirst() {
        browse.seed(cards(TOTAL));
        Inventory inv = open();
        assertThat(plainName(inv.getItem(0))).isEqualTo("warp0");

        fireClick(50, ClickType.LEFT); // the next-page arrow

        Inventory afterFlip = top();
        assertThat(plainName(afterFlip.getItem(0))).isEqualTo("warp45");
        assertThat(filledContentSlots(afterFlip)).isEqualTo(TOTAL - PAGE_SIZE);
        assertThat(browse.last().page()).isEqualTo(1);
    }

    @Test
    void theSortCycleControlChangesTheWarpSortPassedToTheReadModel() {
        browse.seed(cards(TOTAL));
        open();
        assertThat(browse.last().sort()).isEqualTo(WarpSort.RATING);

        fireClick(45, ClickType.LEFT); // the sort-cycle button advances rating -> newest

        assertThat(browse.last().sort()).isEqualTo(WarpSort.NEWEST);
        assertThat(browse.last().page()).isZero();
    }

    @Test
    void leftClickingACardOpensTheDetailViewForThatWarpRatherThanTeleporting() {
        browse.seed(cards(TOTAL));
        open();

        fireClick(0, ClickType.LEFT); // the first card tile

        // The left click now hands the clicked warp to the pwarp-view opener instead of teleporting directly.
        assertThat(viewOpened).containsExactly(PlayerWarpName.of("warp0"));
        verify(teleporter, never()).teleportTo(any(), any());
    }

    @Test
    void rightClickingACardQuickTeleportsThroughUsePlayerWarp() {
        browse.seed(cards(TOTAL));
        PlayerWarp owned = PlayerWarp.create(viewer, viewer.name(), PlayerWarpName.of("warp0"), at(), CLOCK.instant())
                .withId(PlayerWarpId.of(1));
        when(repository.findByName(PlayerWarpName.of("warp0"))).thenReturn(Optional.of(owned));
        open();

        fireClick(0, ClickType.RIGHT); // the quick-teleport shortcut

        verify(teleporter).teleportTo(eq(viewer), any(PlayerWarp.class));
        assertThat(viewOpened).isEmpty();
    }

    private Inventory open() {
        wireEngine();
        menu.open(player, viewer);
        return top();
    }

    /** Build a real engine and register the browse menu with the sponsor sub-group off (the default for most tests). */
    private void wireEngine() {
        wireEngine(com.uxplima.uxmessentials.playerwarps.application.SponsorConfig.disabled());
    }

    /** Build a real engine wired with the paged registry and the list controls, then register the browse menu on it. */
    private void wireEngine(com.uxplima.uxmessentials.playerwarps.application.SponsorConfig sponsorConfig) {
        bindings = new MenuBindings();
        ListControlActions.register(bindings, NOOP);
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(
                renderer,
                scheduler,
                bindings.lists(),
                null,
                null,
                null,
                null,
                BedrockDetector.NONE,
                BedrockScreen.NONE,
                bindings.pagedLists());
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                System::currentTimeMillis,
                bindings.pagedLists());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        menu = new PlayerWarpBrowseMenu(
                menus,
                scheduler,
                browse,
                usePlayerWarp,
                new KeyMessages(),
                (v, name) -> viewOpened.add(name),
                sponsorConfig);
        menu.register(bindings, dataFolder, NOOP);
    }

    private MenuContext subjectContext() {
        return MenuContext.of(viewer, new PlayerWarpBrowseMenu.Subject(Optional.empty(), Map.of()), 0);
    }

    private MenuContext presetContext(Map<String, String> presetFilters) {
        return MenuContext.of(viewer, new PlayerWarpBrowseMenu.Subject(Optional.empty(), presetFilters), 0);
    }

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static int filledContentSlots(Inventory inv) {
        int filled = 0;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                filled++;
            }
        }
        return filled;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static List<WarpCard> cards(int count) {
        return IntStream.range(0, count).mapToObj(PwarpBrowseMenuTest::card).toList();
    }

    /** A card for warp0 flagged as a live sponsor, so it pins and drops from the flow that also holds warp0. */
    private static WarpCard sponsorCard() {
        return new WarpCard(
                PlayerWarpId.of(1),
                "warp0",
                null,
                "Owner",
                "world",
                null,
                null,
                null,
                0,
                0,
                0.0,
                0,
                0,
                BigDecimal.ZERO,
                "default",
                WarpAccess.PUBLIC,
                true,
                false);
    }

    private static WarpCard card(int i) {
        return new WarpCard(
                PlayerWarpId.of(i + 1),
                "warp" + i,
                null,
                "Owner",
                "world",
                null,
                null,
                null,
                i,
                0,
                0.0,
                0,
                0,
                BigDecimal.ZERO,
                "default",
                WarpAccess.PUBLIC,
                false,
                false);
    }

    private static Position at() {
        return Position.of(new com.uxplima.uxmessentials.shared.domain.WorldRef(UUID.randomUUID(), "world"), 0, 64, 0);
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    /** A read model that records every query it is handed and answers one bounded slice from a seeded card list. */
    private static final class SpyBrowse implements PlayerWarpBrowse {
        private final List<WarpQuery> queries = new CopyOnWriteArrayList<>();
        private List<WarpCard> cards = List.of();
        private List<WarpCard> sponsors = List.of();

        void seed(List<WarpCard> cards) {
            this.cards = List.copyOf(cards);
        }

        void seedSponsors(List<WarpCard> sponsors) {
            this.sponsors = List.copyOf(sponsors);
        }

        WarpQuery last() {
            return queries.get(queries.size() - 1);
        }

        @Override
        public Page<WarpCard> page(WarpQuery query) {
            queries.add(query);
            int from = Math.min(query.page() * query.pageSize(), cards.size());
            int to = Math.min(from + query.pageSize(), cards.size());
            return new Page<>(cards.subList(from, to), cards.size(), query.page(), query.pageSize());
        }

        @Override
        public List<WarpCard> activeSponsors(int limit) {
            return sponsors.subList(0, Math.min(limit, sponsors.size()));
        }
    }

    /** Surfaces the warp-name token so a tile's name renders as its warp name; every other key renders as its key. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("pwarp.gui.browse.entry-name")) {
                return placeholders.getOrDefault("warp", "");
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

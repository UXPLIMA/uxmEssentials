package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A page flip on a paged list re-queries the source for the next page rather than re-slicing a cache the engine does
 * not hold, driven end to end through the real {@link MenuListener}, with a scheduler that queues async work so an
 * in-flight query can be observed between clicks. The corpus is 200 entries over a 45-slot page (five pages); the fake
 * source labels each row with its page so a rendered slot proves which page landed, and records the scheduler zone it
 * ran in so the query is shown to run off the entity thread and the render on it.
 */
class PagedListFlipTest {

    private static final int TOTAL = 200;
    private static final int PAGE_SIZE = 45;
    private static final int NEXT_SLOT = 53;

    // 45 content slots (0-44), a page indicator at 45, and a NEXT button at 53.
    private static final String PAGED_HOCON = """
            rows = 6
            items {
              info { slot = 45, material = PAPER, name = "%page%/%max_page%" }
              next { slot = 53, type = NEXT, material = ARROW, name = "Next" }
              warps {
                slots = ["0-44"]
                list {
                  source = "pw:browse"
                  sorts  = ["RATING", "VISITS"]
                  template { material = STONE, name = "%v%" }
                }
              }
            }
            """;

    // A plain in-memory list with its own NEXT button; nine content slots (0-8) so twenty entries span two pages.
    private static final String PLAIN_HOCON = """
            rows = 6
            items {
              next { slot = 53, type = NEXT, material = ARROW, name = "Next" }
              things {
                slots = ["0-8"]
                list { source = "pw:plain", template { material = STONE, name = "%v%" } }
              }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuRenderer renderer;
    private RecordingScheduler scheduler;
    private String renderZone = "";

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        scheduler = new RecordingScheduler();
        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("v", ctx -> {
            renderZone = scheduler.zone();
            return (String) ctx.entry().orElseThrow();
        });
        placeholders.register("page", ctx -> String.valueOf(ctx.page() + 1));
        placeholders.register("max_page", ctx -> String.valueOf(ctx.pageCount()));
        ItemRenderer itemRenderer = new ItemRenderer(guiText, placeholders);
        renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void oneFlipIssuesExactlyOneQueryForTheNextPageKeepingSizeSortAndFilters() {
        FakePagedSource source = pagedRows();
        openPaged(source);
        source.reset();

        clickNext();
        assertThat(source.calls)
                .as("the query is deferred, not run on the click")
                .isZero();
        assertThat(scheduler.pendingAsync()).isEqualTo(1);

        scheduler.drainAsync();
        assertThat(source.calls).isEqualTo(1);
        assertThat(source.lastRequest.page()).isEqualTo(1);
        assertThat(source.lastRequest.size()).isEqualTo(PAGE_SIZE);
        assertThat(source.lastRequest.sort()).isEqualTo("RATING");
        assertThat(source.lastRequest.filters()).isEmpty();
        assertThat(plainName(0)).isEqualTo("p1_0");
        assertThat(plainName(45)).isEqualTo("2/5");
    }

    @Test
    void twoRapidFlipsIssueOneQueryAndAThirdAfterItCompletesIssuesAnother() {
        FakePagedSource source = pagedRows();
        openPaged(source);
        source.reset();

        clickNext();
        clickNext(); // dropped: a query is already in flight
        assertThat(scheduler.pendingAsync())
                .as("a second flip is dropped, not queued")
                .isEqualTo(1);

        scheduler.drainAsync();
        assertThat(source.calls).isEqualTo(1);
        assertThat(page()).isEqualTo(1);

        clickNext(); // the flag is clear now, so this one queries again
        assertThat(scheduler.pendingAsync()).isEqualTo(1);
        scheduler.drainAsync();
        assertThat(source.calls).isEqualTo(2);
        assertThat(source.lastRequest.page()).isEqualTo(2);
        assertThat(page()).isEqualTo(2);
    }

    @Test
    void theQueryRunsOffTheEntityThreadAndTheRenderRunsOnIt() {
        FakePagedSource source = pagedRows();
        openPaged(source);
        source.reset();
        renderZone = "";

        clickNext();
        scheduler.drainAsync();

        assertThat(source.observedZone)
                .as("the source is queried off the entity thread")
                .isEqualTo("async");
        assertThat(renderZone).as("the page is rendered on the entity thread").isEqualTo("entity");
    }

    @Test
    void aPlainListFlipIssuesNoQueryAndStillRePaginates() {
        ListSourceRegistry lists = new ListSourceRegistry();
        lists.register("pw:plain", ctx -> plainRows());
        PagedListSourceRegistry paged = mock(PagedListSourceRegistry.class);
        Menus menus = menus(lists, paged);
        menus.registerSpec("plain", new MenuSpecLoader().parse(PLAIN_HOCON));
        installListener(paged);
        open(menus, "plain");

        assertThat(plainName(0)).isEqualTo("x0");
        clickNext();

        assertThat(page()).isEqualTo(1);
        assertThat(plainName(0)).isEqualTo("x9");
        verifyNoInteractions(paged);
    }

    @Test
    void closingTheMenuMidQueryDoesNotRenderAndDoesNotThrow() {
        FakePagedSource source = pagedRows();
        MenuHolder held = openPaged(source);

        clickNext(); // starts the query; it is now queued in flight
        // Leaving the menu for another window is the close the flip must survive: the holder is no longer on top.
        player.openInventory(Bukkit.createInventory(null, 27));

        assertThatCode(() -> scheduler.drainAsync()).doesNotThrowAnyException();
        assertThat(held.pagedFlipInFlight())
                .as("the in-flight flag is cleared even on the closed path")
                .isFalse();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isNotSameAs(held);
    }

    @Test
    void aFlipPastTheLastPageIsClampedNotWrapped() {
        FakePagedSource source = pagedRows();
        openPaged(source);

        // Five pages (0-4): walk to the last, one flip at a time.
        for (int i = 0; i < 4; i++) {
            clickNext();
            scheduler.drainAsync();
        }
        assertThat(page()).isEqualTo(4);
        assertThat(plainName(0)).isEqualTo("p4_0");
        source.reset();

        clickNext(); // already on the last page
        assertThat(scheduler.pendingAsync())
                .as("a clamped flip issues no query")
                .isZero();
        scheduler.drainAsync();

        assertThat(source.calls).isZero();
        assertThat(page())
                .as("the page is clamped to the last, never wrapped to zero")
                .isEqualTo(4);
        assertThat(plainName(0)).isEqualTo("p4_0");
    }

    @Test
    void aQueryThatThrowsKeepsThePageLogsAndClearsTheInFlightFlagSoTheNextFlipWorks() {
        // Page zero (the open) always answers; only the flip to a later page throws while the switch is armed.
        AtomicBoolean failFlip = new AtomicBoolean(true);
        FakePagedSource source = new FakePagedSource(request -> {
            if (failFlip.get() && request.page() > 0) {
                throw new IllegalStateException("boom");
            }
            return PagedResult.of(pageRows(request.page()), TOTAL);
        });
        MenuHolder held = openPaged(source);
        source.reset();

        List<LogRecord> logged = captureListenerLog(() -> {
            clickNext(); // fires the query
            scheduler.drainAsync(); // the query throws
        });

        assertThat(held.pagedFlipInFlight())
                .as("a thrown query clears the flag so the arrows are freed, not wedged for the session")
                .isFalse();
        assertThat(page()).as("the page already on screen is kept").isZero();
        assertThat(plainName(0)).isEqualTo("p0_0");
        assertThat(logged.stream().map(LogRecord::getMessage))
                .anyMatch(message -> message.contains("event=paged_flip_failed") && message.contains("id=pw:browse"));

        // The flag is clear, so the next flip queries and lands the page it could not reach before.
        failFlip.set(false);
        source.reset();
        clickNext();
        scheduler.drainAsync();

        assertThat(source.calls).isEqualTo(1);
        assertThat(source.lastRequest.page()).isEqualTo(1);
        assertThat(page()).isEqualTo(1);
        assertThat(plainName(0)).isEqualTo("p1_0");
    }

    @Test
    void aQueryThatReturnsNullIsTreatedAsAFailureRatherThanWedgingTheArrows() {
        // A source that answers null instead of throwing used to escape the guard and strand the in-flight flag.
        AtomicBoolean nullOnFlip = new AtomicBoolean(true);
        FakePagedSource source = new FakePagedSource(request -> {
            if (nullOnFlip.get() && request.page() > 0) {
                return null;
            }
            return PagedResult.of(pageRows(request.page()), TOTAL);
        });
        openPaged(source);
        source.reset();

        List<LogRecord> logged = captureListenerLog(() -> {
            clickNext();
            scheduler.drainAsync();
        });

        assertThat(page()).as("the page already on screen is kept").isZero();
        assertThat(logged.stream().map(LogRecord::getMessage))
                .anyMatch(message -> message.contains("event=paged_flip_failed") && message.contains("id=pw:browse"));

        nullOnFlip.set(false);
        source.reset();
        clickNext();
        scheduler.drainAsync();

        assertThat(page()).as("the arrows still work after a null answer").isEqualTo(1);
    }

    // --- fixtures -------------------------------------------------------------------------------------------------

    private FakePagedSource pagedRows() {
        return new FakePagedSource(request -> PagedResult.of(pageRows(request.page()), TOTAL));
    }

    /** Open the paged menu and drain the open's async resolve, returning the holder the viewer is left looking at. */
    private MenuHolder openPaged(FakePagedSource source) {
        PagedListSourceRegistry paged = new PagedListSourceRegistry();
        paged.register("pw:browse", source);
        Menus menus = menus(new ListSourceRegistry(), paged);
        menus.registerSpec("paged", new MenuSpecLoader().parse(PAGED_HOCON));
        installListener(paged);
        open(menus, "paged");
        return holder();
    }

    private Menus menus(ListSourceRegistry lists, PagedListSourceRegistry paged) {
        return new Menus(
                renderer, scheduler, lists, null, null, null, null, BedrockDetector.NONE, BedrockScreen.NONE, paged);
    }

    private void installListener(PagedListSourceRegistry paged) {
        MenuListener listener = new MenuListener(
                renderer,
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                System::currentTimeMillis,
                paged);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private void open(Menus menus, String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
        scheduler.drainAsync();
    }

    private void clickNext() {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, NEXT_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
    }

    private MenuHolder holder() {
        return (MenuHolder) player.getOpenInventory().getTopInventory().getHolder();
    }

    private int page() {
        return holder().ctx().page();
    }

    private String plainName(int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    private static List<Object> pageRows(int page) {
        List<Object> rows = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            rows.add("p" + page + "_" + i);
        }
        return rows;
    }

    private static List<Object> plainRows() {
        List<Object> rows = new ArrayList<>(20);
        for (int i = 0; i < 20; i++) {
            rows.add("x" + i);
        }
        return rows;
    }

    /** Collect what the {@link MenuListener} logs while {@code body} runs: how the flip's failure warning is read. */
    private static List<LogRecord> captureListenerLog(Runnable body) {
        Logger logger = Logger.getLogger(MenuListener.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            body.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }

    /** A paged source that counts its calls, remembers the last request, and notes the scheduler zone it ran in. */
    private final class FakePagedSource
            implements java.util.function.BiFunction<MenuContext, PageRequest, PagedResult<?>> {
        private final Function<PageRequest, PagedResult<?>> responder;
        private int calls;
        private PageRequest lastRequest;
        private String observedZone = "";

        FakePagedSource(Function<PageRequest, PagedResult<?>> responder) {
            this.responder = responder;
        }

        void reset() {
            calls = 0;
            lastRequest = null;
            observedZone = "";
        }

        @Override
        public PagedResult<?> apply(MenuContext ctx, PageRequest request) {
            calls++;
            lastRequest = request;
            observedZone = scheduler.zone();
            return responder.apply(request);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /**
     * A scheduler that runs entity/region/global work inline but queues {@code async} work, so a test can observe a
     * query held in flight between clicks and then release it. Each run stamps a zone label the fixtures read to prove
     * the query ran {@code async} and the render ran on the {@code entity} thread.
     */
    private static final class RecordingScheduler implements Scheduler {
        private final List<Runnable> asyncQueue = new ArrayList<>();
        private String zone = "main";

        String zone() {
            return zone;
        }

        int pendingAsync() {
            return asyncQueue.size();
        }

        void drainAsync() {
            List<Runnable> due = new ArrayList<>(asyncQueue);
            asyncQueue.clear();
            for (Runnable task : due) {
                run("async", task);
            }
        }

        private void run(String inZone, Runnable task) {
            String previous = zone;
            zone = inZone;
            try {
                task.run();
            } finally {
                zone = previous;
            }
        }

        @Override
        public void onGlobal(Runnable task) {
            run("global", task);
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            run("region", task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            run("entity", task);
        }

        @Override
        public void async(Runnable task) {
            asyncQueue.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            run("async", task);
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}

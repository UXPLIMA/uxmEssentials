package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuTextPrompt;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.ListControlActions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The three bottom-bar controls a browse menu drives its paged list with, {@code list-sort}, {@code list-filter} and
 * {@code list-search}, exercised end to end through the real {@link MenuListener}. A control click must enter the very
 * same re-query-and-repaint path a page flip takes (Task 6): a scheduler that queues async work lets the in-flight
 * query be observed between clicks, so each control is shown to defer exactly one query behind the shared in-flight
 * flag and to target page zero because the changed sort or filter makes any later page meaningless. A
 * {@link RecordingPrompt} stands in for the text-input seam so {@code list-search}'s submit/cancel are driven by hand.
 */
class ListControlActionsTest {

    private static final int TOTAL = 200;
    private static final int PAGE_SIZE = 45;
    private static final int NEXT_SLOT = 53;
    private static final int SORT_NEXT_SLOT = 46;
    private static final int SORT_PREV_SLOT = 47;
    private static final int SORT_RESET_SLOT = 48;
    private static final int FILTER_SET_SLOT = 49;
    private static final int FILTER_CLEAR_SLOT = 50;
    private static final int SEARCH_SLOT = 51;
    private static final int UNKNOWN_SLOT = 52;

    // 45 content slots (0-44) for the paged list, a NEXT button, and one button per control action.
    private static final String HOCON = """
            rows = 6
            items {
              next        { slot = 53, type = NEXT, material = ARROW, name = "n" }
              sortNext    { slot = 46, material = LIME_DYE,   name = "s", click { left = ["list-sort:pw:browse"] } }
              sortPrev    { slot = 47, material = ORANGE_DYE, name = "s", click { left = ["list-sort:pw:browse:prev"] } }
              sortReset   { slot = 48, material = RED_DYE,    name = "s", click { left = ["list-sort:pw:browse:reset"] } }
              filterSet   { slot = 49, material = HOPPER,     name = "f", click { left = ["list-filter:pw:browse:category=%argument_cat%"] } }
              filterClear { slot = 50, material = BUCKET,     name = "f", click { left = ["list-filter:pw:browse:category="] } }
              search      { slot = 51, material = COMPASS,    name = "q", click { left = ["list-search:pw:browse:category"] } }
              unknown     { slot = 52, material = BARRIER,    name = "u", click { left = ["list-filter:missing:category=x"] } }
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

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private MenuRenderer renderer;
    private MenuBindings bindings;
    private RecordingScheduler scheduler;
    private RecordingPrompt prompt;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new RecordingScheduler();
        prompt = new RecordingPrompt();
        bindings = new MenuBindings();
        ListControlActions.register(bindings, new NoopLogger());
        bindings.placeholders().register("v", ctx -> (String) ctx.entry().orElseThrow());
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void listSortWithNoArgAdvancesTheSortAndReQueriesAtPageZero() {
        FakePagedSource source = openBrowse(Map.of());

        clickSlot(SORT_NEXT_SLOT);
        assertThat(source.calls)
                .as("the query is deferred, not run on the click")
                .isZero();
        assertThat(scheduler.pendingAsync()).isEqualTo(1);
        assertThat(holder().pagedFlipInFlight())
                .as("a control enters the same in-flight-guarded path a flip does")
                .isTrue();

        scheduler.drainAsync();
        assertThat(source.calls).isEqualTo(1);
        assertThat(source.lastRequest.sort())
                .as("the sort advanced to the next offered one")
                .isEqualTo("VISITS");
        assertThat(source.lastRequest.page())
                .as("a sort change returns to page zero")
                .isZero();
        assertThat(holder().pagedFlipInFlight()).isFalse();
    }

    @Test
    void aControlReQueriesAtPageZeroEvenFromALaterPage() {
        FakePagedSource source = openBrowse(Map.of());

        clickSlot(NEXT_SLOT); // flip to page 1 first
        scheduler.drainAsync();
        assertThat(page()).isEqualTo(1);
        source.reset();

        clickSlot(SORT_NEXT_SLOT);
        scheduler.drainAsync();

        assertThat(source.lastRequest.page())
                .as("the control re-queries page zero, not the page on screen")
                .isZero();
        assertThat(source.lastRequest.sort()).isEqualTo("VISITS");
        assertThat(page()).as("the menu is left on page zero").isZero();
    }

    @Test
    void listSortPrevStepsBackAndResetReturnsToTheFirstSort() {
        FakePagedSource source = openBrowse(Map.of());

        clickSlot(SORT_NEXT_SLOT); // RATING -> VISITS
        scheduler.drainAsync();
        source.reset();

        clickSlot(SORT_PREV_SLOT); // VISITS -> RATING
        scheduler.drainAsync();
        assertThat(source.lastRequest.sort())
                .as("prev steps back to the previous sort")
                .isEqualTo("RATING");

        clickSlot(SORT_NEXT_SLOT); // RATING -> VISITS
        scheduler.drainAsync();
        source.reset();

        clickSlot(SORT_RESET_SLOT); // -> first sort
        scheduler.drainAsync();
        assertThat(source.lastRequest.sort())
                .as("reset returns to the first offered sort")
                .isEqualTo("RATING");
    }

    @Test
    void listFilterWithAValueSetsItAndReQueriesAtPageZero() {
        FakePagedSource source = openBrowse(Map.of("cat", "shops"));

        clickSlot(NEXT_SLOT); // move off page zero to prove the filter resets it
        scheduler.drainAsync();
        source.reset();

        clickSlot(FILTER_SET_SLOT);
        assertThat(scheduler.pendingAsync())
                .as("the filter change defers one query")
                .isEqualTo(1);
        scheduler.drainAsync();

        assertThat(source.lastRequest.filters())
                .as("the filter value carrying %argument_cat% was resolved before it was stored")
                .containsExactly(entry("category", "shops"));
        assertThat(source.lastRequest.page()).isZero();
        assertThat(page()).isZero();
    }

    @Test
    void listFilterWithAnEmptyValueClearsItAndReQueriesAtPageZero() {
        FakePagedSource source = openBrowse(Map.of("cat", "shops"));

        clickSlot(FILTER_SET_SLOT); // set category=shops
        scheduler.drainAsync();
        assertThat(source.lastRequest.filters()).containsExactly(entry("category", "shops"));
        source.reset();

        clickSlot(FILTER_CLEAR_SLOT); // category= (empty) clears
        assertThat(scheduler.pendingAsync()).isEqualTo(1);
        scheduler.drainAsync();

        assertThat(source.lastRequest.filters())
                .as("an empty value clears the filter")
                .isEmpty();
        assertThat(source.lastRequest.page()).isZero();
    }

    @Test
    void listSearchPromptsAndOnSubmitStoresTheTypedLineAsTheFilterAndReQueries() {
        FakePagedSource source = openBrowse(Map.of());

        clickSlot(SEARCH_SLOT);
        assertThat(prompt.prompts).as("the click opened the text prompt").isEqualTo(1);
        assertThat(scheduler.pendingAsync())
                .as("nothing is queried until the line is submitted")
                .isZero();

        prompt.submit("diamond");
        assertThat(scheduler.pendingAsync())
                .as("submit enters the shared re-query path")
                .isEqualTo(1);
        assertThat(holder().pagedFlipInFlight()).isTrue();
        scheduler.drainAsync();

        assertThat(source.lastRequest.filters())
                .as("the typed line became the filter under the search key")
                .containsExactly(entry("category", "diamond"));
        assertThat(source.lastRequest.page()).isZero();
    }

    @Test
    void listSearchCancelChangesNothing() {
        FakePagedSource source = openBrowse(Map.of());

        clickSlot(SEARCH_SLOT);
        prompt.cancel();

        assertThat(scheduler.pendingAsync()).as("a cancel issues no query").isZero();
        assertThat(source.calls).as("a cancel touches the source not at all").isZero();
    }

    @Test
    void anActionNamingAListNotInTheMenuLogsAWarningAndDoesNotCrash() {
        openBrowse(Map.of());

        List<LogRecord> logged = captureListenerLog(() -> assertThatCode(() -> {
                    clickSlot(UNKNOWN_SLOT);
                    scheduler.drainAsync();
                })
                .doesNotThrowAnyException());

        assertThat(scheduler.pendingAsync())
                .as("no query is issued for an unknown list")
                .isZero();
        assertThat(logged.stream().map(LogRecord::getMessage))
                .anyMatch(message ->
                        message.contains("event=list_control_unknown_list") && message.contains("id=missing"));
    }

    // --- fixtures -------------------------------------------------------------------------------------------------

    /** Register the browse menu, install the listener, open it with {@code args}, and drain the open's async resolve. */
    private FakePagedSource openBrowse(Map<String, String> args) {
        FakePagedSource source = new FakePagedSource(request -> PagedResult.of(pageRows(request.page()), TOTAL));
        PagedListSourceRegistry paged = new PagedListSourceRegistry();
        paged.register("pw:browse", source);
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
                paged);
        menus.registerSpec("browse", new MenuSpecLoader().parse(HOCON));
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
                paged,
                prompt);
        server.getPluginManager().registerEvents(listener, plugin);
        menus.open(viewer, "browse", null, 0, args);
        scheduler.drainAsync();
        source.reset();
        return source;
    }

    private void clickSlot(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
    }

    private MenuHolder holder() {
        return (MenuHolder) player.getOpenInventory().getTopInventory().getHolder();
    }

    private int page() {
        return holder().ctx().page();
    }

    private static List<Object> pageRows(int page) {
        List<Object> rows = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            rows.add("p" + page + "_" + i);
        }
        return rows;
    }

    /** Collect what the {@link MenuListener} logs while {@code body} runs: how the unknown-list warning is read. */
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

    /** A paged source that counts its calls and remembers the last request, so a test reads the sort/filters/page it saw. */
    private static final class FakePagedSource
            implements java.util.function.BiFunction<MenuContext, PageRequest, PagedResult<?>> {
        private final Function<PageRequest, PagedResult<?>> responder;
        private int calls;
        private PageRequest lastRequest;

        FakePagedSource(Function<PageRequest, PagedResult<?>> responder) {
            this.responder = responder;
        }

        void reset() {
            calls = 0;
            lastRequest = null;
        }

        @Override
        public PagedResult<?> apply(MenuContext ctx, PageRequest request) {
            calls++;
            lastRequest = request;
            return responder.apply(request);
        }
    }

    /** A stand-in for the text-input seam: it records the callbacks so the test fires submit/cancel by hand. */
    private static final class RecordingPrompt implements MenuTextPrompt {
        int prompts;

        @Nullable Consumer<String> onSubmit;

        @Nullable Runnable onCancel;

        @Override
        public void prompt(
                org.bukkit.entity.Player player,
                String key,
                Component promptLabel,
                @Nullable String initialText,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            this.prompts++;
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }

        void submit(String text) {
            java.util.Objects.requireNonNull(onSubmit, "onSubmit").accept(text);
        }

        void cancel() {
            java.util.Objects.requireNonNull(onCancel, "onCancel").run();
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /**
     * A scheduler that runs entity/region/global work inline but queues {@code async} work, so a test can observe a
     * control's query held in flight between clicks and then release it, the same shape {@code PagedListFlipTest} uses.
     */
    private static final class RecordingScheduler implements Scheduler {
        private final List<Runnable> asyncQueue = new ArrayList<>();

        int pendingAsync() {
            return asyncQueue.size();
        }

        void drainAsync() {
            List<Runnable> due = new ArrayList<>(asyncQueue);
            asyncQueue.clear();
            for (Runnable task : due) {
                task.run();
            }
        }

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
            asyncQueue.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}

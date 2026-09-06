package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.inventory.Inventory;

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
 * The budget that matters for a paged list is that opening (and, by the same path, flipping) is independent of how
 * large the corpus is: the engine asks a paged source for exactly one page and only ever holds that page, whether the
 * store behind it has forty-five rows or a million. This states that as a property a test can hold forever, not a
 * millisecond number that rots. A tripwire corpus of a million rows proves it two ways at once: the source is asked for
 * a single page sized to the slots (not to the corpus), and the corpus throws the instant anything tries to walk it
 * whole, so the old whole-list fetch would fail here loudly rather than merely run slowly.
 */
class PagedListBudgetTest {

    private static final int CORPUS = 1_000_000;
    // 45 content slots (0-44) with a page indicator below them; no page-size, so the request size derives from slots.
    private static final int PAGE_SIZE = 45;

    private static final String PAGED_HOCON = """
            rows = 6
            items {
              info { slot = 45, material = PAPER, name = "%page%/%max_page%" }
              warps {
                slots = ["0-44"]
                list {
                  source = "pw:browse"
                  sorts  = ["RATING"]
                  template { material = STONE, name = "%v%" }
                }
              }
            }
            """;

    private ServerMock server;
    private PlayerMock player;
    private MenuRenderer renderer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");
        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("v", ctx -> (String) ctx.entry().orElseThrow());
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
    void aMillionRowCorpusIsAskedForExactlyOnePageSizedToTheSlots() {
        BudgetSource source = new BudgetSource(CORPUS);

        Inventory inv = openTop(source);

        assertThat(source.calls)
                .as("the engine queries the source once, not once per row")
                .isEqualTo(1);
        assertThat(source.lastRequest.size())
                .as("the source is asked for the page size the slots imply, not the corpus size")
                .isEqualTo(PAGE_SIZE)
                .isNotEqualTo(CORPUS);
        assertThat(source.lastRequest.page()).isZero();
        assertThat(source.rowsHandedToEngine)
                .as("the engine is handed at most one page of rows, never the corpus")
                .isLessThanOrEqualTo(PAGE_SIZE);
        assertThat(source.maxCorpusIndexTouched)
                .as("nothing walked past the first page into the corpus")
                .isLessThan(PAGE_SIZE);
        assertThat(filledContentSlots(inv)).isEqualTo(PAGE_SIZE);
    }

    @Test
    void aFortyFiveRowCorpusIsAskedForTheSameOnePage() {
        BudgetSource source = new BudgetSource(PAGE_SIZE);

        Inventory inv = openTop(source);

        assertThat(source.calls).isEqualTo(1);
        assertThat(source.lastRequest.size())
                .as("the same one page is requested whether the corpus is 45 rows or a million")
                .isEqualTo(PAGE_SIZE);
        assertThat(source.rowsHandedToEngine).isLessThanOrEqualTo(PAGE_SIZE);
        assertThat(filledContentSlots(inv)).isEqualTo(PAGE_SIZE);
    }

    @Test
    void openCostDoesNotScaleWithCorpusSize() {
        BudgetSource small = new BudgetSource(PAGE_SIZE);
        BudgetSource huge = new BudgetSource(CORPUS);

        openTop(small);
        openTop(huge);

        assertThat(huge.calls)
                .as("both opens issue exactly one query, regardless of corpus size")
                .isEqualTo(small.calls)
                .isEqualTo(1);
        assertThat(huge.lastRequest.page()).isEqualTo(small.lastRequest.page());
        assertThat(huge.lastRequest.size())
                .as("the request the engine builds is identical for a tiny and a huge corpus")
                .isEqualTo(small.lastRequest.size());
        assertThat(huge.rowsHandedToEngine)
                .as("the engine receives the same one page of rows either way")
                .isEqualTo(small.rowsHandedToEngine);
    }

    private Inventory openTop(BudgetSource source) {
        PagedListSourceRegistry paged = new PagedListSourceRegistry();
        paged.register("pw:browse", source);
        Menus menus = new Menus(
                renderer,
                new SyncScheduler(),
                new ListSourceRegistry(),
                null,
                null,
                null,
                null,
                BedrockDetector.NONE,
                BedrockScreen.NONE,
                paged);
        menus.registerSpec("test", new MenuSpecLoader().parse(PAGED_HOCON));
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "test", null);
        return player.getOpenInventory().getTopInventory();
    }

    /** How many of the 45 list content slots (0-44) hold an item. */
    private static int filledContentSlots(Inventory inv) {
        int filled = 0;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            if (inv.getItem(slot) != null) {
                filled++;
            }
        }
        return filled;
    }

    /**
     * A paged source over a {@link TripwireCorpus}: it answers a {@link PageRequest} by copying one bounded window out
     * of the corpus and reporting the total. It records how it was asked and what it handed back so the test can prove
     * the query was for one page, not the whole store.
     */
    private static final class BudgetSource implements BiFunction<MenuContext, PageRequest, PagedResult<?>> {
        private final TripwireCorpus corpus;
        private int calls;
        private PageRequest lastRequest;
        private int rowsHandedToEngine;
        private int maxCorpusIndexTouched = -1;

        BudgetSource(int corpusSize) {
            this.corpus = new TripwireCorpus(corpusSize);
        }

        @Override
        public PagedResult<?> apply(MenuContext ctx, PageRequest request) {
            calls++;
            lastRequest = request;
            int from = request.page() * request.size();
            int to = Math.min(from + request.size(), corpus.size());
            List<Object> window = new ArrayList<>(Math.max(0, to - from));
            for (int i = from; i < to; i++) {
                window.add(corpus.get(i));
            }
            maxCorpusIndexTouched = corpus.maxIndexTouched;
            rowsHandedToEngine = window.size();
            return PagedResult.of(window, corpus.size());
        }
    }

    /**
     * A corpus that answers a bounded {@code get(index)} but throws the moment anything tries to enumerate it whole. It
     * lets a paged source read one page's worth of rows by index while making the old whole-list fetch, materialising
     * the corpus into a list, iterating or streaming it, an immediate, loud failure rather than a quiet slow path.
     */
    private static final class TripwireCorpus extends AbstractList<Object> {
        private final int size;
        private int maxIndexTouched = -1;

        TripwireCorpus(int size) {
            this.size = size;
        }

        @Override
        public Object get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }
            maxIndexTouched = Math.max(maxIndexTouched, index);
            return "row" + index;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public java.util.Iterator<Object> iterator() {
            throw new AssertionError("the corpus was enumerated whole; a paged source must read it one page at a time");
        }

        @Override
        public Object[] toArray() {
            throw new AssertionError(
                    "the corpus was materialised whole; a paged source must read it one page at a time");
        }

        @Override
        public <T> T[] toArray(T[] a) {
            throw new AssertionError(
                    "the corpus was materialised whole; a paged source must read it one page at a time");
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}

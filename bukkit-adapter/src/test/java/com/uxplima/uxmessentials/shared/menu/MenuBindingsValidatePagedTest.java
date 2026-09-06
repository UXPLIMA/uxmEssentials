package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

/**
 * {@link MenuBindings#validate} learns the paged list source. A {@code list.source} may now name an in-memory list
 * or a paged one; naming neither is still an unresolved reference. And {@code page-size}/{@code sorts} are paged-only
 * knobs. Declared against a plain in-memory source they do nothing, so that misuse is reported rather than silently
 * dropped, which would let an operator believe their menu pages or sorts when it does not.
 */
class MenuBindingsValidatePagedTest {

    private static final String TITLE = "Browse Warps";

    @Test
    void reportsASourceThatIsNeitherAListNorAPagedList() {
        MenuBindings bindings = new MenuBindings();

        List<String> missing = bindings.validate(List.of(listMenu("warps", "playerwarps:none", "", "")));

        assertThat(missing).hasSize(1);
        assertThat(missing.get(0)).contains(TITLE).contains("warps").contains("playerwarps:none");
    }

    @Test
    void reportsPageSizeOrSortsAgainstAPlainList() {
        MenuBindings bindings = new MenuBindings();
        bindings.list("playerwarps:list", ctx -> List.of());

        MenuSpec withPageSize = listMenu("warps", "playerwarps:list", "page-size = 45", "");
        MenuSpec withSorts = listMenu("warps", "playerwarps:list", "", "sorts = [\"RATING\"]");

        assertThat(bindings.validate(List.of(withPageSize)))
                .singleElement()
                .asString()
                .contains(TITLE)
                .contains("warps")
                .contains("playerwarps:list");
        assertThat(bindings.validate(List.of(withSorts)))
                .singleElement()
                .asString()
                .contains(TITLE)
                .contains("warps")
                .contains("playerwarps:list");
    }

    @Test
    void aPagedSourceWithNoKeysValidatesClean() {
        MenuBindings bindings = new MenuBindings();
        bindings.pagedList("playerwarps:browse", (ctx, page) -> PagedResult.of(List.of(), 0));

        assertThat(bindings.validate(List.of(listMenu("warps", "playerwarps:browse", "", ""))))
                .as("a paged source used with neither key gets the default sort and a slot-derived size")
                .isEmpty();
    }

    @Test
    void aPlainSourceWithNoKeysValidatesClean() {
        MenuBindings bindings = new MenuBindings();
        bindings.list("playerwarps:list", ctx -> List.of());

        assertThat(bindings.validate(List.of(listMenu("warps", "playerwarps:list", "", ""))))
                .isEmpty();
    }

    @Test
    void reportsAListSortButtonWhoseSourceDeclaresNoSorts() {
        MenuBindings bindings = new MenuBindings();
        bindings.pagedList("playerwarps:browse", (ctx, page) -> PagedResult.of(List.of(), 0));
        bindings.action("list-sort", ctx -> {});

        assertThat(bindings.validate(List.of(sortMenu("playerwarps:browse", "playerwarps:browse", ""))))
                .singleElement()
                .asString()
                .contains(TITLE)
                .contains("list-sort:playerwarps:browse")
                .contains("no sorts");
    }

    @Test
    void reportsAListSortNamingAListTheMenuDoesNotContain() {
        MenuBindings bindings = new MenuBindings();
        bindings.pagedList("playerwarps:browse", (ctx, page) -> PagedResult.of(List.of(), 0));
        bindings.action("list-sort", ctx -> {});

        assertThat(bindings.validate(
                        List.of(sortMenu("playerwarps:browse", "playerwarps:other", "sorts = [\"RATING\"]"))))
                .singleElement()
                .asString()
                .contains("list-sort:playerwarps:other")
                .contains("does not contain");
    }

    @Test
    void aListSortOnASourceThatOffersSortsValidatesClean() {
        MenuBindings bindings = new MenuBindings();
        bindings.pagedList("playerwarps:browse", (ctx, page) -> PagedResult.of(List.of(), 0));
        bindings.action("list-sort", ctx -> {});

        assertThat(bindings.validate(
                        List.of(sortMenu("playerwarps:browse", "playerwarps:browse", "sorts = [\"RATING\"]"))))
                .isEmpty();
    }

    /**
     * A menu whose paged list is backed by {@code source} (optionally declaring {@code sortsLine}) and whose one button
     * runs {@code list-sort:sortTarget}, so {@code validate} sees the linkage between the sort button and the list.
     */
    private static MenuSpec sortMenu(String source, String sortTarget, String sortsLine) {
        String hocon = """
                title = "%s"
                rows = 6
                items {
                  sortBtn { slot = 45, material = ARROW, name = "s", click { left = ["list-sort:%s"] } }
                  warps {
                    slots = ["0-8"]
                    list {
                      source = "%s"
                      %s
                      template { material = STONE, name = "entry" }
                    }
                  }
                }
                """.formatted(TITLE, sortTarget, source, sortsLine);
        return new MenuSpecLoader().parse(hocon);
    }

    /**
     * A one-item menu whose single item is a list backed by {@code source}, optionally carrying a {@code page-size}
     * and a {@code sorts} line. The template is deliberately inert, a bare material and a literal name, no
     * placeholders or actions, so the only thing {@code validate} can report is the list source itself.
     */
    private static MenuSpec listMenu(String itemId, String source, String pageSizeLine, String sortsLine) {
        String hocon = """
                title = "%s"
                rows = 6
                items {
                  %s {
                    slots = ["0-8"]
                    list {
                      source = "%s"
                      %s
                      %s
                      template { material = STONE, name = "entry" }
                    }
                  }
                }
                """.formatted(TITLE, itemId, source, pageSizeLine, sortsLine);
        return new MenuSpecLoader().parse(hocon);
    }
}

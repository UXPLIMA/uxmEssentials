package com.uxplima.uxmessentials.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmessentials.communication.domain.InfoPage;
import org.junit.jupiter.api.Test;

/**
 * The pure pagination half of the data-driven info commands: {@link ShowInfoPage} slices an {@link InfoPage} into the
 * requested page and reports the page coordinates the adapter draws chrome from. It pins the headline rules, a body
 * that fits in one page is not paginated, a longer body splits at the configured size, and an out-of-range page is
 * clamped into {@code 1..pageCount} rather than yielding an empty or failing slice.
 */
class ShowInfoPageTest {

    @Test
    void aBodyThatFitsInOnePageIsNotPaginated() {
        InfoPage page = InfoPage.of("rules", List.of("one", "two", "three"), 8);

        InfoPageView view = ShowInfoPage.view(page, 1);

        assertThat(view.command()).isEqualTo("rules");
        assertThat(view.lines()).containsExactly("one", "two", "three");
        assertThat(view.page()).isEqualTo(1);
        assertThat(view.pageCount()).isEqualTo(1);
        assertThat(view.isPaginated()).isFalse();
    }

    @Test
    void aLongerBodySplitsAtTheConfiguredPageSize() {
        InfoPage page = InfoPage.of("info", List.of("a", "b", "c", "d", "e"), 2);

        assertThat(page.pageCount()).isEqualTo(3);
        assertThat(ShowInfoPage.view(page, 1).lines()).containsExactly("a", "b");
        assertThat(ShowInfoPage.view(page, 2).lines()).containsExactly("c", "d");

        InfoPageView last = ShowInfoPage.view(page, 3);
        assertThat(last.lines()).containsExactly("e");
        assertThat(last.page()).isEqualTo(3);
        assertThat(last.pageCount()).isEqualTo(3);
        assertThat(last.isPaginated()).isTrue();
    }

    @Test
    void anOverLargePageRequestIsClampedToTheLastPage() {
        InfoPage page = InfoPage.of("motd", List.of("a", "b", "c", "d"), 2);

        InfoPageView view = ShowInfoPage.view(page, 99);

        assertThat(view.page()).isEqualTo(2); // clamped to pageCount, not an error or empty slice
        assertThat(view.lines()).containsExactly("c", "d");
    }

    @Test
    void aZeroOrNegativePageRequestIsClampedToTheFirstPage() {
        InfoPage page = InfoPage.of("info", List.of("a", "b", "c"), 2);

        assertThat(ShowInfoPage.view(page, 0).page()).isEqualTo(1);
        assertThat(ShowInfoPage.view(page, -5).lines()).containsExactly("a", "b");
    }

    @Test
    void anEmptyBodyIsASinglePageWithNoLines() {
        InfoPage page = InfoPage.of("empty", List.of(), 4);

        InfoPageView view = ShowInfoPage.view(page, 1);

        assertThat(view.lines()).isEmpty();
        assertThat(view.pageCount()).isEqualTo(1);
        assertThat(view.isPaginated()).isFalse();
    }
}

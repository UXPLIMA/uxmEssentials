package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pure coverage of the {@code menu_*} placeholder family, exercising {@link PlaceholderResolver} against a fake
 * {@link MenuPlaceholders}, no PlaceholderAPI, no MockBukkit. It pins the six keys the menu-engine source exposes
 * and the absent-seam degrade contract the drift guard depends on.
 */
class MenuSourcePlaceholderResolverTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Viewer");

    @Test
    void isInMenuReadsYesOrNo() {
        assertThat(resolve(inMenu("shop", 1, 3, Map.of()), "menu_is_in_menu")).contains("yes");
        assertThat(resolve(notInMenu(), "menu_is_in_menu")).contains("no");
    }

    @Test
    void openedReadsTheCurrentSpecIdOrDash() {
        assertThat(resolve(inMenu("shop", 1, 3, Map.of()), "menu_opened")).contains("shop");
        assertThat(resolve(notInMenu(), "menu_opened")).contains("-");
    }

    @Test
    void lastReadsTheHistoryIdOrDash() {
        assertThat(resolve(withLast("shop"), "menu_last")).contains("shop");
        assertThat(resolve(notInMenu(), "menu_last")).contains("-");
    }

    @Test
    void pageAndRowsReadTheNumberOrDash() {
        MenuPlaceholders open = inMenu("shop", 2, 6, Map.of());
        assertThat(resolve(open, "menu_page")).contains("2");
        assertThat(resolve(open, "menu_rows")).contains("6");
        assertThat(resolve(notInMenu(), "menu_page")).contains("-");
        assertThat(resolve(notInMenu(), "menu_rows")).contains("-");
    }

    @Test
    void argumentReadsTheNamedValueOrDash() {
        MenuPlaceholders open = inMenu("shop", 1, 3, Map.of("target", "Steve"));
        assertThat(resolve(open, "menu_argument_target")).contains("Steve");
        assertThat(resolve(open, "menu_argument_missing")).contains("-");
        assertThat(resolve(notInMenu(), "menu_argument_target")).contains("-");
    }

    @Test
    void absentSeamStillResolvesEveryMenuKeyToAPresentValue() {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().build());
        assertThat(resolver.resolve(WHO, true, "menu_is_in_menu")).contains("no");
        for (String key : new String[] {"menu_opened", "menu_last", "menu_page", "menu_rows", "menu_argument_target"}) {
            assertThat(resolver.resolve(WHO, true, key))
                    .as("absent seam degrades %s to a present dash, never Optional.empty", key)
                    .contains("-");
        }
    }

    private static Optional<String> resolve(MenuPlaceholders seam, String key) {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().menu(seam).build());
        return resolver.resolve(WHO, true, key);
    }

    private static MenuPlaceholders inMenu(String specId, int page, int rows, Map<String, String> arguments) {
        return new FakeMenu(Optional.of(specId), page, rows, arguments, Optional.of(specId));
    }

    private static MenuPlaceholders notInMenu() {
        return new FakeMenu(Optional.empty(), 0, 0, Map.of(), Optional.empty());
    }

    private static MenuPlaceholders withLast(String lastId) {
        return new FakeMenu(Optional.empty(), 0, 0, Map.of(), Optional.of(lastId));
    }

    /** A fake open-menu snapshot: an {@code opened} id present means the player is in a menu; {@code last} is separate. */
    private record FakeMenu(
            Optional<String> opened, int page, int rows, Map<String, String> arguments, Optional<String> last)
            implements MenuPlaceholders {

        @Override
        public boolean inMenu(UUID player) {
            return opened.isPresent();
        }

        @Override
        public Optional<String> openedMenu(UUID player) {
            return opened;
        }

        @Override
        public Optional<String> lastMenu(UUID player) {
            return last;
        }

        @Override
        public OptionalInt page(UUID player) {
            return opened.isPresent() ? OptionalInt.of(page) : OptionalInt.empty();
        }

        @Override
        public OptionalInt rows(UUID player) {
            return opened.isPresent() ? OptionalInt.of(rows) : OptionalInt.empty();
        }

        @Override
        public Optional<String> argument(UUID player, String name) {
            return opened.isPresent() ? Optional.ofNullable(arguments.get(name)) : Optional.empty();
        }
    }
}

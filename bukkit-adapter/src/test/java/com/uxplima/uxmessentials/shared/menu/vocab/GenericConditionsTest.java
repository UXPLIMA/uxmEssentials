package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of the generic menu conditions and placeholders. The {@code perm} condition reads the
 * permission node from the condition ref's arguments, the same arg-carrier actions use, so a spec writes
 * {@code perm:some.node} and the engine threads {@code some.node} through to the {@link Permissions} port. The
 * {@code on-page} condition reads the open context's one-based page against a range spec, and the {@code player} and
 * {@code page} placeholders expand against the per-open {@link MenuContext}.
 */
class GenericConditionsTest {

    @Test
    void permConditionChecksTheViewerNode() {
        MenuBindings bindings = new MenuBindings();
        MenuVocabulary.registerConditions(bindings, new GrantOnly("x.allow"), new NoopLogger());
        BiPredicate<MenuContext, Map<String, String>> perm =
                bindings.condition("perm").orElseThrow(() -> new AssertionError("perm condition not registered"));
        MenuContext ctx = MenuContext.of(viewer("Allowed"), null, 0);

        assertThat(perm.test(ctx, Map.of("value", "x.allow"))).isTrue();
        assertThat(perm.test(ctx, Map.of("value", "x.deny"))).isFalse();
    }

    @Test
    void onPageConditionMatchesOneBasedPagesFromTheContext() {
        MenuBindings bindings = new MenuBindings();
        MenuVocabulary.registerConditions(bindings, new GrantOnly("x.allow"), new NoopLogger());
        BiPredicate<MenuContext, Map<String, String>> onPage =
                bindings.condition("on-page").orElseThrow(() -> new AssertionError("on-page condition not registered"));

        // page() is zero-based; the condition reads it one-based to match %page%, so index 1 is page 2.
        assertThat(onPage.test(MenuContext.of(viewer("V"), null, 1), Map.of("value", "2")))
                .isTrue();
        assertThat(onPage.test(MenuContext.of(viewer("V"), null, 0), Map.of("value", "2")))
                .isFalse();
        // A blank spec fails closed on every page.
        assertThat(onPage.test(MenuContext.of(viewer("V"), null, 1), Map.of())).isFalse();
    }

    @Test
    void pageInRangesParsesSinglesRangesAndFailsClosedOnGarbage() {
        // Inclusive A-B ranges.
        assertThat(MenuVocabulary.pageInRanges(2, "1-3")).isTrue();
        assertThat(MenuVocabulary.pageInRanges(4, "1-3")).isFalse();
        // A comma-separated mix of a single and a range.
        assertThat(MenuVocabulary.pageInRanges(1, "1,3-5")).isTrue();
        assertThat(MenuVocabulary.pageInRanges(2, "1,3-5")).isFalse();
        assertThat(MenuVocabulary.pageInRanges(4, "1,3-5")).isTrue();
        // A bare single page matches only itself.
        assertThat(MenuVocabulary.pageInRanges(2, "2")).isTrue();
        assertThat(MenuVocabulary.pageInRanges(3, "2")).isFalse();
        // Whitespace around the numbers and the dash is tolerated.
        assertThat(MenuVocabulary.pageInRanges(2, " 1 - 3 ")).isTrue();
        // Blank and malformed specs match nothing: fail closed.
        assertThat(MenuVocabulary.pageInRanges(2, "")).isFalse();
        assertThat(MenuVocabulary.pageInRanges(2, "x")).isFalse();
        assertThat(MenuVocabulary.pageInRanges(3, "3-")).isFalse();
    }

    @Test
    void playerAndPagePlaceholdersResolve() {
        MenuBindings bindings = new MenuBindings();
        MenuVocabulary.registerPlaceholders(bindings);
        Function<MenuContext, String> player =
                bindings.placeholder("player").orElseThrow(() -> new AssertionError("player not registered"));
        Function<MenuContext, String> page =
                bindings.placeholder("page").orElseThrow(() -> new AssertionError("page not registered"));
        MenuContext ctx = MenuContext.of(viewer("Steve"), null, 2);

        assertThat(player.apply(ctx)).isEqualTo("Steve");
        assertThat(page.apply(ctx)).isEqualTo("3");
    }

    private static PlayerRef viewer(String name) {
        return new PlayerRef(UUID.randomUUID(), name);
    }

    /** A {@link Logger} that discards every line; this test asserts behaviour, not log output. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A {@link Permissions} fake that grants exactly one node and rejects every other; only {@link #has} is used. */
    private static final class GrantOnly implements Permissions {
        private final Set<String> granted;

        GrantOnly(String node) {
            this.granted = Set.of(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}

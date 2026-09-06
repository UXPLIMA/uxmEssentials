package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.custommenus.adapter.CustomPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the global custom-placeholder fallback in isolation over a real {@link MenuBindings}: a definition that
 * references a built-in resolves it, a definition that references another custom resolves it, a cycle terminates
 * without overflowing the stack, an inner {@code {math: …}} block is left verbatim for the renderer's outer pass, an
 * unknown inner token blanks the same way the renderer's own substitution does, a reload swaps which names are claimed,
 * and {@code has} reports a defined name (so validation accepts a {@code %name%} spec) but not an undefined one.
 */
class CustomPlaceholdersTest {

    private MenuBindings bindings;
    private CustomPlaceholders placeholders;

    @BeforeEach
    void setUp() {
        bindings = new MenuBindings();
        // A built-in exact handler a custom template can reference; kept deterministic rather than reaching for a live
        // viewer so the test stays pure JUnit.
        bindings.placeholder("player", ctx -> ctx.viewer().name());
        placeholders = new CustomPlaceholders(bindings);
    }

    @Test
    void aTemplateResolvesAnInnerBuiltinPlaceholder() {
        placeholders.reload(Map.of("welcome", "Hi %player%"));

        assertThat(resolve("welcome")).contains("Hi Steve");
    }

    @Test
    void aCustomTemplateResolvesAnotherCustomTemplate() {
        placeholders.reload(Map.of("a", "%b%", "b", "X"));

        assertThat(resolve("a")).contains("X");
    }

    @Test
    void aCycleTerminatesBoundedInsteadOfOverflowingTheStack() {
        placeholders.reload(Map.of("a", "%b%", "b", "%a%"));

        assertThatCode(() -> resolve("a")).doesNotThrowAnyException();
        assertThat(resolve("a"))
                .as("a cycle resolves to some bounded value, never a StackOverflow")
                .isPresent();
    }

    @Test
    void anInnerMathBlockIsLeftLiteralForTheOuterRenderPass() {
        bindings.placeholder("coins", ctx -> "5");
        placeholders.reload(Map.of("doubled", "{math: %coins% * 2}"));

        // substitute resolves the inner %coins% but must NOT evaluate the math: the ItemRenderer's own pass owns that.
        assertThat(resolve("doubled")).contains("{math: 5 * 2}");
        assertThat(resolve("doubled").orElse("")).doesNotContain("10");
    }

    @Test
    void anUnknownInnerTokenBlanksLikeTheRenderer() {
        placeholders.reload(Map.of("greeting", "a%unknown%b"));

        assertThat(resolve("greeting")).contains("ab");
    }

    @Test
    void reloadSwapsWhichNamesAreClaimed() {
        placeholders.reload(Map.of("old", "1"));
        assertThat(bindings.placeholders().has("old")).isTrue();
        assertThat(bindings.placeholders().has("fresh")).isFalse();

        placeholders.reload(Map.of("fresh", "2"));
        assertThat(bindings.placeholders().has("old"))
                .as("a dropped name stops claiming")
                .isFalse();
        assertThat(bindings.placeholders().has("fresh")).isTrue();
        assertThat(resolve("fresh")).contains("2");
    }

    @Test
    void hasAcceptsADefinedNameAndRejectsAnUndefinedOne() {
        placeholders.reload(Map.of("welcome", "Hi %player%"));

        assertThat(bindings.placeholders().has("welcome"))
                .as("a defined name is claimed so MenuBindings.validate accepts a %welcome% spec")
                .isTrue();
        assertThat(bindings.placeholders().has("undefined")).isFalse();
    }

    private Optional<String> resolve(String id) {
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "Steve"), null, 0);
        return bindings.placeholders().resolve(id, ctx);
    }
}

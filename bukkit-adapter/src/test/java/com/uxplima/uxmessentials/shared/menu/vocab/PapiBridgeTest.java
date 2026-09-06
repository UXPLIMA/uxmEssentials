package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PapiPlaceholders;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the PlaceholderAPI bridge into the menu engine: a fallback resolver lets an unregistered {@code %papi_*%}
 * token still expand, {@code has} accepts a fallback-claimed id so {@code validate} passes such a spec, and the
 * {@code papi-compare} condition compares two (placeholder-resolved) operands. A live PlaceholderAPI is never
 * required. The bridge's resolve seam is exercised through a directly-registered test fallback, and the
 * absent-PlaceholderAPI path is asserted to return empty rather than throw.
 */
class PapiBridgeTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void papiTokenResolvesViaFallbackWhenClaimed() {
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "42");
        ItemRenderer renderer = new ItemRenderer(new GuiText(new KeyMessages()), placeholders);
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

        ItemStack rendered = renderer.render(itemNamed("%papi_x%"), ctx);

        assertThat(plainName(rendered)).isEqualTo("42");
    }

    @Test
    void unknownTokenStillEmpty() {
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        ItemRenderer renderer = new ItemRenderer(new GuiText(new KeyMessages()), placeholders);
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

        ItemStack rendered = renderer.render(itemNamed("%nope%"), ctx);

        assertThat(plainName(rendered)).isEmpty();
    }

    @Test
    void hasReturnsTrueForFallbackClaimedId() {
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        assertThat(placeholders.has("papi_x")).isFalse();

        placeholders.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "");

        assertThat(placeholders.has("papi_x")).isTrue();
        assertThat(placeholders.has("nope")).isFalse();
    }

    @Test
    void papiCompareNumeric() {
        MenuBindings bindings = new MenuBindings();
        MenuVocabulary.registerConditions(bindings, new DenyAll(), new NoopLogger());
        BiPredicate<MenuContext, Map<String, String>> compare = bindings.condition("papi-compare")
                .orElseThrow(() -> new AssertionError("papi-compare condition not registered"));
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

        assertThat(compare.test(ctx, Map.of("left", "10", "op", ">=", "right", "5")))
                .isTrue();
        assertThat(compare.test(ctx, Map.of("left", "3", "op", ">=", "right", "5")))
                .isFalse();
    }

    @Test
    void exprConditionEvaluatesSubstitutedExpression() {
        MenuBindings bindings = new MenuBindings();
        bindings.placeholder("balance", ctx -> "1500");
        bindings.placeholder("world", ctx -> "world_nether");
        MenuVocabulary.registerConditions(bindings, new DenyAll(), new NoopLogger());
        BiPredicate<MenuContext, Map<String, String>> expr =
                bindings.condition("expr").orElseThrow(() -> new AssertionError("expr condition not registered"));
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

        assertThat(expr.test(ctx, Map.of("value", "%balance% >= 1000"))).isTrue();
        assertThat(expr.test(ctx, Map.of("value", "%balance% < 1000"))).isFalse();
        assertThat(expr.test(ctx, Map.of("value", "'%world%' == 'world_nether'")))
                .isTrue();
        assertThat(expr.test(ctx, Map.of("value", "(2 + 3) * 2 == 10"))).isTrue();
    }

    @Test
    void exprConditionFailsClosedOnMalformedInput() {
        MenuBindings bindings = new MenuBindings();
        MenuVocabulary.registerConditions(bindings, new DenyAll(), new NoopLogger());
        BiPredicate<MenuContext, Map<String, String>> expr =
                bindings.condition("expr").orElseThrow(() -> new AssertionError("expr condition not registered"));
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

        assertThat(expr.test(ctx, Map.of("value", "this is not ("))).isFalse();
        assertThat(expr.test(ctx, Map.of("value", "1 / 0 > 0"))).isFalse();
    }

    @Test
    void papiFallbackReturnsEmptyWithoutLivePlaceholderApi() {
        // PapiPlaceholders wires the bridge; with no PlaceholderAPI plugin on the (MockBukkit) server, resolving a
        // claimed token must yield empty rather than throw: the soft-depend never hard-fails.
        MenuBindings bindings = new MenuBindings();
        PapiPlaceholders.registerInto(bindings);
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

        assertThat(bindings.placeholders().has("papi_player_name")).isTrue();
        assertThat(bindings.placeholders().resolve("papi_player_name", ctx)).contains("");
    }

    private static MenuItemSpec itemNamed(String name) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                name,
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static String plainName(ItemStack item) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    /** A {@link Permissions} fake that grants nothing; {@code papi-compare} does not consult it, so only its shape matters. */
    private static final class DenyAll implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** A {@link Logger} that swallows every line; the {@code expr} tests assert behaviour, not log output. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String text = key.key();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return text;
        }
    }
}

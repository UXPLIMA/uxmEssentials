package com.uxplima.uxmessentials.shared.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the registry's fallback contract now that it carries more than one prefix/family resolver. Two disjoint
 * fallbacks (the PlaceholderAPI bridge and the player-data readers) coexist and each route their own family; when two
 * overlap on an id the first registered wins; {@code has} reports an id either fallback claims; and an exact handler
 * still beats a fallback that would also claim the id. The resolvers themselves are trivial stand-ins: this exercises
 * the registry's dispatch, not any live placeholder source.
 */
class PlaceholderRegistryTest {

    private static final MenuContext CTX = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

    @Test
    void twoDisjointFallbacksEachRouteToTheirOwnFamily() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "papi:" + id);
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "data:" + id);

        assertThat(registry.resolve("papi_x", CTX)).contains("papi:papi_x");
        assertThat(registry.resolve("data_y", CTX)).contains("data:data_y");
        assertThat(registry.resolve("neither", CTX)).isEmpty();
    }

    @Test
    void theFirstRegisteredFallbackWinsWhenTwoClaimTheSameId() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback(id -> id.startsWith("shared_"), (id, ctx) -> "first");
        registry.fallback(id -> id.startsWith("shared_"), (id, ctx) -> "second");

        assertThat(registry.resolve("shared_key", CTX)).contains("first");
    }

    @Test
    void hasIsTrueForAnIdEitherFallbackClaimsAndFalseOtherwise() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "");
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "");

        assertThat(registry.has("papi_a")).isTrue();
        assertThat(registry.has("data_b")).isTrue();
        assertThat(registry.has("nope")).isFalse();
    }

    @Test
    void resolveStillThrowsSoACallerThatWouldRatherKnowCan() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("broken", ctx -> {
            throw new IllegalStateException("no");
        });

        Assertions.assertThatThrownBy(() -> registry.resolve("broken", CTX)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolveOrReportYieldsNothingAndNamesTheTokenOnce() {
        // A handler registered through the developer API belongs to somebody else's plugin. When it throws, the
        // render that asked for it has to carry on, and its author has to be told which token it was.
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("broken", ctx -> {
            throw new IllegalStateException("no");
        });
        List<LogRecord> logged = new ArrayList<>();
        Logger logger = Logger.getLogger(PlaceholderRegistry.class.getName());
        Handler collector = collector(logged);
        boolean parents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(collector);
        try {
            assertThat(registry.resolveOrReport("broken", CTX)).isEmpty();
            assertThat(registry.resolveOrReport("broken", CTX)).isEmpty();
            assertThat(registry.resolveOrReport("broken", CTX)).isEmpty();
        } finally {
            logger.removeHandler(collector);
            logger.setUseParentHandlers(parents);
        }

        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getMessage()).contains("broken");
    }

    @Test
    void resolveOrReportIsOtherwiseResolve() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("fine", ctx -> "value");
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "papi:" + id);

        assertThat(registry.resolveOrReport("fine", CTX)).contains("value");
        assertThat(registry.resolveOrReport("papi_x", CTX)).contains("papi:papi_x");
        assertThat(registry.resolveOrReport("unknown", CTX)).isEmpty();
    }

    /** Collects the registry's warnings into {@code records}, so "named once" can be asserted rather than described. */
    private static Handler collector(List<LogRecord> records) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
    }

    @Test
    void anExactHandlerBeatsAFallbackThatWouldAlsoClaimTheId() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("data_exact", ctx -> "handler");
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "fallback");

        assertThat(registry.resolve("data_exact", CTX)).contains("handler");
        assertThat(registry.resolve("data_other", CTX)).contains("fallback");
    }
}

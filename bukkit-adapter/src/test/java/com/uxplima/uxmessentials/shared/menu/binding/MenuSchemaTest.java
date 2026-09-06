package com.uxplima.uxmessentials.shared.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuSchema;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of {@link MenuBindings#schema()}, the Layer-1 export the in-game action/requirement pickers
 * render from. The schema lists the ids each registry actually holds, sorted, so a picker offers exactly the
 * bindings that are wired: no invented per-id arg schema, just the id catalog a viewer chooses from.
 */
class MenuSchemaTest {

    @Test
    void schemaListsTheRegisteredActionAndConditionIds() {
        MenuBindings bindings = new MenuBindings();
        bindings.action("message", ctx -> {});
        bindings.action("open", ctx -> {});
        bindings.condition("perm", (ctx, args) -> true);
        bindings.condition("has-money", (ctx, args) -> true);
        bindings.placeholder("player", ctx -> "");
        bindings.list("warps", ctx -> List.of());

        MenuSchema schema = bindings.schema();

        assertThat(schema.actionIds()).contains("message", "open");
        assertThat(schema.conditionIds()).contains("perm", "has-money");
        assertThat(schema.placeholderIds()).contains("player");
        assertThat(schema.listSourceIds()).contains("warps");
    }

    @Test
    void theIdCatalogsComeBackSorted() {
        MenuBindings bindings = new MenuBindings();
        bindings.action("open", ctx -> {});
        bindings.action("close", ctx -> {});
        bindings.action("message", ctx -> {});

        assertThat(bindings.schema().actionIds()).containsExactly("close", "message", "open");
    }
}

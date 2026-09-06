package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.List;
import java.util.Objects;

/**
 * The Layer-1 export the in-game menu editor's action / condition / placeholder / list-source pickers render from: the
 * sorted id catalog each of the four {@link MenuBindings} registries actually holds. It is the single source a picker
 * reads, so an author is offered exactly the bindings that are wired: nothing the engine cannot resolve.
 *
 * <p>The registries hold only an id → handler map, with no per-id argument schema, so this export carries no invented
 * arg descriptions: every ref an editor builds is a bare id or an {@code id:value} token, the value entered as free
 * text. The placeholder catalog lists only the exactly-registered tokens; the open-ended prefix families
 * ({@code papi_*}, {@code data_*}) claim ids by predicate, so they cannot be enumerated and are left to a typed token.
 *
 * @param actionIds every registered action id, sorted
 * @param conditionIds every registered condition id, sorted
 * @param placeholderIds every exactly-registered placeholder id, sorted
 * @param listSourceIds every registered list-source id, sorted
 */
public record MenuSchema(
        List<String> actionIds, List<String> conditionIds, List<String> placeholderIds, List<String> listSourceIds) {

    public MenuSchema {
        actionIds = List.copyOf(Objects.requireNonNull(actionIds, "actionIds"));
        conditionIds = List.copyOf(Objects.requireNonNull(conditionIds, "conditionIds"));
        placeholderIds = List.copyOf(Objects.requireNonNull(placeholderIds, "placeholderIds"));
        listSourceIds = List.copyOf(Objects.requireNonNull(listSourceIds, "listSourceIds"));
    }
}

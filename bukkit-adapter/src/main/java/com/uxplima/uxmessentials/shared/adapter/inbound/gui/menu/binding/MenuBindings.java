package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListControlSyntax;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;

/**
 * The single entry point a feature uses to give a menu spec its behaviour. Features call {@code action} /
 * {@code condition} / {@code placeholder} / {@code list} / {@code pagedList} once at wiring time to register a
 * handler under an id;
 * the engine later resolves a spec's refs back to those handlers through the matching getter. {@link #validate}
 * turns a spec that names an unregistered id into a loud startup failure instead of a broken menu a player meets.
 */
public final class MenuBindings {

    /** Matches a {@code %token%} placeholder; the captured group is the bare id a feature registers. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");

    private final ActionRegistry actions = new ActionRegistry();

    private final ConditionRegistry conditions = new ConditionRegistry();

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private final ListSourceRegistry lists = new ListSourceRegistry();

    private final PagedListSourceRegistry pagedLists = new PagedListSourceRegistry();

    private final ContentProviderRegistry contents = new ContentProviderRegistry();

    public void action(String id, Consumer<MenuActionContext> handler) {
        actions.register(id, handler);
    }

    public void condition(String id, BiPredicate<MenuContext, Map<String, String>> handler) {
        conditions.register(id, handler);
    }

    public void placeholder(String id, Function<MenuContext, String> handler) {
        placeholders.register(id, handler);
    }

    public void list(String id, Function<MenuContext, List<?>> handler) {
        // An id is an in-memory source or a paged one, never both: a spec pointing at it could not otherwise say
        // which contract the engine should ask for.
        if (pagedLists.has(id)) {
            throw new IllegalStateException(
                    "cannot register list source '" + id + "': already registered as a paged list source");
        }
        lists.register(id, handler);
    }

    /**
     * Register the streaming counterpart of {@link #list}. An id may back an in-memory source or a paged one but
     * never both. They answer different questions (the whole corpus versus one page), so an id claimed by both
     * would leave a spec's reference ambiguous.
     */
    public void pagedList(String id, BiFunction<MenuContext, PageRequest, PagedResult<?>> handler) {
        if (lists.has(id)) {
            throw new IllegalStateException(
                    "cannot register paged list source '" + id + "': already registered as a list source");
        }
        pagedLists.register(id, handler);
    }

    /**
     * Register the provider behind a menu's {@code content {}} region: what fills those slots, which movements the
     * viewer may make in them, and what happens to what is left when the window closes.
     */
    public void content(String id, ContentProvider provider) {
        contents.register(id, provider);
    }

    public Optional<Consumer<MenuActionContext>> action(String id) {
        return actions.get(id);
    }

    public Optional<BiPredicate<MenuContext, Map<String, String>>> condition(String id) {
        return conditions.get(id);
    }

    public Optional<Function<MenuContext, String>> placeholder(String id) {
        return placeholders.get(id);
    }

    public Optional<Function<MenuContext, List<?>>> list(String id) {
        return lists.get(id);
    }

    public Optional<BiFunction<MenuContext, PageRequest, PagedResult<?>>> pagedList(String id) {
        return pagedLists.get(id);
    }

    /**
     * The four backing registries, handed to the renderer and click listener so they resolve refs against the
     * very instances this façade writes to. A feature registering a handler after wiring is therefore visible to
     * an already-built engine: there is one registry per kind, not a copy per consumer.
     */
    public ActionRegistry actions() {
        return actions;
    }

    public ConditionRegistry conditions() {
        return conditions;
    }

    public PlaceholderRegistry placeholders() {
        return placeholders;
    }

    public ListSourceRegistry lists() {
        return lists;
    }

    /**
     * The paged-source registry, the streaming counterpart to {@link #lists()}. This accessor is the only path past
     * the façade to it, so a source that pages its corpus down to the store stays exactly as reachable, and as
     * engine-internal, as an in-memory one.
     */
    public PagedListSourceRegistry pagedLists() {
        return pagedLists;
    }

    /**
     * The content-provider registry, handed to the renderer and click listener so a region resolves to the very
     * provider this façade registered. A region whose id is absent here stays empty and refuses every click.
     */
    public ContentProviderRegistry contents() {
        return contents;
    }

    /**
     * The id catalog the in-game editor's pickers render from. The sorted action / condition / placeholder /
     * list-source ids these four registries currently hold. A picker reads this one export, so it offers exactly the
     * bindings that are wired. Built on demand from the live registries, so a feature that registered late is included.
     */
    public MenuSchema schema() {
        return new MenuSchema(actions.ids(), conditions.ids(), placeholders.ids(), lists.ids());
    }

    /**
     * Every ref id a spec names that no registry knows, de-duplicated. An empty list means the specs are fully
     * wired; anything returned is a missing binding the operator must fix before the menu can open cleanly.
     */
    public List<String> validate(Collection<MenuSpec> specs) {
        Objects.requireNonNull(specs, "specs");
        Set<String> missing = new LinkedHashSet<>();
        for (MenuSpec spec : specs) {
            collectMissing(spec, missing);
        }
        return List.copyOf(missing);
    }

    private void collectMissing(MenuSpec spec, Set<String> missing) {
        addMissing(spec.openRequirement(), conditions::has, missing);
        addMissing(spec.openActions(), actions::has, missing);
        addMissing(spec.closeActions(), actions::has, missing);
        // A token this menu defines in its own placeholders {} block counts as known for this menu: it resolves
        // local-first at render, so validation accepts it just as it accepts a registered built-in.
        Predicate<String> placeholderKnown =
                id -> placeholders.has(id) || spec.placeholders().containsKey(id);
        for (String id : extractPlaceholders(spec.title())) {
            if (!placeholderKnown.test(id)) {
                missing.add(id);
            }
        }
        for (Map.Entry<String, MenuItemSpec> entry : spec.items().entrySet()) {
            collectItemMissing(spec, entry.getKey(), entry.getValue(), placeholderKnown, missing);
        }
        collectListControlMissing(spec, missing);
        for (ContentRegionSpec region : spec.contents().values()) {
            if (!contents.has(region.id())) {
                missing.add(
                        "menu '" + spec.title() + "' content region '" + region.id() + "' has no registered provider");
            }
        }
    }

    /**
     * Validate every {@code list-sort} action this menu wires. A sort button carries the source id of the paged list it
     * drives ({@code list-sort:pw:browse}); the engine can see the linkage between that id and the list items the same
     * menu declares, so a button that names a list this menu does not contain, or one whose source declares no
     * {@code sorts} to cycle, is a loud startup error rather than a click that silently does nothing, an operator who
     * wires a sort button expects it to sort. {@code list-filter} and {@code list-search} carry no such static
     * precondition (any key is valid), so an unknown list id there is caught at click time as a logged no-op instead.
     * The scan mirrors the action-id validation above: the menu's open/close actions and every item's click actions,
     * each resolved against the action registry so {@code list-sort} only triggers this once it is a registered action.
     */
    private void collectListControlMissing(MenuSpec spec, Set<String> missing) {
        Map<String, ListSpec> paged = new HashMap<>();
        for (MenuItemSpec item : spec.items().values()) {
            item.list().ifPresent(list -> paged.put(list.source().id(), list));
        }
        List<Ref> actionRefs = new ArrayList<>(spec.openActions());
        actionRefs.addAll(spec.closeActions());
        for (MenuItemSpec item : spec.items().values()) {
            item.click().actions().values().forEach(actionRefs::addAll);
        }
        for (Ref ref : actionRefs) {
            checkSortRef(spec, ref, paged, missing);
        }
    }

    /** Report a single {@code list-sort} ref that targets a list this menu lacks or a source that offers no sorts. */
    private void checkSortRef(MenuSpec spec, Ref ref, Map<String, ListSpec> paged, Set<String> missing) {
        Ref resolved = ref.resolve(actions::has);
        if (!resolved.id().equals(ListControlSyntax.SORT_ACTION)) {
            return;
        }
        ListControlSyntax.parseSort(resolved.value()).ifPresent(sort -> {
            String where = "menu '" + spec.title() + "' action 'list-sort:" + sort.listId() + "'";
            ListSpec target = paged.get(sort.listId());
            if (target == null) {
                missing.add(where + " names a paged list this menu does not contain");
            } else if (target.sorts().isEmpty()) {
                missing.add(where + " but its list source declares no sorts, so the button cannot sort");
            }
        });
    }

    private void collectItemMissing(
            MenuSpec spec, String itemId, MenuItemSpec item, Predicate<String> placeholderKnown, Set<String> missing) {
        collectViewMissing(item.view(), conditions::has, missing);
        item.click().conditions().values().forEach(refs -> addMissing(refs, conditions::has, missing));
        item.click().actions().values().forEach(refs -> addMissing(refs, actions::has, missing));
        collectTextPlaceholders(item, placeholderKnown, missing);
        item.list().ifPresent(list -> {
            collectListSourceMissing(spec, itemId, list, missing);
            collectItemMissing(spec, itemId + " (list template)", list.template(), placeholderKnown, missing);
        });
    }

    /**
     * Validate a list-backed item's source. It must be registered as either an in-memory list or a paged one, or its
     * id is reported unresolved, exactly as an unknown source has always been reported, so the loader skips just
     * that one menu rather than the server failing to boot, the same tolerance an unresolved action or placeholder
     * gets. Beyond that, {@code page-size} and {@code sorts} are paged-only knobs: a plain in-memory source hands its
     * whole corpus over for the engine to slice and can act on neither, so silently dropping them would let an
     * operator believe their browse menu pages or sorts when it does not. Both cases are reported naming the menu,
     * item and source so the offending line is easy to find.
     */
    private void collectListSourceMissing(MenuSpec spec, String itemId, ListSpec list, Set<String> missing) {
        String sourceId = list.source().id();
        boolean plain = lists.has(sourceId);
        boolean paged = pagedLists.has(sourceId);
        String where = "menu '" + spec.title() + "' item '" + itemId + "' list source '" + sourceId + "'";
        if (!plain && !paged) {
            missing.add(where + " is registered as neither a list nor a paged list source");
            return;
        }
        if (plain && (list.pageSize() != 0 || !list.sorts().isEmpty())) {
            missing.add(where + " is a plain list source, so its page-size and sorts do nothing; "
                    + "register it as a paged source or drop those keys");
        }
    }

    private void collectTextPlaceholders(MenuItemSpec item, Predicate<String> placeholderKnown, Set<String> missing) {
        List<String> texts = new ArrayList<>(item.lore());
        texts.add(item.material());
        texts.add(item.name());
        for (String text : texts) {
            for (String id : extractPlaceholders(text)) {
                if (!placeholderKnown.test(id)) {
                    missing.add(id);
                }
            }
        }
    }

    /**
     * Collect the refs in {@code refs} whose id no registry knows. A ref is first resolved against {@code known}
     * (the matching registry's {@code has}), so a valued token written {@code has-money:100} counts as known when its
     * head {@code has-money} is registered: the same registry-aware split the runtime does at dispatch/render time.
     * The original written token is reported when it is still unknown, so an operator sees exactly what they typed.
     */
    private void addMissing(List<Ref> refs, Predicate<String> known, Set<String> missing) {
        for (Ref ref : refs) {
            if (!known.test(ref.resolve(known).id())) {
                missing.add(ref.id());
            }
        }
    }

    /**
     * Collect the conditions in a {@link RequirementSpec} view block whose id no registry knows. Each requirement's
     * condition ref is resolved against {@code known} first, so a valued condition written {@code has-money:100} counts
     * as known when its head {@code has-money} is registered. The same registry-aware split the runtime does when it
     * gates the item's visibility. The original written token is reported when it is still unknown, so an operator sees
     * exactly what they typed. A block's minimum and inversion are combinator metadata, not ids, so only the condition
     * ids are validated here.
     */
    private void collectViewMissing(RequirementSpec view, Predicate<String> known, Set<String> missing) {
        for (Requirement requirement : view.requirements()) {
            Ref condition = requirement.condition();
            if (!known.test(condition.resolve(known).id())) {
                missing.add(condition.id());
            }
        }
    }

    /** The bare ids of every {@code %token%} placeholder in {@code text}, in order of appearance. */
    private static List<String> extractPlaceholders(String text) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The per-open data a menu binding sees: who is viewing, the optional domain subject the menu was opened for
 * (a warp, a home owner, ...), the current page,, while a list is rendered or clicked, the live list element,
 * any typed command arguments the menu was opened with (an operator {@code command {}} block's
 * {@code %argument_<name>%} values), the menu's own local placeholder definitions, and who triggered the open.
 * The engine creates it; it is public only so feature binding lambdas can read it.
 *
 * <p>The {@code viewer} is who <em>sees</em> the menu. The player it is rendered for and the one every
 * player-scoped placeholder ({@code %player%}, {@code %stat_*%}, {@code %data_*%}, inventory, PAPI) resolves
 * against. The {@code executor} is who <em>triggered</em> the open. They are the same player for an ordinary
 * self-open, so {@code %executor%} then reads identically to {@code %player%}. They diverge only when a menu is
 * opened for another player, {@code /menu open <name> <target>}, where the viewer is the target and the executor
 * is the opener, so a menu the target sees can show "opened for you by {@code %executor%}" distinct from the target
 * named by {@code %player%}. The executor defaults to the viewer, so every existing open is unaffected.
 *
 * <p>The {@code localPlaceholders} map is the open spec's {@code placeholders {}} block, {@code name -> template}
 * pairs the renderer resolves local-first, so a menu can define or override a {@code %name%} token for itself alone.
 * It is empty for a menu that declares no such block, and for every engine child window (a list/confirm/selector/
 * editor), whose minimal spec carries none.
 *
 * <p>Immutable. {@link #withEntry}, {@link #withPage}, {@link #withPageCount}, {@link #withLocalPlaceholders},
 * {@link #withExecutor} and {@link #withPagedViews} return copies rather than mutating, because the same base context
 * is reused across every slot of a list page and must not leak one element's identity into the next; each copy carries
 * the open's arguments, local placeholders, executor and paged-list snapshots through unchanged.
 */
public final class MenuContext {

    private final PlayerRef viewer;

    @Nullable private final Object subject;

    private final int page;

    private final int pageCount;

    @Nullable private final Object entry;

    private final Map<String, String> arguments;

    private final Map<String, String> localPlaceholders;

    private final PlayerRef executor;

    /**
     * The paged lists this render draws, keyed by list-source id. Empty for every open that queried no paged source,
     * which is the historic case. A source id present here tells the renderer the list's rows are already one page (so
     * they are placed without re-slicing) and carries the page and corpus total the page indicator reads. Each value is
     * an immutable snapshot taken from the list's {@link ListQueryState} on the viewer's entity thread, so the renderer
     * never touches that mutable state itself.
     */
    private final Map<String, PagedListView> pagedViews;

    private MenuContext(
            PlayerRef viewer,
            @Nullable Object subject,
            int page,
            int pageCount,
            @Nullable Object entry,
            Map<String, String> arguments,
            Map<String, String> localPlaceholders,
            PlayerRef executor,
            Map<String, PagedListView> pagedViews) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.subject = subject;
        this.page = page;
        this.pageCount = pageCount;
        this.entry = entry;
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.localPlaceholders = Objects.requireNonNull(localPlaceholders, "localPlaceholders");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.pagedViews = Objects.requireNonNull(pagedViews, "pagedViews");
    }

    /** Opens a fresh context with no list element bound yet and a single-page count until the renderer knows better. */
    public static MenuContext of(PlayerRef viewer, @Nullable Object subject, int page) {
        return of(viewer, subject, page, Map.of());
    }

    /**
     * Opens a fresh context carrying the typed command {@code arguments} the menu was opened with, keyed by
     * argument name so the renderer can expand {@code %argument_<name>%} in a title, name or lore. Kept separate
     * from {@link #of(PlayerRef, Object, int)} so the many argument-less opens stay a three-argument call. The local
     * placeholders start empty here; the engine attaches the open spec's block via {@link #withLocalPlaceholders}.
     */
    public static MenuContext of(PlayerRef viewer, @Nullable Object subject, int page, Map<String, String> arguments) {
        return new MenuContext(viewer, subject, page, 1, null, Map.copyOf(arguments), Map.of(), viewer, Map.of());
    }

    public PlayerRef viewer() {
        return viewer;
    }

    /**
     * Who triggered this open, the same player as {@link #viewer()} for an ordinary self-open, and the opener when a
     * menu was opened for another player. The renderer reads it to expand {@code %executor%}; unlike {@code %player%}
     * (the viewer, whom every player-scoped placeholder resolves against) this is never re-pointed at the subject.
     */
    public PlayerRef executor() {
        return executor;
    }

    public int page() {
        return page;
    }

    /** How many pages the menu's list spans, one-based; the renderer stamps it before drawing static items. */
    public int pageCount() {
        return pageCount;
    }

    /**
     * The typed command arguments the menu was opened with, keyed by argument name. Empty for a menu not opened
     * through an argument-carrying command. The renderer reads it to expand {@code %argument_<name>%}; the map is
     * immutable.
     */
    public Map<String, String> arguments() {
        return arguments;
    }

    /**
     * The open spec's own {@code placeholders {}} block, keyed by custom token name. Empty for a menu that declares
     * none. The renderer reads it to resolve a {@code %name%} local-first, ahead of the shared placeholder registry,
     * so a menu-scoped token overrides a built-in or global custom for this menu alone; the map is immutable.
     */
    public Map<String, String> localPlaceholders() {
        return localPlaceholders;
    }

    /**
     * The paged lists this render draws, keyed by list-source id, as immutable snapshots, empty for an open that
     * queried no paged source. The renderer reads it to tell a paged list (already one page) from an in-memory one
     * (the whole corpus, sliced here) and to source the paged list's {@code %page%}/{@code %max_page%}.
     */
    public Map<String, PagedListView> pagedViews() {
        return pagedViews;
    }

    public Optional<Object> subjectRaw() {
        return Optional.ofNullable(subject);
    }

    public Optional<Object> entry() {
        return Optional.ofNullable(entry);
    }

    /**
     * The domain subject cast to {@code type}. A mismatch means a binding asked for the wrong context, so we
     * fail loudly rather than return null.
     */
    public <T> T subject(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = subjectRaw().orElseThrow(() -> new IllegalStateException("menu has no subject"));
        if (!type.isInstance(value)) {
            throw new IllegalStateException("menu subject is not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /** The live list element cast to {@code type}; same fail-loud contract as {@link #subject(Class)}. */
    public <T> T entry(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = entry().orElseThrow(() -> new IllegalStateException("menu has no entry"));
        if (!type.isInstance(value)) {
            throw new IllegalStateException("menu entry is not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /** A copy bound to one list element, leaving viewer, subject, page, page count, arguments, locals and executor untouched. */
    public MenuContext withEntry(Object entry) {
        Objects.requireNonNull(entry, "entry");
        return new MenuContext(
                viewer, subject, page, pageCount, entry, arguments, localPlaceholders, executor, pagedViews);
    }

    /** A copy on a new page, used when the renderer or listener advances pagination; resets nothing else. */
    public MenuContext withPage(int page) {
        return new MenuContext(
                viewer, subject, page, pageCount, entry, arguments, localPlaceholders, executor, pagedViews);
    }

    /** A copy carrying the page count the renderer computed, so a static item's {@code %max_page%} can read it. */
    public MenuContext withPageCount(int pageCount) {
        return new MenuContext(
                viewer, subject, page, pageCount, entry, arguments, localPlaceholders, executor, pagedViews);
    }

    /**
     * A copy carrying the paged-list snapshots this render draws, keyed by list-source id, attached by the engine on
     * the viewer's entity thread once each paged source has answered and its total is recorded on the list's state.
     * Every other field is untouched, and the map is copied defensively so the caller cannot mutate it afterwards.
     */
    public MenuContext withPagedViews(Map<String, PagedListView> pagedViews) {
        Objects.requireNonNull(pagedViews, "pagedViews");
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                executor,
                Map.copyOf(pagedViews));
    }

    /**
     * A copy carrying the open spec's local {@code placeholders {}} block, attached by the engine when a spec menu
     * opens so the renderer can resolve its custom tokens local-first. Every other field is untouched, and the map is
     * copied defensively so the caller cannot mutate it afterwards.
     */
    public MenuContext withLocalPlaceholders(Map<String, String> localPlaceholders) {
        Objects.requireNonNull(localPlaceholders, "localPlaceholders");
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                Map.copyOf(localPlaceholders),
                executor,
                pagedViews);
    }

    /**
     * A copy attributing the open to {@code executor}, who triggered it, while leaving the viewer and every other
     * field untouched. The engine attaches this when a menu is opened for another player so {@code %executor%} names
     * the opener while {@code %player%} still names the viewing target; an ordinary self-open never calls it and keeps
     * the executor defaulted to the viewer.
     */
    public MenuContext withExecutor(PlayerRef executor) {
        Objects.requireNonNull(executor, "executor");
        return new MenuContext(
                viewer, subject, page, pageCount, entry, arguments, localPlaceholders, executor, pagedViews);
    }
}

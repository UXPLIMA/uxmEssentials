package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuOpenEvent;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Pagination;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ConfirmRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.GridRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ListViewRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.SelectorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ActionArguments;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ConfirmState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.EditorRefresh;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.EditorState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.GridViewState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ListQueryState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ListViewState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuRefresh;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.PagedListRows;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.PagedListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.SelectorState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.BedrockFormSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ChildClickHandler;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ConfirmOpener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorOpener;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.MenuTitles;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockIcons;
import com.uxplima.uxmlib.bedrock.BedrockImage;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;

/**
 * The one entry point a feature uses to open a registered menu for a viewer. A feature registers its specs once at
 * wiring time, then calls {@link #open} to show one to a player. The façade first resolves every list source the
 * spec names off any tick thread. A list source may read a database, which must never block the viewer's region
 * thread on Folia, then hops onto the viewer's entity thread (where the live inventory may legally be touched),
 * builds the {@link MenuHolder} that owns every per-open piece of state, caches the resolved lists on it, renders
 * the spec into a fresh inventory the holder backs, and arms the refresh task. Pagination and refresh re-render
 * from the holder's cache, so a page flip never re-queries. The click listener recovers all of this from the
 * window alone, so no player-keyed side map is needed.
 */
public final class Menus {

    /** Operator diagnostics for a menu that names an inventory type the server rejects: logged, then a chest opens. */
    private static final Logger LOG = Logger.getLogger(Menus.class.getName());

    private final MenuRenderer renderer;
    private final Scheduler scheduler;
    private final ListSourceRegistry lists;

    /**
     * The streaming counterpart to {@link #lists}: the sources that answer for one page of a large corpus rather than
     * handing the whole of it over. Empty on every engine wired without one, a list/spec-only test fixture and any
     * production menu set with no paged source. In which case {@link #resolveLists} finds every source in {@link #lists}
     * and never consults this registry, so those opens stay byte-identical to before this seam existed.
     */
    private final PagedListSourceRegistry pagedLists;

    /**
     * The editor renderer, present only on an engine wired for typed property editors. A list/spec-only engine (most
     * test fixtures) leaves it null and never calls {@link #openEditor}; opening an editor on such an engine is a
     * wiring error that fails loudly rather than half-rendering.
     */
    @Nullable private final EditorRenderer editorRenderer;

    /**
     * The action registry an open runs a spec's {@code open-actions} through, and the condition registry it gates a
     * spec's {@code open-requirement} on. Both are null on an engine wired without them, every list/spec-only test
     * fixture. In which case an open neither gates nor fires open-actions, byte-identical to before this seam
     * existed. Only production wiring, which has the fully populated registries, passes them; a spec with no
     * open-requirement/open-actions is unaffected either way.
     */
    @Nullable private final ActionRegistry openActionRegistry;

    @Nullable private final ConditionRegistry openConditionRegistry;

    /**
     * The per-player reopen tracker {@code /menu last} reads. Null on every engine wired without it, every
     * list/spec-only test fixture. In which case an open records nothing, byte-identical to before this seam
     * existed. Only production wiring, which builds one tracker and shares it with the {@code /menu} command,
     * passes it; and even then only a subject-less open (a custom menu) is remembered, in {@link #rememberLastOpen}.
     */
    @Nullable private final LastMenu lastMenu;

    /**
     * Whether a viewer is a Bedrock (Floodgate) player, so an engine menu can be redirected to a native form for them
     * rather than a chest. Defaults to {@link BedrockDetector#NONE}, always Java, on every engine wired without it,
     * so an open there falls straight through to the chest path, byte-identical to before this seam existed. Only
     * production wiring, on a server with Floodgate, passes a detector that ever answers {@code true}.
     */
    private final BedrockDetector bedrock;

    /**
     * The screen that sends a Bedrock viewer a native Cumulus form. Defaults to {@link BedrockScreen#NONE}, a no-op
     * carrying no SDK reference. On every engine wired without it, and it is only ever reached after {@link #bedrock}
     * has confirmed a Bedrock viewer, so a Java-only engine never sends a form and never loads the Cumulus SDK.
     */
    private final BedrockScreen bedrockScreen;

    /** Paints the two-button confirm window; stateless, so one instance serves every confirm open. */
    private final ConfirmRenderer confirmRenderer = new ConfirmRenderer();

    /** Paints a selector child window; stateless, so one instance serves every picker open. */
    private final SelectorRenderer selectorRenderer = new SelectorRenderer();

    /** Paints a paginated entity list; stateless, so one instance serves every list open. */
    private final ListViewRenderer listViewRenderer = new ListViewRenderer();

    /** The opener a property's click hook calls to show its picker as an engine child window; wraps this façade. */
    private final SelectorOpener selectorOpener = this::openSelector;

    /** The opener a property's click hook calls to gate a removal behind an engine confirm child; wraps this façade. */
    private final ConfirmOpener confirmOpener = this::confirm;

    private final Map<String, MenuSpec> specs = new ConcurrentHashMap<>();

    public Menus(MenuRenderer renderer, Scheduler scheduler, ListSourceRegistry lists) {
        this(renderer, scheduler, lists, null);
    }

    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer) {
        this(renderer, scheduler, lists, editorRenderer, null, null);
    }

    /**
     * The action/condition-aware constructor: the same engine plus the registries an open needs to run a spec's
     * {@code open-actions} and gate on its {@code open-requirement}. Delegates to the canonical constructor with a
     * {@code null} reopen tracker, so every existing {@code new Menus(...)} call-site (almost all test fixtures)
     * compiles unchanged and records no reopen target: byte-identical to before that seam existed.
     */
    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer,
            @Nullable ActionRegistry openActionRegistry,
            @Nullable ConditionRegistry openConditionRegistry) {
        this(renderer, scheduler, lists, editorRenderer, openActionRegistry, openConditionRegistry, null);
    }

    /**
     * The reopen-tracker constructor, kept so every existing {@code new Menus(...)} call-site (almost all test
     * fixtures) compiles unchanged. It delegates to the canonical constructor with the Java-only Bedrock defaults
     * {@link BedrockDetector#NONE} and {@link BedrockScreen#NONE}, so an open there never redirects to a form and
     * stays byte-identical to before the Bedrock seam existed. Only production wiring passes the resolved detector
     * and screen.
     */
    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer,
            @Nullable ActionRegistry openActionRegistry,
            @Nullable ConditionRegistry openConditionRegistry,
            @Nullable LastMenu lastMenu) {
        this(
                renderer,
                scheduler,
                lists,
                editorRenderer,
                openActionRegistry,
                openConditionRegistry,
                lastMenu,
                BedrockDetector.NONE,
                BedrockScreen.NONE);
    }

    /**
     * The canonical constructor production wiring uses: the action/condition-aware engine, the reopen tracker
     * {@code /menu last} reads, and the Bedrock detector/screen the open choke-point consults to redirect a Floodgate
     * viewer to a native form. Every other constructor delegates here with {@code null} for the parameters it does not
     * carry and the Java-only Bedrock defaults, so the roughly ninety existing {@code new Menus(...)} call-sites
     * compile unchanged and open exactly as before. With null registries an open skips the requirement gate and runs
     * no open-actions, with a null tracker it records no reopen target, and with the {@code NONE} Bedrock defaults it
     * never redirects to a form. Only production wiring, which has the fully populated registries, the shared tracker,
     * and the resolved Bedrock detector/screen, passes them.
     */
    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer,
            @Nullable ActionRegistry openActionRegistry,
            @Nullable ConditionRegistry openConditionRegistry,
            @Nullable LastMenu lastMenu,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen) {
        this(
                renderer,
                scheduler,
                lists,
                editorRenderer,
                openActionRegistry,
                openConditionRegistry,
                lastMenu,
                bedrock,
                bedrockScreen,
                new PagedListSourceRegistry());
    }

    /**
     * The paged-source-aware canonical constructor: the same engine plus the {@link PagedListSourceRegistry} whose
     * sources answer for one page of a large corpus rather than the whole of it. Every other constructor delegates here
     * with an empty registry, so the roughly ninety existing {@code new Menus(...)} call-sites compile unchanged and
     * resolve every list through the in-memory {@link #lists} exactly as before. An open there never consults the paged
     * registry. Only production wiring, which holds the feature-populated paged registry, passes it.
     */
    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer,
            @Nullable ActionRegistry openActionRegistry,
            @Nullable ConditionRegistry openConditionRegistry,
            @Nullable LastMenu lastMenu,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen,
            PagedListSourceRegistry pagedLists) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.editorRenderer = editorRenderer;
        this.openActionRegistry = openActionRegistry;
        this.openConditionRegistry = openConditionRegistry;
        this.lastMenu = lastMenu;
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.bedrockScreen = Objects.requireNonNull(bedrockScreen, "bedrockScreen");
        this.pagedLists = Objects.requireNonNull(pagedLists, "pagedLists");
    }

    /** Registers a parsed spec under its id; a feature does this once at wiring time. */
    public void registerSpec(String id, MenuSpec spec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        specs.put(id, spec);
    }

    /**
     * Drop the spec registered under {@code id} so the menu can no longer be opened, the unregister half of the menu
     * editor's delete, run once the file is gone. A no-op when no spec carries that id. The loader's single-file reload
     * cannot do this itself (a deleted file is a not-found reload, not an unregister), so the editor calls it directly.
     */
    public void unregisterSpec(String id) {
        Objects.requireNonNull(id, "id");
        specs.remove(id);
    }

    /**
     * The spec registered under {@code id}, or empty when none is, a read-only lookup the {@code /menu dump} and
     * {@code /menu meta} operator diagnostics use to describe a loaded menu without opening it.
     */
    public Optional<MenuSpec> registeredSpec(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(specs.get(id));
    }

    /**
     * Run one menu {@code action} for {@code target} as if they had tapped it, the {@code /menu execute <player>
     * <action>} admin tool. It hops onto the target's own entity thread (where touching the live inventory is legal)
     * and dispatches through the same shared {@link #runActions} runner a form tap uses, with {@code target} as the
     * context viewer so a {@code %player%} in the action resolves to them. No spec need be registered, the action
     * runs standalone. An engine wired without an action registry (a list/spec-only test engine) runs nothing here,
     * matching {@link #runActions}.
     */
    public void execute(PlayerRef target, Ref action) {
        execute(target, action, Map.of());
    }

    /**
     * Run one menu {@code action} for {@code target} with {@code arguments} bound, so an {@code %argument_<name>%}
     * token inside the action resolves from a caller-supplied map rather than from an open window. This is what lets
     * a config-declared command (a custom command, or a menu's own open command) run the shared action vocabulary
     * with its typed arguments in hand and no inventory in sight.
     */
    public void execute(PlayerRef target, Ref action, Map<String, String> arguments) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(arguments, "arguments");
        scheduler.onEntity(target, () -> runActions(MenuContext.of(target, null, 0, arguments), List.of(action)));
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer}, carrying {@code subject} as the domain
     * object the menu is about (or null for a subject-less menu). An unknown spec id is a coding error in the
     * caller's wiring, so it fails loudly here rather than opening an empty window a player would meet.
     */
    public void open(PlayerRef viewer, String specId, @Nullable Object subject) {
        open(viewer, specId, subject, 0);
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer} at {@code page}, the same as
     * {@link #open(PlayerRef, String, Object)} but starting on a chosen page rather than page zero: what an
     * {@code open:<menu> [page]} action reaches for. A negative page is clamped to zero. An unknown spec id is a
     * caller wiring error, so it fails loudly here rather than opening an empty window.
     */
    public void open(PlayerRef viewer, String specId, @Nullable Object subject, int page) {
        open(viewer, specId, subject, page, Map.of());
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer} at {@code page}, carrying the typed command
     * {@code arguments} the menu was opened with. An operator {@code command {}} block's {@code %argument_<name>%}
     * values, keyed by argument name. The arguments ride the {@link MenuContext} to the renderer so a title, item
     * name or lore can expand them. A negative page is clamped to zero; an unknown spec id fails loudly here.
     */
    public void open(
            PlayerRef viewer, String specId, @Nullable Object subject, int page, Map<String, String> arguments) {
        openInternal(viewer, specId, subject, page, arguments, viewer, true);
    }

    /**
     * Open the spec for {@code viewer}, the player who <em>sees</em> the menu, while attributing the open to
     * {@code executor}, the player who <em>triggered</em> it. The overload {@code /menu open <name> <target>} reaches
     * for. The viewer is the target, so {@code %player%} and every player-scoped placeholder still resolve against the
     * target as with any open; the executor rides the {@link MenuContext} so the menu the target sees can expand
     * {@code %executor%} to the opener, distinct from {@code %player%}. An ordinary self-open passes the viewer as the
     * executor (the other overloads do this for you), so the two read the same. A negative page is clamped to zero; an
     * unknown spec id fails loudly here.
     */
    public void open(
            PlayerRef viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            PlayerRef executor) {
        openInternal(viewer, specId, subject, page, arguments, executor, true);
    }

    /**
     * The shared open body every public {@link #open} overload and the internal {@link #reopen} route through. It
     * resolves the spec, clamps the page, resolves list sources off the tick thread, then shows the window on the
     * viewer's entity thread. {@code executor} is who triggered the open, the viewer itself for a self-open, the
     * opener for an open-for-another. Carried through so {@code %executor%} can name it distinctly from the viewer's
     * {@code %player%}. {@code record} decides whether the open joins the viewer's {@code /menu last} / back history: a
     * fresh open records (subject permitting), a back-step or reopen-last replays an already-recorded open and must not
     * push it again: otherwise stepping back would immediately re-stack what it just popped.
     */
    private void openInternal(
            PlayerRef viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            PlayerRef executor,
            boolean record) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(executor, "executor");
        MenuSpec spec = specs.get(specId);
        if (spec == null) {
            throw new IllegalArgumentException("no menu spec registered under id: " + specId);
        }
        int startPage = Math.max(0, page);
        // Snapshot before the hop, not for immutability: MenuContext, LastOpen and OpenMenuInfo each copy at their
        // own door, but every one of those copies runs on the entity thread after the list resolution, so they
        // preserve a caller's mutation rather than prevent it. This one is taken on the calling thread, and it is
        // the only thing standing between a caller that reuses its argument map and a window built from what the
        // map says by the time the second hop lands. A test scheduler that runs inline never gives the caller that
        // turn, which is what makes this copy read as the fourth redundant one.
        Map<String, String> args = Map.copyOf(arguments);
        MenuContext ctx = MenuContext.of(viewer, subject, startPage, args)
                .withLocalPlaceholders(spec.placeholders())
                .withExecutor(executor);
        scheduler.async(() -> {
            ResolvedLists resolved = resolveLists(spec, ctx);
            scheduler.onEntity(
                    viewer,
                    () -> openResolved(viewer, specId, spec, subject, resolved, startPage, args, executor, record));
        });
    }

    /**
     * Reopen the previous menu {@code viewer} had open: the target a {@code back} button steps to. It hops to the
     * viewer's entity thread and, if nothing remains beneath the current open (they are at the root, or the engine was
     * wired without a history), closes the window instead. A previous open whose spec is no longer registered (a
     * since-dropped menu) is treated as nothing-below, so a stale entry closes cleanly rather than raising the loud
     * unknown-spec failure a blind reopen would. The reopen itself replays the recorded open without re-recording it,
     * so stepping back never grows the history.
     */
    public void back(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            closeFor(viewer);
            return;
        }
        lastMenu.back(viewer.uuid())
                .filter(prev -> specs.containsKey(prev.menuId()))
                .ifPresentOrElse(prev -> reopen(viewer, prev), () -> closeFor(viewer));
    }

    /**
     * Reopen the menu {@code viewer} currently has on top of their history: what {@code /menu last} runs. Returns
     * {@code false} (so the caller can show its own feedback) when there is nothing to reopen: the engine carries no
     * history, none has been recorded, or the recorded spec is no longer registered. A successful reopen replays the
     * recorded open without re-recording it, so calling it repeatedly never stacks duplicates.
     */
    public boolean reopenLast(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            return false;
        }
        Optional<LastMenu.LastOpen> last = lastMenu.get(viewer.uuid()).filter(open -> specs.containsKey(open.menuId()));
        last.ifPresent(open -> reopen(viewer, open));
        return last.isPresent();
    }

    /**
     * Replay a recorded open (same page and typed arguments, always subject-less) without recording it again. The
     * back history does not track who originally triggered the open, so the reopen is attributed to the viewer (the
     * player stepping back is looking at their own menu); a menu that leaned on {@code %executor%} would read the
     * viewer here rather than the long-gone original opener.
     */
    private void reopen(PlayerRef viewer, LastMenu.LastOpen open) {
        openInternal(viewer, open.menuId(), null, open.page(), open.arguments(), viewer, false);
    }

    /** Close whatever window {@code viewer} has open, on their entity thread: the back-step to nothing / null-history path. */
    private void closeFor(PlayerRef viewer) {
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                live.closeInventory();
            }
        });
    }

    /**
     * The menu {@code viewer} currently has open, as a plain {@link OpenMenuInfo} value, or empty when they are in
     * no engine menu: what the outbound {@code menu_*} placeholder source reads. It is a best-effort live read: it
     * resolves the online player and inspects the holder backing their open top inventory, which is the engine's
     * single source of truth for an open menu (no player-keyed side map is kept, so nothing can leak and there is
     * nothing else to consult). The read is authoritative when the placeholder resolves on the viewer's own
     * region/main context; a cross-region read on Folia would touch another region's player and is caught and
     * degraded to empty, so a stray off-region request reads "not in a menu" rather than throwing.
     */
    public Optional<OpenMenuInfo> currentMenu(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        try {
            Player live = Bukkit.getPlayer(viewer);
            if (live == null) {
                return Optional.empty();
            }
            InventoryHolder holder = live.getOpenInventory().getTopInventory().getHolder();
            return holder instanceof MenuHolder menu ? Optional.of(openMenuInfo(menu)) : Optional.empty();
        } catch (RuntimeException offRegion) {
            return Optional.empty();
        }
    }

    /**
     * The spec id of the engine menu backing {@code inventory}, or empty when it is not an engine menu window. Unlike
     * {@link #currentMenu(UUID)}, which reads a viewer's current top inventory, this inspects a specific inventory the
     * caller already holds (an {@code InventoryCloseEvent}'s inventory), so a feature can recognise one of its own
     * spec menus closing (to reopen it, say) through the facade, without reaching for the engine-internal
     * {@link MenuHolder} itself. A pure holder read, safe on any thread.
     */
    public Optional<String> menuIdOf(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return inventory.getHolder() instanceof MenuHolder menu ? Optional.of(menu.specId()) : Optional.empty();
    }

    /**
     * Redraw in place the menu {@code viewer} has open, when it is the one registered under {@code specId}, the seam
     * a feature whose window shows live state reaches for when that state changes (the far side of a trade staking an
     * item, say). It hops to the viewer's own entity thread and does nothing at all when they have since closed the
     * window or moved to another menu, so a stale update can never paint over an unrelated screen. The redraw reuses
     * the open window: no second {@code openInventory}, no new holder, and a content region the viewer physically
     * fills is left untouched.
     */
    public void redraw(PlayerRef viewer, String specId) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live == null || !live.isOnline()) {
                return;
            }
            openWindow(viewer, specId)
                    .map(Inventory::getHolder)
                    .filter(MenuHolder.class::isInstance)
                    .map(MenuHolder.class::cast)
                    .ifPresent(this::reRender);
        });
    }

    /**
     * The live window {@code viewer} has open when it is the menu registered under {@code specId}, else empty, how a
     * feature reads or clears the slots of its own {@code content {}} region without keeping a window reference of its
     * own. It is a plain read of the viewer's open inventory, so it must be called on their entity thread (where
     * touching a live inventory is legal), which is where every content-region callback already runs.
     */
    public Optional<Inventory> openWindow(PlayerRef viewer, String specId) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return Optional.empty();
        }
        // A player looking at no window at all has nothing to read here, so the absent case is answered rather than
        // dereferenced.
        Inventory top = live.getOpenInventory().getTopInventory();
        if (top == null) {
            return Optional.empty();
        }
        return top.getHolder() instanceof MenuHolder holder && holder.specId().equals(specId)
                ? Optional.of(top)
                : Optional.empty();
    }

    /** Read the open menu's id, its 1-based page (the context page is 0-based), row count and typed arguments. */
    private static OpenMenuInfo openMenuInfo(MenuHolder holder) {
        return new OpenMenuInfo(
                holder.specId(),
                holder.ctx().page() + 1,
                holder.spec().rows(),
                holder.ctx().arguments());
    }

    /**
     * The id of the most-recently-opened menu in {@code viewer}'s history, the {@code /menu last} target, which
     * persists after that menu closes, unlike {@link #currentMenu}. Read from the thread-safe {@link LastMenu}, so
     * it needs no Bukkit read and answers the same on any thread. Empty when the engine was wired without a history
     * tracker (every list/spec-only fixture) or the viewer has opened no custom menu yet.
     */
    public Optional<String> lastMenuId(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            return Optional.empty();
        }
        return lastMenu.get(viewer).map(LastMenu.LastOpen::menuId);
    }

    /**
     * Open a typed property editor for {@code viewer} editing {@code subject}, as a holder-backed engine menu. It
     * builds the same {@link MenuHolder} every other menu uses, recognised and torn down by the one listener and
     * one {@code closeMenu}, but tags it with an {@link EditorState} so the listener routes its clicks through the
     * editor's property/button slots rather than a spec's. The window is shown on the viewer's entity thread, where
     * touching the live inventory is legal; unlike a list menu it queries no list source, so there is no off-thread
     * resolve step. An engine wired without an editor renderer cannot open an editor. That is a wiring error, so it
     * fails loudly here.
     */
    public void openEditor(PlayerRef viewer, EditorSpec spec, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        scheduler.onEntity(viewer, () -> openEditorResolved(viewer, spec, subject));
    }

    /** On the viewer's entity thread: build the editor holder + window, render the editor, show it. No refresh. */
    private void openEditorResolved(PlayerRef viewer, EditorSpec spec, @Nullable Object subject) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, subject, 0);
        MenuHolder holder = new MenuHolder(editorSpecId(spec), editorMenuSpec(spec), ctx);
        EditorState state = new EditorState(spec, subject);
        holder.attachEditor(state);
        Inventory inv = Bukkit.createInventory(
                holder, spec.layout().rows() * 9, MenuTitles.centre(spec.title(viewer, subject)));
        holder.attach(inv);
        requireEditorRenderer().populate(inv, spec, state, live, viewer);
        live.openInventory(inv);
    }

    /**
     * Re-render an open editor in place: the {@code reopen} target a property's click hook runs after its setter.
     * It hops to the viewer's entity thread, confirms the live top inventory is still this holder's editor window,
     * clears the editor's slot routing, and repaints the same inventory from the live subject so the changed value
     * shows. No second {@code openInventory} and no new holder: the window the viewer is looking at is reused, so the
     * one listener and one teardown keep owning it.
     */
    public void reRenderEditor(MenuHolder holder) {
        Objects.requireNonNull(holder, "holder");
        EditorRefresh.reRender(holder, requireEditorRenderer(), scheduler);
    }

    /**
     * Open a paginated entity list for {@code viewer} as a holder-backed engine menu. The engine's replacement for the
     * bespoke {@code EntityListView}'s uxmLib {@code PaginatedGui}. It builds the same {@link MenuHolder} every other
     * menu uses (recognised and torn down by the one listener and one {@code closeMenu}) but tags it with a
     * {@link ListViewState} so the listener routes its clicks through the list's entity/nav/create/action slots rather
     * than a spec's. The window is shown on the viewer's entity thread, where touching the live inventory is legal; the
     * entity supplier was already resolved off-thread by the caller, so there is no off-thread resolve step here and the
     * imperative icon renderer reads only the snapshot. A page flip re-paginates the same holder (the listener's list
     * branch), so a list arms no refresh timer and stays leak-balanced.
     */
    public void openList(PlayerRef viewer, EntityListSpec spec) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        scheduler.onEntity(viewer, () -> openListResolved(viewer, spec));
    }

    /** On the viewer's entity thread: build the list holder + window, render page zero, show it. No refresh. */
    private void openListResolved(PlayerRef viewer, EntityListSpec spec) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("list:" + spec.getClass().getSimpleName(), listMenuSpec(spec), ctx);
        ListViewState state = new ListViewState(spec);
        holder.attachListView(state);
        Inventory inv = Bukkit.createInventory(holder, spec.rows() * 9, MenuTitles.centre(spec.title()));
        holder.attach(inv);
        int clamped = listViewRenderer.populate(inv, spec, state, live, viewer, 0);
        holder.setCtx(ctx.withPage(clamped));
        live.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a list holder carries: the row count, refresh off, no items, clicks ride state. */
    private static MenuSpec listMenuSpec(EntityListSpec spec) {
        return new MenuSpec("", spec.rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Open a two-button confirm window for {@code viewer}: the engine's replacement for uxmLib's {@code ConfirmMenu}.
     * It builds the same {@link MenuHolder} every other menu uses, so the one listener routes its clicks and the one
     * {@code closeMenu} tears it down: clicking the yes button runs {@code onYes} exactly once, clicking no runs
     * {@code onNo} exactly once, and either click closes the window first. Closing the window (or quitting) without a
     * click runs neither. The window is shown on the viewer's entity thread, where touching the live inventory is
     * legal; the supplied runnables run on that same thread when their button is clicked, mirroring the editor and
     * spec-action click hops. The {@code title} is a {@link Component} the caller already resolved from a
     * {@code MessageKey}, so the window carries no inline user-facing literal.
     */
    public void confirm(PlayerRef viewer, Component title, Runnable onYes, Runnable onNo) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(onYes, "onYes");
        Objects.requireNonNull(onNo, "onNo");
        scheduler.onEntity(viewer, () -> openConfirmResolved(viewer, title, onYes, onNo));
    }

    /** On the viewer's entity thread: build the confirm holder + window, paint the two buttons, show it. No refresh. */
    private void openConfirmResolved(PlayerRef viewer, Component title, Runnable onYes, Runnable onNo) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        if (bedrock.isBedrock(viewer.uuid())) {
            sendConfirmModal(live, viewer, title, onYes, onNo);
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("confirm", confirmMenuSpec(), ctx);
        holder.attachConfirm(new ConfirmState(ConfirmRenderer.YES_SLOT, ConfirmRenderer.NO_SLOT, onYes, onNo));
        Inventory inv = Bukkit.createInventory(holder, ConfirmRenderer.ROWS * 9, MenuTitles.centre(title));
        holder.attach(inv);
        confirmRenderer.populate(inv);
        live.openInventory(inv);
    }

    /**
     * The Bedrock render of the confirm window: a native ModalForm (the caller's title plus a yes/no button pair)
     * instead of the confirm chest, whose lime/red wool carries no text a form could show, so the button labels come
     * from the shared {@link GuiMessageKey} catalog in the viewer's locale. A confirm is always safe to render as a
     * form (it is a plain two-choice prompt, not an item-display menu), so this redirect is unconditional for a Bedrock
     * viewer and never gated on {@code chestOnly}. Cumulus fires the response off the main thread, so each choice is
     * wrapped in a hop back to the viewer's entity thread before {@code onYes}/{@code onNo} runs.
     */
    private void sendConfirmModal(Player live, PlayerRef viewer, Component title, Runnable onYes, Runnable onNo) {
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);
        String yes = renderer.plainMessage(viewer, GuiMessageKey.CONFIRM_YES);
        String no = renderer.plainMessage(viewer, GuiMessageKey.CONFIRM_NO);
        bedrockScreen.sendModalForm(
                live,
                plainTitle,
                null,
                yes,
                no,
                () -> scheduler.onEntity(viewer, onYes),
                () -> scheduler.onEntity(viewer, onNo));
    }

    /** The minimal {@link MenuSpec} a confirm holder carries: three rows, refresh off, no items, clicks ride state. */
    private static MenuSpec confirmMenuSpec() {
        return new MenuSpec(
                "", ConfirmRenderer.ROWS, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * The opener a property hands its picker to: opening a selector through it shows a {@link MenuHolder} child window
     * the one listener routes and the one {@code closeMenu} tears down. Threaded into the editor {@code ClickContext}
     * so an {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty} (and, as they migrate,
     * the list/colour pickers) opens an engine child rather than a uxmLib {@code SimpleGui} on the engine runtime.
     */
    public SelectorOpener selectorOpener() {
        return selectorOpener;
    }

    /**
     * The opener a property hands a destructive step to: it gates the step behind a {@link MenuHolder} confirm child
     * the one listener routes and the one {@code closeMenu} tears down. Threaded into the editor {@code ClickContext}
     * alongside {@link #selectorOpener()} so a {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.property
     * .ListProperty}'s remove gesture opens an engine confirm rather than a uxmLib {@code ConfirmMenu} on the engine
     * runtime.
     */
    public ConfirmOpener confirmOpener() {
        return confirmOpener;
    }

    /**
     * Open a selector child window for {@code viewer}. A flat picker of option buttons, the engine's replacement for
     * a property's uxmLib {@code SimpleGui} selector. It builds the same {@link MenuHolder} every other menu uses, so
     * the one listener routes its clicks and the one {@code closeMenu} tears it down: clicking an option button runs
     * its choose action exactly once, and either that or closing the window (or quitting) ends the picker. The window
     * is shown on the viewer's entity thread, where touching the live inventory is legal; each button's choose action
     * runs on that same thread when clicked, mirroring the editor and confirm hops. The {@code title} is a
     * {@link Component} the caller resolved from a {@code MessageKey}, so the window carries no inline literal.
     */
    public void openSelector(
            PlayerRef viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(buttons, "buttons");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        List<SelectorButton> copy = List.copyOf(buttons);
        scheduler.onEntity(viewer, () -> openSelectorResolved(viewer, title, rows, filler, copy));
    }

    /** On the viewer's entity thread: build the selector holder + window, paint the option buttons, show it. */
    private void openSelectorResolved(
            PlayerRef viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("selector", selectorMenuSpec(rows), ctx);
        Map<Integer, ChildClickHandler> choices = new HashMap<>();
        for (SelectorButton button : buttons) {
            choices.put(button.slot(), button.onClick());
        }
        holder.attachSelector(new SelectorState(choices));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, MenuTitles.centre(title));
        holder.attach(inv);
        selectorRenderer.populate(inv, filler, buttons);
        live.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a selector holder carries: the row count, refresh off, no items, clicks ride state. */
    private static MenuSpec selectorMenuSpec(int rows) {
        return new MenuSpec("", rows, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Open a slot-grid canvas for {@code viewer}. The engine's visual editor window, opened by the custom-menus grid
     * editor. It builds the same {@link MenuHolder} every other menu uses, recognised and torn down by the one
     * listener and one {@code closeMenu}, but tags it with a {@link GridViewState} so the listener routes its clicks
     * through the canvas's content/nav/control slots rather than a spec's. The window is shown on the viewer's entity
     * thread, where touching the live inventory is legal; the {@code spec}'s content supplier is read imperatively on
     * that thread over the caller's already-loaded edit model, so there is no off-thread resolve step. Unlike a list a
     * grid re-reads its content on every draw (a place / move / clear then a {@link GridView#reRender}), so it arms no
     * refresh timer and stays leak-balanced.
     */
    public void openGrid(PlayerRef viewer, GridSpec spec, GridHandlers handlers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(handlers, "handlers");
        scheduler.onEntity(viewer, () -> openGridResolved(viewer, spec, handlers));
    }

    /** On the viewer's entity thread: build the grid holder + window, render page zero, show it. No refresh. */
    private void openGridResolved(PlayerRef viewer, GridSpec spec, GridHandlers handlers) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        int windowRows = GridRenderer.windowRows(spec.menuRows());
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("grid:" + spec.menuRows(), gridMenuSpec(windowRows), ctx);
        GridViewState state = new GridViewState(spec, handlers);
        holder.attachGridView(state);
        Inventory inv = Bukkit.createInventory(holder, windowRows * 9, MenuTitles.centre(spec.title()));
        holder.attach(inv);
        // The grid renderer is built at the use site rather than in the constructor, so an engine wired with a stub or
        // mocked MenuRenderer (many spec-only test fixtures) never dereferences its item renderer unless a grid opens.
        int clamped = new GridRenderer(renderer.itemRenderer()).populate(inv, spec, state, live, viewer, 0);
        holder.setCtx(ctx.withPage(clamped));
        live.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a grid holder carries: the window's row count, refresh off, clicks ride state. */
    private static MenuSpec gridMenuSpec(int windowRows) {
        return new MenuSpec("", windowRows, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Open {@code spec} for {@code viewer} as a live preview. Render an in-memory working copy exactly as a player
     * would see it, through the real {@link MenuRenderer}, without registering the spec. The menu editor uses this so
     * an operator can look over their unsaved edits before committing them to disk: the working copy is frozen into an
     * immutable {@link MenuSpec} and handed here. It builds the same {@link MenuHolder} every other menu uses, so the
     * one listener routes its clicks and the one {@code closeMenu} tears it down, and attaches {@code onClose}, the
     * seam the close path runs once to step the operator back to the grid editor when the preview closes.
     *
     * <p>A preview is deliberately not a full open: it does not gate on the spec's open-requirement, run its
     * open-actions, record it in the viewer's {@code /menu last} history, paint the bottom inventory, or arm a refresh
     * timer: those are the side effects of really opening a menu, and a preview is a look, not an open. Its clicks
     * still run the spec's own click actions (that is what "as a player sees it" means), so a preview of a shop can be
     * clicked through live. Its list sources are resolved off the tick thread like any open, then the window is shown
     * on the viewer's entity thread.
     */
    public void openPreview(PlayerRef viewer, MenuSpec spec, Runnable onClose) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(onClose, "onClose");
        MenuContext ctx = MenuContext.of(viewer, null, 0).withLocalPlaceholders(spec.placeholders());
        scheduler.async(() -> {
            ResolvedLists resolved = resolveLists(spec, ctx);
            scheduler.onEntity(viewer, () -> openPreviewResolved(viewer, spec, ctx, resolved, onClose));
        });
    }

    /** On the viewer's entity thread: build the preview holder + window, render it, attach the back hook, show it. */
    private void openPreviewResolved(
            PlayerRef viewer, MenuSpec spec, MenuContext ctx, ResolvedLists resolved, Runnable onClose) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuHolder holder = new MenuHolder("preview:" + spec.rows(), spec, ctx);
        holder.setResolvedLists(resolved.rows());
        MenuContext renderCtx = attachPagedViews(holder, ctx, resolved.paged());
        holder.attachCloseHook(onClose);
        Inventory inv = createWindow(holder, spec, renderer.title(spec, renderCtx));
        holder.attach(inv);
        renderer.populate(inv, spec, renderCtx, holder::recordSlot, holder.resolvedLists());
        live.openInventory(inv);
    }

    private EditorRenderer requireEditorRenderer() {
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        return editorRenderer;
    }

    /** A stable holder id for an editor open; editors are code-built and not registered, so the type name suffices. */
    private static String editorSpecId(EditorSpec spec) {
        return "editor:" + spec.getClass().getSimpleName();
    }

    /**
     * A minimal {@link MenuSpec} the editor holder carries so {@link MenuRefresh} and the holder's accessors have
     * something coherent to read: the editor's row count, refresh disabled (an editor is repainted on a click, never
     * on a timer), and no items (an editor's buttons live on its {@link EditorState}, not in a spec). The editor
     * render path never reads this spec's items, so an empty item map is exactly right.
     */
    private static MenuSpec editorMenuSpec(EditorSpec spec) {
        return new MenuSpec(
                "", spec.layout().rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * The rows one open resolved for every list the spec names, keyed by source id (a paged source's rows and an
     * in-memory source's whole corpus are stored the same way, so the render path does not care which kind produced
     * them), plus the paging metadata each paged source additionally reported. That metadata is applied to the holder
     * on the viewer's entity thread by {@link #attachPagedViews}; it never rides back onto a tick thread.
     */
    private record ResolvedLists(Map<String, List<?>> rows, Map<String, PagedListMeta> paged) {}

    /** What a paged source reported beyond its rows: the corpus total, and the page size and sorts the request used. */
    private record PagedListMeta(long total, int size, List<String> sorts) {}

    /**
     * Resolve every list source the spec names. Runs off the viewer's region thread because a source may read a
     * database; an unregistered source resolves to an empty list so a wiring gap renders an empty grid rather than
     * failing the open. This is the only place a source is queried for one open. A paged source is asked for a single
     * default page (page zero, the spec's first declared sort, no filters) because on first open there is no holder
     * yet to carry the viewer's page; that {@link PageRequest} is a plain immutable value, so building and using it off
     * the entity thread touches no per-viewer state.
     */
    private ResolvedLists resolveLists(MenuSpec spec, MenuContext ctx) {
        Map<String, List<?>> rows = new HashMap<>();
        Map<String, PagedListMeta> paged = new HashMap<>();
        for (MenuItemSpec item : spec.items().values()) {
            item.list().ifPresent(listSpec -> resolveListSource(item, listSpec, ctx, rows, paged));
        }
        return new ResolvedLists(rows, paged);
    }

    /** Resolve one list-backed item's source: the in-memory registry first, then the paged one, else an empty list. */
    private void resolveListSource(
            MenuItemSpec item,
            ListSpec listSpec,
            MenuContext ctx,
            Map<String, List<?>> rows,
            Map<String, PagedListMeta> paged) {
        String sourceId = listSpec.source().id();
        Optional<Function<MenuContext, List<?>>> plain = lists.get(sourceId);
        if (plain.isPresent()) {
            rows.put(sourceId, plain.get().apply(ctx));
            return;
        }
        Optional<BiFunction<MenuContext, PageRequest, PagedResult<?>>> pagedSource = pagedLists.get(sourceId);
        if (pagedSource.isEmpty()) {
            rows.put(sourceId, List.of());
            return;
        }
        resolvePagedSource(item, listSpec, sourceId, pagedSource.get(), ctx, rows, paged);
    }

    /** Ask a paged source for the default page, store its rows (pinned first) under the id, and note its reported total. */
    private void resolvePagedSource(
            MenuItemSpec item,
            ListSpec listSpec,
            String sourceId,
            BiFunction<MenuContext, PageRequest, PagedResult<?>> source,
            MenuContext ctx,
            Map<String, List<?>> rows,
            Map<String, PagedListMeta> paged) {
        int size = pageSize(item, listSpec);
        String sort = listSpec.sorts().isEmpty() ? "" : listSpec.sorts().get(0);
        PagedResult<?> result = source.apply(ctx, new PageRequest(0, size, sort, Map.of()));
        rows.put(sourceId, PagedListRows.combine(sourceId, size, result, LOG));
        paged.put(sourceId, new PagedListMeta(result.totalCount(), size, listSpec.sorts()));
    }

    /** The page size the default request uses: the spec's explicit page-size, or the item's slot count when it is zero. */
    private static int pageSize(MenuItemSpec item, ListSpec listSpec) {
        return listSpec.pageSize() != 0
                ? listSpec.pageSize()
                : item.slots().slots().size();
    }

    /**
     * On the viewer's entity thread: record each paged list's reported total on its {@link ListQueryState}, the single
     * place per open that state is written, and return a render context carrying an immutable {@link PagedListView} per
     * list so the renderer knows which lists are already paged and what page count their indicator reads. Returns the
     * context unchanged when the open queried no paged source, so an in-memory-only open is untouched.
     */
    private MenuContext attachPagedViews(MenuHolder holder, MenuContext ctx, Map<String, PagedListMeta> paged) {
        if (paged.isEmpty()) {
            return ctx;
        }
        Map<String, PagedListView> views = new HashMap<>();
        paged.forEach((listId, meta) -> {
            ListQueryState state = holder.queryState(listId, meta.sorts());
            state.recordTotal(meta.total());
            views.put(listId, new PagedListView(state.page(), meta.total(), meta.size()));
        });
        MenuContext renderCtx = ctx.withPagedViews(views);
        holder.setCtx(renderCtx);
        return renderCtx;
    }

    /**
     * Fire the public, cancellable {@link MenuOpenEvent} and report whether a listener vetoed the open. Called at the
     * very top of the one open choke-point every open (a fresh open, a {@code back} step, a reopen-last) funnels
     * through, before the Bedrock form branches and the chest build alike, so a cancelled open shows the viewer
     * neither a native form nor a chest. It fires on the viewer's own region thread the open already runs on.
     */
    private static boolean openVetoed(Player live, String specId, int page) {
        MenuOpenEvent event = new MenuOpenEvent(live, specId, page);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    /** On the viewer's entity thread: build the holder-backed window, cache the lists, render, show, arm refresh. */
    private void openResolved(
            PlayerRef viewer,
            String specId,
            MenuSpec spec,
            @Nullable Object subject,
            ResolvedLists resolved,
            int page,
            Map<String, String> arguments,
            PlayerRef executor,
            boolean record) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        if (openVetoed(live, specId, page)) {
            return;
        }
        // Attach the spec's own placeholders {} block so the renderer resolves its %name% tokens local-first, and the
        // executor so %executor% names the opener; the holder carries this ctx, so a refresh/re-render reads it back
        // and keeps both the local map and the executor through the redraw.
        MenuContext ctx = MenuContext.of(viewer, subject, page, arguments)
                .withLocalPlaceholders(spec.placeholders())
                .withExecutor(executor);
        if (!gateOpen(spec, ctx)) {
            return;
        }
        // A Bedrock viewer gets a native Cumulus form instead of the chest, unless the menu opts out (chest-only, for
        // an item-display menu a form cannot represent). An explicit per-menu bedrock {} block wins first: it defines a
        // native CustomForm. The dropdown/slider/toggle/multi-input widgets the automatic SimpleForm degradation
        // cannot express, so a menu that declares one sends that form rather than the degraded button list. Absent a
        // block, the automatic degradation is unchanged. A form is an alternative render at the open choke-point, not a
        // second window, so it builds no holder and arms no refresh; but it is still an open, so it records into the
        // back history and fires the menu's open-actions the same way the chest path does, otherwise a back from a
        // form would have no history to step to and a menu's open-actions would never fire for a Bedrock viewer. A Java
        // viewer (isBedrock false) falls straight through to the chest path unchanged.
        if (bedrock.isBedrock(viewer.uuid())
                && !spec.chestOnly()
                && spec.bedrock().isPresent()) {
            sendBedrockCustomForm(live, spec, ctx);
            afterBedrockOpen(spec, live, ctx, viewer, specId, subject, page, arguments, record);
            return;
        }
        // The resolved list cache is threaded through so a list-backed menu's entries page as form buttons.
        if (bedrock.isBedrock(viewer.uuid()) && !spec.chestOnly()) {
            sendBedrockForm(live, spec, ctx, resolved.rows());
            afterBedrockOpen(spec, live, ctx, viewer, specId, subject, page, arguments, record);
            return;
        }
        MenuHolder holder = new MenuHolder(specId, spec, ctx);
        holder.setResolvedLists(resolved.rows());
        // Record each paged list's reported total on the holder's query state and take an immutable render context that
        // carries a view of each, all on this entity thread, so the mutable state is never touched off it.
        MenuContext renderCtx = attachPagedViews(holder, ctx, resolved.paged());
        Inventory inv = createWindow(holder, spec, renderer.title(spec, renderCtx));
        holder.attach(inv);
        renderer.populate(inv, spec, renderCtx, holder::recordSlot, holder.resolvedLists());
        if (spec.bottomInventory()) {
            paintBottom(holder, spec, renderCtx, live);
        }
        live.openInventory(inv);
        if (record) {
            rememberLastOpen(viewer, specId, subject, page, arguments);
        }
        runOpenActions(spec, live, ctx);
        MenuRefresh.start(holder, scheduler, () -> reRender(holder));
    }

    /**
     * The bookkeeping a Bedrock form open shares with the chest path once the form is on the viewer's screen: record
     * this open into the viewer's back history (subject permitting. The same rule the chest path applies) so a
     * {@code back} from the form can step to it, and fire the menu's open-actions. A form carries no window, so, unlike
     * the chest path. It builds no holder and arms no refresh; this runs only the two pieces that describe an open
     * rather than a window. A reopen (a {@code back} re-sending the previous form) passes {@code record} false, so
     * stepping back never re-stacks the form it just returned to.
     */
    private void afterBedrockOpen(
            MenuSpec spec,
            Player live,
            MenuContext ctx,
            PlayerRef viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            boolean record) {
        if (record) {
            rememberLastOpen(viewer, specId, subject, page, arguments);
        }
        runOpenActions(spec, live, ctx);
    }

    /**
     * Remember this open as the viewer's {@code /menu last} target, but only a subject-less one, a disk-loaded
     * custom menu. A feature menu carries a live domain subject (a warp, a home owner) that must never be reopened
     * blind, and it has its own command, so those are deliberately not recorded. An engine wired without a tracker
     * (every list/spec-only fixture) records nothing, so an open there stays byte-identical to before this seam.
     */
    private void rememberLastOpen(
            PlayerRef viewer, String specId, @Nullable Object subject, int page, Map<String, String> arguments) {
        if (lastMenu != null && subject == null) {
            lastMenu.record(viewer.uuid(), new LastMenu.LastOpen(specId, page, arguments));
        }
    }

    /**
     * Build the window a spec opens into: its declared non-chest {@link InventoryType} when it names one the server
     * accepts, else the default {@code rows}-based chest. A non-chest shape is best-effort. Some types reject a
     * custom holder or title on some servers, so a thrown build is caught, logged once, and downgraded to the chest,
     * meaning a bad {@code inventory-type} never leaves the viewer with a blank or missing window.
     */
    private Inventory createWindow(MenuHolder holder, MenuSpec spec, Component raw) {
        Component title = MenuTitles.centre(raw);
        Optional<InventoryType> type = spec.inventoryType().flatMap(Menus::resolveInventoryType);
        if (type.isEmpty()) {
            return Bukkit.createInventory(holder, spec.rows() * 9, title);
        }
        try {
            return Bukkit.createInventory(holder, type.get(), title);
        } catch (RuntimeException rejected) {
            LOG.warning("menu inventory type '" + spec.inventoryType().orElse("")
                    + "' could not be created, falling back to a chest: " + rejected.getMessage());
            return Bukkit.createInventory(holder, spec.rows() * 9, title);
        }
    }

    /**
     * Map an operator-friendly inventory-type token to the Bukkit {@link InventoryType} that shapes the window.
     * {@code chest}, a blank token, or any name not listed here resolves to empty, i.e. the default {@code rows}-based
     * chest: an unknown type is a soft miss, not a failure. A couple of obvious aliases are accepted so a spec author
     * can write the block name they know ({@code shulker}/{@code shulker_box}, {@code ender}/{@code ender_chest},
     * {@code workbench}/{@code crafting}).
     */
    private static Optional<InventoryType> resolveInventoryType(String name) {
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "hopper" -> Optional.of(InventoryType.HOPPER);
            case "dropper" -> Optional.of(InventoryType.DROPPER);
            case "dispenser" -> Optional.of(InventoryType.DISPENSER);
            case "furnace" -> Optional.of(InventoryType.FURNACE);
            case "anvil" -> Optional.of(InventoryType.ANVIL);
            case "brewing", "brewing_stand" -> Optional.of(InventoryType.BREWING);
            case "beacon" -> Optional.of(InventoryType.BEACON);
            case "shulker", "shulker_box" -> Optional.of(InventoryType.SHULKER_BOX);
            case "barrel" -> Optional.of(InventoryType.BARREL);
            case "lectern" -> Optional.of(InventoryType.LECTERN);
            case "loom" -> Optional.of(InventoryType.LOOM);
            case "ender", "ender_chest", "enderchest" -> Optional.of(InventoryType.ENDER_CHEST);
            case "workbench", "crafting", "crafting_table" -> Optional.of(InventoryType.WORKBENCH);
            default -> Optional.empty();
        };
    }

    /**
     * Whether this menu may open for {@code viewer} given its {@code open-requirement}, evaluated by the shared
     * {@link #passes} gate so the open path and a command's own requirements read a token identically.
     */
    private boolean gateOpen(MenuSpec spec, MenuContext ctx) {
        return passes(ctx.viewer(), spec.openRequirement(), ctx.arguments());
    }

    /**
     * Whether every requirement in {@code requirements} tests true for {@code viewer}, with {@code arguments} bound.
     * The gate is open (the open proceeds, the command runs) when the engine was wired without a condition registry
     * (every list/spec-only test engine) or nothing is required, so an engine that predates this seam behaves
     * byte-identically. Otherwise every requirement ref is an AND gate: each is resolved against the condition
     * registry (the same registry-aware split the click path uses, so a valued token like {@code has-money:100}
     * reaches its handler with {@code value=100}), has its {@code %argument_<name>%} tokens expanded from
     * {@code arguments} (so a gate can read a typed command argument, for example {@code expr:%argument_amount% > 0}),
     * and must test true. An unregistered or false condition fails the gate closed, so a wiring gap keeps the window
     * shut rather than showing it.
     */
    public boolean passes(PlayerRef viewer, List<Ref> requirements, Map<String, String> arguments) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(arguments, "arguments");
        ConditionRegistry conditions = openConditionRegistry;
        if (conditions == null || requirements.isEmpty()) {
            return true;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0, arguments);
        for (Ref ref : requirements) {
            Ref eff = ref.resolve(conditions::has);
            boolean pass = conditions
                    .get(eff.id())
                    .map(p -> p.test(ctx, ActionArguments.resolve(eff.args(), ctx.arguments())))
                    .orElse(false);
            if (!pass) {
                return false;
            }
        }
        return true;
    }

    /**
     * Run the spec's {@code open-actions} in order, now that the window is open on the viewer's entity thread, where
     * touching the live inventory is legal. Skipped when the engine was wired without an action registry (a
     * list/spec-only test engine), so an engine that predates this seam runs nothing extra. Each ref is resolved
     * against the action registry, the same registry-aware split the click path takes, and dispatched through a
     * {@link MenuActionContext} carrying {@link ClickKind#LEFT} as the neutral kind (no gesture fired on an open) and
     * the four-argument, no-control constructor, so a {@code refresh} written as an open-action is a harmless no-op
     * rather than a null-control failure. An action's {@code %argument_<name>%} tokens are expanded from the
     * arguments the menu was opened with, matching the click and render paths. Open-actions fire simply here; the
     * per-action delay and chance modifiers a click action honours are a later concern.
     */
    private void runOpenActions(MenuSpec spec, Player live, MenuContext ctx) {
        ActionRegistry actions = openActionRegistry;
        if (actions == null) {
            return;
        }
        for (Ref ref : spec.openActions()) {
            Ref eff = ref.resolve(actions::has);
            actions.get(eff.id())
                    .ifPresent(handler -> handler.accept(new MenuActionContext(
                            ctx, live, ClickKind.LEFT, ActionArguments.resolve(eff.args(), ctx.arguments()))));
        }
    }

    /**
     * Send the Bedrock viewer a native Cumulus SimpleForm standing in for the chest menu. The button list is built in
     * three runs. The spec's visible static items, then the current page's list entries (the list template stamped
     * per entry), then form-native Previous/Next buttons when the list spans more than one page: each button paired
     * with a {@link Runnable} handler at the same index. The {@code onSelect} callback simply dispatches by index, so
     * every button's own handler owns its threading: a static or entry tap runs that item's click actions on the
     * viewer's entity thread, a page button re-resolves the list off-thread and re-sends. The form send is on the
     * viewer's entity thread (the open path already hopped here); the tap response arrives off-thread, and each handler
     * makes its own hop. The resolved list cache is threaded in so a list-backed menu shows its entries.
     */
    private void sendBedrockForm(Player live, MenuSpec spec, MenuContext ctx, Map<String, List<?>> resolved) {
        PlayerRef viewer = ctx.viewer();
        List<BedrockButton> buttons = new ArrayList<>();
        List<Runnable> handlers = new ArrayList<>();
        appendStaticButtons(spec, ctx, viewer, buttons, handlers);
        int pageCount = appendListButtons(spec, ctx, viewer, resolved, buttons, handlers);
        appendPageButtons(spec, ctx, viewer, pageCount, buttons, handlers);
        bedrockScreen.sendSimpleForm(live, renderer.titleText(spec, ctx), null, buttons, index -> {
            if (index >= 0 && index < handlers.size()) {
                handlers.get(index).run();
            }
        });
    }

    /**
     * Send the Bedrock viewer the menu's explicit {@code bedrock {}} CustomForm: the form-native widgets
     * (label/input/dropdown/slider/toggle) the automatic SimpleForm degradation cannot express. The form's title,
     * intro content and every widget's display text and options are resolved through the renderer's plain-text path
     * (they may carry a {@code %token%}/{@code @key}, exactly like an item name), then handed to the screen. On submit,
     * each widget's value arrives keyed by its {@code name}, and {@code runOnSubmit} binds those as local placeholders
     * and runs the block's on-submit actions on the viewer's entity thread. Cumulus responds off-thread, so the hop
     * is explicit. Closing without submitting is a no-op: the viewer simply dismissed the form.
     */
    private void sendBedrockCustomForm(Player live, MenuSpec spec, MenuContext ctx) {
        BedrockFormSpec form = spec.bedrock().orElseThrow();
        PlayerRef viewer = ctx.viewer();
        String title = renderer.plainText(form.title(), ctx);
        String content = form.content() == null ? null : renderer.plainText(form.content(), ctx);
        List<BedrockWidget> widgets = resolveWidgets(form.widgets(), ctx);
        bedrockScreen.sendCustomForm(
                live,
                title,
                content,
                widgets,
                values -> scheduler.onEntity(viewer, () -> runOnSubmit(spec, ctx, values)),
                () -> {
                    // The viewer dismissed the form without submitting; there is nothing to run, they just closed it.
                });
    }

    /** Resolve each widget's display text (and a dropdown's options) against {@code ctx}, keeping its binding name and numeric bounds. */
    private List<BedrockWidget> resolveWidgets(List<BedrockWidget> widgets, MenuContext ctx) {
        List<BedrockWidget> resolved = new ArrayList<>(widgets.size());
        for (BedrockWidget widget : widgets) {
            resolved.add(resolveWidget(widget, ctx));
        }
        return resolved;
    }

    /** One widget with its display strings resolved to plain text; the {@code name} and numeric bounds pass through unchanged. */
    private BedrockWidget resolveWidget(BedrockWidget widget, MenuContext ctx) {
        return switch (widget) {
            case BedrockWidget.Label label -> new BedrockWidget.Label(renderer.plainText(label.text(), ctx));
            case BedrockWidget.Input input ->
                new BedrockWidget.Input(
                        input.name(),
                        renderer.plainText(input.label(), ctx),
                        renderer.plainText(input.placeholder(), ctx),
                        renderer.plainText(input.defaultText(), ctx));
            case BedrockWidget.Dropdown dropdown ->
                new BedrockWidget.Dropdown(
                        dropdown.name(),
                        renderer.plainText(dropdown.label(), ctx),
                        resolveOptions(dropdown.options(), ctx),
                        dropdown.defaultIndex());
            case BedrockWidget.Slider slider ->
                new BedrockWidget.Slider(
                        slider.name(),
                        renderer.plainText(slider.label(), ctx),
                        slider.min(),
                        slider.max(),
                        slider.step(),
                        slider.defaultValue());
            case BedrockWidget.Toggle toggle ->
                new BedrockWidget.Toggle(toggle.name(), renderer.plainText(toggle.label(), ctx), toggle.defaultValue());
        };
    }

    /** Resolve every dropdown option string to plain text against {@code ctx}. */
    private List<String> resolveOptions(List<String> options, MenuContext ctx) {
        List<String> resolved = new ArrayList<>(options.size());
        for (String option : options) {
            resolved.add(renderer.plainText(option, ctx));
        }
        return resolved;
    }

    /**
     * Append one button per visible actionable static item, each tapping into that item's own left-click actions. The
     * button's icon is sourced from the item's own resolved material spec (a material → a Bedrock texture path, a
     * {@code skull:} → an mc-heads avatar URL), so a form button carries the same face the chest icon would.
     */
    private void appendStaticButtons(
            MenuSpec spec, MenuContext ctx, PlayerRef viewer, List<BedrockButton> buttons, List<Runnable> handlers) {
        for (MenuItemSpec item : renderer.visibleStaticItemsInSlotOrder(spec, ctx)) {
            buttons.add(formButton(item, ctx, viewer));
            handlers.add(() -> scheduler.onEntity(viewer, () -> runFormActions(ctx, item)));
        }
    }

    /** A form button for {@code item} rendered against {@code ctx}: its label plus an icon sourced from its spec. */
    private BedrockButton formButton(MenuItemSpec item, MenuContext ctx, PlayerRef viewer) {
        BedrockImage image = BedrockIcons.forMaterialSpec(renderer.materialSpec(item, ctx), viewer.uuid());
        return new BedrockButton(renderer.buttonText(item, ctx), image);
    }

    /**
     * Append one button per list entry on the current page, labelling each with the list template stamped for that
     * entry and routing a tap through the template's click actions bound to that entry, the form stand-in for a chest
     * list cell's {@code RenderedSlot(template, entry)}. Only the first list-backed item is paged (a spec pairs one
     * scrollable list with its controls, mirroring the chest renderer's page-count rule); a static-only menu has none
     * and stays a single page. Returns the page count so the caller knows whether to add page-nav buttons.
     */
    private int appendListButtons(
            MenuSpec spec,
            MenuContext ctx,
            PlayerRef viewer,
            Map<String, List<?>> resolved,
            List<BedrockButton> buttons,
            List<Runnable> handlers) {
        Optional<MenuItemSpec> listItem = firstListItem(spec);
        if (listItem.isEmpty()) {
            return 1;
        }
        var listSpec = listItem.get().list().orElseThrow();
        List<?> entries = resolved.getOrDefault(listSpec.source().id(), List.of());
        MenuItemSpec template = listSpec.template();
        @SuppressWarnings("unchecked") // a list source's element type is opaque to the engine; entries flow as Object
        Pagination.Page<Object> page = Pagination.paginate(
                (List<Object>) entries, listItem.get().slots().slots(), ctx.page());
        for (Map.Entry<Integer, Object> placement : page.placements()) {
            MenuContext entryCtx = ctx.withEntry(placement.getValue());
            buttons.add(formButton(template, entryCtx, viewer));
            handlers.add(() -> scheduler.onEntity(viewer, () -> runFormActions(entryCtx, template)));
        }
        return page.pageCount();
    }

    /**
     * Append the form-native Previous/Next buttons a paged list needs: a Previous when the viewer is past page zero,
     * a Next when a further page exists, each re-sending the form one page over. These are text-only (no icon, a page
     * control needs none). A single-page menu (a static-only menu or a list that fits one page) adds neither, so its
     * form is byte-identical to before this slice.
     */
    private void appendPageButtons(
            MenuSpec spec,
            MenuContext ctx,
            PlayerRef viewer,
            int pageCount,
            List<BedrockButton> buttons,
            List<Runnable> handlers) {
        if (ctx.page() > 0) {
            buttons.add(new BedrockButton(renderer.plainMessage(viewer, GuiMessageKey.PAGE_PREVIOUS), null));
            handlers.add(() -> resendBedrockPage(viewer, spec, ctx, ctx.page() - 1));
        }
        if (ctx.page() + 1 < pageCount) {
            buttons.add(new BedrockButton(renderer.plainMessage(viewer, GuiMessageKey.PAGE_NEXT), null));
            handlers.add(() -> resendBedrockPage(viewer, spec, ctx, ctx.page() + 1));
        }
    }

    /** The first list-backed item in a spec, the one whose entries page as form buttons; empty for a static-only menu. */
    private static Optional<MenuItemSpec> firstListItem(MenuSpec spec) {
        for (MenuItemSpec item : spec.items().values()) {
            if (item.list().isPresent()) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Re-send the Bedrock form one page over: the target a Previous/Next form button runs. Because a list source may
     * read a database, the list is re-resolved off the tick thread for the new page, then the render hops back onto the
     * viewer's entity thread, the very async-resolve→entity-render discipline the initial open takes. The viewer may
     * have gone offline between the tap and the re-render, so the online player is re-fetched and a missing one skipped.
     */
    private void resendBedrockPage(PlayerRef viewer, MenuSpec spec, MenuContext baseCtx, int page) {
        MenuContext newCtx = baseCtx.withPage(Math.max(0, page));
        scheduler.async(() -> {
            ResolvedLists resolved = resolveLists(spec, newCtx);
            scheduler.onEntity(viewer, () -> {
                Player p = Bukkit.getPlayer(viewer.uuid());
                if (p != null) {
                    sendBedrockForm(p, spec, newCtx, resolved.rows());
                }
            });
        });
    }

    /**
     * Run the tapped item's left-click actions against {@code ctx}, on the viewer's entity thread the caller already
     * hopped onto. A tap is a plain click, so it runs the item's {@code actionsFor(LEFT)} chain, which already merges
     * the shared {@link ClickKind#ANY} block. Through the shared {@link #runActions} runner, so a form tap reaches
     * the identical handler a chest click would. Per-click requirements and deny routing are a later item; this runs
     * the actions only.
     */
    private void runFormActions(MenuContext ctx, MenuItemSpec item) {
        runActions(ctx, item.click().actionsFor(ClickKind.LEFT));
    }

    /**
     * Run the {@code bedrock {}} block's on-submit actions with the submitted widget values bound. Each value arrives
     * keyed by its widget {@code name}; they are layered over the menu's own {@code placeholders {}} block (the values
     * winning) and carried as the context's local placeholders, so a {@code %warpname%}/{@code %cost%} token in an
     * on-submit action's argument resolves to the submitted value through the local-placeholder channel, exactly the
     * way the click path expands an item-drag's {@code %drag_*%} tokens. Runs on the viewer's entity thread the caller
     * already hopped onto, through the same shared {@link #runActions} runner a form tap uses.
     */
    private void runOnSubmit(MenuSpec spec, MenuContext ctx, Map<String, String> values) {
        MenuContext submitCtx = ctx.withLocalPlaceholders(merge(spec.placeholders(), values));
        runActions(submitCtx, spec.bedrock().orElseThrow().onSubmit());
    }

    /** The menu's own local placeholders with the submitted values layered on top, the submitted value winning a clash. */
    private static Map<String, String> merge(Map<String, String> base, Map<String, String> overrides) {
        if (base.isEmpty()) {
            return overrides;
        }
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }

    /**
     * Run a list of action refs against {@code ctx} on the viewer's entity thread the caller already hopped onto,
     * through the very {@link ActionRegistry} the click listener resolves against (production hands both the same
     * {@code MenuBindings.actions()} instance). Each ref is split registry-aware, then its argument values have their
     * {@code %argument_<name>%} tokens and their menu-local {@code %name%} tokens expanded, the same two-channel
     * substitution the click path's dispatch applies, so a form tap or an on-submit action reaches the identical
     * handler with the identical arguments a chest click would. Skipped when the engine was wired without an action
     * registry (a list/spec-only test engine), matching {@link #runOpenActions}.
     */
    private void runActions(MenuContext ctx, List<Ref> refs) {
        ActionRegistry actions = openActionRegistry;
        if (actions == null) {
            return;
        }
        Player live = Bukkit.getPlayer(ctx.viewer().uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        for (Ref ref : refs) {
            Ref eff = ref.resolve(actions::has);
            Map<String, String> args = ActionArguments.resolveLocals(
                    ActionArguments.resolve(eff.args(), ctx.arguments()), ctx.localPlaceholders());
            actions.get(eff.id())
                    .ifPresent(handler -> handler.accept(new MenuActionContext(ctx, live, ClickKind.LEFT, args)));
        }
    }

    /** Redraw an open menu in place on its viewer's thread, but only if that window is still this holder's. */
    private void reRender(MenuHolder holder) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player p = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (p == null) {
                return;
            }
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
                return;
            }
            holder.clearClickMap();
            renderer.populate(
                    holder.getInventory(),
                    holder.spec(),
                    holder.ctx(),
                    holder::recordSlot,
                    holder.resolvedLists(),
                    false);
            if (holder.spec().bottomInventory()) {
                // Re-paint the bottom too, but do not re-snapshot. The viewer's real items were captured on open and
                // are held on the holder until close; populateBottom clears and redraws only the menu tiles.
                renderer.populateBottom(
                        p.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists());
            }
        });
    }

    /**
     * The open-time half of a bottom-inventory menu: snapshot the viewer's real 36 bottom slots onto the holder, then
     * paint the menu's bottom items into them. The snapshot is what the close restores (and what a death drops in
     * place of the menu tiles), so it is taken before {@code populateBottom} clears and repaints the canvas. Runs on
     * the viewer's own entity thread, where touching the live inventory is legal, and only for a menu whose spec
     * sets the flag; an ordinary menu never reaches here and never touches the player inventory.
     */
    private void paintBottom(MenuHolder holder, MenuSpec spec, MenuContext ctx, Player live) {
        holder.setBottomSnapshot(live.getInventory().getStorageContents());
        renderer.populateBottom(live.getInventory(), spec, ctx, holder::recordSlot, holder.resolvedLists());
    }

    /**
     * Close every menu this engine owns, cancelling its refresh task first so no timer survives the disable. The
     * online-roster sweep runs on the global region thread (the only thread on which the roster is coherent on
     * Folia), matching the scoreboard/tablist tear-down pattern. The click listener is uninstalled by the bootstrap
     * wiring, not here, so a closed window cannot be re-clicked.
     */
    public void shutdown() {
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryHolder open =
                        player.getOpenInventory().getTopInventory().getHolder();
                if (open instanceof MenuHolder holder) {
                    holder.cancelRefresh();
                    player.closeInventory();
                }
            }
        });
    }
}

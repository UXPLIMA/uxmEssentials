package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the per-warp welcome-messages list editor with the menu engine and opens it. A three-row list panel for
 * one warp reached from the editor's welcome button: one entry per stored welcome message across the top two rows,
 * an add button, a back button to the editor, and a clear-all button on the bottom row. A left click on an entry
 * edits its text through the input seam, a right click deletes it, and a shift click cycles its delivery type; the
 * add button writes a new message through the input seam; the clear button drops every message; the back button
 * reopens the warp editor. Each mutation saves the edited warp through the shared {@link EditableWarp} loader and
 * re-opens this list with the new subject so the operator sees the result.
 *
 * <p>The edited warp is handed in as the menu subject, its name, owner, and a welcome-message snapshot read off the
 * viewer's entity thread at open, so the entry list (the {@code warps:welcome} list source) and every entry line
 * fill from the {@code warp_welcome_*} placeholders without the renderer touching a port. The panel holds no new
 * domain logic: it replays the old bespoke window's handlers verbatim through the engine, on the warp-browse list
 * pattern. The editor is injected after this view to break their re-open cycle. Every visible string resolves from
 * the warps catalog.
 */
@NullMarked
public final class WarpWelcomeMessagesView {

    /** The engine spec id this list registers and opens under. */
    public static final String SPEC_ID = "warp-welcome";

    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-welcome.conf";
    private static final int ROWS = 3;

    /** The entry grid's capacity: the top two rows of the three-row chest, as the old fixed view drew. */
    private static final int ENTRY_LIMIT = 18;

    private final Menus menus;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final WarpEditorView editorView;
    private final EditableWarpLoader loader;

    private WarpWelcomeMessagesView(
            Menus menus,
            Scheduler scheduler,
            TextInput textInput,
            WarpEditorView editorView,
            EditableWarpLoader loader) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * Build the welcome list over the warps wiring's collaborators. The editable-warp loader is built here from the
     * server-warp repository and the editor view (the same pair the editor loads through), so the warps wiring needs
     * only the public collaborators it already holds. Mirrors {@code WarpSoundMenu.create}.
     */
    public static WarpWelcomeMessagesView create(
            Menus menus,
            Scheduler scheduler,
            TextInput textInput,
            WarpRepository repository,
            WarpEditorView editorView) {
        EditableWarpLoader loader = new EditableWarpLoader(repository, editorView);
        return new WarpWelcomeMessagesView(menus, scheduler, textInput, editorView, loader);
    }

    /** Register the welcome list source, its entry placeholders, the action buttons, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("warps:welcome", ctx -> subject(ctx).entries());
        bindings.placeholder(
                "warp_welcome_material",
                ctx -> materialFor(entry(ctx).message().type()).name());
        bindings.placeholder(
                "warp_welcome_index", ctx -> Integer.toString(entry(ctx).index() + 1));
        bindings.placeholder("warp_welcome_text", ctx -> entry(ctx).message().message());
        bindings.placeholder("warp_welcome_type", ctx -> entry(ctx).message().type());
        bindings.action("warps:welcome-edit", this::editEntry);
        bindings.action("warps:welcome-remove", this::removeEntry);
        bindings.action("warps:welcome-cycle", this::cycleEntry);
        bindings.action("warps:welcome-add", this::add);
        bindings.action("warps:welcome-clear", this::clear);
        bindings.action("warps:welcome-back", this::back);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /**
     * Open the welcome list for the warp {@code warpName} ({@code warpOwner} null for a server warp). The messages
     * are read on the viewer's entity thread into a snapshot handed to the engine as the subject, so the engine
     * renders off that snapshot without a port read of its own. A warp that no longer exists closes the menu.
     */
    public void open(Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        scheduler.onEntity(viewer, () -> {
            EditableWarp warp = loader.load(warpName, warpOwner);
            if (warp == null) {
                player.closeInventory();
                return;
            }
            menus.open(viewer, SPEC_ID, snapshot(warpName, warpOwner, warp.welcomeMessages()));
        });
    }

    /** Build the menu subject: the warp's name and owner plus its welcome messages capped to the entry grid. */
    private static WelcomeList snapshot(String warpName, @Nullable PlayerRef warpOwner, List<WelcomeMessage> messages) {
        List<IndexedWelcome> entries = new ArrayList<>();
        for (int i = 0; i < Math.min(messages.size(), ENTRY_LIMIT); i++) {
            entries.add(new IndexedWelcome(i, messages.get(i)));
        }
        return new WelcomeList(warpName, warpOwner, List.copyOf(entries));
    }

    /** Left-click an entry: capture replacement text through the input seam; cancel re-opens the list unchanged. */
    private void editEntry(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        IndexedWelcome target = entry(ctx);
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.welcome", WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT),
                input -> applyEdit(player, viewer, list, target.index(), input),
                () -> open(player, viewer, list.warpName(), list.warpOwner()));
    }

    /** Replace the entry's text, keeping its delivery type, then save and re-open. Package-private for the test. */
    void applyEdit(Player player, PlayerRef viewer, WelcomeList list, int index, String input) {
        List<WelcomeMessage> messages = currentMessages(list);
        if (index >= 0 && index < messages.size()) {
            WelcomeMessage existing = messages.get(index);
            messages.set(index, new WelcomeMessage(input, existing.type()));
            saveWelcome(list, messages);
        }
        open(player, viewer, list.warpName(), list.warpOwner());
    }

    /** Right-click an entry: drop it, then save and re-open. */
    private void removeEntry(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        int index = entry(ctx).index();
        List<WelcomeMessage> messages = currentMessages(list);
        if (index >= 0 && index < messages.size()) {
            messages.remove(index);
            saveWelcome(list, messages);
        }
        reopen(ctx, list);
    }

    /** Shift-click an entry: cycle its delivery type, keeping its text, then save and re-open. */
    private void cycleEntry(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        int index = entry(ctx).index();
        List<WelcomeMessage> messages = currentMessages(list);
        if (index >= 0 && index < messages.size()) {
            WelcomeMessage existing = messages.get(index);
            messages.set(index, new WelcomeMessage(existing.message(), nextType(existing.type())));
            saveWelcome(list, messages);
        }
        reopen(ctx, list);
    }

    /** Click add: capture a new message through the input seam; cancel re-opens the list unchanged. */
    private void add(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.welcome", WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT),
                input -> applyAdd(player, viewer, list, input),
                () -> open(player, viewer, list.warpName(), list.warpOwner()));
    }

    /** Append a new CHAT-typed message, then save and re-open. Package-private for the golden test. */
    void applyAdd(Player player, PlayerRef viewer, WelcomeList list, String input) {
        List<WelcomeMessage> messages = currentMessages(list);
        messages.add(new WelcomeMessage(input, "CHAT"));
        saveWelcome(list, messages);
        open(player, viewer, list.warpName(), list.warpOwner());
    }

    /** Click clear: drop every message, then save and re-open. */
    private void clear(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        saveWelcome(list, List.of());
        reopen(ctx, list);
    }

    /** Click back: reopen the warp editor for this warp. */
    private void back(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        editorView.open(ctx.player(), ctx.viewer(), list.warpName(), list.warpOwner());
    }

    /** The warp's live welcome messages as a mutable list, or an empty list when the warp is gone. */
    private List<WelcomeMessage> currentMessages(WelcomeList list) {
        EditableWarp warp = loader.load(list.warpName(), list.warpOwner());
        return warp == null ? new ArrayList<>() : new ArrayList<>(warp.welcomeMessages());
    }

    /** Save the warp's welcome messages through the shared loader; a stale warp is a no-op. */
    private void saveWelcome(WelcomeList list, List<WelcomeMessage> messages) {
        EditableWarp warp = loader.load(list.warpName(), list.warpOwner());
        if (warp != null) {
            warp.setWelcomeMessages(messages);
        }
    }

    /** Re-read the warp and re-open the list with a fresh subject so the operator sees the result. */
    private void reopen(MenuActionContext ctx, WelcomeList list) {
        open(ctx.player(), ctx.viewer(), list.warpName(), list.warpOwner());
    }

    private static Material materialFor(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CHAT" -> Material.PAPER;
            case "ACTION_BAR" -> Material.REPEATER;
            case "TITLE" -> Material.GOLDEN_HELMET;
            case "SUBTITLE" -> Material.IRON_HELMET;
            case "BOSS_BAR" -> Material.DRAGON_EGG;
            default -> Material.WRITABLE_BOOK;
        };
    }

    private static String nextType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CHAT" -> "ACTION_BAR";
            case "ACTION_BAR" -> "TITLE";
            case "TITLE" -> "SUBTITLE";
            case "SUBTITLE" -> "BOSS_BAR";
            default -> "CHAT";
        };
    }

    private WelcomeList subject(MenuContext ctx) {
        return ctx.subject(WelcomeList.class);
    }

    private WelcomeList subject(MenuActionContext ctx) {
        return ctx.subject(WelcomeList.class);
    }

    private IndexedWelcome entry(MenuContext ctx) {
        return ctx.entry(IndexedWelcome.class);
    }

    private IndexedWelcome entry(MenuActionContext ctx) {
        return ctx.entry(IndexedWelcome.class);
    }

    /**
     * The subject of an open welcome list: the warp's name, its owner ({@code null} for a server warp), and its
     * welcome messages capped to the entry grid, paired with their slot index. The list source and the entry
     * placeholders read this, so the menu carries no port read of its own.
     *
     * @param warpName the warp's name
     * @param warpOwner the player warp's owner, or {@code null} for a server warp
     * @param entries the indexed welcome messages, in order
     */
    public record WelcomeList(String warpName, @Nullable PlayerRef warpOwner, List<IndexedWelcome> entries) {

        public WelcomeList {
            Objects.requireNonNull(warpName, "warpName");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /**
     * One welcome message paired with its slot index, so the entry's name line can show {@code Message #N} and an
     * action knows which message the click targeted.
     *
     * @param index the entry's zero-based position in the list
     * @param message the welcome message
     */
    public record IndexedWelcome(int index, WelcomeMessage message) {

        public IndexedWelcome {
            Objects.requireNonNull(message, "message");
        }
    }
}

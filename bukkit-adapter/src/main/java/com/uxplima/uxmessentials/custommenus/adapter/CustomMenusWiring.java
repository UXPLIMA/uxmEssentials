package com.uxplima.uxmessentials.custommenus.adapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.GuiPlusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.GuiPlusConverter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConverter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConverter;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuCommand;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuOpenCommand;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuClickActionsView;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuCommandEditorView;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuEditorView;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuGridView;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuItemEditorView;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuPropertiesView;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuRefListEditor;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.gui.MenuRequirementsView;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuEditLockListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuOpenerInteractListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuOpenerJoinListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuSwapListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerItems;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the custommenus context's adapters over the shared menu engine: a {@link CustomMenuLoader} that reads
 * the operator's {@code menus/*.conf} into {@link Menus}, run once on enable, the {@code /menu} command, one
 * {@link MenuOpenCommand} per menu that declared its own {@code command {}} block ({@code /shop}, {@code /store}),
 * and the opener-item listeners loaded from {@code menus/openers.conf}. This is the one place the context is wired
 * nothing else news up its classes.
 *
 * <p>The loaded menu names are held in an {@link AtomicReference} the {@code /menu list} and tab-completion read and
 * {@code /menu reload} swaps atomically: a reload re-runs the loader (re-registering specs into the engine,
 * overwriting the previous ones) and publishes the fresh name set in one assignment, so a reader sees either the old
 * or the new list, never a half-applied one. The parsed openers are held in a second {@link AtomicReference} swapped
 * the same way, so a {@code /menu reload} also re-reads {@code openers.conf}. The join listener reads the fresh list
 * on the next join, the interact listener reads the fresh menu-name set, and the swap listener reads the fresh
 * off-hand-swap menu (or none) on the next swap. (Open commands are the one exception:
 * Brigadier only registers at startup, so a reload cannot add or drop a {@code /shop}-style command: that needs a
 * restart, as in DeluxeMenus.) A disabled module never reaches this wiring, so it loads no menus and registers
 * nothing.
 */
@NullMarked
public final class CustomMenusWiring {

    private CustomMenusWiring() {}

    public static Wired wire(
            Plugin plugin,
            Menus menus,
            MenuBindings bindings,
            Path dataFolder,
            Logger log,
            Scheduler scheduler,
            Messages messages,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            ManagementGuiRegistry guiRegistry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiRegistry, "guiRegistry");

        // The custom-placeholder fallback registers here, after every built-in / data / stat / papi family the main
        // wiring registered, so a reserved-prefix custom name is still claimed by its prefix fallback first and a
        // custom name can never shadow a built-in exact handler. The loader owns it and swaps its definitions on each
        // load pass, so a /menu reload re-reads placeholders.conf alongside the menus and openers.
        CustomPlaceholders customPlaceholders = new CustomPlaceholders(bindings);
        CustomMenuLoader loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, log, customPlaceholders);
        OpenerLoader openerLoader = new OpenerLoader(log);
        Path menusDir = dataFolder.resolve("menus");
        Path openersFile = menusDir.resolve("openers.conf");

        CustomMenuLoader.LoadResult first = loader.loadFrom(menusDir);
        OpenerLoader.OpenerConfig firstOpeners = openerLoader.loadConfig(openersFile, Set.copyOf(first.loadedNames()));
        AtomicReference<List<String>> names = new AtomicReference<>(first.loadedNames());
        // The per-menu open-command blocks, kept beside the names so /menu save can re-emit a menu's command {} block
        // byte-faithfully. A full reload swaps this the same way it swaps the names; a single-file reload does not
        // touch it (Brigadier can't add or drop a /shop-style command after startup anyway).
        AtomicReference<Map<String, OpenCommandSpec>> openCommands = new AtomicReference<>(first.openCommands());
        AtomicReference<List<OpenerSpec>> openers = new AtomicReference<>(firstOpeners.openers());
        // The off-hand-swap menu rides its own reference, swapped on reload beside the openers, so the swap listener
        // reads the fresh menu (or none) on the next swap without being re-registered.
        AtomicReference<Optional<String>> swapMenu = new AtomicReference<>(firstOpeners.swapMenu());

        Supplier<List<String>> nameSupplier = names::get;
        Supplier<List<OpenerSpec>> openerSupplier = openers::get;
        Supplier<Optional<String>> swapMenuSupplier = swapMenu::get;
        Supplier<CustomMenuLoader.LoadResult> reload = () -> {
            CustomMenuLoader.LoadResult result = loader.loadFrom(menusDir);
            OpenerLoader.OpenerConfig reloaded = openerLoader.loadConfig(openersFile, Set.copyOf(result.loadedNames()));
            names.set(result.loadedNames());
            openCommands.set(result.openCommands());
            openers.set(reloaded.openers());
            swapMenu.set(reloaded.swapMenu());
            return result;
        };
        // Per-menu reload for /menu reload <menu>: re-parse the single menus/<name>.conf and re-register just that
        // spec (leaving every other loaded menu in place). A newly-loaded name is folded into the shared names
        // reference so /menu list and tab-completion pick it up; this does not re-read openers.conf, which the
        // reload-all supplier above owns.
        Function<String, CustomMenuLoader.SingleLoad> reloadOne = name -> {
            CustomMenuLoader.SingleLoad result = loader.loadSingle(menusDir, name);
            if (result.found() && result.loaded() > 0) {
                names.updateAndGet(current -> withName(current, name));
            }
            return result;
        };
        // The DeluxeMenus, zMenu, OGUI and GUIPlus converters behind /menu convert <deluxemenus|zmenu|ogui|guiplus>
        // <path>. Each writes into the same menus/ directory the loader reads, so a converted spec is picked up on the
        // next /menu reload (none of the converters reloads itself).
        DeluxeMenusConvertService deluxeMenusConvert =
                new DeluxeMenusConvertService(menusDir, new DeluxeMenusConverter(), log);
        ZMenuConvertService zMenuConvert = new ZMenuConvertService(menusDir, new ZMenuConverter(), log);
        OguiConvertService oguiConvert = new OguiConvertService(menusDir, new OguiConverter(), log);
        GuiPlusConvertService guiPlusConvert = new GuiPlusConvertService(menusDir, new GuiPlusConverter(), log);
        // The save path behind /menu save: the writer inverts the loader, the persistence service validates against the
        // same bindings before writing, and openCommandFor hands the command {} block back so a menu keeps its opener.
        MenuSpecPersistence persistence = new MenuSpecPersistence(new MenuSpecWriter(), bindings, log);
        Function<String, Optional<OpenCommandSpec>> openCommandFor = name -> {
            Map<String, OpenCommandSpec> current = openCommands.get();
            return current == null ? Optional.empty() : Optional.ofNullable(current.get(name));
        };
        // The in-game editor (/menu editor, the /uxmess gui hub entry): the file-level CRUD service composes the
        // save path with a "forget" hook that unregisters a deleted/renamed-away menu from the engine and drops it
        // from the shared name / open-command references, so a GUI delete leaves neither a registered spec nor a
        // dangling name. The picker/overview view is engine-based (EntityListView + EntityEditorView + TextInput).
        java.util.function.Consumer<String> forget = name -> {
            menus.unregisterSpec(name);
            names.updateAndGet(current -> withoutName(current, name));
            openCommands.updateAndGet(current -> withoutCommand(current, name));
        };
        MenuEditorService editorService = new MenuEditorService(
                menusDir, persistence, menus::registeredSpec, openCommandFor, reloadOne, forget, log);
        // One-editor-per-menu lock: the grid and property editors take it on open (refusing a second viewer with a
        // "being edited by …" line), the picker release it on returning to the browser, and the quit listener release
        // it on disconnect, so a menu is never rewritten while another operator has it open.
        MenuEditLocks editLocks = new MenuEditLocks();
        // The slot-grid canvas (the overview's "Slot editor" button) and the overview reference each other, the
        // overview opens the grid, the grid's Back returns to the overview, so the grid is built first with a Back
        // hook that reads the overview through a holder set once the overview exists, breaking the construction cycle.
        // The item property editor and the grid reference each other the same way: the grid opens the item editor for a
        // clicked cell, the item editor's Back reopens the grid, so the item editor is built first with a Back hook
        // that
        // reads the grid through a holder set once the grid exists.
        AtomicReference<MenuEditorView> editorViewRef = new AtomicReference<>();
        AtomicReference<MenuGridView> gridViewRef = new AtomicReference<>();
        AtomicReference<MenuPropertiesView> propertiesViewRef = new AtomicReference<>();
        // The click-action and view-requirement builders (the item editor's two new rows). The requirement editor and
        // the item editor reference each other. The item editor opens the requirement editor, the requirement editor's
        // back reopens the item editor, so the requirement editor is built first with a back hook that reads the item
        // editor through a holder set once it exists, breaking the construction cycle (mirroring the grid↔overview
        // one).
        // Both builders read the id catalog from the live bindings' schema export, so a late-registered feature is
        // offered. The arg-entry input key is stable so an operator can flip it between anvil and chat like any other.
        AtomicReference<MenuItemEditorView> itemEditorViewRef = new AtomicReference<>();
        MenuRefListEditor refListEditor =
                new MenuRefListEditor(guiText, scheduler, textInput, "menu.action-editor.arg");
        MenuClickActionsView clickActionsView = new MenuClickActionsView(guiText, refListEditor, bindings::schema);
        MenuRequirementsView requirementsView = new MenuRequirementsView(
                menus,
                guiText,
                scheduler,
                guiLayouts,
                refListEditor,
                bindings::schema,
                (player, viewer) -> Objects.requireNonNull(itemEditorViewRef.get(), "itemEditorView")
                        .reopen(player, viewer));
        MenuItemEditorView itemEditorView = new MenuItemEditorView(
                menus,
                guiText,
                scheduler,
                messages,
                textInput,
                guiLayouts,
                (player, viewer) ->
                        Objects.requireNonNull(gridViewRef.get(), "gridView").reopenGrid(viewer),
                clickActionsView,
                requirementsView);
        itemEditorViewRef.set(itemEditorView);
        // The menu-level property editor (the overview's "Edit properties" row and the create-from-scratch landing)
        // and its open-command sub-editor. The command editor and the property editor reference each other, the
        // property editor opens the command editor, the command editor's back reopens the property editor, so the
        // command editor is built first with a back hook that reads the property editor through a holder set once it
        // exists. A property-editor Save writes the session's own command block, so rememberCommand mirrors it into the
        // live open-command reference: a later grid save (which re-attaches from that reference) then keeps the edit
        // rather than reverting it.
        MenuCommandEditorView commandEditorView = new MenuCommandEditorView(
                menus,
                guiText,
                scheduler,
                messages,
                textInput,
                guiLayouts,
                (player, viewer) -> Objects.requireNonNull(propertiesViewRef.get(), "propertiesView")
                        .reopen(player, viewer));
        java.util.function.BiConsumer<String, Optional<OpenCommandSpec>> rememberCommand =
                (name, command) -> openCommands.updateAndGet(current -> withCommand(current, name, command));
        MenuPropertiesView propertiesView = new MenuPropertiesView(
                menus,
                guiText,
                scheduler,
                messages,
                editorService,
                editLocks,
                menus::registeredSpec,
                openCommandFor,
                rememberCommand,
                guiLayouts,
                bindings::schema,
                refListEditor,
                commandEditorView,
                textInput,
                (player, id) -> Objects.requireNonNull(gridViewRef.get(), "gridView")
                        .open(player, BukkitRefs.toRef(player), id),
                (player, id) -> Objects.requireNonNull(editorViewRef.get(), "editorView")
                        .overview()
                        .open(player, BukkitRefs.toRef(player), id),
                (player, viewer) -> Objects.requireNonNull(editorViewRef.get(), "editorView")
                        .open(player, viewer));
        propertiesViewRef.set(propertiesView);
        MenuGridView gridView = new MenuGridView(
                menus,
                guiText,
                scheduler,
                editorService,
                editLocks,
                menus::registeredSpec,
                (player, id) -> Objects.requireNonNull(editorViewRef.get(), "editorView")
                        .overview()
                        .open(player, BukkitRefs.toRef(player), id),
                itemEditorView);
        gridViewRef.set(gridView);
        MenuEditorView editorView = new MenuEditorView(
                menus,
                guiText,
                scheduler,
                messages,
                editorService,
                editLocks,
                nameSupplier,
                menus::registeredSpec,
                guiLayouts,
                textInput,
                (player, id) -> gridView.open(player, BukkitRefs.toRef(player), id),
                (player, id) -> propertiesView.open(player, BukkitRefs.toRef(player), id));
        editorViewRef.set(editorView);
        guiRegistry.register(new ManagementGuiEntry(
                "custommenus",
                CustomMenusMessageKey.MENU_EDITOR_TITLE,
                org.bukkit.Material.CHEST,
                "uxmessentials.menu.editor",
                editorView::open));
        MenuCommand command = new MenuCommand(
                menus,
                nameSupplier,
                reload,
                reloadOne,
                deluxeMenusConvert,
                zMenuConvert,
                oguiConvert,
                guiPlusConvert,
                persistence,
                openCommandFor,
                player -> editorView.open(player, BukkitRefs.toRef(player)),
                (player, slot) -> gridView.captureHeldItem(player, BukkitRefs.toRef(player), slot),
                menusDir,
                scheduler,
                messages);

        // The open commands a menu declares in its `command {}` block are built once here, from this first load,
        // and handed back for the bootstrap to register at the LifecycleEvents.COMMANDS event. Brigadier only
        // accepts command registrations at that startup event, so, as in DeluxeMenus, a later `/menu reload`
        // refreshes menu CONTENT (specs re-parsed, /menu open sees the new set) but does NOT add, rename, or drop
        // open commands for new or edited menus: that needs a server restart. We deliberately do not try to
        // re-register Brigadier nodes on reload.
        List<CommandRegistration> commands = new ArrayList<>();
        commands.add(command);
        first.openCommands()
                .forEach((menuId, spec) -> commands.add(new MenuOpenCommand(menus, menuId, spec, messages)));

        OpenerItems openerItems = new OpenerItems(plugin);
        List<Listener> listeners = List.of(
                new MenuOpenerInteractListener(menus, nameSupplier, openerItems),
                new MenuOpenerJoinListener(openerSupplier, openerItems),
                new MenuSwapListener(menus, swapMenuSupplier, nameSupplier),
                new MenuEditLockListener(editLocks));
        return new Wired(commands, nameSupplier, listeners);
    }

    /** {@code current} with {@code name} appended when absent, as a fresh immutable list; unchanged when already present. */
    private static List<String> withName(List<String> current, String name) {
        if (current.contains(name)) {
            return current;
        }
        List<String> next = new ArrayList<>(current);
        next.add(name);
        return List.copyOf(next);
    }

    /** {@code current} with {@code name} removed, as a fresh immutable list; unchanged when it was absent. */
    private static List<String> withoutName(List<String> current, String name) {
        if (!current.contains(name)) {
            return current;
        }
        List<String> next = new ArrayList<>(current);
        next.remove(name);
        return List.copyOf(next);
    }

    /**
     * {@code current} with {@code name} bound to {@code command} (or dropped when it is empty), as a fresh immutable
     * map. How a property-editor Save that added, changed, or cleared a menu's {@code command {}} block keeps the live
     * open-command reference in step, so a subsequent grid save re-attaches the edited block rather than the stale one.
     */
    private static Map<String, OpenCommandSpec> withCommand(
            Map<String, OpenCommandSpec> current, String name, Optional<OpenCommandSpec> command) {
        Map<String, OpenCommandSpec> next = new java.util.LinkedHashMap<>(current == null ? Map.of() : current);
        command.ifPresentOrElse(spec -> next.put(name, spec), () -> next.remove(name));
        return Map.copyOf(next);
    }

    /** {@code current} with the {@code name} entry dropped, as a fresh immutable map; unchanged when it was absent. */
    private static Map<String, OpenCommandSpec> withoutCommand(Map<String, OpenCommandSpec> current, String name) {
        if (!current.containsKey(name)) {
            return current;
        }
        Map<String, OpenCommandSpec> next = new java.util.LinkedHashMap<>(current);
        next.remove(name);
        return Map.copyOf(next);
    }

    /**
     * The wired custommenus adapters handed back to bootstrap: the {@code /menu} command (plus any open commands),
     * the loaded-menu-names supplier (so a later consumer, e.g. a management hub entry, can read the current set),
     * and the opener-item and off-hand-swap listeners. There is no stop hook: the loader registers specs into the
     * shared engine, which the bootstrap tears down centrally on disable.
     */
    public record Wired(
            List<CommandRegistration> commands, Supplier<List<String>> menuNames, List<Listener> listeners) {
        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(menuNames, "menuNames");
            listeners = List.copyOf(listeners);
        }
    }
}

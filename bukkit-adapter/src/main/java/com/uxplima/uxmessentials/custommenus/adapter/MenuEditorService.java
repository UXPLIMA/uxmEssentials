package com.uxplima.uxmessentials.custommenus.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The file-level CRUD the menu editor drives over {@code menus/*.conf}: create a blank menu, duplicate one, rename one,
 * delete one. It composes the editor's existing spec service, the {@link MenuSpecPersistence} save (validate → write)
 *. With the loader's single-file reload and a small "forget" hook that drops a menu from the running engine, so a GUI
 * edit lands on disk and in the engine in one call.
 *
 * <p>Bukkit-free by construction: it works with the pure spec model, the persistence service, and {@code java.nio}, and
 * reaches the engine only through the injected {@link Function}s (read a registered spec, single-file reload, forget an
 * id), never a Bukkit type, so it runs off the tick thread and is exercised by plain JUnit. The caller schedules it
 * off-tick (its file writes and reloads must not sit on the region thread) and turns its typed {@link EditOutcome} into
 * an operator message. It never throws for an expected failure: an unsafe or taken name, a missing source, an
 * unwritable path all come back as an outcome, not a stack trace.
 *
 * <p>Name safety is enforced up front. A new name must be a plain filename token ({@code a-z A-Z 0-9 _ -}) so it can
 * never traverse the directory, must not collide with a reserved non-menu file ({@code openers} / {@code patterns} /
 * {@code placeholders}) or the bundled starter {@code example}, and must not already name a menu, a create or a
 * rename never silently overwrites an operator's menu.
 */
@NullMarked
public final class MenuEditorService {

    /** A safe menu id: one or more filename-safe characters, so it can neither traverse nor carry an extension. */
    private static final Pattern SAFE_NAME = Pattern.compile("[a-zA-Z0-9_-]+");

    /** The reserved file stems the loader treats as non-menu configs, plus the bundled starter menu. */
    private static final Set<String> RESERVED_NAMES = Set.of("openers", "patterns", "placeholders", "example");

    /** The rows a freshly created blank menu opens with, a small chest an operator then lays out. */
    private static final int BLANK_ROWS = 3;

    private final Path menusDir;
    private final MenuSpecPersistence persistence;
    private final Function<String, Optional<MenuSpec>> registeredSpec;
    private final Function<String, Optional<OpenCommandSpec>> openCommandFor;
    private final Function<String, CustomMenuLoader.SingleLoad> reloadOne;
    private final Consumer<String> forget;
    private final Logger log;

    public MenuEditorService(
            Path menusDir,
            MenuSpecPersistence persistence,
            Function<String, Optional<MenuSpec>> registeredSpec,
            Function<String, Optional<OpenCommandSpec>> openCommandFor,
            Function<String, CustomMenuLoader.SingleLoad> reloadOne,
            Consumer<String> forget,
            Logger log) {
        this.menusDir = Objects.requireNonNull(menusDir, "menusDir");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.registeredSpec = Objects.requireNonNull(registeredSpec, "registeredSpec");
        this.openCommandFor = Objects.requireNonNull(openCommandFor, "openCommandFor");
        this.reloadOne = Objects.requireNonNull(reloadOne, "reloadOne");
        this.forget = Objects.requireNonNull(forget, "forget");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Create a blank menu named {@code rawName}: a minimal valid spec (a rows-only chest, no items) written to
     * {@code menus/<name>.conf} and loaded into the engine. Rejected before any write when the name is unsafe,
     * reserved, or already in use.
     */
    public EditOutcome createBlank(String rawName) {
        String name = rawName.strip();
        EditOutcome invalid = validateNewName(name);
        if (invalid != null) {
            return invalid;
        }
        return persist(name, blankSpec(name), null, EditOutcome.CREATED);
    }

    /**
     * Copy the registered menu {@code from} to a new menu {@code to}: the source spec written under the new name and
     * loaded. The copy carries no {@code command {}} block (a duplicate is a fresh menu that gets its own opener) so
     * two menus never claim the same open command. Rejected when the new name is unsafe, reserved, or taken, or when
     * the source is not a loaded menu.
     */
    public EditOutcome duplicate(String from, String rawTo) {
        String to = rawTo.strip();
        EditOutcome invalid = validateNewName(to);
        if (invalid != null) {
            return invalid;
        }
        Optional<MenuSpec> source = registeredSpec.apply(from);
        if (source.isEmpty()) {
            return EditOutcome.SOURCE_MISSING;
        }
        return persist(to, source.get(), null, EditOutcome.DUPLICATED);
    }

    /**
     * Rename the registered menu {@code from} to {@code to}: its spec (and its {@code command {}} block, so the menu
     * keeps its opener) written under the new name, then the old file removed and its id forgotten by the engine.
     * Rejected when the new name is unsafe, reserved, or taken, or when the source is not a loaded menu. A write that
     * validates but fails to land leaves the old menu untouched.
     */
    public EditOutcome rename(String from, String rawTo) {
        String to = rawTo.strip();
        EditOutcome invalid = validateNewName(to);
        if (invalid != null) {
            return invalid;
        }
        Optional<MenuSpec> source = registeredSpec.apply(from);
        if (source.isEmpty()) {
            return EditOutcome.SOURCE_MISSING;
        }
        OpenCommandSpec command = openCommandFor.apply(from).orElse(null);
        EditOutcome written = persist(to, source.get(), command, EditOutcome.RENAMED);
        if (written == EditOutcome.RENAMED) {
            removeOld(from);
        }
        return written;
    }

    /**
     * Delete the menu {@code name}: its {@code menus/<name>.conf} file removed and its id forgotten by the engine, so
     * it is no longer registered or openable. Reports {@link EditOutcome#SOURCE_MISSING} when there is no such menu on
     * disk or in the engine.
     */
    public EditOutcome delete(String name) {
        Objects.requireNonNull(name, "name");
        if (registeredSpec.apply(name).isEmpty() && !Files.isRegularFile(fileFor(name))) {
            return EditOutcome.SOURCE_MISSING;
        }
        MenuSpecPersistence.DeleteStatus status = persistence.delete(menusDir, name);
        if (status == MenuSpecPersistence.DeleteStatus.IO_ERROR) {
            return EditOutcome.IO_ERROR;
        }
        forget.accept(name);
        return EditOutcome.DELETED;
    }

    /**
     * Re-serialise the registered menu {@code name} back to its file and reload it. The editor's "Save" over the
     * P0 write path. The spec (and its {@code command {}} block) round-trips through a {@link MenuEditSession} so the
     * save exercises the same edit model the later phases mutate. Reports {@link EditOutcome#SOURCE_MISSING} for an
     * unregistered menu.
     */
    public EditOutcome save(String name) {
        Objects.requireNonNull(name, "name");
        Optional<MenuSpec> source = registeredSpec.apply(name);
        if (source.isEmpty()) {
            return EditOutcome.SOURCE_MISSING;
        }
        OpenCommandSpec command = openCommandFor.apply(name).orElse(null);
        MenuEditSession session = MenuEditSession.from(source.get(), command);
        return persist(name, session.toSpec(), session.command().orElse(null), EditOutcome.SAVED);
    }

    /**
     * Save the edited {@code session} back to menu {@code name}'s file and reload it: the slot-grid editor's "Save".
     * Unlike {@link #save(String)}, which re-serialises the registered spec unchanged, this writes the caller's mutated
     * {@link MenuEditSession}, so a place / move / clear made in the grid lands on disk. The menu's {@code command {}}
     * block is re-attached from the live open-commands, so a grid save never drops a menu's opener even though the grid
     * session carries only the visual model. Reports {@link EditOutcome#INVALID_REFS} / {@link EditOutcome#IO_ERROR}
     * the same way every other write does.
     */
    public EditOutcome saveSession(String name, MenuEditSession session) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(session, "session");
        OpenCommandSpec command = openCommandFor.apply(name).orElse(null);
        return persist(name, session.toSpec(), command, EditOutcome.SAVED);
    }

    /**
     * Save the edited {@code session} back to menu {@code name}'s file with the caller-supplied {@code command} block
     * the menu-property editor's "Save". Unlike {@link #saveSession(String, MenuEditSession)}, which re-attaches the
     * live open command and so is safe for the grid (whose session never carries one), this writes the exact command
     * the property editor edited, so adding a {@code command {}} block to a menu that had none, changing it, or clearing
     * it all land on disk. Reports {@link EditOutcome#INVALID_REFS} / {@link EditOutcome#IO_ERROR} like every write.
     */
    public EditOutcome saveSession(String name, MenuEditSession session, @Nullable OpenCommandSpec command) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(session, "session");
        return persist(name, session.toSpec(), command, EditOutcome.SAVED);
    }

    /** Validate a spec, write it, and, on a clean write, reload it; maps the persistence status to an outcome. */
    private EditOutcome persist(String name, MenuSpec spec, @Nullable OpenCommandSpec command, EditOutcome success) {
        MenuSpecPersistence.SaveResult result = persistence.save(menusDir, name, spec, command);
        return switch (result.status()) {
            case SAVED -> {
                var unused = reloadOne.apply(name);
                yield success;
            }
            case INVALID_REFS -> EditOutcome.INVALID_REFS;
            case IO_ERROR -> EditOutcome.IO_ERROR;
        };
    }

    /** Remove the renamed-away file and drop its id from the engine; a delete failure is logged, the rename still holds. */
    private void removeOld(String from) {
        MenuSpecPersistence.DeleteStatus status = persistence.delete(menusDir, from);
        if (status == MenuSpecPersistence.DeleteStatus.IO_ERROR) {
            log.warn("renamed {} but could not remove its old file", from);
        }
        forget.accept(from);
    }

    /** The outcome to reply with when {@code name} may not be used for a new menu, or {@code null} when it is fine. */
    private @Nullable EditOutcome validateNewName(String name) {
        if (name.isEmpty() || !SAFE_NAME.matcher(name).matches()) {
            return EditOutcome.NAME_INVALID;
        }
        if (RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
            return EditOutcome.NAME_RESERVED;
        }
        if (registeredSpec.apply(name).isPresent() || Files.exists(fileFor(name))) {
            return EditOutcome.NAME_TAKEN;
        }
        return null;
    }

    private Path fileFor(String name) {
        return menusDir.resolve(name + ".conf");
    }

    /** A minimal valid menu: the name as its title, a small chest, and no items: a canvas the operator lays out. */
    private static MenuSpec blankSpec(String name) {
        return new MenuSpec(name, BLANK_ROWS, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * The result of an editor file operation. The four success variants name the operation that landed (so the caller
     * can show the matching confirmation); the failures are shared across the operations.
     */
    public enum EditOutcome {
        /** A blank menu was created. */
        CREATED,
        /** A menu was copied to a new name. */
        DUPLICATED,
        /** A menu was renamed. */
        RENAMED,
        /** A menu was deleted. */
        DELETED,
        /** A menu was re-serialised and reloaded. */
        SAVED,
        /** The name is not a safe filename token. */
        NAME_INVALID,
        /** The name is one of the reserved non-menu configs (or the bundled starter). */
        NAME_RESERVED,
        /** The name already belongs to a menu. */
        NAME_TAKEN,
        /** The source menu is not loaded / does not exist. */
        SOURCE_MISSING,
        /** The spec named an unregistered action / condition / placeholder and was refused. */
        INVALID_REFS,
        /** The file could not be written or removed. */
        IO_ERROR;

        /** Whether this outcome is one of the four that mean the operation landed. */
        public boolean isSuccess() {
            return this == CREATED || this == DUPLICATED || this == RENAMED || this == DELETED || this == SAVED;
        }
    }
}

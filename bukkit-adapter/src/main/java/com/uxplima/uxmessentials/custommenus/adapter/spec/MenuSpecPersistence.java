package com.uxplima.uxmessentials.custommenus.adapter.spec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Writes an edited {@link MenuSpec} back to its {@code menus/<name>.conf} file. The save half of the menu editor's
 * spec service. It gates every write behind the same {@link MenuBindings#validate} check the loader runs on enable, so
 * a menu whose refs name an unregistered action, condition, placeholder, or list source is never written: an editor
 * cannot save a spec that would only be skipped with a warning the next time it loads. A spec that validates is
 * serialized through {@link MenuSpecWriter} and written to disk, replacing the file the loader will re-read.
 *
 * <p>Bukkit-free by construction: it works with the pure spec model, the writer, and {@code java.nio}, so it can run
 * off the tick thread and is exercised by plain JUnit. It never reloads the engine itself. The caller triggers the
 * single-file reload once the write returns, and it never throws for an expected failure: an unwritable path is a
 * logged {@link Status#IO_ERROR} outcome, an invalid ref set a {@link Status#INVALID_REFS} outcome, so the command
 * can turn either into an operator message rather than a stack trace.
 */
public final class MenuSpecPersistence {

    /** The extension every menu spec is written under: the one the engine's loader reads. */
    private static final String CONF_EXTENSION = ".conf";

    private final MenuSpecWriter writer;
    private final MenuBindings bindings;
    private final Logger log;

    public MenuSpecPersistence(MenuSpecWriter writer, MenuBindings bindings, Logger log) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Validate {@code spec} against the live bindings and, when it is clean, serialize it (with the optional
     * {@code command} open-command block) to {@code menusDir/<name>.conf}. Returns the outcome rather than throwing:
     * {@link Status#INVALID_REFS} with the offending ids when validation fails (no file written), {@link
     * Status#IO_ERROR} when the write fails, {@link Status#SAVED} otherwise.
     */
    public SaveResult save(Path menusDir, String name, MenuSpec spec, @Nullable OpenCommandSpec command) {
        Objects.requireNonNull(menusDir, "menusDir");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(spec, "spec");
        List<String> missing = bindings.validate(List.of(spec));
        if (!missing.isEmpty()) {
            return SaveResult.invalidRefs(missing);
        }
        try {
            String hocon = writer.write(spec, command);
            Files.createDirectories(menusDir);
            Files.writeString(menusDir.resolve(name + CONF_EXTENSION), hocon);
            return SaveResult.saved();
        } catch (IOException failure) {
            log.warn("could not write menu {} : {}", name, String.valueOf(failure.getMessage()));
            return SaveResult.ioError();
        }
    }

    /**
     * Delete {@code menusDir/<name>.conf} from disk: the file half of the editor's delete. Returns the outcome rather
     * than throwing: {@link DeleteStatus#DELETED} when the file existed and was removed, {@link DeleteStatus#NOT_FOUND}
     * when there was no such file (an already-deleted or never-written menu), {@link DeleteStatus#IO_ERROR} when the
     * removal failed. The caller unregisters the spec from the engine once the file is gone; this only touches disk, so
     * it runs off the tick thread like {@link #save}.
     */
    public DeleteStatus delete(Path menusDir, String name) {
        Objects.requireNonNull(menusDir, "menusDir");
        Objects.requireNonNull(name, "name");
        try {
            boolean removed = Files.deleteIfExists(menusDir.resolve(name + CONF_EXTENSION));
            return removed ? DeleteStatus.DELETED : DeleteStatus.NOT_FOUND;
        } catch (IOException failure) {
            log.warn("could not delete menu {} : {}", name, String.valueOf(failure.getMessage()));
            return DeleteStatus.IO_ERROR;
        }
    }

    /** Why a delete ended the way it did. */
    public enum DeleteStatus {
        DELETED,
        NOT_FOUND,
        IO_ERROR
    }

    /**
     * The outcome of a save: whether the spec was written, and, for a rejected one, the ref ids that failed
     * validation, so the caller can name them to the operator.
     */
    public record SaveResult(Status status, List<String> missingRefs) {

        public SaveResult {
            Objects.requireNonNull(status, "status");
            missingRefs = List.copyOf(Objects.requireNonNull(missingRefs, "missingRefs"));
        }

        static SaveResult saved() {
            return new SaveResult(Status.SAVED, List.of());
        }

        static SaveResult invalidRefs(List<String> missing) {
            return new SaveResult(Status.INVALID_REFS, missing);
        }

        static SaveResult ioError() {
            return new SaveResult(Status.IO_ERROR, List.of());
        }

        /** Whether the spec was written to disk. */
        public boolean isSaved() {
            return status == Status.SAVED;
        }
    }

    /** Why a save ended the way it did. */
    public enum Status {
        SAVED,
        INVALID_REFS,
        IO_ERROR
    }
}

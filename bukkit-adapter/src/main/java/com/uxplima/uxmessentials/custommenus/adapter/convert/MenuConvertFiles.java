package com.uxplima.uxmessentials.custommenus.adapter.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The file-walk shared by the menu converters' one-shot services: it resolves an operator's {@code <path>} argument
 * to the concrete {@code .yml} inputs and derives the {@code .conf} output name, so {@link DeluxeMenusConvertService}
 * and {@link ZMenuConvertService} keep only the per-format read/convert/write and share this identical path logic.
 *
 * <p>A path is absolute or taken relative to the menus directory; a directory yields its {@code .yml}/{@code .yaml}
 * files in a stable order, a single file yields itself, and anything else. A missing path, a non-YAML file, a
 * malformed path. Yields nothing (logged under the {@code menu-convert} prefix) so the calling service reports
 * not-found rather than crashing.
 */
final class MenuConvertFiles {

    private MenuConvertFiles() {}

    /** Resolve the raw path to the concrete {@code .yml} inputs: a directory's contents, a single file, or none. */
    static List<Path> resolveInputs(Path menusDir, String rawPath, Logger log) {
        Path resolved = resolve(menusDir, rawPath, log);
        if (resolved == null) {
            return List.of();
        }
        if (Files.isDirectory(resolved)) {
            return ymlFiles(resolved, log);
        }
        return Files.isRegularFile(resolved) && isYaml(resolved) ? List.of(resolved) : List.of();
    }

    /** The file name without its YAML extension: the id the converted {@code .conf} is written under. */
    static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Interpret {@code rawPath} as absolute or relative to the menus directory; a malformed path resolves to none. */
    private static @Nullable Path resolve(Path menusDir, String rawPath, Logger log) {
        try {
            Path path = Path.of(rawPath);
            return path.isAbsolute() ? path : menusDir.resolve(path);
        } catch (InvalidPathException malformed) {
            log.warn("menu-convert could not read path {} : {}", rawPath, String.valueOf(malformed.getMessage()));
            return null;
        }
    }

    /** Every {@code .yml}/{@code .yaml} file directly under a directory, in a stable order. */
    private static List<Path> ymlFiles(Path dir, Logger log) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                    .filter(MenuConvertFiles::isYaml)
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            log.warn("menu-convert could not list {} : {}", dir, String.valueOf(failure.getMessage()));
            return List.of();
        }
    }

    /** Whether a file name ends in a YAML extension DeluxeMenus and zMenu write. */
    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}

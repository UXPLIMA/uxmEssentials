package com.uxplima.uxmessentials.migration.convert.ax;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import org.jspecify.annotations.NullMarked;

/**
 * The settled connection inputs for the AxPlayerWarps source, read once from the {@code axplayerwarps} config subtree:
 * the JDBC URL, the optional credential pair, and the plugins directory the H2-file fallback searches. It resolves a
 * {@link JdbcConnection} two ways, exactly like the LiteBans source:
 *
 * <ul>
 *   <li>when {@code jdbcUrl} is set (a networked MySQL/MariaDB/PostgreSQL AxPlayerWarps, or an explicit H2 URL), it is
 *       used verbatim with the credentials;</li>
 *   <li>when {@code jdbcUrl} is blank, the source falls back to AxPlayerWarps' file default: it locates the
 *       {@code data.mv.db} H2 file under {@code plugins/AxPlayerWarps/} and opens it <em>read-only</em>, so the live
 *       server's own database file is never written.</li>
 * </ul>
 *
 * <p>AxPlayerWarps uses a fixed {@code axplayerwarps_} table set, so, unlike LiteBans, there is no table prefix to
 * carry or sanitise. The H2 file fallback shares the LiteBans caveat: a live AxPlayerWarps H2 file was written by that
 * plugin's bundled engine, and a much newer reader may refuse its storage format; the connectable-URL path (a network
 * backend, or an H2 URL an operator points at the file) is the dependable route.
 */
@NullMarked
public final class AxPlayerWarpsConfig {

    private static final String H2_SUFFIX = ".mv.db";
    private static final String AX_DIR = "AxPlayerWarps";

    private final Optional<String> jdbcUrl;
    private final String username;
    private final String password;
    private final Optional<Path> pluginsDir;

    public AxPlayerWarpsConfig(Optional<String> jdbcUrl, String username, String password, Optional<Path> pluginsDir) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl").filter(url -> !url.isBlank());
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.pluginsDir = Objects.requireNonNull(pluginsDir, "pluginsDir");
    }

    /**
     * Resolve the connection to read from: the configured JDBC URL when present, else a read-only H2 URL over a
     * discovered {@code plugins/AxPlayerWarps/*.mv.db} file. Empty when neither is available.
     */
    public Optional<JdbcConnection> connection() {
        if (jdbcUrl.isPresent()) {
            return Optional.of(JdbcConnection.of(jdbcUrl.orElseThrow(), username, password));
        }
        return h2DataFile().map(AxPlayerWarpsConfig::readOnlyH2Url).map(JdbcConnection::of);
    }

    /** Locate an {@code .mv.db} file under {@code plugins/AxPlayerWarps/}, if the directory exists and holds one. */
    Optional<Path> h2DataFile() {
        Optional<Path> dir = pluginsDir.map(plugins -> plugins.resolve(AX_DIR));
        if (dir.isEmpty() || !Files.isDirectory(dir.orElseThrow())) {
            return Optional.empty();
        }
        try (Stream<Path> listing = Files.list(dir.orElseThrow())) {
            return listing.filter(AxPlayerWarpsConfig::isH2File).findFirst();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static boolean isH2File(Path file) {
        return Files.isRegularFile(file)
                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(H2_SUFFIX);
    }

    /**
     * Build a read-only H2 file URL. H2 names a file database by its path <em>without</em> the {@code .mv.db} suffix,
     * so the suffix is stripped before it is handed to the driver.
     */
    static String readOnlyH2Url(Path mvDbFile) {
        String full = mvDbFile.toAbsolutePath().toString();
        String base = full.substring(0, full.length() - H2_SUFFIX.length());
        return "jdbc:h2:file:" + base + ";ACCESS_MODE_DATA=r";
    }
}

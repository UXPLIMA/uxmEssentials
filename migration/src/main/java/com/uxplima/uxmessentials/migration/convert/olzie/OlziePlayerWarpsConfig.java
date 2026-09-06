package com.uxplima.uxmessentials.migration.convert.olzie;

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
 * The settled connection inputs for the Olzie PlayerWarps source, read once from the {@code olzieplayerwarps} config
 * subtree: the JDBC URL, the optional credential pair, and the plugins directory the SQLite-file fallback searches. It
 * resolves a {@link JdbcConnection} two ways, like the AxPlayerWarps source:
 *
 * <ul>
 *   <li>when {@code jdbcUrl} is set (a networked MySQL/MariaDB Olzie, or an explicit SQLite URL) it is used, with the
 *       credentials for a network backend;</li>
 *   <li>when {@code jdbcUrl} is blank, the source falls back to Olzie's file default: it locates the
 *       {@code database.db} SQLite file under {@code plugins/PlayerWarps/data/} and opens it read-only.</li>
 * </ul>
 *
 * <p>SQLite is the schema this importer was written against (the recovered live database). The org.xerial driver
 * rejects flipping a connection to read-only after it is opened, so every SQLite URL, the file fallback and an
 * operator-supplied one alike. Is normalised to open in read-only mode ({@code open_mode=1}); the shared
 * {@code JdbcSource} then finds the connection already read-only and its advisory {@code setReadOnly(true)} is a no-op.
 * This is the SQLite equivalent of the AxPlayerWarps source's read-only H2 URL. A network (MySQL/MariaDB) URL is used
 * verbatim, since those drivers accept the advisory read-only flag directly.
 */
@NullMarked
public final class OlziePlayerWarpsConfig {

    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String SQLITE_SUFFIX = ".db";
    private static final String OLZIE_DIR = "PlayerWarps";
    private static final String OLZIE_DATA_SUBDIR = "data";
    private static final String READ_ONLY_PARAM = "open_mode=1";

    private final Optional<String> jdbcUrl;
    private final String username;
    private final String password;
    private final Optional<Path> pluginsDir;

    public OlziePlayerWarpsConfig(
            Optional<String> jdbcUrl, String username, String password, Optional<Path> pluginsDir) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl").filter(url -> !url.isBlank());
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.pluginsDir = Objects.requireNonNull(pluginsDir, "pluginsDir");
    }

    /**
     * Resolve the connection to read from: the configured JDBC URL when present (a SQLite URL forced read-only, a
     * network URL verbatim with credentials), else a read-only SQLite URL over a discovered
     * {@code plugins/PlayerWarps/data/*.db} file. Empty when neither is available.
     */
    public Optional<JdbcConnection> connection() {
        if (jdbcUrl.isPresent()) {
            return Optional.of(configuredConnection(jdbcUrl.orElseThrow()));
        }
        return sqliteDataFile().map(OlziePlayerWarpsConfig::readOnlySqliteUrl).map(JdbcConnection::of);
    }

    private JdbcConnection configuredConnection(String url) {
        if (isSqlite(url)) {
            // A file SQLite backend ignores credentials; force it read-only so the driver never rejects the flag.
            return JdbcConnection.of(readOnlySqliteUrl(url));
        }
        return JdbcConnection.of(url, username, password);
    }

    /** Locate a {@code .db} file under {@code plugins/PlayerWarps/data/}, if the directory exists and holds one. */
    Optional<Path> sqliteDataFile() {
        Optional<Path> dir =
                pluginsDir.map(plugins -> plugins.resolve(OLZIE_DIR).resolve(OLZIE_DATA_SUBDIR));
        if (dir.isEmpty() || !Files.isDirectory(dir.orElseThrow())) {
            return Optional.empty();
        }
        try (Stream<Path> listing = Files.list(dir.orElseThrow())) {
            return listing.filter(OlziePlayerWarpsConfig::isSqliteFile).findFirst();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static boolean isSqlite(String url) {
        return url.strip().toLowerCase(Locale.ROOT).startsWith(SQLITE_PREFIX);
    }

    private static boolean isSqliteFile(Path file) {
        return Files.isRegularFile(file)
                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SQLITE_SUFFIX);
    }

    /** Build a read-only SQLite file URL from a discovered database file. */
    static String readOnlySqliteUrl(Path dbFile) {
        return SQLITE_PREFIX + "file:" + dbFile.toAbsolutePath() + "?" + READ_ONLY_PARAM;
    }

    /**
     * Normalise a SQLite JDBC URL to open read-only. The org.xerial driver only honours read-only when it is set on
     * open, so the URL carries {@code open_mode=1}; a URL that already pins the open mode is left untouched. The
     * {@code file:} form is used because the read-only open flag applies to file databases.
     */
    static String readOnlySqliteUrl(String rawUrl) {
        if (rawUrl.contains("open_mode=")) {
            return rawUrl;
        }
        String body = rawUrl.substring(SQLITE_PREFIX.length());
        if (body.startsWith("file:")) {
            body = body.substring("file:".length());
        }
        String separator = body.contains("?") ? "&" : "?";
        return SQLITE_PREFIX + "file:" + body + separator + READ_ONLY_PARAM;
    }
}

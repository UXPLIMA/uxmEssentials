package com.uxplima.uxmessentials.migration.convert.jdbc;

import java.util.Objects;
import java.util.Optional;

/**
 * The settled coordinates a {@link JdbcSource} opens a connection from: the JDBC URL and an optional
 * credential pair. A file-backed source (H2/SQLite) carries a URL and no credentials; a networked source
 * (MySQL/MariaDB/PostgreSQL) carries both. The username and password travel together, a present username
 * implies a present password, so the source either authenticates with both or connects with neither.
 *
 * @param url the JDBC URL ({@code jdbc:h2:file:…}, {@code jdbc:mariadb://…})
 * @param username the database user, or empty for a credential-less file source
 * @param password the database password, or empty for a credential-less file source
 */
public record JdbcConnection(String url, Optional<String> username, Optional<String> password) {

    public JdbcConnection {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (url.isBlank()) {
            throw new IllegalArgumentException("jdbc url must not be blank");
        }
    }

    /** A credential-less connection, for an embedded file database. */
    public static JdbcConnection of(String url) {
        return new JdbcConnection(url, Optional.empty(), Optional.empty());
    }

    /**
     * A connection with credentials when the username is non-blank, else a credential-less one, so an
     * empty config username (LiteBans' file default) maps to an embedded connection rather than an empty
     * login that some drivers reject.
     */
    public static JdbcConnection of(String url, String username, String password) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (username.isBlank()) {
            return of(url);
        }
        return new JdbcConnection(url, Optional.of(username), Optional.of(password));
    }
}

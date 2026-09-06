package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The identity of a single world backup archive: its on-disk file-stem under the backups directory.
 * Constrained to a safe shape so it can never carry a path separator or a traversal segment: the value
 * is therefore filesystem-safe and non-traversing, and can be appended to a directory path directly.
 */
public record BackupId(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]+");

    public BackupId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("backup id must not be blank");
        }
        if (value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("backup id must not be a traversal segment: " + value);
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("backup id must match " + VALID.pattern() + ": " + value);
        }
    }

    public static BackupId of(String value) {
        return new BackupId(value);
    }
}

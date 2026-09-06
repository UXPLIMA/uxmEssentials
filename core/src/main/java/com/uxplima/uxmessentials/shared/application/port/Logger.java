package com.uxplima.uxmessentials.shared.application.port;

/**
 * Outbound logging port for operator-facing diagnostics.
 *
 * <p>Lines an operator reads in {@code latest.log} or the audit file are not player-facing, so they
 * stay as parameterized literals through this port rather than going through the {@code MessageKey}
 * catalog (the catalog is the player path; the logger is the operator path, see {@code docs/13-i18n}
 * §35). Messages use {@code {}} placeholders, SLF4J-style, expanded by the adapter; application code
 * never imports SLF4J directly so {@code :core} stays free of infrastructure.
 */
public interface Logger {

    /** Routine progress an operator may want to see at default verbosity. */
    void info(String message, Object... args);

    /** A recoverable anomaly worth surfacing without failing the operation. */
    void warn(String message, Object... args);

    /** A failure; the throwable carries the cause for the operator's investigation. */
    void error(String message, Throwable cause);

    /** Verbose detail, off by default, enabled for troubleshooting. */
    void debug(String message, Object... args);
}

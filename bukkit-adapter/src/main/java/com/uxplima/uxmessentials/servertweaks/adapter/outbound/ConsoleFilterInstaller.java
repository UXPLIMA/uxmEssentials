package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jspecify.annotations.NullMarked;

/**
 * Attaches and detaches the {@link ConsoleSpamFilter} on the server's root Log4j2 logger config, which is the
 * catch-all every console line flows through. Attaching to the {@code LoggerConfig} (rather than the {@code Logger})
 * is what makes the filter removable: the same filter instance can be dropped again on module stop or reload through
 * the {@link org.apache.logging.log4j.core.filter.Filterable#removeFilter removeFilter} seam, leaving the logging
 * pipeline exactly as it was found.
 *
 * <p>Idempotent by an internal flag so a double-install (or a stop that never installed) is a safe no-op, and every
 * touch of the logging tree is followed by {@link LoggerContext#updateLoggers()} so the change takes effect. Install
 * and uninstall are called on the main thread during module enable/stop, so the flag alone is enough: no lock is
 * held over the logging reconfiguration.
 */
@NullMarked
public final class ConsoleFilterInstaller {

    private final ConsoleSpamFilter filter;
    private final Logger log;
    private boolean installed;

    public ConsoleFilterInstaller(ConsoleSpamFilter filter, Logger log) {
        this.filter = Objects.requireNonNull(filter, "filter");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Add the filter to the root logger config so it screens every console line from now on. */
    public void install() {
        if (installed) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        LoggerConfig root = configuration.getRootLogger();
        filter.start();
        root.addFilter(filter);
        context.updateLoggers();
        installed = true;
        log.info("console-filter attached to the root logger");
    }

    /** Remove the filter again, restoring the logging pipeline; safe to call even if never installed. */
    public void uninstall() {
        if (!installed) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        LoggerConfig root = configuration.getRootLogger();
        root.removeFilter(filter);
        filter.stop();
        context.updateLoggers();
        installed = false;
        log.info("console-filter detached from the root logger");
    }
}

package com.uxplima.uxmessentials.economy.fakes;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * A {@link Logger} for the economy tests that keeps the ERROR lines it is handed. The economy's one
 * logging path (a compensating credit that itself failed, leaving the payer short) is worth asserting
 * on: a test checks it fired exactly once and carried the context an operator needs. INFO/WARN/DEBUG are
 * dropped; only the error path is under test.
 */
public final class RecordingLogger implements Logger {

    private final List<String> errors = new ArrayList<>();

    @Override
    public void info(String message, Object... args) {}

    @Override
    public void warn(String message, Object... args) {}

    @Override
    public void error(String message, Throwable cause) {
        errors.add(message);
    }

    @Override
    public void debug(String message, Object... args) {}

    /** The ERROR messages captured so far, in the order they were logged. */
    public List<String> errors() {
        return List.copyOf(errors);
    }
}

package com.uxplima.uxmessentials.shared.application.command;

import java.util.Objects;

/**
 * A non-fatal note produced while resolving the catalog. A dropped colliding alias, a blank name that
 * fell back to the default, or a primary-name collision. Surfaced to the operator log at startup so a
 * misconfigured entry is visible without failing the build or skipping the whole command surface.
 */
public record CatalogWarning(String message) {

    public CatalogWarning {
        Objects.requireNonNull(message, "message");
    }
}

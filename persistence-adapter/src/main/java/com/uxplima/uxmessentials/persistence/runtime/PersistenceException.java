package com.uxplima.uxmessentials.persistence.runtime;

import org.jspecify.annotations.NullMarked;

/**
 * The persistence layer's own unchecked failure. Infrastructure faults a use case cannot model as a
 * domain {@code Result}. A data-source that will not open, a migration that fails to apply, a query
 * that throws. Are wrapped here at the adapter boundary so callers never catch raw {@code SQLException}
 * or jOOQ exceptions and the project's "no swallowed exceptions, always wrap" rule is honoured. A
 * <em>modelled</em> failure (quota reached, name taken) is a domain {@code Result}, never this.
 */
@NullMarked
public final class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }
}

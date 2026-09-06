package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

/**
 * Raised when an expression cannot be evaluated, a syntax error, an operand of the wrong kind, a divide-by-zero,
 * a non-finite result (overflow), an identifier outside the allow-list, or input that exceeds the size/nesting
 * caps. A checked exception on purpose: the caller (a menu condition) is expected to catch it and fail closed
 * rather than letting a bad operator config abort the menu.
 */
public final class ExpressionException extends Exception {

    public ExpressionException(String message) {
        super(message);
    }
}

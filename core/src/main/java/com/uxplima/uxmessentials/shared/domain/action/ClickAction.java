package com.uxplima.uxmessentials.shared.domain.action;

import java.util.Objects;

/**
 * One typed effect a click target runs on a matching click: the {@link ClickTrigger} that fires it, the
 * {@link ClickActionType} that decides what it does, and the raw {@code value} the type interprets (a command line,
 * a MiniMessage source, a sound key, or a target server name). The value is operator-authored content stored
 * verbatim. The domain validates that the parts are present, not what they mean; the adapter's runner gives the
 * value its meaning per type.
 *
 * @param trigger which click fires this action
 * @param type what this action does
 * @param value the type-specific payload, stored as given
 */
public record ClickAction(ClickTrigger trigger, ClickActionType type, String value) {

    public ClickAction {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}

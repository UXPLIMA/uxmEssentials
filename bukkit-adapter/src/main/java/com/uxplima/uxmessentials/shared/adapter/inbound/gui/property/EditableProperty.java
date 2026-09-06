package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One editable field in an {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView}: the
 * description of a single button (its label, the lore line that reports the current value, and its icon) plus
 * the behaviour run when the viewer clicks it. The editor draws one button per property at its configured
 * slot and routes a click on that slot back to {@link #onClick(ClickContext)}.
 *
 * <p>A property carries no domain logic of its own. Its {@link #onClick} performs a presentation step, cycle
 * a flag, open an anvil, step a number, open a sub-selector, and then hands the new value to a
 * caller-supplied setter (a {@code Consumer} that calls the module's existing application use case). The
 * framework is the inbound adapter; the use case is the only place the change is actually made, so the GUI and
 * the equivalent command always go through the same code.
 *
 * <p>Implementations are canon-styled (label/value text via {@link MessageKey} catalog entries), schedule any
 * write off the tick thread through the shared {@code Scheduler}, and gate destructive steps behind a
 * confirm. The supplied set is {@link ToggleProperty}, {@link TextProperty}, {@link NumberProperty},
 * {@link EnumProperty}, and {@link ListProperty}; a module composes them, it does not subclass them.
 */
@NullMarked
public interface EditableProperty {

    /** The catalog key for this field's button name (e.g. "Difficulty", "Public"). */
    MessageKey label();

    /**
     * The rendered current value to show in the button lore for {@code viewer}, already resolved to a display
     * string in the viewer's locale (e.g. "On", "42", "Easy"). The editor wraps it in the configured
     * value-lore catalog line, so this returns the bare value text, not a full lore block.
     */
    String valueLore(PlayerRef viewer);

    /** The button icon material; from the editor layout conf, never hardcoded by a property. */
    Material icon();

    /** Perform the edit for a click on this field's button. */
    void onClick(ClickContext context);
}

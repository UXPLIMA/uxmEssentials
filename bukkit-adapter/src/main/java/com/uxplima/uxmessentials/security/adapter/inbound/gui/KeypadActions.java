package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The two verification actions the keypad delegates to the controller when a player clicks submit or the "type a code"
 * button. Keeping them behind this interface lets {@link PinKeypadView} (in the GUI package) route the engine's
 * submit / code actions without depending on the concrete controller, and lets the controller own the
 * verify/lockout/trust decision the GUI has no business knowing. The GUI is the hands; the controller is the judgement.
 */
@NullMarked
public interface KeypadActions {

    /** The player submitted {@code candidate} (a PIN or code) from the keypad, verify it and unfreeze or count a fail. */
    void submit(Player player, PlayerRef viewer, String candidate);

    /** The player asked to type an authenticator code instead, hand off to the text-input prompt. */
    void requestTotp(Player player, PlayerRef viewer);
}

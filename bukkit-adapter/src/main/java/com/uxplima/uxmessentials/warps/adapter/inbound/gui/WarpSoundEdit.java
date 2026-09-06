package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * The subject the sound-selector menu is opened for: which warp is being edited and whether the click sets its
 * departure ({@code true}) or arrival ({@code false}) sound. Carrying the direction on the subject is what lets a
 * single {@code warp-sounds} spec serve both editor buttons. The {@code warp:set-sound} action reads it to decide
 * which side to write, so there is no separate departure/arrival spec or action pair.
 *
 * @param warp the warp whose sound is being edited
 * @param departure {@code true} for the departure sound, {@code false} for the arrival sound
 */
@NullMarked
public record WarpSoundEdit(WarpName warp, boolean departure) {

    public WarpSoundEdit {
        Objects.requireNonNull(warp, "warp");
    }
}

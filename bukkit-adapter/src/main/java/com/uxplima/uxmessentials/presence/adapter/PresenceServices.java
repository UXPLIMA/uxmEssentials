package com.uxplima.uxmessentials.presence.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.SetNick;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed presence use cases the commands, listeners, and AFK sweep share, built once per module start
 * by {@code PresenceWiring} from the kernel ports and the context's own in-memory store and audience. Held so every
 * collaborator reads the same use cases; the context's only adapter-side runtime state is the in-memory presence map
 * (cleared on stop) and the self-rescheduling sweep (stopped on disable).
 *
 * <p>Vanish is no longer a presence use case. It moved to the dedicated {@code vanish} context, the single vanish
 * authority. The presence settings panel toggles vanish through a handle onto that context instead (threaded in by
 * bootstrap), and presence's own readers see the vanish state through the store's overlay.
 *
 * @param markAfk {@code /afk [reason]} toggle and the sweep's auto-mark
 * @param clearAfk the sync activity listeners' return-from-AFK on move/chat/command
 * @param setNick {@code /nick <name>} and {@code /nick <player> <name>}
 * @param clearNick {@code /nick off}
 */
@NullMarked
public record PresenceServices(MarkAfk markAfk, ClearAfkOnActivity clearAfk, SetNick setNick, ClearNick clearNick) {

    public PresenceServices {
        Objects.requireNonNull(markAfk, "markAfk");
        Objects.requireNonNull(clearAfk, "clearAfk");
        Objects.requireNonNull(setNick, "setNick");
        Objects.requireNonNull(clearNick, "clearNick");
    }
}

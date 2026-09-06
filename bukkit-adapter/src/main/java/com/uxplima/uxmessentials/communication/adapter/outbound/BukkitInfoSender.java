package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Renders an {@link InfoPage} to one viewer, line by line, through the {@link MessageSink}. Each line is
 * operator-authored MiniMessage content the sink parses into a Component once and delivers on the viewer's region
 * thread, never a plugin {@code MessageKey}, so an info page is not parity-checked. The {@code {player}}
 * placeholder is substituted per viewer before delivery so a page may greet the reader by name.
 *
 * <p>Shared by the dynamic info commands ({@code /rules}, {@code /motd}, …) and the optional
 * send-info-after-death hook, so the page-rendering contract lives in one place.
 */
@NullMarked
public final class BukkitInfoSender {

    private final MessageSink sink;

    public BukkitInfoSender(MessageSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Deliver every line of {@code page} to {@code viewer}, substituting {@code {player}} with the viewer name. */
    public void send(PlayerRef viewer, InfoPage page) {
        Objects.requireNonNull(page, "page");
        send(viewer, page.lines());
    }

    /** Deliver {@code lines} to {@code viewer}, substituting {@code {player}} with the viewer name in each line. */
    public void send(PlayerRef viewer, List<String> lines) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(lines, "lines");
        PlaceholderBindings bindings = PlaceholderBindings.empty().with("player", viewer.name());
        for (String line : lines) {
            sink.deliver(viewer, bindings.apply(line));
        }
    }
}

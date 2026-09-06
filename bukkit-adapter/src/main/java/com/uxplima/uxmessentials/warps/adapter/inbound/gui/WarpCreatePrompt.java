package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * The warp manager's "create new warp" flow, pulled out so both the engine-rendered manager and any caller share the
 * one path: prompt for a name, validate it, create the warp at the operator's current position through {@link SetWarp}
 *, the same use case {@code /warp create} drives, then open that warp's editor; or, on a blank or spaced name, send
 * the invalid-name message and reopen the manager. Cancelling the prompt reopens the manager too. The flow itself adds
 * no domain logic: it drives {@link SetWarp} and the bespoke {@link WarpEditorView}, exactly as the old manager's
 * create button did.
 */
@NullMarked
public final class WarpCreatePrompt {

    private final Messages messages;
    private final TextInput textInput;
    private final SetWarp setWarp;
    private final WarpEditorView editorView;

    public WarpCreatePrompt(Messages messages, TextInput textInput, SetWarp setWarp, WarpEditorView editorView) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.setWarp = Objects.requireNonNull(setWarp, "setWarp");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
    }

    /**
     * Start the create-name prompt for {@code viewer}. On a valid name the warp is created at the operator's current
     * position and its editor opens; on an invalid one the player is told and {@code reopen} runs; cancelling the
     * prompt also runs {@code reopen}.
     *
     * @param reopen reopen the surface the prompt was launched from (the manager) on an invalid name or a cancel
     */
    public void start(Player player, PlayerRef viewer, Runnable reopen) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(reopen, "reopen");
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.create-name", WarpsMessageKey.WARP_MANAGER_CREATE_PROMPT),
                name -> onName(player, viewer, name, reopen),
                reopen);
    }

    private void onName(Player player, PlayerRef viewer, String name, Runnable reopen) {
        String clean = name.trim();
        if (clean.isEmpty() || clean.contains(" ")) {
            player.sendMessage(StyledText.render(
                    messages.resolve(viewer, WarpsMessageKey.WARP_MANAGER_ERROR_INVALID_NAME, Map.of())));
            reopen.run();
            return;
        }
        var location = Objects.requireNonNull(player.getLocation(), "player location");
        setWarp.set(viewer, WarpName.of(clean), BukkitRefs.toPosition(location));
        editorView.open(player, viewer, clean, null);
    }

    /** Convenience seam: start the create flow, reopening {@code manager} on an invalid name or a cancel. */
    public BiConsumer<Player, PlayerRef> boundTo(BiConsumer<Player, PlayerRef> manager) {
        Objects.requireNonNull(manager, "manager");
        return (player, viewer) -> start(player, viewer, () -> manager.accept(player, viewer));
    }
}

package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The kit manager's "create new kit" flow, pulled out so both the engine-rendered manager and any caller share the
 * one path: prompt for a name, sanitize it, create an empty kit, then open that kit's settings, or, on a blank or
 * spaced name, send the invalid-name message and reopen the manager. Cancelling the prompt reopens the manager too.
 * The flow itself adds no domain logic: it drives {@link CreateKit} and the bespoke {@link KitSettingsView}, exactly
 * as the old manager's create button did.
 */
@NullMarked
public final class KitCreatePrompt {

    private final Messages messages;
    private final TextInput textInput;
    private final CreateKit createKit;
    private final KitRepository repository;
    private final KitSettingsView settingsView;

    public KitCreatePrompt(
            Messages messages,
            TextInput textInput,
            CreateKit createKit,
            KitRepository repository,
            KitSettingsView settingsView) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.createKit = Objects.requireNonNull(createKit, "createKit");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
    }

    /**
     * Start the create-name prompt for {@code viewer}. On a valid name an empty kit is created and its settings open;
     * on an invalid one the player is told and {@code reopen} runs; cancelling the prompt also runs {@code reopen}.
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
                InputRequest.of("kit.create-name", KitsMessageKey.KIT_EDITOR_PROMPT_CREATE),
                name -> onName(player, viewer, name, reopen),
                reopen);
    }

    private void onName(Player player, PlayerRef viewer, String name, Runnable reopen) {
        String clean = sanitizeId(name);
        if (clean.isEmpty()) {
            player.sendMessage(StyledText.render(
                    messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NAME, java.util.Map.of())));
            reopen.run();
            return;
        }
        KitId id = KitId.of(clean);
        createKit.create(viewer, KitDefinition.builder().id(id).build());
        repository.find(id).ifPresent(kit -> settingsView.open(player, viewer, kit));
    }

    private static String sanitizeId(String name) {
        String clean = name.trim().toLowerCase(java.util.Locale.ROOT);
        return clean.contains(" ") ? "" : clean;
    }

    /** Convenience seam: start the create flow, reopening {@code manager} on an invalid name or a cancel. */
    public BiConsumer<Player, PlayerRef> boundTo(BiConsumer<Player, PlayerRef> manager) {
        Objects.requireNonNull(manager, "manager");
        return (player, viewer) -> start(player, viewer, () -> manager.accept(player, viewer));
    }
}

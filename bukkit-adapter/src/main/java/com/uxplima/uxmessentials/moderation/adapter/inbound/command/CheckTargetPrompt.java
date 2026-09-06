package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The bare {@code /checkban} and {@code /checkmute} prompt flow: prompt for a player name through the shared
 * text-input seam, resolve the submitted name through the moderation {@code TargetResolver}, and hand the resolved
 * target to the existing chat-output check use case ({@code CheckBan.show} / {@code CheckMute.show}). The
 * maintainer's requirement is "sonuç chatta çıkar". The prompt only captures the name; the result is the same
 * chat line the raw {@code /checkban <player>} / {@code /checkmute <player>} subcommand prints, not a GUI screen.
 *
 * <p>One instance per check command, parameterised by its input key, prompt key and use-case call. The name
 * resolves off the tick thread (a profile lookup may block) and the check runs off-thread too; an unknown name gets
 * the same unknown-target reply the raw command's resolver gives. A cancelled prompt is a no-op, mirroring the bare
 * command doing nothing without a name. Package-private {@link #checkByName} is the seam a test drives without a
 * live prompt.
 */
@NullMarked
final class CheckTargetPrompt {

    private final ModerationServices services;
    private final TextInput textInput;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final String inputKey;
    private final MessageKey promptKey;
    private final BiConsumer<PlayerRef, PlayerRef> check;

    CheckTargetPrompt(
            ModerationServices services,
            TextInput textInput,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink,
            String inputKey,
            MessageKey promptKey,
            BiConsumer<PlayerRef, PlayerRef> check) {
        this.services = Objects.requireNonNull(services, "services");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.inputKey = Objects.requireNonNull(inputKey, "inputKey");
        this.promptKey = Objects.requireNonNull(promptKey, "promptKey");
        this.check = Objects.requireNonNull(check, "check");
    }

    /**
     * Open the name prompt for {@code viewer}; a submitted name flows through {@link #checkByName}. A cancel is a
     * no-op, the raw {@code /checkban}/{@code /checkmute} opener simply does nothing when no name is given.
     */
    void open(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        PlayerRef actor = BukkitRefs.toRef(viewer);
        textInput.prompt(
                viewer,
                actor,
                InputRequest.of(inputKey, promptKey),
                name -> {
                    if (!name.isBlank()) {
                        checkByName(actor, name);
                    }
                },
                () -> {});
    }

    /**
     * Resolve {@code name} off the tick thread, then run the check off-thread too. An unknown name replies with
     * the shared unknown-target line (mirroring the raw command), and no check fires.
     */
    void checkByName(PlayerRef actor, String name) {
        String typed = name.strip();
        scheduler.async(() -> {
            Optional<PlayerRef> target = services.targets().resolve(typed);
            if (target.isEmpty()) {
                sink.deliver(
                        actor,
                        messages.resolve(
                                actor, ModerationCommandSupport.UNKNOWN_PLAYER, java.util.Map.of("player", typed)));
                return;
            }
            check.accept(actor, target.get());
        });
    }
}

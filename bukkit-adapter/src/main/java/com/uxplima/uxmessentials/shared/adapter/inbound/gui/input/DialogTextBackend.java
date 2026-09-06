package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.dialog.DialogInputScreen;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The dialog backend of the text-input seam: prompts through uxmLib's native {@link DialogInputScreen}, the Paper
 * server-side Dialog carrying one line of text, added in Minecraft 1.21.6, and reports the typed line. A dialog field
 * can be pre-seeded, so unlike the sign backend this honours {@code initialText}. The uxmLib screen delivers its submit
 * and cancel on the main server thread; the entity-thread hop and the cancel-keyword check live upstream in
 * {@link TextInput} (its outcome wrapper marshals every backend's result onto the viewer's region), so this stays a thin
 * adapter exactly like {@link SignTextBackend}: it never schedules for itself.
 *
 * <p>A live {@link DialogInputScreen} cannot be driven under MockBukkit, so the show step is the {@link Prompt} seam:
 * production supplies the native, screen-backed one via {@link #paperNative(GuiText)}; a test supplies a fake that drives the
 * submit and cancel callbacks directly, so the submit/cancel contract is covered without a live Paper dialog.
 */
@NullMarked
final class DialogTextBackend implements TextInputBackend {

    /** The dialog's internal field key the typed line is read back by; never shown to the player. */
    private static final String FIELD_KEY = "input";

    /**
     * Shows a one-line text dialog to a player and delivers the typed line to {@code onSubmit}, or a dismissal to
     * {@code onCancel}. Production is the native {@link DialogInputScreen}; a test fakes it so the backend's contract is
     * exercised without a live Paper dialog.
     */
    @FunctionalInterface
    interface Prompt {
        void show(
                Player player,
                Component title,
                Component label,
                Component submitLabel,
                Component cancelLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onCancel);
    }

    private final Prompt prompt;
    private final GuiText guiText;

    DialogTextBackend(Prompt prompt, GuiText guiText) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
    }

    /** The production backend, prompting through a native {@link DialogInputScreen}. */
    static DialogTextBackend paperNative(GuiText guiText) {
        return new DialogTextBackend(DialogTextBackend::showNative, guiText);
    }

    @Override
    public void open(
            Player player,
            PlayerRef viewer,
            Component promptText,
            @Nullable String initialText,
            Consumer<InputResult> outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(promptText, "prompt");
        Objects.requireNonNull(outcome, "outcome");
        // The screen carries a single line, so the resolved prompt serves as both the window title and the field
        // label. TextInput's outcome wrapper owns the hop onto the viewer's region, so this forwards raw.
        prompt.show(
                player,
                promptText,
                promptText,
                guiText.text(viewer, GuiMessageKey.INPUT_DIALOG_SUBMIT),
                guiText.text(viewer, GuiMessageKey.INPUT_DIALOG_CANCEL),
                initialText,
                line -> outcome.accept(new InputResult.Submitted(line)),
                () -> outcome.accept(InputResult.Cancelled.INSTANCE));
    }

    private static void showNative(
            Player player,
            Component title,
            Component label,
            Component submitLabel,
            Component cancelLabel,
            @Nullable String initial,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        DialogInputScreen screen = DialogInputScreen.create(title, FIELD_KEY, label, submitLabel, cancelLabel);
        if (initial != null) {
            screen.initial(initial);
        }
        screen.prompt(player, onSubmit, onCancel);
    }
}

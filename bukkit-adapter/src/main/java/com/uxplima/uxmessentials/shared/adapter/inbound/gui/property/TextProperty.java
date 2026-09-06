package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a {@link TextInput} prompt (anvil or chat, per the operator's per-key config),
 * validates the typed line, and hands the accepted value to a setter. The prompt's hint is a catalog line; the
 * validator turns the raw text into the accepted value or rejects it (an empty {@link Optional}), so a module can
 * trim, length-check, or pattern-match without the framework knowing the rules. An accepted value is written through
 * the caller's setter off the tick thread via the shared {@link Scheduler}; on a rejected submit and on cancel the
 * editor is redrawn so the viewer lands back where they were.
 *
 * <p>The {@code inputKey} identifies this field to the input config. Every text field in an entity editor shares the
 * one {@code editor.text-field} key, so an operator flips all editor text fields to chat (or anvil) with a single
 * override. The {@link TextInput} seam already hops the callback onto the viewer's region thread and handles the
 * cancel keywords, so this property only validates and sets.
 */
@NullMarked
public final class TextProperty implements EditableProperty {

    private final String inputKey;
    private final MessageKey label;
    private final MessageKey promptHint;
    private final Material icon;
    private final Supplier<String> current;
    private final Function<String, Optional<String>> validator;
    private final Consumer<String> setter;
    private final TextInput textInput;
    private final Scheduler scheduler;

    public TextProperty(
            String inputKey,
            MessageKey label,
            MessageKey promptHint,
            Material icon,
            Supplier<String> current,
            Function<String, Optional<String>> validator,
            Consumer<String> setter,
            TextInput textInput,
            Scheduler scheduler) {
        this.inputKey = Objects.requireNonNull(inputKey, "inputKey");
        this.label = Objects.requireNonNull(label, "label");
        this.promptHint = Objects.requireNonNull(promptHint, "promptHint");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.current = Objects.requireNonNull(current, "current");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return current.get();
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        textInput.prompt(
                context.player(),
                context.viewer(),
                InputRequest.of(inputKey, promptHint),
                raw -> applyInput(context, raw),
                context.reopen());
    }

    /**
     * Apply a submitted line: validate it, and on acceptance write it through the setter off-thread and redraw; on
     * rejection redraw without writing. The seam delegates here; it is also the seam a test drives to pin the
     * validate-then-set behaviour without opening a live prompt, the same pattern the home rename editor uses.
     */
    public void applyInput(ClickContext context, String raw) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(raw, "raw");
        Optional<String> accepted = validator.apply(raw);
        if (accepted.isEmpty()) {
            context.reopen().run();
            return;
        }
        String value = accepted.get();
        scheduler.async(() -> {
            setter.accept(value);
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }
}

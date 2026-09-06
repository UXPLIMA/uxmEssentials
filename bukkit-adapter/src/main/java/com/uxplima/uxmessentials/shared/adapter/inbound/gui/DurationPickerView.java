package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A reusable duration step for the timed sanction flows ({@code /tempban}, {@code /tempmute}): a fixed grid of
 * preset duration buttons (each labelled with its own {@code SanctionDuration}-grammar string, e.g. {@code 30m},
 * {@code 1h}, {@code 7d}) plus a "custom" button that opens an anvil for a typed span. Clicking a preset, or
 * submitting a string the supplied validator accepts, fires the caller's {@code onPick} with that exact duration
 * string; a malformed typed string replies with the caller's reject {@link MessageKey} and reopens the anvil.
 *
 * <p>The view holds no sanction logic. One instance is shared across callers. The framework collaborators (the menu
 * engine, text, scheduler, anvil, sink, messages) live on the instance, and the per-use parts (the title, the preset
 * list, the pick callback, the reject key, and an optional back action) are passed to {@link #open} through a
 * {@link Request}. The typed string is validated through {@link Request#validator}, which a caller backs with
 * {@code SanctionDuration.parse} so the moderation context owns its own grammar; this class only distinguishes
 * "accepted" from "rejected".
 *
 * <p>It draws through the menu engine's selector runtime ({@link Menus#openSelector}): a single-page child window of
 * option buttons routed and torn down by the one menu listener and one {@code closeMenu}. Each preset, the custom
 * button, and the optional back button is one {@link SelectorButton} whose icon (name and lore) is baked here, so the
 * engine only places it and wires the click. No roster or list source is read, so the engine shows the window straight
 * on the viewer's entity thread (where its clicks also run); the validator is pure and runs inline on the click thread.
 */
@NullMarked
public final class DurationPickerView {

    private static final int ROWS = 3;
    private static final String INPUT_KEY = "picker.duration";
    private static final int CUSTOM_SLOT = 22;
    private static final int BACK_SLOT = 18;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material PRESET_ICON = Material.CLOCK;
    private static final Material CUSTOM_ICON = Material.WRITABLE_BOOK;
    private static final Material BACK_ICON = Material.ARROW;

    // The default preset ladder offered when a caller does not supply its own, a sensible spread from a short
    // cooldown to a month, all valid SanctionDuration grammar.
    private static final List<String> DEFAULT_PRESETS =
            List.of("30m", "1h", "6h", "12h", "1d", "3d", "7d", "14d", "30d");

    private final Menus menus;
    private final GuiText guiText;
    private final TextInput textInput;
    private final Messages messages;
    private final MessageSink sink;

    public DurationPickerView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            TextInput textInput,
            Messages messages,
            MessageSink sink) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        // The engine hops onto the viewer's entity thread inside openSelector, so the scheduler is only validated here.
        Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The sensible default preset ladder, exposed so a caller can offer it without re-listing the spans. */
    public static List<String> defaultPresets() {
        return DEFAULT_PRESETS;
    }

    /** Open the picker for {@code viewer}; a preset click or an accepted typed span fires {@code request.onPick}. */
    public void open(Player viewer, PlayerRef viewerRef, Request request) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(request, "request");
        menus.openSelector(
                viewerRef, guiText.text(viewerRef, request.title()), ROWS, FILLER, buttons(viewer, viewerRef, request));
    }

    /** Build one selector button per preset, the custom-span anvil button, and (when supplied) the back button. */
    private List<SelectorButton> buttons(Player viewer, PlayerRef viewerRef, Request request) {
        List<SelectorButton> buttons = new ArrayList<>();
        List<String> presets = request.presets();
        for (int i = 0; i < presets.size() && presetSlot(i) < CUSTOM_SLOT; i++) {
            String preset = presets.get(i);
            buttons.add(SelectorButton.of(
                    presetSlot(i),
                    presetIcon(viewerRef, preset),
                    () -> request.onPick().accept(preset)));
        }
        buttons.add(
                SelectorButton.of(CUSTOM_SLOT, customIcon(viewerRef), () -> promptCustom(viewer, viewerRef, request)));
        request.onBack().ifPresent(back -> buttons.add(SelectorButton.of(BACK_SLOT, backIcon(viewerRef), back)));
        return buttons;
    }

    /** Open the input prompt for a typed span; a submission flows through {@link #resolveTyped}. */
    private void promptCustom(Player viewer, PlayerRef viewerRef, Request request) {
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of(INPUT_KEY, GuiMessageKey.DURATION_PICKER_CUSTOM_PROMPT),
                text -> resolveTyped(viewer, viewerRef, request, text),
                () -> open(viewer, viewerRef, request));
    }

    /**
     * Validate the typed span on the viewer's entity thread: a rejected string replies with the reject key and
     * reopens the anvil, an accepted one fires the pick callback with the trimmed text. Package-private so the
     * accept/reject branches are unit-tested without driving a live anvil.
     */
    void resolveTyped(Player viewer, PlayerRef viewerRef, Request request, String input) {
        String trimmed = input.strip();
        if (!request.validator().apply(trimmed)) {
            sink.deliver(viewerRef, messages.resolve(viewerRef, request.rejectKey(), Map.of("input", trimmed)));
            promptCustom(viewer, viewerRef, request);
            return;
        }
        request.onPick().accept(trimmed);
    }

    private ItemStack presetIcon(PlayerRef viewer, String preset) {
        return ItemBuilder.of(PRESET_ICON)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, GuiMessageKey.DURATION_PICKER_PRESET_NAME, Map.of("duration", preset)),
                        List.of(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_PRESET_LORE))))
                .build();
    }

    private ItemStack customIcon(PlayerRef viewer) {
        return ItemBuilder.of(CUSTOM_ICON)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, GuiMessageKey.DURATION_PICKER_CUSTOM),
                        List.of(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_CUSTOM_LORE))))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(BACK_ICON)
                .name(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_BACK))
                .build();
    }

    // The presets occupy the centre of the top two rows; the bottom row carries the custom and back buttons.
    private static int presetSlot(int index) {
        return index + (index / 7) * 2 + 1;
    }

    /**
     * One picker invocation's caller-supplied parts, keeping {@link DurationPickerView} generic over the sanction
     * verb: the menu title, the preset spans offered, the callback fired with the chosen duration string, the
     * validator that decides whether a typed span is acceptable, the reply key for a rejected one, and an optional
     * back action (the previous step in the flow).
     *
     * @param title the menu-title catalog key
     * @param presets the preset duration strings offered as buttons (SanctionDuration grammar)
     * @param onPick invoked with the chosen duration string (a clicked preset, or an accepted typed span)
     * @param validator decides whether a typed span is acceptable for this verb (e.g. rejecting a permanent parse
     *     for {@code /tempban}); a caller backs it with {@code SanctionDuration.parse}
     * @param rejectKey the reply key for a rejected typed span (filled with {@code {input}})
     * @param onBack the action the back button runs, or empty for no back button
     */
    public record Request(
            MessageKey title,
            List<String> presets,
            Consumer<String> onPick,
            Function<String, Boolean> validator,
            MessageKey rejectKey,
            java.util.Optional<Runnable> onBack) {

        public Request {
            Objects.requireNonNull(title, "title");
            presets = List.copyOf(Objects.requireNonNull(presets, "presets"));
            Objects.requireNonNull(onPick, "onPick");
            Objects.requireNonNull(validator, "validator");
            Objects.requireNonNull(rejectKey, "rejectKey");
            Objects.requireNonNull(onBack, "onBack");
        }
    }
}

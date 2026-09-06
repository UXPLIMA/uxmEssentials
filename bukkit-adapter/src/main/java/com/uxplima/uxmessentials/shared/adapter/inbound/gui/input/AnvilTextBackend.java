package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anvil backend of the text-input seam: opens a uxmLib {@link AnvilInput}, putting the resolved prompt line on the
 * left-slot item (its name is the on-screen hint, exactly as the property widgets did before). A vanilla anvil seeds
 * its rename field from that item's display name, so when an {@code initialText} is supplied it becomes the item name
 *: the field opens pre-filled with it; otherwise the styled prompt is the hint and the field opens empty. The anvil's
 * own outcome ({@code Submitted}/{@code Cancelled}) maps one-to-one onto {@link InputResult}; the cancel-keyword check
 * happens upstream in {@link TextInput}, so this backend stays a thin adapter over {@link AnvilInput}.
 */
@NullMarked
final class AnvilTextBackend implements TextInputBackend {

    private static final Material PROMPT_ITEM = Material.PAPER;

    private final AnvilInput anvil;

    AnvilTextBackend(AnvilInput anvil) {
        this.anvil = Objects.requireNonNull(anvil, "anvil");
    }

    @Override
    public void open(
            Player player,
            PlayerRef viewer,
            Component prompt,
            @Nullable String initialText,
            Consumer<InputResult> outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(outcome, "outcome");
        ItemStack promptItem = buildPromptItem(prompt, initialText);
        anvil.open(player, promptItem, result -> outcome.accept(map(result)));
    }

    private static ItemStack buildPromptItem(Component prompt, @Nullable String initialText) {
        Component name = (initialText == null || initialText.isEmpty()) ? prompt : Component.text(initialText);
        return ItemBuilder.of(PROMPT_ITEM).name(name).build();
    }

    private static InputResult map(AnvilResult result) {
        if (result instanceof AnvilResult.Submitted submitted) {
            return new InputResult.Submitted(submitted.text());
        }
        return InputResult.Cancelled.INSTANCE;
    }
}

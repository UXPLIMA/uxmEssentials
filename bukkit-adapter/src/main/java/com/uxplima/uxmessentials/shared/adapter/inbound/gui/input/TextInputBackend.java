package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One way to capture a line of text from a player, the anvil or the chat implementation. Both receive the already
 * resolved prompt {@link Component} (so neither touches the catalog) and report a raw {@link InputResult} exactly
 * once; {@link TextInput} owns backend selection and the cancel-keyword policy, so a backend only has to capture text
 * and hand it back.
 */
@NullMarked
interface TextInputBackend {

    /**
     * Open the prompt for {@code player} and call {@code outcome} once with the result.
     *
     * @param player the live player to prompt
     * @param viewer the viewer reference (locale + identity), for any per-player rendering the backend needs
     * @param prompt the resolved prompt line, the anvil hint or the chat message
     * @param initialText the anvil pre-fill, or {@code null}; the chat backend ignores it
     * @param outcome fired once with the submission or a structural cancellation
     */
    void open(
            Player player,
            PlayerRef viewer,
            Component prompt,
            @Nullable String initialText,
            Consumer<InputResult> outcome);
}

package com.uxplima.uxmessentials.shared.application.message;

/**
 * The cross-cutting management-GUI message keys, the {@code gui.*} block the central hub and any
 * shared GUI scaffolding owns, distinct from a feature context's own menu keys.
 *
 * <p>The {@code /uxmess gui} hub lists every module's registered management-GUI entry as a clickable
 * icon. Its title, the per-entry name and lore, the navigation labels, and the empty-state line all
 * resolve here rather than being inlined, so the hub honours the locale pipeline and the UI-style canon
 * exactly as every feature menu does. The hub is shared infrastructure owned by no single context, so
 * its keys sit in the shared kernel under the {@code gui} prefix.
 *
 * <p>Like every {@link MessageKey} enum the constant name and the catalog key map 1:1
 * ({@code HUB_TITLE} ↔ {@code gui.hub.title}); the locale-parity guard asserts each has an {@code en}
 * entry and the catalog aggregate enumerates this enum.
 */
public enum GuiMessageKey implements MessageKey {

    // the /uxmess gui management hub
    HUB_TITLE("gui.hub.title"),
    HUB_ENTRY_LORE("gui.hub.entry.lore"),
    HUB_EMPTY("gui.hub.empty"),
    HUB_PREV("gui.hub.prev"),
    HUB_NEXT("gui.hub.next"),

    // the shared text-input seam, the cancel acknowledgement and the chat-mode cancel hint
    INPUT_CANCELLED("gui.input.cancelled"),
    INPUT_CANCEL_HINT("gui.input.cancel-hint"),

    // the native text dialog: the words on its two buttons. uxmLib wrote these itself until 0.46.0 and it
    // stopped, because a library that words a button decides how a plugin sounds. They resolve per viewer,
    // like every other line here, so the dialog speaks the language the player is being served.
    INPUT_DIALOG_SUBMIT("gui.input.dialog-submit"),
    INPUT_DIALOG_CANCEL("gui.input.dialog-cancel"),

    // the shared confirm window. The two button labels a Bedrock ModalForm needs (the chest paints wordless wool)
    CONFIRM_YES("gui.confirm.yes"),
    CONFIRM_NO("gui.confirm.no"),

    // the shared paged form. The Previous/Next nav buttons a Bedrock SimpleForm adds when a list spans pages
    PAGE_PREVIOUS("gui.page.previous"),
    PAGE_NEXT("gui.page.next"),

    // the shared colour-picker widget: its chrome buttons
    COLOUR_PICKER_TITLE("gui.colour-picker.title"),
    COLOUR_PICKER_CUSTOM("gui.colour-picker.custom"),
    COLOUR_PICKER_CUSTOM_PROMPT("gui.colour-picker.custom-prompt"),
    COLOUR_PICKER_CLEAR("gui.colour-picker.clear"),
    COLOUR_PICKER_BACK("gui.colour-picker.back"),

    // the shared player-picker widget. Online head entries plus the offline-name anvil button and nav
    PLAYER_PICKER_HEAD_NAME("gui.player-picker.head-name"),
    PLAYER_PICKER_HEAD_LORE("gui.player-picker.head-lore"),
    PLAYER_PICKER_CUSTOM("gui.player-picker.custom"),
    PLAYER_PICKER_CUSTOM_LORE("gui.player-picker.custom-lore"),
    PLAYER_PICKER_CUSTOM_PROMPT("gui.player-picker.custom-prompt"),
    PLAYER_PICKER_PREV("gui.player-picker.prev"),
    PLAYER_PICKER_NEXT("gui.player-picker.next"),

    // the shared duration-picker widget. Preset duration buttons plus the custom-span anvil button and back
    DURATION_PICKER_PRESET_NAME("gui.duration-picker.preset-name"),
    DURATION_PICKER_PRESET_LORE("gui.duration-picker.preset-lore"),
    DURATION_PICKER_CUSTOM("gui.duration-picker.custom"),
    DURATION_PICKER_CUSTOM_LORE("gui.duration-picker.custom-lore"),
    DURATION_PICKER_CUSTOM_PROMPT("gui.duration-picker.custom-prompt"),
    DURATION_PICKER_BACK("gui.duration-picker.back"),

    // the shared colour-picker widget. The 16 standard named-colour swatches
    COLOUR_WHITE("gui.colour.white"),
    COLOUR_ORANGE("gui.colour.orange"),
    COLOUR_MAGENTA("gui.colour.magenta"),
    COLOUR_LIGHT_BLUE("gui.colour.light-blue"),
    COLOUR_YELLOW("gui.colour.yellow"),
    COLOUR_LIME("gui.colour.lime"),
    COLOUR_PINK("gui.colour.pink"),
    COLOUR_GRAY("gui.colour.gray"),
    COLOUR_LIGHT_GRAY("gui.colour.light-gray"),
    COLOUR_CYAN("gui.colour.cyan"),
    COLOUR_PURPLE("gui.colour.purple"),
    COLOUR_BLUE("gui.colour.blue"),
    COLOUR_BROWN("gui.colour.brown"),
    COLOUR_GREEN("gui.colour.green"),
    COLOUR_RED("gui.colour.red"),
    COLOUR_BLACK("gui.colour.black");

    private final String key;

    GuiMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

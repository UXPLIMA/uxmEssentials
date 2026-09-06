package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The teleport-sound selector a warp editor opens to pick a warp's departure or arrival sound: a three-row picker
 * with one icon per preset sound, a custom-name button, a back button to the editor, and a remove button. Clicking a
 * sound runs the same set the old bespoke window did. The warp's departure or arrival sound through the shared
 * {@link EditableWarp} loader, then returns the viewer to the warp editor.
 *
 * <p>The window draws through the menu engine's selector runtime ({@link Menus#openSelector}), so it is a
 * holder-backed engine selector routed and torn down by the one menu listener and one {@code closeMenu}. The option
 * list is the same preset set the original fixed view drew, so a player sees an identical menu, only the machinery
 * behind it changed. The selector serves both server and player warps: the editable warp is resolved through the
 * loader from the warp name and its (nullable) owner, so the single picker covers either kind exactly as before. The
 * preset list is still exposed through {@link #getOptions()} so the engine-rendered server-warp sound menu can share
 * the same options. Every visible string resolves from the warps catalog.
 */
@NullMarked
public final class WarpSoundSelectorView {

    private static final int ROWS = 3;
    private static final int OPTION_LIMIT = 18;
    private static final int CUSTOM_SLOT = 18;
    private static final int BACK_SLOT = 22;
    private static final int REMOVE_SLOT = 26;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    private final Messages messages;
    private final Menus menus;
    private final EditableWarpLoader loader;
    private final WarpEditorView editorView;
    private final TextInput textInput;

    private WarpSoundSelectorView(
            Messages messages, Menus menus, EditableWarpLoader loader, WarpEditorView editorView, TextInput textInput) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /**
     * Build the sound selector over the warps wiring's collaborators. The editable-warp loader is built here from the
     * server-warp repository and the editor view (the same pair the editor listener loads through), so the warps wiring
     * needs only the public collaborators it already holds. Mirrors {@code WarpSoundMenu.create}.
     */
    public static WarpSoundSelectorView create(
            Messages messages, Menus menus, WarpRepository repository, WarpEditorView editorView, TextInput textInput) {
        EditableWarpLoader loader = new EditableWarpLoader(repository, editorView);
        return new WarpSoundSelectorView(messages, menus, loader, editorView, textInput);
    }

    /** Open the sound selector for {@code warpName} (server warp when {@code warpOwner} is null), returning to the editor on pick. */
    public void open(
            Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner, boolean isDeparture) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        WarpsMessageKey titleKey = isDeparture
                ? WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_TITLE_DEPARTURE
                : WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_TITLE_ARRIVAL;
        menus.openSelector(
                viewer,
                text(viewer, titleKey),
                ROWS,
                FILLER,
                buttons(player, viewer, warpName, warpOwner, isDeparture));
    }

    private List<SelectorButton> buttons(
            Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, boolean isDeparture) {
        List<SelectorButton> buttons = new ArrayList<>();
        List<SoundOption> options = getOptions();
        for (int i = 0; i < Math.min(options.size(), OPTION_LIMIT); i++) {
            SoundOption opt = options.get(i);
            ItemStack icon = ItemBuilder.of(opt.material())
                    .name(Tiles.blankName())
                    .lore(Tiles.titled(
                            text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_ENTRY_NAME,
                                    Map.of("sound", opt.displayName())),
                            List.of(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_ENTRY_LORE,
                                    Map.of("sound", opt.soundName())))))
                    .build();
            buttons.add(SelectorButton.of(
                    i, icon, () -> pick(player, viewer, name, owner, isDeparture, Optional.of(opt.soundName()))));
        }
        buttons.add(SelectorButton.of(
                CUSTOM_SLOT, customIcon(viewer), () -> custom(player, viewer, name, owner, isDeparture)));
        buttons.add(SelectorButton.of(BACK_SLOT, backIcon(viewer), () -> editorView.open(player, viewer, name, owner)));
        buttons.add(SelectorButton.of(
                REMOVE_SLOT,
                removeIcon(viewer),
                () -> pick(player, viewer, name, owner, isDeparture, Optional.empty())));
        return buttons;
    }

    /** Set the chosen sound on the warp's departure or arrival side, then reopen the editor: the old click's effect. */
    private void pick(
            Player player,
            PlayerRef viewer,
            String name,
            @Nullable PlayerRef owner,
            boolean isDeparture,
            Optional<String> sound) {
        saveSound(name, owner, isDeparture, sound);
        editorView.open(player, viewer, name, owner);
    }

    /** Prompt for a custom sound name through the shared input seam, exactly as the old custom button did. */
    private void custom(Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, boolean isDeparture) {
        player.closeInventory();
        MessageKey promptKey = isDeparture
                ? WarpsMessageKey.WARP_EDITOR_SOUND_DEPARTURE_PROMPT
                : WarpsMessageKey.WARP_EDITOR_SOUND_ARRIVAL_PROMPT;
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.sound", promptKey),
                input -> pick(player, viewer, name, owner, isDeparture, Optional.of(input.toLowerCase(Locale.ROOT))),
                () -> open(player, viewer, name, owner, isDeparture));
    }

    private void saveSound(String name, @Nullable PlayerRef owner, boolean isDeparture, Optional<String> sound) {
        EditableWarp warp = loader.load(name, owner);
        if (warp == null) {
            return;
        }
        if (isDeparture) {
            warp.setDepartureSound(sound);
        } else {
            warp.setArrivalSound(sound);
        }
    }

    private ItemStack customIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.ANVIL)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_CUSTOM_NAME),
                        List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_CUSTOM_LORE))))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.ARROW)
                .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                .build();
    }

    private ItemStack removeIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.LAVA_BUCKET)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_REMOVE_NAME),
                        List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_REMOVE_LORE))))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /** The preset sounds the selector grids, shared with the engine-rendered server-warp sound menu. */
    public List<SoundOption> getOptions() {
        return List.of(
                new SoundOption("minecraft:entity.enderman.teleport", Material.ENDER_PEARL, "Enderman Teleport"),
                new SoundOption("minecraft:entity.player.teleport", Material.CHORUS_FRUIT, "Player Teleport"),
                new SoundOption("minecraft:block.portal.travel", Material.OBSIDIAN, "Portal Travel"),
                new SoundOption("minecraft:block.note_block.chime", Material.NOTE_BLOCK, "Note Block Chime"),
                new SoundOption("minecraft:block.note_block.bell", Material.BELL, "Note Block Bell"),
                new SoundOption("minecraft:block.note_block.flute", Material.FEATHER, "Note Block Flute"),
                new SoundOption("minecraft:block.note_block.guitar", Material.STRING, "Note Block Guitar"),
                new SoundOption("minecraft:block.note_block.harp", Material.REDSTONE, "Note Block Harp"),
                new SoundOption("minecraft:block.beacon.activate", Material.BEACON, "Beacon Activate"),
                new SoundOption(
                        "minecraft:entity.experience_orb.pickup", Material.EXPERIENCE_BOTTLE, "Experience Pickup"),
                new SoundOption("minecraft:entity.firework_rocket.launch", Material.FIREWORK_ROCKET, "Firework Launch"),
                new SoundOption("minecraft:entity.lightning_bolt.thunder", Material.LIGHTNING_ROD, "Thunder Strike"),
                new SoundOption("minecraft:entity.wither.spawn", Material.WITHER_SKELETON_SKULL, "Wither Spawn"),
                new SoundOption("minecraft:block.anvil.use", Material.ANVIL, "Anvil Use"));
    }

    public record SoundOption(String soundName, Material material, String displayName) {}
}

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
 * The teleport-particle selector a warp editor opens to pick a warp's departure or arrival particle: a three-row
 * picker with one icon per preset particle, a custom-name button, a back button to the editor, and a remove button.
 * Clicking a particle runs the same set the old bespoke window did. The warp's departure or arrival particle through
 * the shared {@link EditableWarp} loader, then returns the viewer to the warp editor.
 *
 * <p>The window draws through the menu engine's selector runtime ({@link Menus#openSelector}), so it is a
 * holder-backed engine selector routed and torn down by the one menu listener and one {@code closeMenu}. The option
 * list is the same preset set the original fixed view drew, so a player sees an identical menu, only the machinery
 * behind it changed. The selector serves both server and player warps: the editable warp is resolved through the
 * loader from the warp name and its (nullable) owner, so the single picker covers either kind exactly as before.
 * Every visible string resolves from the warps catalog.
 */
@NullMarked
public final class WarpParticleSelectorView {

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

    private WarpParticleSelectorView(
            Messages messages, Menus menus, EditableWarpLoader loader, WarpEditorView editorView, TextInput textInput) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /**
     * Build the particle selector over the warps wiring's collaborators. The editable-warp loader is built here from
     * the server-warp repository and the editor view (the same pair the editor listener loads through), so the warps
     * wiring needs only the public collaborators it already holds. Mirrors {@code WarpSoundMenu.create}.
     */
    public static WarpParticleSelectorView create(
            Messages messages, Menus menus, WarpRepository repository, WarpEditorView editorView, TextInput textInput) {
        EditableWarpLoader loader = new EditableWarpLoader(repository, editorView);
        return new WarpParticleSelectorView(messages, menus, loader, editorView, textInput);
    }

    /** Open the particle selector for {@code warpName} (server warp when {@code warpOwner} is null), returning to the editor on pick. */
    public void open(
            Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner, boolean isDeparture) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        WarpsMessageKey titleKey = isDeparture
                ? WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_DEPARTURE
                : WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_ARRIVAL;
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
        List<ParticleOption> options = getOptions();
        for (int i = 0; i < Math.min(options.size(), OPTION_LIMIT); i++) {
            ParticleOption opt = options.get(i);
            ItemStack icon = ItemBuilder.of(opt.material())
                    .name(Tiles.blankName())
                    .lore(Tiles.titled(
                            text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_NAME,
                                    Map.of("particle", opt.displayName())),
                            List.of(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_LORE,
                                    Map.of("particle", opt.particleName())))))
                    .build();
            buttons.add(SelectorButton.of(
                    i, icon, () -> pick(player, viewer, name, owner, isDeparture, Optional.of(opt.particleName()))));
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

    /** Set the chosen particle on the warp's departure or arrival side, then reopen the editor: the old click's effect. */
    private void pick(
            Player player,
            PlayerRef viewer,
            String name,
            @Nullable PlayerRef owner,
            boolean isDeparture,
            Optional<String> particle) {
        saveParticle(name, owner, isDeparture, particle);
        editorView.open(player, viewer, name, owner);
    }

    /** Prompt for a custom particle name through the shared input seam, exactly as the old custom button did. */
    private void custom(Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, boolean isDeparture) {
        player.closeInventory();
        MessageKey promptKey = isDeparture
                ? WarpsMessageKey.WARP_EDITOR_PARTICLE_DEPARTURE_PROMPT
                : WarpsMessageKey.WARP_EDITOR_PARTICLE_ARRIVAL_PROMPT;
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.particle", promptKey),
                input -> pick(player, viewer, name, owner, isDeparture, Optional.of(input.toUpperCase(Locale.ROOT))),
                () -> open(player, viewer, name, owner, isDeparture));
    }

    private void saveParticle(String name, @Nullable PlayerRef owner, boolean isDeparture, Optional<String> particle) {
        EditableWarp warp = loader.load(name, owner);
        if (warp == null) {
            return;
        }
        if (isDeparture) {
            warp.setDepartureParticle(particle);
        } else {
            warp.setArrivalParticle(particle);
        }
    }

    private ItemStack customIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.ANVIL)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_NAME),
                        List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_LORE))))
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
                        text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_NAME),
                        List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_LORE))))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /** The preset particles the selector grids. */
    public List<ParticleOption> getOptions() {
        return List.of(
                new ParticleOption("minecraft:portal", Material.OBSIDIAN, "Portal"),
                new ParticleOption("minecraft:witch", Material.BREWING_STAND, "Witch"),
                new ParticleOption("minecraft:dragon_breath", Material.DRAGON_BREATH, "Dragon Breath"),
                new ParticleOption("minecraft:flame", Material.TORCH, "Flame"),
                new ParticleOption("minecraft:happy_villager", Material.EMERALD, "Happy Villager"),
                new ParticleOption("minecraft:heart", Material.RED_DYE, "Heart"),
                new ParticleOption("minecraft:cloud", Material.FEATHER, "Cloud"),
                new ParticleOption("minecraft:reverse_portal", Material.CRYING_OBSIDIAN, "Reverse Portal"),
                new ParticleOption("minecraft:totem_of_undying", Material.TOTEM_OF_UNDYING, "Totem"),
                new ParticleOption("minecraft:smoke", Material.CAMPFIRE, "Smoke"),
                new ParticleOption("minecraft:enchant", Material.ENCHANTING_TABLE, "Enchant"),
                new ParticleOption("minecraft:glow", Material.GLOW_INK_SAC, "Glow"),
                new ParticleOption("minecraft:soul", Material.SOUL_SOIL, "Soul"),
                new ParticleOption("minecraft:explosion", Material.TNT, "Explosion"));
    }

    public record ParticleOption(String particleName, Material material, String displayName) {}
}

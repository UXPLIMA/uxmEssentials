package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundSelectorView.SoundOption;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the warp sound selector with the menu engine and opens it. This is the pilot proving the engine
 * end-to-end on real domain code, and the shape every feature menu follows when Phase 3 migrates the rest: a small
 * class that registers a list source, the placeholders its entries need, and the click actions, then loads the
 * {@code warp-sounds} spec and hands it to {@link Menus}.
 *
 * <p>The selector serves a server warp; the menu opens with a {@link WarpSoundEdit} subject carrying the warp and
 * whether the click sets its departure or arrival sound, so the single spec covers both editor buttons. The option
 * grid is the same preset list the original fixed view drew, so a player sees an identical menu, only the
 * machinery behind it changed.
 */
@NullMarked
public final class WarpSoundMenu {

    /** The engine spec id this menu registers and opens under. */
    static final String SPEC_ID = "warp-sounds";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-sounds.conf";

    private final Menus menus;
    private final WarpSoundSelectorView optionSource;
    private final EditableWarpLoader loader;
    private final WarpEditorView editorView;
    private final TextInput textInput;

    private WarpSoundMenu(
            Menus menus,
            WarpSoundSelectorView optionSource,
            EditableWarpLoader loader,
            WarpEditorView editorView,
            TextInput textInput) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.optionSource = Objects.requireNonNull(optionSource, "optionSource");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /**
     * Build the sound menu over the warps wiring's collaborators. The editable-warp loader is built here from the
     * server-warp repository and the editor view (the same pair the editor listener loads through), so the warps
     * wiring needs only the public collaborators it already holds.
     */
    public static WarpSoundMenu create(
            Menus menus,
            WarpSoundSelectorView optionSource,
            WarpRepository repository,
            WarpEditorView editorView,
            TextInput textInput) {
        EditableWarpLoader loader = new EditableWarpLoader(repository, editorView);
        return new WarpSoundMenu(menus, optionSource, loader, editorView, textInput);
    }

    /** Register the bindings the spec names and the spec itself; called once at warps wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("warp:sound-options", ctx -> optionSource.getOptions());
        bindings.placeholder("sound", ctx -> ctx.entry(SoundOption.class).displayName());
        bindings.placeholder(
                "sound_material", ctx -> ctx.entry(SoundOption.class).material().name());
        bindings.action("warp:set-sound", this::setSound);
        bindings.action("warp:custom-sound", this::customSound);
        bindings.action("warp:remove-sound", this::removeSound);
        bindings.action("warp:edit-back", this::back);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /** Open the sound selector for a server warp on the side the {@code edit} subject names. */
    public void open(PlayerRef viewer, WarpSoundEdit edit) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(edit, "edit");
        menus.open(viewer, SPEC_ID, edit);
    }

    /** Set the clicked preset sound on the warp's departure or arrival side, then reopen the editor. */
    private void setSound(MenuActionContext ctx) {
        SoundOption option = ctx.entry(SoundOption.class);
        saveSound(ctx, Optional.of(option.soundName()));
        reopenEditor(ctx);
    }

    /** Prompt for a custom sound name through the shared text-input seam, exactly as the old custom button did. */
    private void customSound(MenuActionContext ctx) {
        WarpSoundEdit edit = ctx.subject(WarpSoundEdit.class);
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        String name = edit.warp().value();
        WarpsMessageKey promptKey = edit.departure()
                ? WarpsMessageKey.WARP_EDITOR_SOUND_DEPARTURE_PROMPT
                : WarpsMessageKey.WARP_EDITOR_SOUND_ARRIVAL_PROMPT;
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.sound", promptKey),
                input -> {
                    save(edit, Optional.of(input.toLowerCase(Locale.ROOT)));
                    editorView.open(player, viewer, name, null);
                },
                () -> open(viewer, edit));
    }

    /** Clear the warp's sound on the subject's side, then reopen the editor. */
    private void removeSound(MenuActionContext ctx) {
        saveSound(ctx, Optional.empty());
        reopenEditor(ctx);
    }

    /** The back button: reopen the warp editor for this warp. */
    private void back(MenuActionContext ctx) {
        reopenEditor(ctx);
    }

    private void saveSound(MenuActionContext ctx, Optional<String> sound) {
        save(ctx.subject(WarpSoundEdit.class), sound);
    }

    private void save(WarpSoundEdit edit, Optional<String> sound) {
        EditableWarp warp = loader.load(edit.warp().value(), null);
        if (warp == null) {
            return;
        }
        if (edit.departure()) {
            warp.setDepartureSound(sound);
        } else {
            warp.setArrivalSound(sound);
        }
    }

    private void reopenEditor(MenuActionContext ctx) {
        editorView.open(
                ctx.player(),
                ctx.viewer(),
                ctx.subject(WarpSoundEdit.class).warp().value(),
                null);
    }
}

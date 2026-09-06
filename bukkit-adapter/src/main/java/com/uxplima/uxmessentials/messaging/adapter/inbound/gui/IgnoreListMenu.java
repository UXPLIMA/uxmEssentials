package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the ignore-list manager ({@code /ignore} with no arguments) with the menu engine and opens it. A small
 * class that registers the ignored-player list source, the placeholder its entries need, and the un-ignore and
 * add-by-name click actions, then loads the {@code messaging-ignore} spec and hands it to {@link Menus}, the same
 * shape the vault selector and warp sound menus follow.
 *
 * <p>The owner's ignore list is a database read, so the {@code messaging:ignores} source resolves it off the
 * viewer's region thread (the engine does the hop) and touches no Bukkit API. The {@code ignore_target} placeholder
 * reads the bound entry to label each head, and the click actions route through the same {@link Unignore} and
 * {@link Ignore} use cases the {@code /unignore} and {@code /ignore <player>} verbs drive, so a notifier line still
 * fires and the store stays consistent. The add button prompts for a name through the shared text-input seam, then
 * ignores that player and reopens, exactly as the old view's create button did.
 */
@NullMarked
public final class IgnoreListMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "messaging-ignore";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/messaging/gui/messaging-ignore.conf";

    private static final String IGNORE_ADD_INPUT_KEY = "messaging.ignore-add";

    private final Menus menus;
    private final Scheduler scheduler;
    private final IgnoreStore ignores;
    private final Ignore ignoreUseCase;
    private final Unignore unignoreUseCase;
    private final PlayerLookup players;
    private final TextInput textInput;

    public IgnoreListMenu(
            Menus menus,
            Scheduler scheduler,
            IgnoreStore ignores,
            Ignore ignoreUseCase,
            Unignore unignoreUseCase,
            PlayerLookup players,
            TextInput textInput) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.ignoreUseCase = Objects.requireNonNull(ignoreUseCase, "ignoreUseCase");
        this.unignoreUseCase = Objects.requireNonNull(unignoreUseCase, "unignoreUseCase");
        this.players = Objects.requireNonNull(players, "players");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /** Register the bindings the spec names and the spec itself; called once at messaging wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("messaging:ignores", this::ignored);
        bindings.placeholder("ignore_target", this::target);
        bindings.action("messaging:unignore", this::unignore);
        bindings.action("messaging:ignore-add", this::promptAdd);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the ignore-list manager for {@code viewer} (the live player resolved by the engine). */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, SPEC_ID, null);
    }

    /** The viewer's ignore entries, read off the region thread (it reads the database); touches no Bukkit API. */
    private List<IgnoreEntry> ignored(MenuContext ctx) {
        return ignores.load(ctx.viewer()).entries();
    }

    /** The bound entry's ignored-player name, for the head label and lore. */
    private String target(MenuContext ctx) {
        return ctx.entry(IgnoreEntry.class).ignored().name();
    }

    /** Left-click a head: un-ignore that player through the same {@link Unignore} use case, then reopen the list. */
    private void unignore(MenuActionContext ctx) {
        PlayerRef owner = ctx.viewer();
        IgnoreEntry entry = ctx.entry(IgnoreEntry.class);
        scheduler.async(() -> {
            unignoreUseCase.unignore(owner, entry.ignored());
            open(owner);
        });
    }

    /** Prompt for a name through the shared text-input seam, exactly as the old create button did. */
    private void promptAdd(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef owner = ctx.viewer();
        player.closeInventory();
        textInput.prompt(
                player,
                owner,
                InputRequest.of(IGNORE_ADD_INPUT_KEY, MessagingMessageKey.GUI_IGNORE_ADD_PROMPT),
                name -> addByName(owner, name),
                () -> open(owner));
    }

    /**
     * Resolve {@code name} to an online player and ignore them through the use case, then reopen the list. An
     * unknown name reopens the list unchanged (the same online resolution {@code /ignore <player>} applies; the
     * self-check and idempotency are the use case's). This is the seam the add prompt routes a submitted line to; it
     * is public only so the golden test can drive it without firing a live prompt.
     */
    public void addByName(PlayerRef owner, String name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Optional<PlayerRef> target = players.findOnlineByName(name);
        scheduler.async(() -> {
            target.ifPresent(ref -> ignoreUseCase.ignore(owner, ref));
            open(owner);
        });
    }
}

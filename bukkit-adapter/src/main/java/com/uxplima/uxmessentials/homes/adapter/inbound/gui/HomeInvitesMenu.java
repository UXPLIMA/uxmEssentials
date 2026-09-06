package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenus.ActionMenuOpener;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.InviteToHome;
import com.uxplima.uxmessentials.homes.application.ListHomeInvites;
import com.uxplima.uxmessentials.homes.application.UninviteFromHome;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the invited-players list (opened from a home's action menu) with the menu engine and opens it. A small
 * class that registers the invited-player list source, the placeholders its heads need, and the revoke, add, and back
 * click actions, then loads the {@code home-invites} spec and hands it to {@link Menus}, the same shape the ignore
 * list and vault selector follow.
 *
 * <p>The home's invite set is a database read whose names resolve through the offline-capable kernel
 * {@link PlayerLookup}, so the {@code homes:invited-players} source runs off the viewer's region thread (the engine
 * does the hop) and touches no Bukkit API. When the home has no invites the source emits one placeholder entry so the
 * "no invited players" head shows in the first cell, exactly as the old view did. The {@code invited_player}
 * placeholder resolves each head's label and the revoke action routes through the same {@link UninviteFromHome} use
 * case {@code /uninvite} drives; the add button prompts for a name through the shared text-input seam, then invites
 * that player and reopens. The back button returns to the action menu through the shared {@link ActionMenuOpener}.
 */
@NullMarked
public final class HomeInvitesMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "home-invites";

    private static final String SPEC_RESOURCE = "modules/homes/gui/home-invites.conf";

    private static final String INVITE_ADD_INPUT_KEY = "home.invite-add";

    private final Menus menus;
    private final Scheduler scheduler;
    private final Messages messages;
    private final ListHomeInvites listInvites;
    private final InviteToHome inviteToHome;
    private final UninviteFromHome uninviteFromHome;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final TextInput textInput;
    private final ActionMenuOpener actionMenu;

    public HomeInvitesMenu(
            Menus menus,
            Scheduler scheduler,
            Messages messages,
            ListHomeInvites listInvites,
            InviteToHome inviteToHome,
            UninviteFromHome uninviteFromHome,
            PlayerLookup players,
            Notifier notifier,
            TextInput textInput,
            ActionMenuOpener actionMenu) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.listInvites = Objects.requireNonNull(listInvites, "listInvites");
        this.inviteToHome = Objects.requireNonNull(inviteToHome, "inviteToHome");
        this.uninviteFromHome = Objects.requireNonNull(uninviteFromHome, "uninviteFromHome");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.actionMenu = Objects.requireNonNull(actionMenu, "actionMenu");
    }

    /** Register the bindings the spec names and the spec itself; called once at homes wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("homes:invited-players", this::invited);
        bindings.placeholder("invited_player", this::name);
        bindings.placeholder("invited_player_lore", this::lore);
        bindings.action("homes:uninvite", this::revoke);
        bindings.action("homes:invite-add", this::promptAdd);
        bindings.action("homes:invites-back", this::back);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the invited-players list for {@code home}; the live player is resolved by the engine. */
    public void open(PlayerRef viewer, Home home) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        menus.open(viewer, SPEC_ID, home);
    }

    /**
     * The home's invited players, read off the region thread (it reads the database and resolves names through the
     * offline-capable lookup); touches no Bukkit API. Sorted by name so the order is stable. An empty set yields a
     * single empty-marker entry so the "no invited players" head shows, exactly as the old view did.
     */
    private List<InvitedEntry> invited(MenuContext ctx) {
        Home home = ctx.subject(Home.class);
        Set<UUID> invited = listInvites.of(home.owner(), home.slot());
        List<InvitedEntry> entries = new ArrayList<>();
        for (UUID uuid : invited) {
            String resolved = players.findByUuid(uuid).map(PlayerRef::name).orElse(uuid.toString());
            entries.add(new InvitedEntry(uuid, resolved));
        }
        entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        if (entries.isEmpty()) {
            entries.add(new InvitedEntry(null, ""));
        }
        return entries;
    }

    /** The bound head's label: the invited player's name, or the empty-list line for the marker entry. */
    private String name(MenuContext ctx) {
        InvitedEntry entry = ctx.entry(InvitedEntry.class);
        if (entry.uuid() == null) {
            return messages.resolve(ctx.viewer(), HomesMessageKey.HOME_INVITES_EMPTY_NAME, Map.of());
        }
        return messages.resolve(
                ctx.viewer(), HomesMessageKey.HOME_INVITES_ENTRY_NAME, Map.of("invited_player", entry.name()));
    }

    /** The bound head's lore: the revoke hint for a real entry, nothing for the empty marker. */
    private String lore(MenuContext ctx) {
        InvitedEntry entry = ctx.entry(InvitedEntry.class);
        if (entry.uuid() == null) {
            return "";
        }
        return messages.resolve(ctx.viewer(), HomesMessageKey.HOME_INVITES_ENTRY_LORE, Map.of());
    }

    /** Left-click a head: revoke that invite through the use case, then reopen the list (no-op on the marker). */
    private void revoke(MenuActionContext ctx) {
        InvitedEntry entry = ctx.entry(InvitedEntry.class);
        UUID target = entry.uuid();
        if (target == null) {
            return;
        }
        Home home = ctx.subject(Home.class);
        PlayerRef viewer = ctx.viewer();
        scheduler.async(() -> {
            uninviteFromHome.uninvite(home.owner(), home.slot(), new PlayerRef(target, entry.name()));
            scheduler.onEntity(viewer, () -> open(viewer, home));
        });
    }

    /** Prompt for a name through the shared text-input seam, exactly as the old add button did. */
    private void promptAdd(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Home home = ctx.subject(Home.class);
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(INVITE_ADD_INPUT_KEY, HomesMessageKey.HOME_INVITES_ADD_PROMPT),
                name -> addByName(viewer, home, name),
                () -> open(viewer, home));
    }

    /**
     * Resolve {@code name} and invite that player to {@code home}, then reopen the list. An unknown name sends the
     * unknown-player line and reopens unchanged, mirroring the old add flow. The lookup runs off the tick thread (it
     * may call a blocking offline lookup); public only so the golden test can drive it without a live prompt.
     */
    public void addByName(PlayerRef viewer, Home home, String name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(name, "name");
        String typed = name.strip();
        scheduler.async(() -> {
            Optional<PlayerRef> resolved = players.findByName(typed);
            if (resolved.isEmpty()) {
                scheduler.onEntity(viewer, () -> {
                    notifier.send(viewer, HomesMessageKey.HOME_INVITES_UNKNOWN_PLAYER, Map.of("player", typed));
                    open(viewer, home);
                });
                return;
            }
            inviteToHome.invite(home.owner(), home.slot(), resolved.get());
            scheduler.onEntity(viewer, () -> open(viewer, home));
        });
    }

    /** Left-click the back button: return to the home action menu for this home. */
    private void back(MenuActionContext ctx) {
        actionMenu.open(ctx.player(), ctx.viewer(), ctx.subject(Home.class));
    }

    /**
     * One invited player resolved to a display name, or the empty-list marker when {@link #uuid} is {@code null}.
     * The placeholders and the revoke action branch on whether the uuid is present, so a real head and the empty
     * placeholder share one list template: the same owned/locked pattern the vault selector uses.
     *
     * @param uuid the invited player's id, or {@code null} for the empty-list marker
     * @param name the invited player's resolved display name, or empty for the marker
     */
    public record InvitedEntry(@Nullable UUID uuid, String name) {

        public InvitedEntry {
            Objects.requireNonNull(name, "name");
        }
    }
}

package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Assembles the messaging module's three player-facing GUIs. The settings panel, the ignore-list manager, and
 * the mailbox. Over the existing use cases and stores, and hands the openers to the commands and the
 * {@code /uxmess gui} hub. Each view reads the raw store the corresponding command reads (so the rendered state
 * matches in-game state) and writes through the same use case (so notifiers and persistence stay consistent).
 * The views are constructed once here and reused for every viewer.
 *
 * <p>The four stores are passed in individually rather than as the wiring's package-private {@code Stores}
 * record, so this assembler stays decoupled from the wiring's internal shape.
 */
@NullMarked
public final class MessagingGuiViews {

    private final MessagingSettingsView settingsView;
    private final IgnoreListMenu ignoreMenu;
    private final MailboxMenu mailboxMenu;

    private MessagingGuiViews(MessagingSettingsView settingsView, IgnoreListMenu ignoreMenu, MailboxMenu mailboxMenu) {
        this.settingsView = settingsView;
        this.ignoreMenu = ignoreMenu;
        this.mailboxMenu = mailboxMenu;
    }

    /** Build the three views over the messaging use cases, the raw stores, and the module's GUI layouts. */
    public static MessagingGuiViews create(
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            Permissions permissions,
            MessagingServices services,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            IgnoreStore ignores,
            MailRepository mail,
            PlayerLookup players,
            TextInput textInput,
            GuiLayouts layouts,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder,
            Logger log) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(toggles, "toggles");
        Objects.requireNonNull(socialSpy, "socialSpy");
        Objects.requireNonNull(ignores, "ignores");
        Objects.requireNonNull(mail, "mail");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(layouts, "layouts");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");

        MessagingSettingsView settingsView = new MessagingSettingsView(
                guiText, scheduler, layouts, messages, toggles, socialSpy, permissions, menus);

        // The ignore-list manager now renders through the menu engine: its grid geometry lives in the
        // messaging-ignore spec, its un-ignore and add-by-name clicks route through the same use cases.
        IgnoreListMenu ignoreMenu = new IgnoreListMenu(
                menus, scheduler, ignores, services.ignore(), services.unignore(), players, textInput);
        ignoreMenu.register(menuBindings, dataFolder, log);

        // The mailbox renders through the menu engine too: the list and per-mail detail geometry live in the
        // messaging-mailbox / messaging-mail-detail specs, its clear button routes through the same ClearMail use
        // case, and opening the list marks the box read in the same off-thread hop the old view did.
        MailboxMenu mailboxMenu = new MailboxMenu(menus, guiText, scheduler, mail, services.clearMail());
        mailboxMenu.register(menuBindings, dataFolder, log);

        return new MessagingGuiViews(settingsView, ignoreMenu, mailboxMenu);
    }

    /** Open the messaging settings panel for {@code viewer}. */
    public void openSettings(Player player, PlayerRef viewer) {
        settingsView.open(player, viewer);
    }

    /** Open the ignore-list manager for {@code viewer}; the engine resolves the live player from {@code viewer}. */
    public void openIgnore(Player player, PlayerRef viewer) {
        ignoreMenu.open(viewer);
    }

    /** Open the mailbox for {@code viewer}; the engine resolves the live player from {@code viewer}. */
    public void openMailbox(Player player, PlayerRef viewer) {
        mailboxMenu.open(viewer);
    }

    /** The settings panel, for tests. */
    public MessagingSettingsView settingsView() {
        return settingsView;
    }

    /** The ignore-list manager, for tests. */
    public IgnoreListMenu ignoreMenu() {
        return ignoreMenu;
    }

    /** The mailbox, for tests. */
    public MailboxMenu mailboxMenu() {
        return mailboxMenu;
    }
}

package com.uxplima.uxmessentials.discordlink.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordBridge;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player Discord link-status panel ({@code /discordlink gui}, and the discordlink entry on the
 * {@code /uxmess gui} hub): a {@link SettingsPanelView} of two buttons read FRESH each open: a read-only status
 * line showing the viewer's current binding, and a state-dependent action. When the viewer is not linked the
 * action runs the {@link BeginLink} use case (exactly what {@code /discordlink} does), telling them the generated
 * code through the same {@link Notifier} chat messages; when they are linked the action runs the
 * {@link Unlink} use case behind a confirm (exactly what {@code /discordunlink} does).
 *
 * <p>The panel holds no domain logic of its own. Linking is code-based. The redemption itself happens in Discord
 * through the bridge's {@code /link} slash command, not in any in-game menu, so the panel honestly surfaces only
 * what the use cases support: the status, a generate-code button, and an unlink button. Every visible string is a
 * {@link DiscordlinkMessageKey}; the use-case writes hop off the tick thread through the kernel {@link Scheduler}.
 */
@NullMarked
public final class DiscordStatusView {

    private static final String MODULE = "discordlink";
    private static final String PANEL_LAYOUT = "discord-status";
    private static final int STATUS_SLOT = 11;
    private static final int ACTION_SLOT = 15;

    private final SettingsPanelView panel;
    private final BeginLink beginLink;
    private final Unlink unlink;
    private final LinkStatus linkStatus;
    private final Notifier notifier;
    private final Messages messages;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final DiscordBridge bridge;
    private final Menus menus;

    public DiscordStatusView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            BeginLink beginLink,
            Unlink unlink,
            LinkStatus linkStatus,
            Notifier notifier,
            DiscordBridge bridge,
            Menus menus) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.beginLink = Objects.requireNonNull(beginLink, "beginLink");
        this.unlink = Objects.requireNonNull(unlink, "unlink");
        this.linkStatus = Objects.requireNonNull(linkStatus, "linkStatus");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.menus = Objects.requireNonNull(menus, "menus");

        EntityEditorLayout layout = guiLayouts.loadEntityEditor(
                MODULE, PANEL_LAYOUT, EntityEditorLayout.codeDefault(List.of(STATUS_SLOT, ACTION_SLOT), 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .menus(menus)
                .layout(layout)
                .title(DiscordlinkMessageKey.GUI_TITLE)
                .valueLore(DiscordlinkMessageKey.GUI_VALUE_LORE)
                .backName(DiscordlinkMessageKey.GUI_BACK)
                .settings(this::buttons)
                .onBack((player, who) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, Objects.requireNonNull(viewer, "viewer"));
    }

    /** The settings panel, exposed package-private so the test can assert the button↔slot mapping. */
    SettingsPanelView panel() {
        return panel;
    }

    private List<EditableProperty> buttons(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        boolean linked = linkStatus.status(viewer).isPresent();
        ActionProperty status = new ActionProperty(
                DiscordlinkMessageKey.GUI_STATUS, Material.PLAYER_HEAD, this::statusHint, (player, reopen) -> {});
        EditableProperty action = linked ? unlinkButton() : linkButton();
        return List.of(status, action);
    }

    private ActionProperty linkButton() {
        return new ActionProperty(
                DiscordlinkMessageKey.GUI_LINK,
                Material.NAME_TAG,
                who -> messages.resolve(who, DiscordlinkMessageKey.GUI_LINK_HINT, Map.of()),
                (player, reopen) -> {
                    PlayerRef viewer = BukkitRefs.toRef(player);
                    generateCode(viewer);
                    reopen.run();
                });
    }

    private ActionProperty unlinkButton() {
        return new ActionProperty(
                DiscordlinkMessageKey.GUI_UNLINK,
                Material.BARRIER,
                who -> messages.resolve(who, DiscordlinkMessageKey.GUI_UNLINK_HINT, Map.of()),
                (player, reopen) -> confirmUnlink(player, reopen));
    }

    private void confirmUnlink(Player player, Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        Component title = guiText.text(viewer, DiscordlinkMessageKey.GUI_UNLINK_CONFIRM);
        // The unlink gate is the engine's two-button confirm menu: yes runs Unlink, no reopens the status panel.
        menus.confirm(viewer, title, () -> runUnlink(viewer, reopen), reopen);
    }

    private void runUnlink(PlayerRef viewer, Runnable reopen) {
        scheduler.async(() -> {
            DiscordlinkMessageKey key = unlink.unlink(viewer).isOk()
                    ? DiscordlinkMessageKey.DISCORD_LINK_UNLINKED
                    : DiscordlinkMessageKey.DISCORD_LINK_NOT_LINKED;
            notifier.send(viewer, key);
            scheduler.onEntity(viewer, reopen);
        });
    }

    /**
     * Generate a fresh code through the {@link BeginLink} use case and tell the player the code and how-to through
     * the same chat messages {@code /discordlink} sends. Package-private so the panel test can drive the seam.
     */
    void generateCode(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (!bridge.available()) {
            // Mirror the /discordlink gate: with no connected bridge a code redeems nowhere, so decline instead.
            notifier.send(viewer, DiscordlinkMessageKey.DISCORD_NOT_CONFIGURED);
            return;
        }
        LinkCode code = beginLink.begin(viewer);
        notifier.send(viewer, DiscordlinkMessageKey.DISCORD_LINK_CODE, Map.of("code", code.value()));
        notifier.send(viewer, DiscordlinkMessageKey.DISCORD_LINK_HOWTO, Map.of("code", code.value()));
    }

    private String statusHint(PlayerRef viewer) {
        Optional<ConfirmedLink> link = linkStatus.status(viewer);
        return link.map(confirmed -> messages.resolve(
                        viewer,
                        DiscordlinkMessageKey.GUI_STATUS_LINKED,
                        Map.of("discord", confirmed.discordId().value())))
                .orElseGet(() -> messages.resolve(viewer, DiscordlinkMessageKey.GUI_STATUS_UNLINKED, Map.of()));
    }
}

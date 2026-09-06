package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player messaging settings panel ({@code /msgsettings}, and the messaging entry on the
 * {@code /uxmess gui} hub): a player flips their own message-accept switch (the {@code /msgtoggle} state), and a
 * staff member additionally flips their own social-spy switch (the {@code /socialspy} ALL flag). Each toggle
 * reads and writes the same store the corresponding command does, so opening the panel always shows the live
 * state and a click is the same mutation the command makes.
 *
 * <p>The social-spy toggle is staff-only: it is built into the panel only for a viewer who holds the social-spy
 * permission, so a player without it sees one toggle and the second property slot stays empty. The panel holds
 * no logic of its own: the settings are re-read fresh on every open, closing over the viewer.
 *
 * <p>The panel renders through the menu engine's property-editor runtime: the geometry and materials still come
 * from the {@code messaging-settings} layout conf, the title/value/back text still resolve through the messaging
 * catalog, but the window is a holder-backed engine editor opened via {@link Menus#openEditor}, routed and torn
 * down by the one menu listener and one {@code closeMenu}. The subject is the viewer's own {@link PlayerRef} (the
 * panel edits the viewer themselves), so the toggle list closes over it on every draw.
 */
@NullMarked
public final class MessagingSettingsView {

    private static final String MODULE = "messaging";
    private static final String LAYOUT = "messaging-settings";

    /** The staff node that gates the social-spy command, reused to gate the panel's social-spy toggle. */
    private static final String SOCIALSPY_PERMISSION = "uxmessentials.msg.socialspy";

    private final GuiText guiText;
    private final Menus menus;
    private final EntityEditorLayout layout;
    private final Messages messages;
    private final MessageToggleStore toggles;
    private final SocialSpyStore socialSpy;
    private final Permissions permissions;
    private final Scheduler scheduler;
    private final EditorSpec spec;

    public MessagingSettingsView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            Permissions permissions,
            Menus menus) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.socialSpy = Objects.requireNonNull(socialSpy, "socialSpy");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.layout = guiLayouts.loadEntityEditor(MODULE, LAYOUT, EntityEditorLayout.codeDefault(List.of(11, 15), 22));
        this.spec = buildSpec();
    }

    /** Open the panel for {@code viewer} as an engine editor; the engine resolves the live player and entity-hops. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        menus.openEditor(viewer, spec, viewer);
    }

    /**
     * The setting drawn at {@code slot} for {@code viewer}, or empty when the slot carries none. Exposed so a
     * test can assert the slot↔toggle mapping without opening a live menu, mirroring the bespoke panel's accessor.
     */
    public java.util.Optional<EditableProperty> settingAt(int slot, PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<EditableProperty> props = settings(viewer);
        int index = layout.propertySlots().indexOf(slot);
        return index >= 0 && index < props.size()
                ? java.util.Optional.of(props.get(index))
                : java.util.Optional.empty();
    }

    /** Build the editor spec once: the layout geometry, the catalog title/value/back, and the toggle provider. */
    private EditorSpec buildSpec() {
        return EditorSpec.builder()
                .layout(layout)
                .title((viewer, subject) -> guiText.text(viewer, MessagingMessageKey.GUI_SETTINGS_TITLE))
                .valueLore(MessagingMessageKey.GUI_SETTINGS_VALUE_LORE)
                .backName(MessagingMessageKey.GUI_SETTINGS_BACK)
                .properties(subject -> settings((PlayerRef) Objects.requireNonNull(subject, "subject")))
                .onBack((player, viewer) -> player.closeInventory())
                .build();
    }

    private List<EditableProperty> settings(PlayerRef viewer) {
        List<EditableProperty> properties = new ArrayList<>(2);
        properties.add(ToggleProperty.ofBoolean(
                MessagingMessageKey.GUI_SETTINGS_ACCEPT,
                Material.WRITABLE_BOOK,
                () -> toggles.acceptsMessages(viewer),
                (who, on) -> onOff(who, on),
                on -> {
                    // The store flip toggles the current value; only flip when the target differs from now, so a
                    // re-click that lands on the same state is a no-op rather than a double-toggle.
                    if (toggles.acceptsMessages(viewer) != on) {
                        toggles.toggle(viewer);
                    }
                },
                scheduler));
        // Social spy is a staff capability; only staff who can already run /socialspy get the toggle.
        if (permissions.has(viewer, SOCIALSPY_PERMISSION)) {
            properties.add(ToggleProperty.ofBoolean(
                    MessagingMessageKey.GUI_SETTINGS_SOCIALSPY,
                    Material.ENDER_EYE,
                    () -> socialSpy.isSpying(viewer),
                    (who, on) -> onOff(who, on),
                    on -> {
                        if (socialSpy.isSpying(viewer) != on) {
                            socialSpy.toggle(viewer);
                        }
                    },
                    scheduler));
        }
        return List.copyOf(properties);
    }

    private String onOff(PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? MessagingMessageKey.GUI_SETTINGS_VALUE_ON : MessagingMessageKey.GUI_SETTINGS_VALUE_OFF,
                Map.of());
    }
}

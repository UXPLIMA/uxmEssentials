package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Assembles the {@code /jail} management GUI's three capabilities and threads the navigation between them: the
 * jail-a-player flow ({@link JailGuiFlow}, capability A), the jail-list manager ({@link JailListView},
 * capability B), and the jailed-players release list ({@link ModerationJailedMenu}, capability C). The hub is the
 * jail flow's player picker carrying two footer buttons. [Jails] opening the jail list, [Jailed players]
 * opening the release list, so the operator reaches every capability from the one screen {@code /jail} opens.
 *
 * <p>The views are constructed once here and reused for every viewer. The footer buttons read the live player at
 * click time (re-deriving a {@link PlayerRef}) so a hub built once still routes each click to the clicker. The
 * three openers are exposed individually too, for the {@code /jails} and {@code /jailedplayers} bare-root
 * commands that jump straight to one capability. Layouts come from the module's {@code gui/*.conf}
 * (operator-editable, code default otherwise); every visible string is a catalog key.
 */
@NullMarked
public final class JailGuiViews {

    private static final String MODULE = "moderation";

    /** The jail-list create button sits in the bottom row; a paginated default puts nav at 45/53, create at 49. */
    private static final int CREATE_SLOT = 49;

    private final JailGuiFlow flow;
    private final JailListView jailList;
    private final ModerationJailedMenu jailedPlayers;

    private JailGuiViews(JailGuiFlow flow, JailListView jailList, ModerationJailedMenu jailedPlayers) {
        this.flow = flow;
        this.jailList = jailList;
        this.jailedPlayers = jailedPlayers;
    }

    /** Build the three jail views over the existing use cases, the shared pickers, and the module's GUI layouts. */
    public static JailGuiViews create(
            Menus menus,
            MenuBindings menuBindings,
            GuiText guiText,
            Scheduler scheduler,
            ModerationServices services,
            com.uxplima.uxmessentials.moderation.application.port.Sanctions sanctions,
            com.uxplima.uxmessentials.moderation.application.port.JailLocator jailLocator,
            ModerationJailedMenu jailedPlayers,
            PlayerPickerView picker,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView durations,
            TextInput textInput,
            Messages messages,
            MessageSink sink,
            GuiLayouts layouts,
            Path dataFolder,
            Logger log) {
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(sanctions, "sanctions");
        Objects.requireNonNull(jailLocator, "jailLocator");
        Objects.requireNonNull(jailedPlayers, "jailedPlayers");
        Objects.requireNonNull(picker, "picker");
        Objects.requireNonNull(durations, "durations");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(layouts, "layouts");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");

        EntityListLayout listLayout = layouts.loadEntityList(MODULE, "jail-list", jailListCodeDefault());

        JailListView jailList = new JailListView(
                menus, guiText, messages, scheduler, services, sanctions, jailLocator, textInput, listLayout);
        jailList.register(menuBindings, dataFolder, log);
        JailGuiFlow flow = new JailGuiFlow(menus, guiText, scheduler, services, picker, durations, messages, sink);
        return new JailGuiViews(flow, jailList, jailedPlayers);
    }

    /** Open the hub: the jail-a-player picker with the [Jails] and [Jailed players] footer buttons. */
    public void openHub(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        flow.open(player, viewer, footers());
    }

    /** Open the jail-list manager directly (the {@code /jails} bare-root opener). */
    public void openJailList(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        jailList.open(player, viewer);
    }

    /** Open the jailed-players release list directly (the {@code /jailedplayers} bare-root opener). */
    public void openJailedPlayers(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        jailedPlayers.open(viewer);
    }

    /** The hub's two footer buttons, re-deriving the clicker's ref so a hub built once routes each click. */
    private JailGuiFlow.Footers footers() {
        return new JailGuiFlow.Footers(List.of(
                new PlayerPickerView.FooterButton(
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILS,
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILS_LORE,
                        Material.IRON_BARS,
                        clicker -> jailList.open(clicker, BukkitRefs.toRef(clicker))),
                new PlayerPickerView.FooterButton(
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILED,
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILED_LORE,
                        Material.PLAYER_HEAD,
                        clicker -> jailedPlayers.open(BukkitRefs.toRef(clicker)))));
    }

    private static EntityListLayout jailListCodeDefault() {
        return EntityListLayout.withCreate(Material.IRON_BARS, CREATE_SLOT, Material.ANVIL);
    }
}

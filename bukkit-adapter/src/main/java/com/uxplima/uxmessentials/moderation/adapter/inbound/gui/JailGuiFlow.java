package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The bare-{@code /jail} GUI flow (capability A): a player picker into a jail chooser into the shared duration
 * picker, ending in the audited {@code Jail} use case. Picking a target (an online head or a typed offline name)
 * opens a paginated list of the defined jail names; choosing a jail opens the {@link DurationPickerView} with a
 * permanent option plus the timed presets; choosing a duration jails the target. The chosen target and jail are
 * carried immutably through the hops, no live target {@link Player} is held, so a target that logs off between
 * steps is still jailed (an offline jail re-applied at the next login, exactly as the raw command does).
 *
 * <p>Jail has no silent variant, so there is no confirm screen: a chosen duration executes directly, which keeps
 * the flow to three clicks (target → jail → duration). The reusable views stay generic: this flow supplies the
 * moderation {@code TargetResolver} as the offline-name resolver, the unknown-target reply, and the jail-grammar
 * validator (which, unlike the tempban/tempmute flows, accepts a permanent parse). The picker's two footer
 * buttons ([Jails] and [Jailed players]) come from the {@link Footers} hook so this flow stays free of the
 * jail-list / jailed-players view types.
 */
@NullMarked
public final class JailGuiFlow {

    /** A jail may be permanent; this token leads the duration presets and {@link SanctionDuration} parses it so. */
    static final String PERMANENT_TOKEN = "permanent";

    private static final Material JAIL_ICON = Material.IRON_BARS;

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final ModerationServices services;
    private final PlayerPickerView picker;
    private final DurationPickerView durations;
    private final EntityListLayout chooserLayout;
    private final Messages messages;
    private final MessageSink sink;

    public JailGuiFlow(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            ModerationServices services,
            PlayerPickerView picker,
            DurationPickerView durations,
            Messages messages,
            MessageSink sink) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.services = Objects.requireNonNull(services, "services");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.durations = Objects.requireNonNull(durations, "durations");
        this.chooserLayout = EntityListLayout.paginatedDefault(JAIL_ICON);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Open the player-picker hub; a chosen target advances to the jail chooser. */
    public void open(Player viewer, PlayerRef viewerRef, Footers footers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(footers, "footers");
        picker.open(viewer, viewerRef, pickRequest(viewer, viewerRef, footers));
    }

    private PlayerPickerView.Request pickRequest(Player viewer, PlayerRef viewerRef, Footers footers) {
        return new PlayerPickerView.Request(
                ModerationMessageKey.MOD_GUI_JAIL_HUB_TITLE,
                target -> openChooser(viewer, viewerRef, target),
                name -> services.targets().resolve(name),
                ModerationMessageKey.UNKNOWN_TARGET,
                footers.buttons());
    }

    /** The jail-chooser step: a paginated list of the defined jail names, each a button to the duration step. */
    private void openChooser(Player viewer, PlayerRef viewerRef, PlayerRef target) {
        AtomicReference<List<String>> snapshot = new AtomicReference<>(List.of());
        EntityListView<String> chooser = EntityListView.<String>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(chooserLayout)
                .title(ModerationMessageKey.MOD_GUI_JAIL_CHOOSE_TITLE)
                .navNames(ModerationMessageKey.MOD_GUI_JAIL_CHOOSE_PREV, ModerationMessageKey.MOD_GUI_JAIL_CHOOSE_NEXT)
                .entities(snapshot::get)
                .iconRenderer(this::chooserIcon)
                .onSelect((player, jail) -> openDuration(player, BukkitRefs.toRef(player), target, jail))
                .build();
        scheduler.async(() -> {
            List<String> names = services.listJails().names();
            snapshot.set(names);
            scheduler.onEntity(viewerRef, () -> openChooserOrNotice(viewer, viewerRef, names, chooser));
        });
    }

    private void openChooserOrNotice(
            Player viewer, PlayerRef viewerRef, List<String> names, EntityListView<String> chooser) {
        if (names.isEmpty()) {
            sink.deliver(
                    viewerRef, messages.resolve(viewerRef, ModerationMessageKey.MOD_GUI_JAIL_CHOOSE_EMPTY, Map.of()));
            return;
        }
        chooser.open(viewer, viewerRef);
    }

    private ItemStack chooserIcon(PlayerRef viewer, String jail) {
        Map<String, String> ph = Map.of("jail", jail);
        return ItemBuilder.of(JAIL_ICON)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_CHOOSE_ENTRY_NAME, ph),
                        guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_CHOOSE_ENTRY_LORE, ph)))
                .build();
    }

    /** The duration step: permanent plus the timed presets; a chosen span jails the target in the chosen jail. */
    private void openDuration(Player viewer, PlayerRef viewerRef, PlayerRef target, String jail) {
        durations.open(
                viewer,
                viewerRef,
                new DurationPickerView.Request(
                        ModerationMessageKey.MOD_GUI_JAIL_DURATION_TITLE,
                        permanentFirstPresets(),
                        duration -> {
                            // Close before applying so a rapid double-click on the same preset can't fire a second
                            // jail (a duplicate audit line + event), matching the sanction confirm flow's guard.
                            viewer.closeInventory();
                            services.jail().jail(viewerRef, target, jail, duration, Optional.empty());
                        },
                        JailGuiFlow::isJailDuration,
                        ModerationMessageKey.MOD_GUI_JAIL_DURATION_REJECT,
                        Optional.of(() -> openChooser(viewer, viewerRef, target))));
    }

    /** The permanent token followed by the standard timed presets: a jail may be permanent. */
    private static List<String> permanentFirstPresets() {
        List<String> presets = new ArrayList<>();
        presets.add(PERMANENT_TOKEN);
        presets.addAll(DurationPickerView.defaultPresets());
        return List.copyOf(presets);
    }

    /** A jail span is valid when it is a permanent token or a positive, well-formed duration (never malformed). */
    private static boolean isJailDuration(String raw) {
        return !SanctionDuration.parse(raw).malformed();
    }

    /**
     * The two footer buttons the player-picker hub carries: [Jails] (the jail-list manager) and [Jailed players]
     * (the release list). The jail flow itself only opens the picker, so the hub that owns the three views hands
     * these in, keeping {@link JailGuiFlow} free of the other view types.
     *
     * @param buttons the footer buttons in display order, supplied by the hub
     */
    public record Footers(List<PlayerPickerView.FooterButton> buttons) {

        public Footers {
            buttons = List.copyOf(Objects.requireNonNull(buttons, "buttons"));
        }
    }
}

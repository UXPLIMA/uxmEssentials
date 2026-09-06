package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-punishment detail and manage view: a config-laid-out property grid drawn through the shared
 * {@link EntityEditorView}, showing one {@link ActivePunishment}'s full detail as read-only label buttons
 * (target, type, issuer, reason, remaining time), a "view player history" button that opens
 * {@link PlayerHistoryView}, a back button to the list, and a confirm-gated <em>Revoke</em> wired through the
 * editor's delete slot. Revoking dispatches by {@link PunishmentKind} to the existing {@code unban} /
 * {@code unmute} / {@code unjail} use case, keyed by the target UUID, the GUI adds no revoke logic of its own.
 *
 * <p>The view holds no domain logic: the labels read the projection the list already resolved, and the revoke
 * call is the same audited path the {@code /unban} / {@code /unmute} / {@code /unjail} commands take. The
 * revoke runs off the tick thread through the {@link Scheduler}; the back navigation and the history open are
 * supplied by the caller.
 */
@NullMarked
public final class PunishmentDetailView {

    private static final String EMPTY_VALUE_HINT = "";

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final PunishmentRevoker revoker;
    private final Clock clock;
    private final EntityEditorView<ActivePunishment> editor;

    public PunishmentDetailView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            PunishmentRevoker revoker,
            Clock clock,
            EntityEditorLayout layout,
            BiConsumer<Player, PlayerRef> onBack,
            BiConsumer<Player, ActivePunishment> onHistory) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.revoker = Objects.requireNonNull(revoker, "revoker");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(onBack, "onBack");
        Objects.requireNonNull(onHistory, "onHistory");
        this.editor = EntityEditorView.<ActivePunishment>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title((viewer, p) -> guiText.text(
                        viewer, ModerationMessageKey.MOD_GUI_DETAIL_TITLE, Map.of("player", p.targetName())))
                .valueLore(ModerationMessageKey.MOD_GUI_DETAIL_VALUE_LORE)
                .backName(ModerationMessageKey.MOD_GUI_DETAIL_BACK)
                .properties(punishment -> properties(punishment, onHistory))
                .onBack(onBack)
                .onDelete(
                        ModerationMessageKey.MOD_GUI_DETAIL_REVOKE,
                        ModerationMessageKey.MOD_GUI_DETAIL_REVOKE_CONFIRM,
                        this::revoke)
                .build();
    }

    /** Open the detail/manage view for {@code punishment} on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, ActivePunishment punishment) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(punishment, "punishment");
        editor.open(player, viewer, punishment);
    }

    /** The slot↔property seam the framework exposes, so tests can assert the grid without firing a click. */
    public EntityEditorView<ActivePunishment> grid() {
        return editor;
    }

    private List<EditableProperty> properties(
            ActivePunishment punishment, BiConsumer<Player, ActivePunishment> onHistory) {
        return List.of(
                new LabelProperty(
                        ModerationMessageKey.MOD_GUI_DETAIL_TARGET,
                        Material.PLAYER_HEAD,
                        viewer -> punishment.targetName()),
                new LabelProperty(
                        ModerationMessageKey.MOD_GUI_DETAIL_TYPE,
                        Material.PAPER,
                        viewer -> plain(viewer, punishment.kind().label())),
                new LabelProperty(
                        ModerationMessageKey.MOD_GUI_DETAIL_ISSUER,
                        Material.WRITABLE_BOOK,
                        viewer -> punishment.issuer().name()),
                new LabelProperty(
                        ModerationMessageKey.MOD_GUI_DETAIL_REASON,
                        Material.BOOK,
                        viewer -> reason(viewer, punishment)),
                new LabelProperty(
                        ModerationMessageKey.MOD_GUI_DETAIL_REMAINING,
                        Material.CLOCK,
                        viewer -> remaining(viewer, punishment)),
                new ModerationActionProperty(
                        ModerationMessageKey.MOD_GUI_DETAIL_HISTORY,
                        Material.BOOKSHELF,
                        EMPTY_VALUE_HINT,
                        (player, reopen) -> onHistory.accept(player, punishment)));
    }

    private void revoke(Player player, ActivePunishment punishment) {
        PlayerRef actor = com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toRef(player);
        PlayerRef target = new PlayerRef(punishment.target(), punishment.targetName());
        scheduler.async(() -> revoker.revoke(actor, target, punishment.kind()));
    }

    private String reason(PlayerRef viewer, ActivePunishment punishment) {
        return punishment
                .reason()
                .filter(r -> !r.isBlank())
                .orElseGet(() -> plain(viewer, ModerationMessageKey.MOD_GUI_VALUE_NONE));
    }

    private String remaining(PlayerRef viewer, ActivePunishment punishment) {
        if (punishment.isPermanent()) {
            return plain(viewer, ModerationMessageKey.MOD_GUI_VALUE_PERMANENT);
        }
        Duration left = punishment
                .until()
                .map(until -> Duration.between(clock.instant(), until))
                .or(punishment::remaining)
                .orElse(Duration.ZERO);
        return SanctionDuration.format(left.isNegative() ? Duration.ZERO : left);
    }

    /** Render a catalog key to a plain value string for a label's lore, in the viewer's locale. */
    private String plain(PlayerRef viewer, ModerationMessageKey key) {
        Component rendered = guiText.text(viewer, key);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(rendered);
    }
}

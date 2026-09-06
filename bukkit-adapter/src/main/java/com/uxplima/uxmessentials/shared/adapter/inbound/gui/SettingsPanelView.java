package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A per-player settings panel: a config-driven grid of toggle/display/action buttons, each bound to a get/set of
 * one of the opening player's own preferences. Unlike an entity editor that edits an arbitrary entity, the panel
 * always edits the viewer themselves, so the "subject" is the viewer's own {@link PlayerRef}, and the properties
 * close over it. There is no list around it and no delete button: a player opens their own panel and flips their own
 * switches, every flip routing through the module's existing per-player use case.
 *
 * <p>The panel is a thin shim over the menu engine's property-editor runtime: it turns the {@code (layout +
 * property list)} a module hands it into an {@link EditorSpec} and opens it through {@link Menus#openEditor}, so the
 * window is a holder-backed engine editor routed and torn down by the one menu listener and one {@code closeMenu}.
 * The geometry and materials still come from a {@code modules/<m>/gui/<name>.conf} loaded the same way every
 * management GUI loads its layout (the delete slot in that conf, if any, is ignored. A settings panel never
 * deletes), all text is a {@link MessageKey}, and every build/flip hops through the shared {@link Scheduler} on the
 * viewer's thread with the write off it. The back button closes the menu.
 */
@NullMarked
public final class SettingsPanelView {

    private final Menus menus;
    private final EntityEditorLayout layout;
    private final Function<PlayerRef, List<EditableProperty>> properties;
    private final EditorSpec spec;

    private SettingsPanelView(Builder builder) {
        GuiText guiText = Objects.requireNonNull(builder.guiText, "guiText");
        MessageKey title = Objects.requireNonNull(builder.title, "title");
        this.menus = Objects.requireNonNull(builder.menus, "menus");
        this.layout = Objects.requireNonNull(builder.layout, "layout");
        this.properties = Objects.requireNonNull(builder.properties, "properties");
        BiConsumer<Player, PlayerRef> onBack = Objects.requireNonNull(builder.onBack, "onBack");
        Objects.requireNonNull(builder.scheduler, "scheduler");
        this.spec = EditorSpec.builder()
                .layout(layout)
                .title((viewer, subject) -> guiText.text(viewer, title))
                .valueLore(Objects.requireNonNull(builder.valueLore, "valueLore"))
                .backName(Objects.requireNonNull(builder.backName, "backName"))
                // The subject is always the viewer's own PlayerRef, so the property provider re-reads its
                // settings closing over it on every draw exactly as the bespoke panel re-read them per open.
                .properties(subject -> settingsFor((PlayerRef) Objects.requireNonNull(subject, "subject")))
                .onBack(onBack)
                .build();
    }

    /** Start building a settings panel; required fields are validated at {@link Builder#build}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Open the panel for {@code viewer} as an engine editor; the engine resolves the live player and entity-hops. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        menus.openEditor(viewer, spec, viewer);
    }

    /**
     * The setting drawn at {@code slot} for {@code viewer}, or empty when the slot carries none. Exposed so a
     * module's tests can assert the slot↔property mapping without opening a live menu. The mapping is the same the
     * engine renderer uses: the i-th property goes in the i-th of the layout's property slots.
     */
    public Optional<EditableProperty> settingAt(int slot, PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<EditableProperty> props = settingsFor(viewer);
        int index = layout.propertySlots().indexOf(slot);
        return index >= 0 && index < props.size() ? Optional.of(props.get(index)) : Optional.empty();
    }

    private List<EditableProperty> settingsFor(PlayerRef viewer) {
        return properties.apply(viewer);
    }

    /** Fluent builder: a module names its layout, its title/value/back keys, and its per-viewer settings. */
    @NullMarked
    public static final class Builder {
        private @Nullable GuiText guiText;
        private @Nullable Scheduler scheduler;
        private @Nullable Menus menus;
        private @Nullable EntityEditorLayout layout;
        private @Nullable MessageKey title;
        private @Nullable MessageKey valueLore;
        private @Nullable MessageKey backName;
        private @Nullable Function<PlayerRef, List<EditableProperty>> properties;
        private @Nullable BiConsumer<Player, PlayerRef> onBack;

        private Builder() {}

        public Builder guiText(GuiText guiText) {
            this.guiText = Objects.requireNonNull(guiText, "guiText");
            return this;
        }

        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        /** The menu engine the panel opens through; the editor it builds is a holder-backed engine menu. */
        public Builder menus(Menus menus) {
            this.menus = Objects.requireNonNull(menus, "menus");
            return this;
        }

        public Builder layout(EntityEditorLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        /** The panel title, resolved per viewer (a settings panel has no entity name to wrap). */
        public Builder title(MessageKey title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The catalog line each setting's current value is rendered into (carries a {@code {value}} placeholder). */
        public Builder valueLore(MessageKey valueLore) {
            this.valueLore = Objects.requireNonNull(valueLore, "valueLore");
            return this;
        }

        public Builder backName(MessageKey backName) {
            this.backName = Objects.requireNonNull(backName, "backName");
            return this;
        }

        /** The settings for a viewer, re-read each open: one {@link EditableProperty} per toggle/row/action. */
        public Builder settings(Function<PlayerRef, List<EditableProperty>> settings) {
            this.properties = Objects.requireNonNull(settings, "settings");
            return this;
        }

        /** What the back button does (e.g. close the menu, or reopen the hub). */
        public Builder onBack(BiConsumer<Player, PlayerRef> onBack) {
            this.onBack = Objects.requireNonNull(onBack, "onBack");
            return this;
        }

        /** Build the panel; the constructor validates that every required field was set. */
        public SettingsPanelView build() {
            return new SettingsPanelView(this);
        }
    }
}

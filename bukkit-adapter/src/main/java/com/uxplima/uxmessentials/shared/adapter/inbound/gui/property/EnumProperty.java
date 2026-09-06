package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.LayoutFit;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a small selector sub-menu listing the options, one button per option drawn into
 * the configured option slots; clicking an option hands it to the setter and returns to the editor. The
 * selector's title, the per-option name (resolved from the option through the caller's display function), and
 * its geometry (rows, option slots, option-icon and filler materials) all come from the caller: nothing is
 * hardcoded. The currently-selected option is highlighted with an enchant glint so the viewer can see where
 * they are.
 *
 * <p>The chosen option is written through the caller's setter off the tick thread via the shared
 * {@link Scheduler}, then the editor is redrawn. The setter is the module's existing application use case
 * wrapped as a {@link Consumer}; this property holds no domain logic. The selector opens as an engine child
 * window the one menu listener routes, so click routing and teardown stay on a single holder.
 *
 * <p>Every option draws with the same configured {@code optionIcon} by default; a caller that wants a
 * per-option icon (e.g. the NPC type selector showing each mob's spawn egg) passes an {@code optionIconFn}
 * that maps an option to its own material. The function is only consulted for the button material, the name,
 * geometry, glint, and selection behaviour are identical either way.
 *
 * @param <E> the option type (typically an enum)
 */
@NullMarked
public final class EnumProperty<E> implements EditableProperty {

    private final MessageKey label;
    private final MessageKey selectorTitle;
    private final Material icon;
    private final GuiText guiText;
    private final List<E> options;
    private final Supplier<E> current;
    private final BiFunction<PlayerRef, E, String> display;
    private final Consumer<E> setter;
    private final Function<E, Material> optionIconFn;
    private final Material fillerIcon;
    private final List<Integer> optionSlots;
    private final int rows;
    private final Scheduler scheduler;

    /** A selector whose options all share one {@code optionIcon}. */
    public EnumProperty(
            MessageKey label,
            MessageKey selectorTitle,
            Material icon,
            GuiText guiText,
            List<E> options,
            Supplier<E> current,
            BiFunction<PlayerRef, E, String> display,
            Consumer<E> setter,
            Material optionIcon,
            Material fillerIcon,
            List<Integer> optionSlots,
            int rows,
            Scheduler scheduler) {
        this(
                label,
                selectorTitle,
                icon,
                guiText,
                options,
                current,
                display,
                setter,
                optionIcon,
                option -> Objects.requireNonNull(optionIcon, "optionIcon"),
                fillerIcon,
                optionSlots,
                rows,
                scheduler);
    }

    /**
     * A selector that draws each option with its own material via {@code optionIconFn}; the {@code optionIcon}
     * is kept as the fallback the function may return for an option with no icon of its own.
     */
    public EnumProperty(
            MessageKey label,
            MessageKey selectorTitle,
            Material icon,
            GuiText guiText,
            List<E> options,
            Supplier<E> current,
            BiFunction<PlayerRef, E, String> display,
            Consumer<E> setter,
            Material optionIcon,
            Function<E, Material> optionIconFn,
            Material fillerIcon,
            List<Integer> optionSlots,
            int rows,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.selectorTitle = Objects.requireNonNull(selectorTitle, "selectorTitle");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.options = List.copyOf(Objects.requireNonNull(options, "options"));
        if (this.options.isEmpty()) {
            throw new IllegalArgumentException("an enum property needs at least one option");
        }
        this.current = Objects.requireNonNull(current, "current");
        this.display = Objects.requireNonNull(display, "display");
        this.setter = Objects.requireNonNull(setter, "setter");
        Objects.requireNonNull(optionIcon, "optionIcon");
        this.optionIconFn = Objects.requireNonNull(optionIconFn, "optionIconFn");
        this.fillerIcon = Objects.requireNonNull(fillerIcon, "fillerIcon");
        this.optionSlots = List.copyOf(Objects.requireNonNull(optionSlots, "optionSlots"));
        if (this.optionSlots.isEmpty()) {
            throw new IllegalArgumentException("optionSlots must not be empty");
        }
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        this.rows = rows;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return display.apply(viewer, current.get());
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        // The selector opens as an engine child window the one menu listener routes, keeping the whole flow on a
        // single holder and teardown.
        context.opener()
                .openSelector(
                        context.viewer(),
                        guiText.text(context.viewer(), selectorTitle),
                        rows,
                        fillerIcon,
                        selectorButtons(context));
    }

    /** One engine selector button per option (icon plus its choose action), glint baked onto the selected option. */
    private List<SelectorButton> selectorButtons(ClickContext context) {
        E selected = current.get();
        List<SelectorButton> buttons = new ArrayList<>();
        int drawn = LayoutFit.drawable("enum option-slots", options.size(), optionSlots);
        for (int i = 0; i < drawn; i++) {
            E option = options.get(i);
            // An enum option is single-gesture: any click chooses it, so the gesture is ignored.
            buttons.add(SelectorButton.of(
                    optionSlots.get(i), optionIcon(context.viewer(), option, selected), () -> choose(context, option)));
        }
        return buttons;
    }

    private void choose(ClickContext context, E option) {
        scheduler.async(() -> {
            setter.accept(option);
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }

    private ItemStack optionIcon(PlayerRef viewer, E option, E selected) {
        // The option name is a plain value string; wrap it in the value token so it picks up the canon accent.
        // The icon is the per-option material (the type selector's spawn eggs); the fixed-icon constructor passes
        // a function that always returns the one configured material.
        ItemBuilder builder = ItemBuilder.of(optionIconFn.apply(option))
                .name(StyledText.render("<value>" + display.apply(viewer, option) + "</value>"));
        if (Objects.equals(option, selected)) {
            // A glint marks the live option; HIDE_ENCHANTS keeps the enchant invisible in the lore.
            builder.enchant(Enchantment.UNBREAKING, 1).flags(ItemFlag.HIDE_ENCHANTS);
        }
        return builder.build();
    }
}

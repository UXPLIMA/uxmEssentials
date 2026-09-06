package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.LayoutFit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorOpener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a config-driven colour-picker sub-menu: a palette of the 16 standard Minecraft
 * named colours (each a coloured icon at a configured slot), a custom-hex button that opens an anvil parsing
 * {@code #RRGGBB} / {@code #AARRGGBB} into a packed ARGB int, a clear button that resets the value to the
 * property's "no override" sentinel, and a back button. Selecting a swatch or submitting a valid hex hands the
 * chosen ARGB int to the {@link IntConsumer} setter; the clear button fires the clear {@link Runnable}; an
 * invalid hex re-opens the picker without writing.
 *
 * <p>The consumer supplies the current-value getter, the ARGB setter, the clear runnable, the sentinel, and the
 * catalog keys, so the same widget edits a hologram's background colour, its glow colour, or any other packed
 * ARGB field. Every write runs off the tick thread through the shared {@link Scheduler}, then the editor is
 * redrawn. The setters are the module's existing application use cases wrapped as callbacks; this property holds
 * no domain logic. Geometry and materials come from a {@link ColourPickerLayout} loaded from conf, so nothing is
 * hardcoded.
 *
 * <p>The picker opens as an engine child window the one menu listener routes. Its swatch/custom/clear/back buttons
 * are single-gesture {@code SelectorButton}s and a swatch, clear, or back reopens the parent editor through the
 * context's reopen hook, so the whole flow stays on a single holder and teardown.
 */
@NullMarked
public final class ColourProperty implements EditableProperty {

    private static final int OPAQUE_ALPHA = 0xFF;
    private static final int BYTE_MASK = 0xFF;
    private static final String INPUT_KEY = "editor.colour-hex";

    private final MessageKey label;
    private final Material icon;
    private final IntSupplier current;
    private final IntConsumer setter;
    private final Runnable clear;
    private final int sentinel;
    private final Function<PlayerRef, String> clearedDisplay;
    private final GuiText guiText;
    private final ColourPickerText text;
    private final ColourPickerLayout layout;
    private final TextInput textInput;
    private final Scheduler scheduler;

    public ColourProperty(
            MessageKey label,
            Material icon,
            IntSupplier current,
            IntConsumer setter,
            Runnable clear,
            int sentinel,
            Function<PlayerRef, String> clearedDisplay,
            GuiText guiText,
            ColourPickerText text,
            ColourPickerLayout layout,
            TextInput textInput,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.current = Objects.requireNonNull(current, "current");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.clear = Objects.requireNonNull(clear, "clear");
        this.sentinel = sentinel;
        this.clearedDisplay = Objects.requireNonNull(clearedDisplay, "clearedDisplay");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.text = Objects.requireNonNull(text, "text");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
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
        int value = current.getAsInt();
        return value == sentinel ? clearedDisplay.apply(viewer) : toHex(value);
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        scheduler.onEntity(context.viewer(), () -> open(context));
    }

    /**
     * Open the picker as an engine child window: the swatch/custom/clear/back buttons are handed to the engine opener
     * as single-gesture {@link SelectorButton}s so the one menu listener routes them. A swatch writes its packed ARGB
     * and reopens the parent editor; custom opens the anvil seam; clear fires the clear runnable then reopens the
     * parent; back reopens the parent, all through the {@code context.reopen()} contract.
     */
    private void open(ClickContext context) {
        SelectorOpener opener = context.opener();
        int selected = current.getAsInt();
        List<ColourSwatch> palette = ColourSwatch.palette();
        List<Integer> slots = layout.paletteSlots();
        List<Material> icons = layout.paletteIcons();
        List<SelectorButton> buttons = new ArrayList<>();
        int drawn = LayoutFit.drawable("colour-picker palette-slots", palette.size(), slots);
        for (int i = 0; i < drawn; i++) {
            ColourSwatch swatch = palette.get(i);
            ItemStack icon = swatchIcon(context.viewer(), swatch, icons.get(i), selected);
            buttons.add(SelectorButton.of(slots.get(i), icon, () -> pick(context, swatch.argb())));
        }
        buttons.add(SelectorButton.of(layout.customSlot(), customIcon(context), () -> openCustom(context)));
        buttons.add(SelectorButton.of(layout.clearSlot(), clearIcon(context), () -> clear(context)));
        buttons.add(SelectorButton.of(
                layout.backSlot(), backIcon(context), () -> context.reopen().run()));
        opener.openSelector(
                context.viewer(),
                guiText.text(context.viewer(), text.title()),
                layout.rows(),
                layout.filler(),
                buttons);
    }

    private ItemStack swatchIcon(PlayerRef viewer, ColourSwatch swatch, Material material, int selected) {
        ItemBuilder builder = ItemBuilder.of(material).name(guiText.text(viewer, swatch.nameKey()));
        if (swatch.argb() == selected) {
            // A glint marks the live colour; HIDE_ENCHANTS keeps the enchant invisible in the lore.
            builder.enchant(Enchantment.UNBREAKING, 1).flags(ItemFlag.HIDE_ENCHANTS);
        }
        return builder.build();
    }

    private ItemStack customIcon(ClickContext context) {
        return ItemBuilder.of(layout.customIcon())
                .name(guiText.text(context.viewer(), text.customName()))
                .build();
    }

    private ItemStack clearIcon(ClickContext context) {
        return ItemBuilder.of(layout.clearIcon())
                .name(guiText.text(context.viewer(), text.clearName()))
                .build();
    }

    private ItemStack backIcon(ClickContext context) {
        return ItemBuilder.of(layout.backIcon())
                .name(guiText.text(context.viewer(), text.backName()))
                .build();
    }

    private void openCustom(ClickContext context) {
        textInput.prompt(
                context.player(),
                context.viewer(),
                InputRequest.of(INPUT_KEY, text.customPrompt()),
                raw -> applyCustom(context, raw),
                () -> open(context));
    }

    /**
     * Apply a typed custom-hex line: a valid {@code #RRGGBB}/{@code #AARRGGBB} writes through the setter and
     * redraws the editor; an invalid line re-opens the picker without writing. Exposed package-private as the
     * seam a test drives directly, the same pattern {@code TextProperty.applyInput} uses.
     */
    public void applyCustom(ClickContext context, String raw) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(raw, "raw");
        Optional<Integer> parsed = ColourHex.parse(raw);
        if (parsed.isEmpty()) {
            open(context);
            return;
        }
        pick(context, parsed.get());
    }

    private void pick(ClickContext context, int argb) {
        scheduler.async(() -> {
            setter.accept(argb);
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }

    private void clear(ClickContext context) {
        scheduler.async(() -> {
            clear.run();
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }

    /** Render a packed ARGB int as {@code #AARRGGBB}, or {@code #RRGGBB} when fully opaque, for the value lore. */
    private static String toHex(int argb) {
        int alpha = argb >>> 24 & BYTE_MASK;
        if (alpha == OPAQUE_ALPHA) {
            return "#" + pad(argb & 0xFFFFFF, 6);
        }
        return "#" + pad(argb, 8);
    }

    private static String pad(int value, int width) {
        String hex = Integer.toHexString(value).toUpperCase(Locale.ROOT);
        return "0".repeat(Math.max(0, width - hex.length())) + hex;
    }
}

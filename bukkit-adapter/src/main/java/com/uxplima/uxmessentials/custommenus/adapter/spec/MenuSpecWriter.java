package com.uxplima.uxmessentials.custommenus.adapter.spec;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.BedrockFormSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickBranch;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.DataComponents;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDragSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.LoreMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RichMeta;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Serializes a parsed {@link MenuSpec} (and the optional {@code command {}} block a menu file carries) back into the
 * HOCON an operator would have written, the exact inverse of {@code MenuSpecLoader.load} +
 * {@code CustomMenuLoader.parseOpenCommand}. It is the missing half of the menu-editor spec service: the loader turns
 * a file into a model, this turns a model back into a file, and the two compose into a lossless round-trip so an
 * in-game edit can be written back without hand-formatting HOCON.
 *
 * <p>The contract is model-faithful, not byte-faithful: comments, key order, and the loader's convenience shorthands
 * ({@code update-interval}, {@code slot} for a single index, the {@code permission}/{@code pages} view sugar) are
 * canonicalised to their fully-expanded form, but re-loading the emitted text yields a {@link MenuSpec} that
 * {@code equals} the original. Only non-default keys are emitted where the loader treats absence as a default, so the
 * output stays clean and a menu that declared little writes little. Load-time inputs the loader consumes before the
 * model exists ({@code patterns {}}, {@code layout}, the {@code fill-item}'s auto-computed slots) are not (and need
 * not be) reproduced: the items they expand into are written concretely and round-trip on their own.
 *
 * <p>Pure and Bukkit-free like the spec model it walks: it references only the {@code spec/} records, the menu's
 * {@code command {}} value object, and Configurate, so it is exercised by plain JUnit. The HOCON is rendered through
 * the same {@link HoconConfigurationLoader} save path the converters use.
 */
public final class MenuSpecWriter {

    /** The reserved id the loader stores the {@code fill-item} background under; written back as {@code fill-item}. */
    private static final String FILL_ITEM_ID = "__fill__";

    /** The HOCON gesture key each {@link ClickKind} is written under: the kebab spelling the loader accepts. */
    private static final Map<ClickKind, String> GESTURE_KEYS = gestureKeys();

    /** Serialize a menu with no {@code command {}} block. */
    public String write(MenuSpec spec) {
        return write(spec, null);
    }

    /**
     * Serialize {@code spec} (and, when non-null, the {@code command}'s {@code command {}} open-command block) into
     * one menu file's HOCON. Re-loading the result through {@code MenuSpecLoader} (and {@code parseOpenCommand} for the
     * command block) reproduces an equal {@link MenuSpec} and {@link OpenCommandSpec}.
     */
    public String write(MenuSpec spec, @Nullable OpenCommandSpec command) {
        Objects.requireNonNull(spec, "spec");
        CommentedConfigurationNode root =
                HoconConfigurationLoader.builder().build().createNode();
        try {
            writeMenu(root, spec);
            if (command != null) {
                writeCommand(root.node("command"), command);
            }
        } catch (SerializationException failure) {
            throw new IllegalStateException("failed to serialize menu spec", failure);
        }
        return render(root);
    }

    // --- menu-level keys ----------------------------------------------------------------------------------------

    private void writeMenu(CommentedConfigurationNode root, MenuSpec spec) throws SerializationException {
        if (!spec.title().isEmpty()) {
            root.node("title").set(spec.title());
        }
        root.node("rows").set(spec.rows());
        if (spec.inventoryType().isPresent()) {
            root.node("inventory-type").set(spec.inventoryType().get());
        }
        setIfTrue(root.node("bottom-inventory"), spec.bottomInventory());
        setIfTrue(root.node("chest-only"), spec.chestOnly());
        if (spec.clickCooldownMs() != 0L) {
            root.node("click-cooldown").set(spec.clickCooldownMs());
        }
        writeRefresh(root, spec.refresh());
        writeRefsIfAny(root.node("open-requirement"), spec.openRequirement());
        writeRefsIfAny(root.node("open-actions"), spec.openActions());
        writeRefsIfAny(root.node("close-actions"), spec.closeActions());
        writePlaceholders(root.node("placeholders"), spec.placeholders());
        spec.bedrock().ifPresent(bedrock -> setQuiet(() -> writeBedrock(root.node("bedrock"), bedrock)));
        writeContent(root.node("content"), spec.contents());
        writeItems(root, spec.items());
    }

    /**
     * Emit the {@code content {}} block a menu hands to a feature's providers, so a spec that carries one survives a
     * round trip through the editor. A menu with no region writes nothing, as before this block existed.
     */
    private void writeContent(CommentedConfigurationNode node, Map<String, ContentRegionSpec> contents)
            throws SerializationException {
        for (ContentRegionSpec region : contents.values()) {
            CommentedConfigurationNode child = node.node(region.id());
            child.node("slots").set(slotTokens(region.slots()));
            setIfTrue(child.node("editable"), region.editable());
        }
    }

    /** Emit the refresh block only when it says something: an enabled refresh or a stashed non-zero interval. */
    private void writeRefresh(CommentedConfigurationNode root, RefreshSpec refresh) throws SerializationException {
        if (!refresh.enabled() && refresh.intervalTicks() == 0) {
            return;
        }
        CommentedConfigurationNode node = root.node("refresh");
        node.node("enabled").set(refresh.enabled());
        node.node("interval-ticks").set(refresh.intervalTicks());
    }

    /** Emit the menu's own {@code placeholders {}} block, sorted for a deterministic file, when it has any. */
    private void writePlaceholders(CommentedConfigurationNode node, Map<String, String> placeholders)
            throws SerializationException {
        for (Map.Entry<String, String> entry : new TreeMap<>(placeholders).entrySet()) {
            node.node(entry.getKey()).set(entry.getValue());
        }
    }

    /** Emit the {@code items {}} block: the fill item as {@code fill-item}, the rest under their sorted ids. */
    private void writeItems(CommentedConfigurationNode root, Map<String, MenuItemSpec> items)
            throws SerializationException {
        MenuItemSpec fill = items.get(FILL_ITEM_ID);
        if (fill != null) {
            writeItem(root.node("fill-item"), fill, true);
        }
        CommentedConfigurationNode itemsNode = root.node("items");
        for (Map.Entry<String, MenuItemSpec> entry : new TreeMap<>(items).entrySet()) {
            if (!entry.getKey().equals(FILL_ITEM_ID)) {
                writeItem(itemsNode.node(entry.getKey()), entry.getValue(), false);
            }
        }
    }

    // --- one item -----------------------------------------------------------------------------------------------

    /**
     * Emit one item's fields. The {@code fill} flag drops the two the loader forces for the background icon, its
     * auto-computed slots and its lowest priority, so re-loading recomputes them identically.
     */
    private void writeItem(CommentedConfigurationNode node, MenuItemSpec item, boolean fill)
            throws SerializationException {
        node.node("material").set(item.material());
        if (!item.name().isEmpty()) {
            node.node("name").set(item.name());
        }
        writeStringsIfAny(node.node("lore"), item.lore());
        if (item.loreMode() != LoreMode.REPLACE) {
            node.node("lore-mode").set(item.loreMode().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!fill && !item.slots().slots().isEmpty()) {
            node.node("slots").set(slotTokens(item.slots()));
        }
        if (!fill && item.priority() != 0) {
            node.node("priority").set(item.priority());
        }
        if (item.type() != ItemType.NONE) {
            node.node("type").set(item.type().name().toLowerCase(java.util.Locale.ROOT));
        }
        setIfTrue(node.node("update"), item.update());
        writeDecor(node.node("decor"), item.decor());
        writeView(node, item.view());
        writeClick(node.node("click"), item);
        if (item.list().isPresent()) {
            writeList(node.node("list"), item.list().get());
        }
        if (item.itemDrag().isPresent()) {
            writeItemDrag(node.node("item-drag"), item.itemDrag().get());
        }
    }

    // --- decor --------------------------------------------------------------------------------------------------

    /** Emit the {@code decor {}} block, skipping it entirely when the item carries the default (empty) decoration. */
    private void writeDecor(CommentedConfigurationNode node, ItemDecor decor) throws SerializationException {
        RichMeta meta = decor.meta();
        writeAmount(node, decor, meta);
        writeModelData(node, decor, meta);
        // A placeholder-driven glow is written back as its token, so an edit-and-save round trip keeps it dynamic.
        if (meta.dynamicGlow().isPresent()) {
            setIfPresent(node.node("glow"), meta.dynamicGlow().orElseThrow());
        } else {
            setIfTrue(node.node("glow"), decor.glow());
        }
        writeStringsIfAny(node.node("flags"), decor.flagTokens());
        setIfTrue(node.node("unbreakable"), meta.unbreakable());
        writeStringsIfAny(node.node("enchantments"), meta.enchantments());
        writeStringsIfAny(node.node("stored-enchantments"), meta.storedEnchantments());
        setIfPresent(node.node("leather-color"), meta.leatherColor().orElse(null));
        writePotion(node.node("potion"), meta);
        writeStringsIfAny(node.node("banner").node("patterns"), meta.bannerPatterns());
        writeTrim(node.node("trim"), meta);
        if (meta.damage().isPresent()) {
            node.node("damage").set(meta.damage().get());
        }
        setIfPresent(node.node("item-model"), meta.itemModel().orElse(null));
        writeComponents(node, meta.components());
    }

    /** The static or dynamic stack {@code amount}: a placeholder token overrides, else a count only when not one. */
    private void writeAmount(CommentedConfigurationNode node, ItemDecor decor, RichMeta meta)
            throws SerializationException {
        if (meta.dynamicAmount().isPresent()) {
            node.node("amount").set(meta.dynamicAmount().get());
        } else if (decor.amount() != 1) {
            node.node("amount").set(decor.amount());
        }
    }

    /** The static or dynamic {@code model-data}: a placeholder token overrides, else the id when one is set. */
    private void writeModelData(CommentedConfigurationNode node, ItemDecor decor, RichMeta meta)
            throws SerializationException {
        if (meta.dynamicModelData().isPresent()) {
            node.node("model-data").set(meta.dynamicModelData().get());
        } else if (decor.modelData().isPresent()) {
            node.node("model-data").set(decor.modelData().get());
        }
    }

    /** Emit the {@code potion {}} sub-block when the item declares a base type, a colour, or custom effects. */
    private void writePotion(CommentedConfigurationNode node, RichMeta meta) throws SerializationException {
        RichMeta.PotionSpec potion = meta.potion();
        if (potion.equals(RichMeta.PotionSpec.NONE)) {
            return;
        }
        setIfPresent(node.node("type"), potion.type().orElse(null));
        setIfPresent(node.node("color"), potion.color().orElse(null));
        writeStringsIfAny(node.node("effects"), potion.effects());
    }

    /** Emit the {@code trim {}} sub-block (material + pattern) when the item declares one. */
    private void writeTrim(CommentedConfigurationNode node, RichMeta meta) throws SerializationException {
        if (meta.trim().isEmpty()) {
            return;
        }
        RichMeta.TrimSpec trim = meta.trim().get();
        node.node("material").set(trim.material());
        node.node("pattern").set(trim.pattern());
    }

    /** Emit the native data-components (rarity, tooltip, glint, food, tool, …) that carry a non-default value. */
    private void writeComponents(CommentedConfigurationNode node, DataComponents components)
            throws SerializationException {
        setIfPresent(node.node("rarity"), components.rarity().orElse(null));
        setIfPresent(node.node("tooltip-style"), components.tooltipStyle().orElse(null));
        setBoxed(node.node("hide-tooltip"), components.hideTooltip().orElse(null));
        setBoxed(
                node.node("hide-vanilla-tooltip"),
                components.hideVanillaTooltip().orElse(null));
        writeStringsIfAny(node.node("hidden-components"), components.hiddenComponents());
        setBoxed(node.node("enchant-glint"), components.enchantGlint().orElse(null));
        setBoxed(node.node("enchantable"), components.enchantable().orElse(null));
        writeStringsIfAny(node.node("attribute-modifiers"), components.attributeModifiers());
        writeFood(node.node("food"), components);
        writeTool(node.node("tool"), components);
    }

    private void writeFood(CommentedConfigurationNode node, DataComponents components) throws SerializationException {
        if (components.food().isEmpty()) {
            return;
        }
        DataComponents.FoodSpec food = components.food().get();
        setBoxed(node.node("nutrition"), food.nutrition().orElse(null));
        setBoxed(node.node("saturation"), food.saturation().orElse(null));
        setBoxed(node.node("can-always-eat"), food.canAlwaysEat().orElse(null));
    }

    private void writeTool(CommentedConfigurationNode node, DataComponents components) throws SerializationException {
        if (components.tool().isEmpty()) {
            return;
        }
        DataComponents.ToolSpec tool = components.tool().get();
        setBoxed(node.node("default-mining-speed"), tool.defaultMiningSpeed().orElse(null));
        setBoxed(node.node("damage-per-block"), tool.damagePerBlock().orElse(null));
    }

    // --- view ---------------------------------------------------------------------------------------------------

    /**
     * Emit an item's {@code view} visibility gate as a {@code requirements}/{@code minimum} block. A gate that carries
     * only the {@code pages} shorthand's {@code on-page} requirement is written back through that shorthand: unlike
     * every other valued ref its token would not round-trip (the loader splits {@code id:value} only for a fixed set of
     * generic prefixes, and {@code on-page} is not among them: it is reachable only via the sugar).
     */
    private void writeView(CommentedConfigurationNode itemNode, RequirementSpec view) throws SerializationException {
        if (view.equals(RequirementSpec.NONE)) {
            return;
        }
        List<Requirement> requirements = new ArrayList<>();
        for (Requirement requirement : view.requirements()) {
            if (isPagesShorthand(requirement)) {
                itemNode.node("pages").set(requirement.condition().value());
            } else {
                requirements.add(requirement);
            }
        }
        CommentedConfigurationNode node = itemNode.node("view");
        if (!requirements.isEmpty()) {
            writeRequirements(node.node("requirements"), requirements);
        }
        if (view.minimum() != 0) {
            node.node("minimum").set(view.minimum());
        }
    }

    /** Whether a view requirement is the plain {@code on-page:<pages>} the loader's {@code pages} shorthand produces. */
    private static boolean isPagesShorthand(Requirement requirement) {
        return !requirement.inverted()
                && !requirement.optional()
                && requirement.success().isEmpty()
                && requirement.deny().isEmpty()
                && requirement.condition().id().equals("on-page")
                && requirement.condition().args().containsKey("value");
    }

    // --- click --------------------------------------------------------------------------------------------------

    /** Emit the {@code click {}} block: one entry per gesture that binds actions, a requirement block, or an else. */
    private void writeClick(CommentedConfigurationNode node, MenuItemSpec item) throws SerializationException {
        for (ClickKind kind : ClickKind.values()) {
            List<Ref> actions = item.click().actions().getOrDefault(kind, List.of());
            RequirementSpec requirement = item.click().requirements().get(kind);
            ClickBranch orElse = item.click().orElse().get(kind);
            if (!item.click().actions().containsKey(kind) && requirement == null && orElse == null) {
                continue;
            }
            writeGesture(node.node(GESTURE_KEYS.get(kind)), actions, requirement, orElse);
        }
    }

    /**
     * Emit one gesture. With no requirement block and no else it is the bare action list the loader's historic form
     * reads; otherwise it is a map carrying the actions, the requirement block, and any else-chain.
     */
    private void writeGesture(
            CommentedConfigurationNode node,
            List<Ref> actions,
            @Nullable RequirementSpec requirement,
            @Nullable ClickBranch orElse)
            throws SerializationException {
        if (requirement == null && orElse == null) {
            if (actions.isEmpty()) {
                node.setList(String.class, List.of());
            } else {
                writeRefs(node, actions);
            }
            return;
        }
        writeRefsIfAny(node.node("actions"), actions);
        writeRequirementBlock(node, requirement == null ? RequirementSpec.NONE : requirement);
        if (orElse != null) {
            writeElse(node.node("else"), orElse);
        }
    }

    /** Emit an else-branch (its gate, its actions, its nested else), recursing so a whole fallback ladder writes out. */
    private void writeElse(CommentedConfigurationNode node, ClickBranch branch) throws SerializationException {
        writeRequirementBlock(node, branch.requirement());
        writeRefsIfAny(node.node("actions"), branch.actions());
        if (branch.orElse().isPresent()) {
            writeElse(node.node("else"), branch.orElse().get());
        }
    }

    /** Emit the {@code requirements}/{@code minimum}/{@code deny}/{@code stop-at-success} fields of a gate. */
    private void writeRequirementBlock(CommentedConfigurationNode node, RequirementSpec spec)
            throws SerializationException {
        if (!spec.requirements().isEmpty()) {
            writeRequirements(node.node("requirements"), spec.requirements());
        }
        if (spec.minimum() != 0) {
            node.node("minimum").set(spec.minimum());
        }
        writeRefsIfAny(node.node("deny"), spec.deny());
        setIfTrue(node.node("stop-at-success"), spec.stopAtSuccess());
    }

    /** Emit a requirement list: each is a compact {@code [!]token}, or a map when it carries per-requirement structure. */
    private void writeRequirements(CommentedConfigurationNode node, List<Requirement> requirements)
            throws SerializationException {
        for (Requirement requirement : requirements) {
            writeRequirement(node.appendListNode(), requirement);
        }
    }

    private void writeRequirement(CommentedConfigurationNode entry, Requirement requirement)
            throws SerializationException {
        String token = (requirement.inverted() ? "!" : "") + refToken(requirement.condition());
        if (!requirement.optional()
                && requirement.success().isEmpty()
                && requirement.deny().isEmpty()) {
            entry.set(token);
            return;
        }
        entry.node("require").set(token);
        setIfTrue(entry.node("optional"), requirement.optional());
        writeRefsIfAny(entry.node("success"), requirement.success());
        writeRefsIfAny(entry.node("deny"), requirement.deny());
    }

    // --- list / item-drag ---------------------------------------------------------------------------------------

    private void writeList(CommentedConfigurationNode node, ListSpec list) throws SerializationException {
        node.node("source").set(refToken(list.source()));
        writeItem(node.node("template"), list.template(), false);
    }

    private void writeItemDrag(CommentedConfigurationNode node, ItemDragSpec drag) throws SerializationException {
        CommentedConfigurationNode rules = node.node("rules");
        writeStringsIfAny(rules.node("materials"), drag.rules().materials());
        if (drag.rules().minAmount() != 1) {
            rules.node("min-amount").set(drag.rules().minAmount());
        }
        if (!drag.rules().nameContains().isEmpty()) {
            rules.node("name-contains").set(drag.rules().nameContains());
        }
        setIfTrue(node.node("consume"), drag.consume());
        writeRefsIfAny(node.node("actions"), drag.actions());
    }

    // --- bedrock ------------------------------------------------------------------------------------------------

    private void writeBedrock(CommentedConfigurationNode node, BedrockFormSpec bedrock) throws SerializationException {
        node.node("title").set(bedrock.title());
        setIfPresent(node.node("content"), bedrock.content());
        for (BedrockWidget widget : bedrock.widgets()) {
            writeWidget(node.node("widgets").appendListNode(), widget);
        }
        writeRefsIfAny(node.node("on-submit"), bedrock.onSubmit());
    }

    /** Emit one form widget under its {@code type} discriminator, mirroring the loader's {@code parseWidget}. */
    private void writeWidget(CommentedConfigurationNode node, BedrockWidget widget) throws SerializationException {
        switch (widget) {
            case BedrockWidget.Label label -> {
                node.node("type").set("label");
                node.node("text").set(label.text());
            }
            case BedrockWidget.Input input -> writeInputWidget(node, input);
            case BedrockWidget.Dropdown dropdown -> writeDropdownWidget(node, dropdown);
            case BedrockWidget.Slider slider -> writeSliderWidget(node, slider);
            case BedrockWidget.Toggle toggle -> writeToggleWidget(node, toggle);
        }
    }

    private void writeInputWidget(CommentedConfigurationNode node, BedrockWidget.Input input)
            throws SerializationException {
        node.node("type").set("input");
        node.node("name").set(input.name());
        node.node("label").set(input.label());
        node.node("placeholder").set(input.placeholder());
        node.node("default").set(input.defaultText());
    }

    private void writeDropdownWidget(CommentedConfigurationNode node, BedrockWidget.Dropdown dropdown)
            throws SerializationException {
        node.node("type").set("dropdown");
        node.node("name").set(dropdown.name());
        node.node("label").set(dropdown.label());
        node.node("options").set(dropdown.options());
        node.node("default").set(dropdown.defaultIndex());
    }

    private void writeSliderWidget(CommentedConfigurationNode node, BedrockWidget.Slider slider)
            throws SerializationException {
        node.node("type").set("slider");
        node.node("name").set(slider.name());
        node.node("label").set(slider.label());
        node.node("min").set(slider.min());
        node.node("max").set(slider.max());
        node.node("step").set(slider.step());
        node.node("default").set(slider.defaultValue());
    }

    private void writeToggleWidget(CommentedConfigurationNode node, BedrockWidget.Toggle toggle)
            throws SerializationException {
        node.node("type").set("toggle");
        node.node("name").set(toggle.name());
        node.node("label").set(toggle.label());
        node.node("default").set(toggle.defaultValue());
    }

    // --- command block ------------------------------------------------------------------------------------------

    /** Emit the top-level {@code command {}} open-command block, the inverse of {@code parseOpenCommand}. */
    private void writeCommand(CommentedConfigurationNode node, OpenCommandSpec command) throws SerializationException {
        node.node("name").set(command.name());
        writeStringsIfAny(node.node("aliases"), command.aliases());
        setIfPresent(node.node("permission"), command.permission().orElse(null));
        setIfPresent(node.node("deny-message"), command.denyMessage().orElse(null));
        setIfTrue(node.node("console"), command.consoleAllowed());
        for (ArgumentSpec argument : command.arguments()) {
            writeArgument(node.node("arguments").appendListNode(), argument);
        }
        setIfPresent(node.node("usage"), command.usage().orElse(null));
    }

    private void writeArgument(CommentedConfigurationNode node, ArgumentSpec argument) throws SerializationException {
        node.node("name").set(argument.name());
        node.node("type").set(argTypeToken(argument.type()));
        setIfTrue(node.node("greedy"), argument.greedy());
    }

    /** The config token an {@link ArgumentSpec.ArgType} parses back from, the inverse of {@code ArgType.parse}. */
    private static String argTypeToken(ArgumentSpec.ArgType type) {
        return switch (type) {
            case STRING -> "string";
            case INT -> "int";
            case DOUBLE -> "double";
            case BOOL -> "bool";
            case MATERIAL -> "material";
            case WORLD -> "world";
            case ONLINE_PLAYER -> "online-player";
            case PLAYER -> "player";
        };
    }

    // --- refs / slots -------------------------------------------------------------------------------------------

    /** Emit a ref list only when it has entries, so an absent list stays absent and re-reads as empty. */
    private void writeRefsIfAny(CommentedConfigurationNode node, List<Ref> refs) throws SerializationException {
        if (!refs.isEmpty()) {
            writeRefs(node, refs);
        }
    }

    private void writeRefs(CommentedConfigurationNode node, List<Ref> refs) throws SerializationException {
        for (Ref ref : refs) {
            writeRef(node.appendListNode(), ref);
        }
    }

    /**
     * Emit one action ref: a bare {@code id:value} token when it carries no per-action modifiers, otherwise a map that
     * layers the {@code delay}/{@code chance}/{@code deny} the loader's map form reads.
     */
    private void writeRef(CommentedConfigurationNode entry, Ref ref) throws SerializationException {
        if (ref.delayTicks() == 0 && ref.chance() == 100.0 && ref.deny().isEmpty()) {
            entry.set(refToken(ref));
            return;
        }
        entry.node("do").set(refToken(ref));
        if (ref.delayTicks() != 0) {
            entry.node("delay").set(ref.delayTicks());
        }
        if (ref.chance() != 100.0) {
            entry.node("chance").set(ref.chance());
        }
        if (ref.deny().isPresent()) {
            entry.node("deny").set(refToken(ref.deny().get()));
        }
    }

    /** A ref's compact token: its bare id, or {@code id:value} when it carries a value argument. */
    private static String refToken(Ref ref) {
        return ref.args().isEmpty() ? ref.id() : ref.id() + ":" + ref.args().getOrDefault("value", "");
    }

    /** A slot set as its compact token list, collapsing each maximal ascending run into an {@code lo-hi} range. */
    private static List<String> slotTokens(SlotSet set) {
        List<Integer> slots = set.slots();
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < slots.size()) {
            int start = slots.get(i);
            int end = start;
            while (i + 1 < slots.size() && slots.get(i + 1) == end + 1) {
                end = slots.get(++i);
            }
            tokens.add(start == end ? String.valueOf(start) : start + "-" + end);
            i++;
        }
        return tokens;
    }

    // --- small helpers ------------------------------------------------------------------------------------------

    private static void writeStringsIfAny(CommentedConfigurationNode node, List<String> values)
            throws SerializationException {
        if (!values.isEmpty()) {
            node.set(values);
        }
    }

    private static void setIfTrue(CommentedConfigurationNode node, boolean value) throws SerializationException {
        if (value) {
            node.set(true);
        }
    }

    private static void setIfPresent(CommentedConfigurationNode node, @Nullable String value)
            throws SerializationException {
        if (value != null) {
            node.set(value);
        }
    }

    /** Set an optional boxed value (Integer/Double/Boolean) only when present, so an unset field stays absent. */
    private static void setBoxed(CommentedConfigurationNode node, @Nullable Object value)
            throws SerializationException {
        if (value != null) {
            node.set(value);
        }
    }

    /** Run a serializing action that may throw, wrapping the checked exception so it can sit inside a lambda. */
    private static void setQuiet(SerializingAction action) {
        try {
            action.run();
        } catch (SerializationException failure) {
            throw new IllegalStateException("failed to serialize menu spec", failure);
        }
    }

    /** A serializing step usable from a lambda: it may raise the checked {@link SerializationException}. */
    @FunctionalInterface
    private interface SerializingAction {
        void run() throws SerializationException;
    }

    private static Map<ClickKind, String> gestureKeys() {
        Map<ClickKind, String> keys = new java.util.EnumMap<>(ClickKind.class);
        keys.put(ClickKind.LEFT, "left");
        keys.put(ClickKind.RIGHT, "right");
        keys.put(ClickKind.SHIFT_LEFT, "shift-left");
        keys.put(ClickKind.SHIFT_RIGHT, "shift-right");
        keys.put(ClickKind.MIDDLE, "middle");
        keys.put(ClickKind.DROP, "drop");
        keys.put(ClickKind.CONTROL_DROP, "control-drop");
        keys.put(ClickKind.DOUBLE_CLICK, "double-click");
        keys.put(ClickKind.ANY, "any");
        return Map.copyOf(keys);
    }

    /** Render the populated spec node to HOCON text through the same save path the menu converters use. */
    private static String render(CommentedConfigurationNode root) {
        StringWriter writer = new StringWriter();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(writer))
                .build();
        try {
            loader.save(root);
        } catch (ConfigurateException failure) {
            throw new IllegalStateException("failed to render menu spec", failure);
        }
        return writer.toString();
    }
}

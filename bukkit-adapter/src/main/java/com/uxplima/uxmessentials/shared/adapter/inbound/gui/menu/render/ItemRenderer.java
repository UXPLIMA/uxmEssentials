package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.ExpressionException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Expressions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.DataComponents;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.LoreMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RichMeta;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.Tooltips;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves one {@link MenuItemSpec} into the {@link ItemStack} a viewer sees. The spec carries only raw text
 * a {@code @key}, an inline literal, or a {@code %placeholder%}, so this is where those forms turn into a
 * concrete material, an Adventure name and lore, and the cosmetic decor. Material placeholders expand to a
 * material name; name/lore placeholders expand inline before the text is rendered or looked up in the catalog.
 * Unknown materials fall back to {@link Material#STONE} rather than failing a render, so one bad spec line
 * never blanks a whole menu.
 */
@NullMarked
public final class ItemRenderer {

    /** A single {@code %token%} placeholder. {@code group(1)} is the bare token name. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\w+)%");

    /**
     * A {@code {math: <expr>}} block evaluated after the {@code %token%} pass; {@code group(1)} is the inner
     * expression. The {@code math:} head and the brace-free body distinguish it from a catalog {@code {token}}
     * argument, which carries neither a colon nor whitespace, so the two brace grammars never collide.
     */
    private static final Pattern MATH = Pattern.compile("\\{math:\\s*([^{}]*)\\}");

    /** Prefix marking a token as a typed command argument resolved from the open context rather than the registry. */
    private static final String ARGUMENT_PREFIX = "argument_";

    /** The deepest a menu-local placeholder template may nest before expansion stops; a cycle simply hits this. */
    private static final int MAX_LOCAL_DEPTH = 8;

    /**
     * Per-render recursion depth for menu-local placeholder expansion. A render is synchronous on one region/entity
     * thread, so a thread-local counter isolates concurrent renders on different threads and needs no lock. It is
     * incremented on each {@link #substituteLocal} entry and decremented in a {@code finally}, so a template that
     * throws still unwinds the count. This mirrors the global {@code CustomPlaceholders} guard.
     */
    private static final ThreadLocal<int[]> LOCAL_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** Default custom-effect duration in ticks when a {@code effect:amplifier} token omits the duration. */
    private static final int DEFAULT_EFFECT_TICKS = 600;

    private final GuiText guiText;
    private final PlaceholderRegistry placeholders;
    private final IconProviders iconProviders;

    /**
     * The plain two-argument form, kept so the engine's many existing call sites are untouched. It renders with
     * the {@link IconProviders#defaults() default} icon chain, skull sources and viewer equipment, so a menu
     * gains those for free; HeadDatabase needs the hook and so is only present on the three-argument form.
     */
    public ItemRenderer(GuiText guiText, PlaceholderRegistry placeholders) {
        this(guiText, placeholders, IconProviders.defaults());
    }

    /**
     * The full form the composition root uses: the same renderer plus an explicit {@link IconProviders} chain,
     * which bootstrap builds with HeadDatabase wired in so {@code hdb:<id>} resolves (and degrades to a plain
     * head when HeadDatabase is absent).
     */
    public ItemRenderer(GuiText guiText, PlaceholderRegistry placeholders, IconProviders iconProviders) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.iconProviders = Objects.requireNonNull(iconProviders, "iconProviders");
    }

    /**
     * Resolve a menu's raw title string for {@code ctx} the same way an item name resolves: every {@code %token%}
     * is substituted, then a {@code @key} title is looked up in the viewer's catalog with its {@code {token}}
     * arguments filled from the same placeholders. This lets a subject-driven title (a home's label, a warp's name)
     * render through the open context rather than being frozen to a static key. A blank title yields an empty
     * component so a spec may still open titleless.
     */
    public Component title(String rawTitle, MenuContext ctx) {
        Objects.requireNonNull(rawTitle, "rawTitle");
        Objects.requireNonNull(ctx, "ctx");
        return resolveText(rawTitle, ctx);
    }

    /**
     * The item's resolved display name followed by its lore, as one flat label. What the hybrid form renderer shows
     * on the button that stands in for this item. A Bedrock SimpleForm button carries a single {@code \n}-separated
     * string rather than a rich icon, so the name and each spec lore line are put through the same {@code
     * %token%}/{@code @key} resolution {@link #render} uses, flattened of formatting, and joined one per line; a
     * Bedrock viewer then reads the same name and lore a Java viewer sees on the icon. An item with no lore yields
     * just the name, and a blank spec lore line stays a blank line so the operator's spacing carries over. The spec
     * lore ({@link MenuItemSpec#lore}) is used deliberately. The operator's written lore, not any lore a {@code b64:}
     * or provider icon embeds, since that is what belongs on a form button.
     */
    public String buttonText(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        StringBuilder label = new StringBuilder(plainLine(item.name(), ctx));
        for (String line : item.lore()) {
            label.append('\n').append(plainLine(line, ctx));
        }
        return label.toString();
    }

    /**
     * The resolved icon material spec for {@code item}. The very string the icon providers and the material fallback
     * read, with any {@code %token%} expanded ({@code %head%} → {@code skull:Notch}, or a literal {@code DIAMOND}). The
     * hybrid Bedrock renderer reads this to source a form button's image, so a per-entry {@code skull:%player%} icon
     * resolves against the entry's own context exactly as it would on a chest.
     */
    public String materialSpec(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        return resolveMaterialSpec(item.material(), ctx);
    }

    /**
     * A raw spec string resolved through the same {@code %token%}/{@code @key} path an item name takes, then flattened
     * to plain text. A Bedrock CustomForm's title, intro content and widget labels/options are flat strings, so the
     * hybrid form renderer resolves each operator-written string here, carrying a per-entry or per-open token through
     *, exactly as a button label or a menu title would.
     */
    public String plainText(String raw, MenuContext ctx) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(ctx, "ctx");
        return plainLine(raw, ctx);
    }

    /**
     * One text line resolved through the same {@code %token%}/{@code @key} path an icon name or lore line takes, then
     * flattened to plain text. A Bedrock form label is a flat string, so a button's name and lore reach it stripped
     * of all formatting.
     */
    private String plainLine(String line, MenuContext ctx) {
        return PlainTextComponentSerializer.plainText().serialize(resolveText(line, ctx));
    }

    /**
     * A shared {@link MessageKey} resolved for {@code viewer} and flattened to plain text, the label a Bedrock form
     * needs where the chest paints a wordless icon (a confirm window's yes/no wool). Goes through the same
     * {@link GuiText} catalog path an item name takes, so the label honours the viewer's locale, then drops all
     * formatting to a flat string.
     */
    public String plainMessage(PlayerRef viewer, MessageKey key) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        return PlainTextComponentSerializer.plainText().serialize(guiText.text(viewer, key));
    }

    public ItemStack render(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        String materialSpec = resolveMaterialSpec(item.material(), ctx);
        Optional<ItemStack> base = iconProviders.resolve(materialSpec, ctx);
        Component name = resolveText(item.name(), ctx);
        List<Component> lore = combineLore(item, base, ctx);
        // The canon writes a tile's title on the first lore line under a blank name; a lore-less button keeps
        // the one-line name it was written with.
        List<Component> titled = Tiles.titled(name, lore);
        Component shown = titled == lore ? name : Tiles.blankName();
        ItemStack built = applyDecor(builderFor(base, materialSpec).name(shown).lore(titled), item.decor(), ctx)
                .build();
        // Tag every tile the engine renders. Including an equipment/self-inventory icon, which is already a clone of
        // the viewer's real item, so any display copy that escapes into a real inventory is strippable. The mark rides
        // on the display copy only; the player's own items are never touched here (MenuItemMark, MenuAntiDupeListener).
        ItemStack marked = MenuItemMark.mark(built);
        // Last, and on the finished stack: the whole-tooltip flag and the hidden set live on one component, so
        // whichever is written second decides, and a spec that asked for no tooltip must not get one back.
        hideVanillaLines(marked, item.decor().meta());
        return marked;
    }

    /**
     * Combine the base icon's own lore with the spec lore per {@code item}'s {@link LoreMode}. REPLACE keeps only
     * the spec lore (the historic behaviour); APPEND puts the base lore first, then the spec lore beneath it;
     * PREPEND puts the spec lore first, then the base lore. The base lore comes from the resolved icon, a
     * serialized {@code b64:} stack or a provider item may carry its own, so a plain material, which has no lore
     * of its own, renders identically under any mode.
     */
    private List<Component> combineLore(MenuItemSpec item, Optional<ItemStack> base, MenuContext ctx) {
        List<Component> specLore = lore(item, ctx);
        if (item.loreMode() == LoreMode.REPLACE) {
            return specLore;
        }
        List<Component> baseLore =
                base.flatMap(icon -> Optional.ofNullable(icon.lore())).orElse(List.of());
        if (baseLore.isEmpty()) {
            return specLore;
        }
        List<Component> combined = new ArrayList<>(baseLore.size() + specLore.size());
        if (item.loreMode() == LoreMode.APPEND) {
            combined.addAll(baseLore);
            combined.addAll(specLore);
        } else {
            combined.addAll(specLore);
            combined.addAll(baseLore);
        }
        return combined;
    }

    /**
     * Build the lore components for {@code item}. Each spec line maps to one component as before, except an
     * inline/placeholder literal whose resolved value carries newlines, which expands into one component per
     * {@code \n}-separated segment. This lets a per-entry icon (the kit/warp browse rows) emit a variable number
     * of lore lines from a single {@code %placeholder%}, a {@code ✔/✘} per requirement, plus the conditional
     * cooldown/cost/claimable lines: rather than being capped at the spec's fixed line count.
     */
    public List<Component> lore(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        List<Component> out = new ArrayList<>(item.lore().size());
        for (String line : item.lore()) {
            appendLore(line, ctx, out);
        }
        return out;
    }

    /**
     * Append the component(s) for one lore spec line to {@code out}. A blank spec stays one blank line, and a
     * {@code @key} catalog line stays a single component (the catalog owns its own layout, so its output is never
     * split here). Any other line is an inline/placeholder literal: its {@code %token%}s are substituted, then the
     * result is split on {@code \n} so a multi-line placeholder value becomes one lore component per segment. The
     * {@code -1} split limit keeps trailing empty segments, and a value with no newline yields exactly one
     * component, identical to a plain literal line.
     */
    private void appendLore(String spec, MenuContext ctx, List<Component> out) {
        if (spec.isEmpty()) {
            out.add(Component.empty());
            return;
        }
        if (spec.startsWith("@")) {
            out.add(resolveText(spec, ctx));
            return;
        }
        String substituted = applyMath(substitutePlaceholders(dropMathMissingAnOperand(spec, ctx), ctx));
        for (String segment : substituted.split("\n", -1)) {
            out.add(StyledText.render(segment));
        }
    }

    /**
     * The resolved material spec string: every {@code %token%} expands in place through its placeholder and the rest
     * of the value is kept, so a literal spec is itself. This is the string the icon providers and the material
     * fallback both read, which is why the text around a token matters as much as the token: {@code %head%} yielding
     * {@code skull:Notch} reaches the skull provider, {@code skull:%player%} reaches it as {@code skull:Notch} rather
     * than as a bare name no provider claims, and {@code DIAMOND} reaches the material lookup. Substituting in place
     * rather than replacing the whole value is what the lore and name paths have always done; a material that
     * resolves to a name nothing recognises falls back to stone silently, so getting this wrong showed up as a plain
     * grey icon rather than as an error.
     */
    private String resolveMaterialSpec(String raw, MenuContext ctx) {
        return substitutePlaceholders(raw, ctx);
    }

    /**
     * The {@link ItemBuilder} for a resolved {@code base}: from an icon provider's stack (a skull source, the
     * viewer's equipment, a serialized {@code b64:} stack, an HDB head) when one claimed the spec, else a plain
     * item of the named material. A provider's stack still has the item's name, lore and decor layered on top by
     * the caller, exactly as a material item does. It is split out from lore-combining so the resolved base stack
     * whose own lore an append/prepend mode reads, stays visible to {@link #render}.
     */
    private ItemBuilder builderFor(Optional<ItemStack> base, String spec) {
        return base.map(ItemBuilder::from).orElseGet(() -> ItemBuilder.of(materialOrStone(spec)));
    }

    /**
     * The material named by {@code name}, falling back to {@link Material#STONE} for a blank or unknown name so a
     * typo (or a provider-shaped spec no provider claimed) never aborts the render.
     */
    private Material materialOrStone(String name) {
        if (name.isBlank()) {
            return Material.STONE;
        }
        Material material = Material.matchMaterial(name);
        return material != null ? material : Material.STONE;
    }

    /**
     * Resolve one text line. Every {@code %token%} is substituted with its placeholder value first; then a line
     * that originally began with {@code @} is looked up in the locale catalog (the rest is the message key),
     * while any other line is rendered as an inline MiniMessage literal. An empty spec yields an empty component
     * so the item simply has no name/lore line rather than a stray blank.
     */
    private Component resolveText(String s, MenuContext ctx) {
        if (s.isEmpty()) {
            return Component.empty();
        }
        String substituted = substitutePlaceholders(dropMathMissingAnOperand(s, ctx), ctx);
        if (s.startsWith("@")) {
            String key = substituted.substring(1);
            // The catalog entry may carry {token} arguments (e.g. {sound}, {warp}); fill them from the same
            // placeholders a %token% would use, so a per-entry list item shows that entry's value.
            return guiText.text(ctx.viewer(), () -> key, placeholders.resolveAll(ctx));
        }
        return StyledText.render(applyMath(substituted));
    }

    /**
     * Evaluate every {@code {math: <expr>}} block in an already-token-substituted line, replacing each with the
     * evaluator's formatted numeric result. Running after the {@code %token%} pass means the inner expression is
     * already literal, so {@code {math: %coins% * 2}} evaluates the resolved number rather than the token. A line
     * with no math block returns unchanged (the {@code indexOf} fast-path avoids allocating a matcher for the common
     * case); a block the sandbox rejects becomes empty rather than aborting the render, the same fail-soft stance the
     * rest of the renderer takes. Material specs never flow through here, so a {@code %token%} material is untouched.
     */
    private String applyMath(String text) {
        if (text.indexOf("{math:") < 0) {
            return text;
        }
        Matcher matcher = MATH.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(evaluateMath(matcher.group(1))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Drop every {@code {math: …}} block in an unsubstituted line whose expression names a token that resolves to
     * nothing, so the block renders as the gap a missing operand deserves rather than as a number.
     *
     * <p>The token pass runs before the math pass, which leaves an unresolved token as a hole in the expression.
     * {@code {math: %missing% * 2}} becomes {@code " * 2"}, which the evaluator refuses, so the line shows a gap
     * and the operator sees something is wrong. {@code {math: %missing% + 1}} becomes {@code " + 1"}, which
     * parses, because plus is also a unary prefix, and the player is shown the number 1. Minus behaves the same
     * way, and those two are the commonest operators in a menu file: the arithmetic that reads worst is the
     * arithmetic that fails invisibly.
     *
     * <p>This runs on the line as written, before substitution, which is why it can tell a hole from a value and
     * why the pass order does not have to change. A block that arrives inside a local placeholder's value is
     * checked by {@link #substituteLocal} on that template for the same reason. A block assembled by a registry
     * placeholder's return value is not reachable this way and keeps the old behaviour; changing that means
     * reordering the passes, which trades this defect for a different one.
     *
     * <p>Tokens inside a math block resolve twice, once here and once in the substitution that follows. The pass
     * is gated on the line carrying a math block at all, so nothing else pays for it.
     */
    private String dropMathMissingAnOperand(String raw, MenuContext ctx) {
        if (raw.indexOf("{math:") < 0) {
            return raw;
        }
        Matcher matcher = MATH.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String block = missesAnOperand(matcher.group(1), ctx) ? "" : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(block));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Whether any {@code %token%} in an unsubstituted math expression resolves to nothing. */
    private boolean missesAnOperand(String expression, MenuContext ctx) {
        Matcher matcher = PLACEHOLDER.matcher(expression);
        while (matcher.find()) {
            if (resolveToken(matcher.group(1), ctx).isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** Evaluate one substituted expression to the evaluator's formatted number, or empty when it cannot be parsed. */
    private static String evaluateMath(String expression) {
        try {
            return Expressions.format(Expressions.evaluateNumber(expression));
        } catch (ExpressionException | RuntimeException rejected) {
            // A malformed or non-numeric math block renders blank rather than leaking the raw expression or aborting.
            return "";
        }
    }

    /** Replace every {@code %token%} in {@code source} with its resolved value (argument or registry, or empty). */
    private String substitutePlaceholders(String source, MenuContext ctx) {
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = resolveToken(matcher.group(1), ctx);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Resolve one bare {@code %token%} name to its text, most-specific source first. An {@code argument_<name>} token
     * is a typed command argument the menu was opened with, so it reads from {@link MenuContext#arguments()} (an
     * unknown name yields empty). Otherwise the menu's own {@code placeholders {}} block is consulted <em>before</em>
     * the shared registry, so a menu-scoped {@code %name%} overrides a built-in, data reader or global custom for this
     * menu alone; a local template is expanded through {@link #substituteLocal}. Any token the local block does not
     * define resolves through the placeholder registry exactly as before.
     */
    private String resolveToken(String token, MenuContext ctx) {
        if (token.startsWith(ARGUMENT_PREFIX)) {
            return ctx.arguments().getOrDefault(token.substring(ARGUMENT_PREFIX.length()), "");
        }
        String local = ctx.localPlaceholders().get(token);
        if (local != null) {
            return substituteLocal(local, ctx);
        }
        return placeholders.resolveOrReport(token, ctx).orElse("");
    }

    /**
     * Expand a menu-local placeholder template, resolving every inner {@code %token%} through {@link #resolveToken}
     * so a nested local resolves local-first while a built-in/data/global inner token resolves via the registry. A
     * {@code {math: …}} block and MiniMessage {@code <tags>} are left verbatim for the outer line passes that own them,
     * so a local {@code "{math: %coins% * 2}"} becomes {@code "{math: 50 * 2}"} here and the outer {@code applyMath}
     * evaluates it. The depth guard bounds recursion: at {@link #MAX_LOCAL_DEPTH} the template is returned unexpanded,
     * so a cycle ({@code a=%b%}, {@code b=%a%}) terminates instead of overflowing the stack.
     */
    private String substituteLocal(String template, MenuContext ctx) {
        int[] depth = LOCAL_DEPTH.get();
        if (depth[0] >= MAX_LOCAL_DEPTH) {
            return template;
        }
        depth[0]++;
        try {
            Matcher matcher = PLACEHOLDER.matcher(dropMathMissingAnOperand(template, ctx));
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(resolveToken(matcher.group(1), ctx)));
            }
            matcher.appendTail(out);
            return out.toString();
        } finally {
            depth[0]--;
        }
    }

    /** Layer the spec's amount, glow, model data, item flags, and native rich meta onto the in-progress item. */
    private ItemBuilder applyDecor(ItemBuilder builder, ItemDecor decor, MenuContext ctx) {
        applyAmount(builder, decor, ctx);
        builder.glow(glowing(decor, ctx));
        applyModelData(builder, decor, ctx);
        ItemFlag[] flags = resolveFlags(decor.flagTokens());
        if (flags.length > 0) {
            builder.flags(flags);
        }
        applyRichMeta(builder, decor.meta());
        return builder;
    }

    /**
     * Whether the item glints: a {@code %token%} glow resolves through the placeholder path and counts as on for
     * {@code true}/{@code yes}/{@code 1}, otherwise the static spec value stands. This is what lets one list
     * template glint only the entry a screen considers selected.
     */
    private boolean glowing(ItemDecor decor, MenuContext ctx) {
        Optional<String> token = decor.meta().dynamicGlow();
        if (token.isEmpty()) {
            return decor.glow();
        }
        String resolved = substitutePlaceholders(token.get(), ctx).strip();
        return resolved.equalsIgnoreCase("true") || resolved.equalsIgnoreCase("yes") || resolved.equals("1");
    }

    /**
     * Set the stack amount, preferring a {@code dynamicAmount} {@code %token%} resolved to a number over the static
     * spec value and clamping the floor to one. A value above the material's max stack size keeps a single item
     * rather than aborting the render, matching the fail-soft stance the rest of the decor takes.
     */
    private void applyAmount(ItemBuilder builder, ItemDecor decor, MenuContext ctx) {
        int amount = Math.max(1, dynamicInt(decor.meta().dynamicAmount(), ctx).orElse(decor.amount()));
        try {
            builder.amount(amount);
        } catch (IllegalArgumentException aboveMax) {
            // A dynamic amount above the material's max stack size keeps a single item rather than aborting.
            builder.amount(1);
        }
    }

    /** Apply the custom model data, preferring a {@code dynamicModelData} {@code %token%} over the static value. */
    private void applyModelData(ItemBuilder builder, ItemDecor decor, MenuContext ctx) {
        dynamicInt(decor.meta().dynamicModelData(), ctx)
                .or(decor::modelData)
                .ifPresent(data -> safely(() -> builder.customModelData(data)));
    }

    /** Resolve a {@code %token%} string through the placeholder path and parse it to an int, when both succeed. */
    private Optional<Integer> dynamicInt(Optional<String> token, MenuContext ctx) {
        return token.map(raw -> substitutePlaceholders(raw, ctx)).flatMap(ItemRenderer::parseInt);
    }

    /**
     * Apply every native rich-meta value the operator declared, resolving each token against the live registries.
     * Anything that does not resolve. An unknown enchant, an unsupported colour, a banner/trim registry the
     * runtime cannot model: is skipped rather than aborting the render, the same way an unknown flag is skipped.
     */
    private void applyRichMeta(ItemBuilder builder, RichMeta meta) {
        if (meta.unbreakable()) {
            safely(() -> builder.unbreakable(true));
        }
        meta.enchantments().forEach(token -> applyEnchant(builder, token, false));
        meta.storedEnchantments().forEach(token -> applyEnchant(builder, token, true));
        meta.leatherColor()
                .flatMap(ItemRenderer::parseColor)
                .ifPresent(color -> safely(() -> builder.leatherColor(color)));
        applyPotion(builder, meta.potion());
        meta.bannerPatterns().forEach(token -> applyBannerPattern(builder, token));
        meta.trim().ifPresent(trim -> applyTrim(builder, trim));
        meta.damage().ifPresent(damage -> applyDamage(builder, damage));
        meta.itemModel().ifPresent(model -> applyItemModel(builder, model));
        applyDataComponents(builder, meta.components());
    }

    /**
     * Apply the native data-components the operator declared. Each one resolves and applies independently and
     * fail-soft: an unknown rarity, a malformed tooltip-style key, an unresolved attribute/operation/slot, or a
     * component a runtime cannot model is skipped rather than aborting the render, the same as an unknown flag.
     */
    private void applyDataComponents(ItemBuilder builder, DataComponents components) {
        components
                .rarity()
                .flatMap(ItemRenderer::parseRarity)
                .ifPresent(rarity -> safely(() -> builder.rarity(rarity)));
        components.tooltipStyle().ifPresent(raw -> applyTooltipStyle(builder, raw));
        components
                .enchantGlint()
                .ifPresent(glint -> safely(() -> builder.editMeta(meta -> meta.setEnchantmentGlintOverride(glint))));
        components.enchantable().ifPresent(value -> safely(() -> builder.enchantable(value)));
        applyAttributeModifiers(builder, components.attributeModifiers());
        components.food().ifPresent(food -> applyFood(builder, food));
        components.tool().ifPresent(tool -> applyTool(builder, tool));
    }

    /**
     * Decide which components the client may write a tooltip line for, and hide the rest.
     *
     * <p>A menu icon is a button and the client does not know that: left alone it writes the mining speed under an
     * IRON_PICKAXE, "Dyed" under a tinted chestplate and the flight duration under a firework, beneath a lore that
     * already said what the button does. So the default silences {@link Tooltips#VANILLA_COMPONENTS}, minus
     * whatever the {@code decor} block asked for: an operator who wrote {@code enchantments} or {@code trim} meant
     * that line to be read, and the line the client added by itself is the one nobody chose. {@code
     * hide-vanilla-tooltip=false} gives every line back, and {@code hidden-components} names an exact set and wins
     * over both.
     *
     * <p>This is about drawing and not about behaviour: hiding {@code EQUIPPABLE} removes the line, not the equip.
     * A menu stops the equip by cancelling the click, which is where that belongs.
     */
    private static void hideVanillaLines(ItemStack item, RichMeta meta) {
        Set<DataComponentType> hidden = hiddenFor(meta);
        safely(() -> Tooltips.hide(item, hidden));
        // After, not before: both live on the same component, and hiding the client's lines must not hand back a
        // tooltip the spec asked to take away entirely.
        meta.components()
                .hideTooltip()
                .ifPresent(whole -> safely(() -> item.editMeta(edited -> edited.setHideTooltip(whole))));
    }

    /** The components to silence for {@code meta}: the decision this class owns, split out so a test can read it. */
    static Set<DataComponentType> hiddenFor(RichMeta meta) {
        DataComponents components = meta.components();
        if (!components.hiddenComponents().isEmpty()) {
            return components.hiddenComponents().stream()
                    .map(token -> fromRegistry(RegistryKey.DATA_COMPONENT_TYPE, keyOf(token)))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (!components.hideVanillaTooltip().orElse(true)) {
            return Set.of();
        }
        Set<DataComponentType> hidden = new HashSet<>(Tooltips.VANILLA_COMPONENTS);
        hidden.removeAll(declaredByTheOperator(meta));
        return Set.copyOf(hidden);
    }

    /**
     * The components the {@code decor} block filled in itself, whose tooltip lines are therefore content rather
     * than noise. Everything else in {@link Tooltips#VANILLA_COMPONENTS} comes from the material alone.
     */
    private static Set<DataComponentType> declaredByTheOperator(RichMeta meta) {
        Set<DataComponentType> declared = new HashSet<>();
        if (meta.unbreakable()) {
            declared.add(DataComponentTypes.UNBREAKABLE);
        }
        if (!meta.enchantments().isEmpty()) {
            declared.add(DataComponentTypes.ENCHANTMENTS);
        }
        if (!meta.storedEnchantments().isEmpty()) {
            declared.add(DataComponentTypes.STORED_ENCHANTMENTS);
        }
        if (meta.leatherColor().isPresent()) {
            declared.add(DataComponentTypes.DYED_COLOR);
        }
        if (!meta.potion().equals(RichMeta.PotionSpec.NONE)) {
            declared.add(DataComponentTypes.POTION_CONTENTS);
        }
        if (!meta.bannerPatterns().isEmpty()) {
            declared.add(DataComponentTypes.BANNER_PATTERNS);
        }
        if (meta.trim().isPresent()) {
            declared.add(DataComponentTypes.TRIM);
        }
        if (!meta.components().attributeModifiers().isEmpty()) {
            declared.add(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        }
        if (meta.components().tool().isPresent()) {
            declared.add(DataComponentTypes.TOOL);
        }
        return declared;
    }

    /** Override the tooltip-style with a resource-pack key, skipping a malformed or unsupported key. */
    private void applyTooltipStyle(ItemBuilder builder, String raw) {
        NamespacedKey key = keyOf(raw);
        if (key == null) {
            return;
        }
        safely(() -> builder.tooltipStyle(key));
    }

    /** Apply each {@code attribute:amount:operation:slot} modifier, deriving a unique key per entry by its index. */
    private void applyAttributeModifiers(ItemBuilder builder, List<String> tokens) {
        for (int index = 0; index < tokens.size(); index++) {
            applyAttributeModifier(builder, tokens.get(index), index);
        }
    }

    /**
     * Apply one {@code attribute:amount:operation:slot} modifier. The operation defaults to {@code add_number} and
     * the slot to {@code any} when omitted; an unresolved attribute, amount, operation, or slot skips the modifier.
     */
    private void applyAttributeModifier(ItemBuilder builder, String token, int index) {
        String[] parts = token.split(":", 4);
        if (parts.length < 2) {
            return;
        }
        Optional<Attribute> attribute = fromRegistry(RegistryKey.ATTRIBUTE, attributeKey(parts[0]));
        Optional<Double> amount = parseDouble(parts[1]);
        Optional<AttributeModifier.Operation> operation = parseOperation(parts.length > 2 ? parts[2] : "add_number");
        Optional<EquipmentSlotGroup> slot = parseSlotGroup(parts.length > 3 ? parts[3] : "");
        if (attribute.isEmpty() || amount.isEmpty() || operation.isEmpty() || slot.isEmpty()) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(
                "uxmessentials:" + attribute.get().getKey().value() + "_" + index);
        if (key == null) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(key, amount.get(), operation.get(), slot.get());
        safely(() -> builder.attribute(attribute.get(), modifier));
    }

    /** Build the food component from the declared overrides, leaving any unset field at the component's default. */
    private void applyFood(ItemBuilder builder, DataComponents.FoodSpec spec) {
        safely(() -> builder.editMeta(meta -> {
            FoodComponent food = meta.getFood();
            spec.nutrition().ifPresent(food::setNutrition);
            spec.saturation().ifPresent(saturation -> food.setSaturation(saturation.floatValue()));
            spec.canAlwaysEat().ifPresent(food::setCanAlwaysEat);
            meta.setFood(food);
        }));
    }

    /** Build the tool component from the declared overrides, leaving any unset field at the component's default. */
    private void applyTool(ItemBuilder builder, DataComponents.ToolSpec spec) {
        safely(() -> builder.editMeta(meta -> {
            ToolComponent tool = meta.getTool();
            spec.defaultMiningSpeed().ifPresent(speed -> tool.setDefaultMiningSpeed(speed.floatValue()));
            spec.damagePerBlock().ifPresent(tool::setDamagePerBlock);
            meta.setTool(tool);
        }));
    }

    /** Map the spec's raw flag tokens to Bukkit {@link ItemFlag}s, skipping any token that is not a flag name. */
    private ItemFlag[] resolveFlags(List<String> tokens) {
        List<ItemFlag> flags = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            try {
                flags.add(ItemFlag.valueOf(enumName(token)));
            } catch (IllegalArgumentException unknownFlag) {
                // A spec naming a flag that does not exist on this server should not abort the render.
            }
        }
        return flags.toArray(ItemFlag[]::new);
    }

    /**
     * Apply one {@code name:level} enchant token to the item or its stored-enchant book. The level defaults to one
     * when absent or unparseable, and an id that does not resolve in the enchantment registry is skipped.
     */
    private void applyEnchant(ItemBuilder builder, String token, boolean stored) {
        int colon = token.lastIndexOf(':');
        Optional<Integer> parsed = colon < 0 ? Optional.empty() : parseInt(token.substring(colon + 1));
        String name = colon < 0 || parsed.isEmpty() ? token : token.substring(0, colon);
        fromRegistry(RegistryKey.ENCHANTMENT, keyOf(name)).ifPresent(enchant -> {
            int level = Math.max(1, parsed.orElse(1));
            safely(() -> {
                if (stored) {
                    builder.storedEnchant(enchant, level);
                } else {
                    builder.enchant(enchant, level);
                }
            });
        });
    }

    /** Apply a potion's base type, display colour, and any {@code effect:amplifier:durationTicks} custom effects. */
    private void applyPotion(ItemBuilder builder, RichMeta.PotionSpec potion) {
        potion.type()
                .flatMap(name -> fromRegistry(Registry.POTION, keyOf(name)))
                .ifPresent(base ->
                        safely(() -> builder.editTypedMeta(PotionMeta.class, meta -> meta.setBasePotionType(base))));
        potion.color().flatMap(ItemRenderer::parseColor).ifPresent(color -> safely(() -> builder.potionColor(color)));
        potion.effects()
                .forEach(token -> parsePotionEffect(token).ifPresent(e -> safely(() -> builder.potionEffect(e))));
    }

    /** Append one {@code pattern:dyecolor} banner pattern, skipping an unknown pattern id or dye name. */
    private void applyBannerPattern(ItemBuilder builder, String token) {
        int colon = token.lastIndexOf(':');
        if (colon <= 0) {
            return;
        }
        Optional<PatternType> type = fromRegistry(RegistryKey.BANNER_PATTERN, keyOf(token.substring(0, colon)));
        Optional<DyeColor> color = parseDye(token.substring(colon + 1));
        if (type.isPresent() && color.isPresent()) {
            safely(() -> builder.bannerPattern(color.get(), type.get()));
        }
    }

    /** Set the armour trim from a trim-material id and trim-pattern id, skipping either that does not resolve. */
    private void applyTrim(ItemBuilder builder, RichMeta.TrimSpec trim) {
        Optional<TrimMaterial> material = fromRegistry(RegistryKey.TRIM_MATERIAL, keyOf(trim.material()));
        Optional<TrimPattern> pattern = fromRegistry(RegistryKey.TRIM_PATTERN, keyOf(trim.pattern()));
        if (material.isPresent() && pattern.isPresent()) {
            safely(() -> builder.editTypedMeta(
                    ArmorMeta.class, meta -> meta.setTrim(new ArmorTrim(material.get(), pattern.get()))));
        }
    }

    /** Set the durability damage, a no-op on a non-damageable item; a negative value is ignored. */
    private void applyDamage(ItemBuilder builder, int damage) {
        if (damage >= 0) {
            safely(() -> builder.damage(damage));
        }
    }

    /** Override the item model with a resource-pack key, skipping a malformed or unsupported key. */
    private void applyItemModel(ItemBuilder builder, String raw) {
        NamespacedKey key = keyOf(raw);
        if (key == null) {
            return;
        }
        safely(() -> builder.itemModel(key));
    }

    /** Parse a {@code effect:amplifier:durationTicks} token into a {@link PotionEffect}, when its type resolves. */
    private static Optional<PotionEffect> parsePotionEffect(String token) {
        String[] parts = token.split(":", 3);
        Optional<PotionEffectType> type = fromRegistry(Registry.EFFECT, keyOf(parts[0]));
        if (type.isEmpty()) {
            return Optional.empty();
        }
        int amplifier = parts.length > 1 ? Math.max(0, parseInt(parts[1]).orElse(0)) : 0;
        int duration = parts.length > 2 ? parseInt(parts[2]).orElse(DEFAULT_EFFECT_TICKS) : DEFAULT_EFFECT_TICKS;
        return Optional.of(new PotionEffect(type.get(), duration, amplifier));
    }

    /**
     * The enum-constant spelling of an operator's token: trimmed, upper-cased, and with the hyphen a conf file
     * favours folded to the underscore a Java constant uses. Every enum-valued decor token resolves through
     * this, so none of them is stricter than the ones beside it.
     *
     * <p>{@code flags} was the exception and read {@code valueOf} on the raw string while the four parsers next
     * to it folded case, so {@code flags = ["hide_attributes"]} was dropped in a file whose every other key is
     * lower case. The icon still rendered, carrying the tooltip section the operator had asked to hide, which
     * is exactly what a server with no flags configured looks like.
     */
    private static String enumName(String token) {
        return token.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /** A colour as a {@code #RRGGBB} hex, an {@code r,g,b} triple, or a named dye colour; empty when unparseable. */
    private static Optional<Color> parseColor(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (value.startsWith("#")) {
                return Optional.of(Color.fromRGB(Integer.parseInt(value.substring(1), 16)));
            }
            if (value.contains(",")) {
                return rgbTriple(value);
            }
            return Optional.of(DyeColor.valueOf(enumName(value)).getColor());
        } catch (IllegalArgumentException malformed) {
            // A bad hex, an out-of-range channel, or an unknown colour name is skipped, like an unknown flag.
            return Optional.empty();
        }
    }

    /** Parse an {@code r,g,b} triple into a colour; empty when it is not exactly three channels. */
    private static Optional<Color> rgbTriple(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        return Optional.of(Color.fromRGB(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())));
    }

    /** A {@link DyeColor} by case-insensitive name, present only when it matches a known dye. */
    private static Optional<DyeColor> parseDye(String name) {
        try {
            return Optional.of(DyeColor.valueOf(enumName(name)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** An {@link ItemRarity} by case-insensitive name (COMMON/UNCOMMON/RARE/EPIC), empty when it is not a rarity. */
    private static Optional<ItemRarity> parseRarity(String name) {
        try {
            return Optional.of(ItemRarity.valueOf(enumName(name)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** An {@link AttributeModifier.Operation} by name ({@code add_number}/{@code add_scalar}/{@code multiply_scalar_1}). */
    private static Optional<AttributeModifier.Operation> parseOperation(String name) {
        try {
            return Optional.of(AttributeModifier.Operation.valueOf(enumName(name)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** An {@link EquipmentSlotGroup} by name; a blank token is the whole-item {@code any}, an unknown one is skipped. */
    private static Optional<EquipmentSlotGroup> parseSlotGroup(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "", "any" -> Optional.of(EquipmentSlotGroup.ANY);
            case "hand", "mainhand", "main_hand" -> Optional.of(EquipmentSlotGroup.MAINHAND);
            case "off_hand", "offhand" -> Optional.of(EquipmentSlotGroup.OFFHAND);
            case "feet" -> Optional.of(EquipmentSlotGroup.FEET);
            case "legs" -> Optional.of(EquipmentSlotGroup.LEGS);
            case "chest" -> Optional.of(EquipmentSlotGroup.CHEST);
            case "head" -> Optional.of(EquipmentSlotGroup.HEAD);
            case "armor" -> Optional.of(EquipmentSlotGroup.ARMOR);
            case "body" -> Optional.of(EquipmentSlotGroup.BODY);
            default -> Optional.empty();
        };
    }

    /**
     * A registry key for an attribute name. A legacy category prefix ({@code generic.}, {@code player.}, …) is
     * stripped so the old {@code generic.attack_damage} spelling still resolves to the modern flat {@code attack_damage}
     * key; null when the remainder is not a valid key.
     */
    private static @Nullable NamespacedKey attributeKey(String raw) {
        String name = raw.trim();
        int dot = name.lastIndexOf('.');
        return keyOf(dot >= 0 ? name.substring(dot + 1) : name);
    }

    /** The registry entry for {@code key}, empty when the key is null or the data-driven registry cannot model it. */
    private static <T extends Keyed> Optional<T> fromRegistry(RegistryKey<T> registry, @Nullable NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                    RegistryAccess.registryAccess().getRegistry(registry).get(key));
        } catch (RuntimeException registryUnavailable) {
            return Optional.empty();
        }
    }

    /** The entry for {@code key} in a directly-held {@link Registry}, empty when null or the runtime cannot model it. */
    private static <T extends Keyed> Optional<T> fromRegistry(Registry<T> registry, @Nullable NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(registry.get(key));
        } catch (RuntimeException registryUnavailable) {
            return Optional.empty();
        }
    }

    /** A registry key from a raw id, lower-cased so an UPPER_SNAKE config value still resolves; null when malformed. */
    private static @Nullable NamespacedKey keyOf(String raw) {
        return NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** Parse a trimmed integer, empty when the value is not a number. */
    private static Optional<Integer> parseInt(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Parse a trimmed double, empty when the value is not a number. */
    private static Optional<Double> parseDouble(String raw) {
        try {
            return Optional.of(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /**
     * Run one meta application, swallowing any runtime failure so a single unsupported value never aborts the whole
     * render. A meta type the test runtime cannot model (an unimplemented registry or setter) still ships working on
     * real Paper; here it simply degrades to a no-op, the same fail-soft contract as an unknown flag token.
     */
    private static void safely(Runnable application) {
        try {
            application.run();
        } catch (RuntimeException unsupported) {
            // This meta type is not modelled by the runtime (or its value did not resolve); skip, never abort.
        }
    }
}

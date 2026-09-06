package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.DataComponents;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RichMeta;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Renders single menu items against a fake catalog so the material-resolution and decor paths can be checked
 * without loading a real spec. MockBukkit gives a server context so {@code ItemStack}/{@code ItemMeta} behave
 * as on a live server; the catalog returns the key verbatim, which is enough for material/decor assertions.
 */
class ItemRendererTest {

    private ItemRenderer renderer;
    private MenuContext ctx;
    private PlaceholderRegistry placeholders;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        GuiText guiText = new GuiText(new KeyMessages());
        placeholders = new PlaceholderRegistry();
        placeholders.register("icon", c -> "DIAMOND");
        renderer = new ItemRenderer(guiText, placeholders);
        ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPlaceholderMaterial() {
        ItemStack it = renderer.render(item("%icon%", new ItemDecor(1, Optional.empty(), false, List.of())), ctx);
        assertThat(it.getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void unknownMaterialFallsBackToStone() {
        ItemStack it =
                renderer.render(item("not_a_material", new ItemDecor(1, Optional.empty(), false, List.of())), ctx);
        assertThat(it.getType()).isEqualTo(Material.STONE);
    }

    @Test
    void glowMakesTheItemEnchanted() {
        ItemStack it = renderer.render(item("STONE", new ItemDecor(1, Optional.empty(), true, List.of())), ctx);
        // ItemBuilder.glow(true) uses the native glint override, not a dummy enchant, so assert the override.
        assertThat(it.getItemMeta().getEnchantmentGlintOverride()).isTrue();
    }

    @Test
    void aFlagTokenIsAcceptedInTheCaseTheConfFileIsWrittenIn() {
        // Every other key in a menu conf is lower case, and the four enum parsers beside this one fold case, so
        // an operator writing the flag the same way had it dropped: the icon rendered carrying the tooltip
        // section they had asked to hide, which is what an icon with no flags set looks like.
        ItemStack it = renderer.render(
                item("STONE", new ItemDecor(1, Optional.empty(), false, List.of("hide_attributes"))), ctx);
        assertThat(it.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)).isTrue();
    }

    @Test
    void aFlagTokenMayUseTheHyphenTheRestOfTheConfUses() {
        ItemStack it = renderer.render(
                item("STONE", new ItemDecor(1, Optional.empty(), false, List.of("hide-armor-trim"))), ctx);
        assertThat(it.getItemMeta().hasItemFlag(ItemFlag.HIDE_ARMOR_TRIM)).isTrue();
    }

    @Test
    void theDocumentedUpperCaseFlagSpellingKeepsWorking() {
        ItemStack it = renderer.render(
                item("STONE", new ItemDecor(1, Optional.empty(), false, List.of("HIDE_ENCHANTS"))), ctx);
        assertThat(it.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS)).isTrue();
    }

    @Test
    void aTokenThatNamesNoFlagIsSkippedRatherThanFailingTheRender() {
        ItemStack it = renderer.render(
                item("STONE", new ItemDecor(1, Optional.empty(), false, List.of("hide_nothing", "hide_dye"))), ctx);
        assertThat(it.getItemMeta().hasItemFlag(ItemFlag.HIDE_DYE)).isTrue();
    }

    @Test
    void aMathBlockWhoseOperandIsMissingShowsNothingRatherThanAPlausibleNumber() {
        // The token pass leaves an unresolved token as a hole, so "%missing% + 1" reached the evaluator as
        // " + 1". Plus is also a unary prefix, so it parsed and the player was shown the number 1. Minus did
        // the same. Those are the two commonest operators in a menu file.
        assertThat(plainLore(renderer, itemWithLore(List.of("{math: %missing% + 1}"))))
                .containsExactly("");
        assertThat(plainLore(renderer, itemWithLore(List.of("{math: %missing% - 1}"))))
                .containsExactly("");
    }

    @Test
    void aMathBlockWhoseOperandIsMissingAlreadyShowedNothingForTheOtherOperators() {
        // Star and slash are not unary, so these always failed to parse and rendered as a gap. Pinned so the
        // fix for the two that did not is not read later as the whole of the behaviour.
        assertThat(plainLore(renderer, itemWithLore(List.of("{math: %missing% * 2}"))))
                .containsExactly("");
    }

    @Test
    void aMathBlockWithEveryOperandPresentStillEvaluates() {
        placeholders.register("coins", c -> "50");

        assertThat(plainLore(renderer, itemWithLore(List.of("{math: %coins% + 1}"))))
                .containsExactly("51");
    }

    @Test
    void aRegisteredHandlerReturningNothingIsAMissingOperandToo() {
        // A registry that answers "" looks nothing like one that answers nothing from inside the expression, and
        // an empty string is not a number in any position, so this is the same hole as an unregistered token.
        placeholders.register("blank", c -> "");

        assertThat(plainLore(renderer, itemWithLore(List.of("{math: %blank% + 1}"))))
                .containsExactly("");
    }

    @Test
    void aHandlerThatThrowsCostsItsOwnTokenAndNotTheWindow() {
        // Handlers arrive through the developer API, so one of them throwing is somebody else's defect. It used
        // to escape the renderer and take the whole window with it, while the same handler failing inside
        // resolveAll cost only the token it would have filled.
        placeholders.register("angry", c -> {
            throw new IllegalStateException("no");
        });

        assertThat(plainLore(renderer, itemWithLore(List.of("before %angry% after"))))
                .containsExactly("before  after");
    }

    @Test
    void aMathBlockInsideALocalPlaceholderIsCheckedOnTheTemplateToo() {
        // substituteLocal resolves the inner tokens and leaves the block for the outer math pass, so a hole
        // arrives already anonymous there. The template is checked before that happens.
        MenuContext local = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0)
                .withLocalPlaceholders(Map.of("total", "{math: %missing% + 1}"));

        assertThat(plainLore(renderer, itemWithLore(List.of("%total%")), local)).containsExactly("");
    }

    @Test
    void multiLinePlaceholderExpandsIntoSeparateLoreLines() {
        // A per-entry icon (kit/warp browse) emits a variable number of lines through one placeholder; the engine
        // must turn each newline-separated segment into its own lore component, in order.
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("status", c -> "line one\nline two\nline three");
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);

        assertThat(plainLore(r, itemWithLore(List.of("%status%"))))
                .containsExactly("line one", "line two", "line three");
    }

    @Test
    void singleLineLiteralStaysOneLoreLine() {
        // Regression: a spec line with no embedded newline must still render as exactly one component.
        assertThat(plainLore(renderer, itemWithLore(List.of("just one line")))).containsExactly("just one line");
    }

    @Test
    void catalogLineIsNotSplitEvenWhenMultiLine() {
        // Catalog output owns its own layout, so an @key line stays a single lore component even if the resolved
        // text carries a newline: only inline/placeholder literals expand. Asserted on the renderer's own output,
        // since the downstream ItemBuilder breaks any newline-bearing component into visual lines.
        assertThat(plainLore(renderer, itemWithLore(List.of("@first\nsecond")))).containsExactly("first\nsecond");
    }

    @Test
    void mixedLoreExpandsOnlyTheMultiLinePlaceholder() {
        // A real browse icon mixes a catalog header, a multi-line placeholder body, and a plain footer: 1 + 2 + 1.
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("status", c -> "ok\ncost 10");
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);

        assertThat(plainLore(r, itemWithLore(List.of("@some_key", "%status%", "plain"))))
                .containsExactly("some_key", "ok", "cost 10", "plain");
    }

    @Test
    void buttonTextIsNameThenLoreOnePerLineFlattened() {
        // A Bedrock form button reads the icon's name and lore as one flat \n-separated string; formatting is dropped.
        String text = renderer.buttonText(itemNamedWithLore("Shop", List.of("Buy items", "<gray>cheap")), ctx);

        assertThat(text).isEqualTo("Shop\nBuy items\ncheap");
    }

    @Test
    void materialSpecExpandsAPlaceholderTokenToTheResolvedSpec() {
        // The form renderer reads this to source a button icon; a %token% material must expand exactly as it does for
        // the chest icon, so %icon% (registered to DIAMOND) resolves to the concrete spec the icon providers read.
        assertThat(renderer.materialSpec(item("%icon%", new ItemDecor(1, Optional.empty(), false, List.of())), ctx))
                .isEqualTo("DIAMOND");
    }

    @Test
    void materialSpecPassesASkullSpecThroughVerbatim() {
        // A literal skull spec has no %token%, so it reaches the icon path unchanged, the skull provider (and the
        // Bedrock icon sourcing) both read this exact string.
        assertThat(renderer.materialSpec(
                        item("skull:Notch", new ItemDecor(1, Optional.empty(), false, List.of())), ctx))
                .isEqualTo("skull:Notch");
    }

    @Test
    void materialSpecSubstitutesATokenInsideAPrefixedSpec() {
        // The prefix is part of the spec, not decoration around it: skull:%player% has to reach the skull provider as
        // skull:Notch. Expanding the token and discarding everything around it left a bare name no icon provider
        // claims, and the material fallback rendered that as stone without saying anything.
        placeholders.register("player", c -> "Notch");
        assertThat(renderer.materialSpec(
                        item("skull:%player%", new ItemDecor(1, Optional.empty(), false, List.of())), ctx))
                .isEqualTo("skull:Notch");
    }

    @Test
    void buttonTextWithNoLoreIsJustTheName() {
        // No lore lines means the button label is byte-identical to the bare name it always was.
        assertThat(renderer.buttonText(itemNamedWithLore("Shop", List.of()), ctx))
                .isEqualTo("Shop");
    }

    @Test
    void buttonTextKeepsBlankLoreLinesAsBlankLines() {
        // The operator's spacing carries over: a blank lore line stays a blank line in the flat label.
        assertThat(renderer.buttonText(itemNamedWithLore("Shop", List.of("top", "", "bottom")), ctx))
                .isEqualTo("Shop\ntop\n\nbottom");
    }

    @Test
    void aButtonHidesTheLinesTheClientWritesByItself() {
        // The reason this exists: an IRON_PICKAXE used as a menu button drew its mining speed and attack damage
        // under the lore that already said what the button does, in a colour the menu never chose.
        ItemStack it = renderer.render(item("IRON_PICKAXE", new ItemDecor(1, Optional.empty(), false, List.of())), ctx);

        assertThat(hidden(it)).contains(DataComponentTypes.TOOL, DataComponentTypes.ATTRIBUTE_MODIFIERS);
    }

    @Test
    void aLineTheDecorBlockAskedForIsKept() {
        // Authorship decides: the client added the mining speed, the operator added the enchantment, and only the
        // one nobody chose is noise. A shop tile selling a sharpness book must still show what it sells.
        ItemDecor decor = enchanted("sharpness:5");
        ItemStack it = renderer.render(item("DIAMOND_SWORD", decor), ctx);

        assertThat(hidden(it)).doesNotContain(DataComponentTypes.ENCHANTMENTS);
        assertThat(hidden(it)).contains(DataComponentTypes.WEAPON);
    }

    @Test
    void hideVanillaTooltipFalseGivesEveryLineBack() {
        ItemStack it = renderer.render(item("IRON_PICKAXE", tooltip(Optional.of(false), List.of())), ctx);

        assertThat(hidden(it)).isEmpty();
    }

    @Test
    void hiddenComponentsNamesAnExactSet() {
        // The tile that wants the usual silence with one line back names its own set instead of taking the default.
        ItemStack it = renderer.render(
                item("LEATHER_CHESTPLATE", tooltip(Optional.empty(), List.of("dyed_color", "not_a_component"))), ctx);

        // The unresolvable token is skipped rather than aborting the render, as every other decor token is.
        assertThat(hidden(it)).containsExactly(DataComponentTypes.DYED_COLOR);
    }

    @Test
    void hidingTheClientsLinesDoesNotHandBackAWholeTooltipTheSpecTookAway() {
        // Both settings live on the same component, so the order they are written in decides whether hide-tooltip
        // survives. It has to: a spec that asked for no tooltip at all means it.
        DataComponents whole = new DataComponents(
                Optional.empty(),
                Optional.empty(),
                Optional.of(true),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty());
        ItemStack it = renderer.render(item("STONE", decorWith(RichMeta.NONE.withComponents(whole))), ctx);

        assertThat(it.getItemMeta().isHideTooltip()).isTrue();
    }

    /**
     * The components the client may not draw a line for, as the rendered stack carries them.
     *
     * <p>Only read this from a stack the renderer returned. A tooltip display survives {@code new ItemStack(other)}
     * on a real server (the copy constructor keeps the delegate) but not under MockBukkit, whose clone does not
     * model data components, so an assertion taken across a copy here proves nothing either way.
     */
    private static Set<DataComponentType> hidden(ItemStack item) {
        TooltipDisplay display = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        return display == null ? Set.of() : display.hiddenComponents();
    }

    /** A decor block whose operator declared one enchantment, and nothing else. */
    private static ItemDecor decorWith(RichMeta meta) {
        return new ItemDecor(1, Optional.empty(), false, List.of(), meta);
    }

    private static ItemDecor enchanted(String token) {
        return decorWith(new RichMeta(
                false,
                List.of(token),
                List.of(),
                Optional.empty(),
                RichMeta.PotionSpec.NONE,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    }

    private static ItemDecor tooltip(Optional<Boolean> hideVanilla, List<String> hiddenComponents) {
        DataComponents components = new DataComponents(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                hideVanilla,
                hiddenComponents,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty());
        return decorWith(RichMeta.NONE.withComponents(components));
    }

    private static MenuItemSpec item(String material, ItemDecor decor) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                material,
                "",
                List.of(),
                decor,
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static MenuItemSpec itemNamedWithLore(String name, List<String> lore) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                name,
                lore,
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static MenuItemSpec itemWithLore(List<String> lore) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                "",
                lore,
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    // Assert on the renderer's own lore components, before ItemBuilder breaks any newline-bearing line into
    // visual lines on the stack; this is the layer the expansion feature owns.
    private List<String> plainLore(ItemRenderer r, MenuItemSpec spec) {
        return plainLore(r, spec, ctx);
    }

    private static List<String> plainLore(ItemRenderer r, MenuItemSpec spec, MenuContext c) {
        return r.lore(spec, c).stream()
                .map(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(line))
                .toList();
    }

    @Test
    void argumentTokenExpandsFromTheOpenContextArguments() {
        // A command opened with typed arguments carries them on the context; %argument_<name>% must render its value
        // in a name (and lore) without needing a registered placeholder.
        MenuContext argCtx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("amount", "5"));

        ItemStack it = renderer.render(itemNamed("You gave %argument_amount%"), argCtx);

        assertThat(plainName(it)).isEqualTo("You gave 5");
    }

    @Test
    void anUnknownArgumentTokenRendersEmpty() {
        MenuContext argCtx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("amount", "5"));

        ItemStack it = renderer.render(itemNamed("[%argument_missing%]"), argCtx);

        assertThat(plainName(it)).isEqualTo("[]");
    }

    @Test
    void anArgumentTokenInLoreExpandsWhileANormalPlaceholderStillResolves() {
        // Regression guard: adding the argument_ special-case must not shadow the registry: %icon% still resolves
        // (registered to "DIAMOND" in setUp), and %argument_target% resolves from the context arguments.
        MenuContext argCtx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("target", "Steve"));

        assertThat(plainLore(renderer, itemWithLore(List.of("to %argument_target%", "icon %icon%")), argCtx))
                .containsExactly("to Steve", "icon DIAMOND");
    }

    @Test
    void catalogKeyFillsTokensFromPlaceholders() {
        // A list template re-renders each entry with ctx.withEntry(...); the catalog key carries {sound}, which
        // the engine must fill from the registered placeholder so the option shows that entry's name, not "{sound}".
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("sound", c -> c.entry(String.class));
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);
        MenuContext entryCtx =
                MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0).withEntry("Enderman Teleport");

        ItemStack it = r.render(itemNamed("@Sound: {sound}"), entryCtx);

        assertThat(plainName(it)).isEqualTo("Sound: Enderman Teleport");
    }

    @Test
    void aLocalPlaceholderOverridesABuiltin() {
        // The menu defines its own %player%, which must win over the registered built-in for this menu alone.
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("player", c -> "REAL");
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);
        MenuContext localCtx = ctx.withLocalPlaceholders(Map.of("player", "OVERRIDDEN"));

        assertThat(plainName(r.render(itemNamed("%player%"), localCtx))).isEqualTo("OVERRIDDEN");
    }

    @Test
    void aNestedLocalTokenResolvesLocalFirst() {
        // a -> %b% -> "X": the inner %b% must resolve against the local block, not the (empty) registry.
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), new PlaceholderRegistry());
        MenuContext localCtx = ctx.withLocalPlaceholders(Map.of("a", "%b%", "b", "X"));

        assertThat(plainName(r.render(itemNamed("%a%"), localCtx))).isEqualTo("X");
    }

    @Test
    void aLocalCycleTerminatesBoundedInsteadOfOverflowing() {
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), new PlaceholderRegistry());
        MenuContext localCtx = ctx.withLocalPlaceholders(Map.of("a", "%b%", "b", "%a%"));

        assertThatCode(() -> r.render(itemNamed("%a%"), localCtx)).doesNotThrowAnyException();
        assertThat(plainName(r.render(itemNamed("%a%"), localCtx)))
                .as("a cycle stops at the depth cap and leaves an unresolved token, never a StackOverflow")
                .contains("%");
    }

    @Test
    void aLocalMathTemplateIsEvaluatedByTheOuterRenderPass() {
        // The local substitute resolves the inner %coins% but leaves {math: …} for the renderer's outer math pass.
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("coins", c -> "5");
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);
        MenuContext localCtx = ctx.withLocalPlaceholders(Map.of("doubled", "{math: %coins% * 2}"));

        assertThat(plainName(r.render(itemNamed("%doubled%"), localCtx))).isEqualTo("10");
    }

    private static MenuItemSpec itemNamed(String name) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                name,
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static String plainName(ItemStack item) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            // Mirror the real catalog: the key's text carries {token} arguments the placeholders map fills.
            String text = key.key();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return text;
        }
    }
}

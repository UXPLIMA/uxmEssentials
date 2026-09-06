package com.uxplima.uxmessentials.ranks.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.ranks.adapter.inbound.command.RanksCommand;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /ranks} ladder panel, driven through the menu engine end to end with MockBukkit. The panel draws the
 * current-standing display (DIAMOND@11), the next-rank display (EXPERIENCE_BOTTLE@13), the rank-up button
 * (EMERALD@15) and close (BARRIER@22) over a grey-glass backdrop; the two display items fill their lore from the
 * {@code ranks_*} placeholders the subject carries, so the current rank name and the next rank's cost and
 * requirements actually render. Clicking the rank-up button fires through the engine's own {@link MenuListener}
 * and runs the real {@link Rankup} pipeline, the pointer advances one rung, then redraws with the new standing;
 * clicking close shuts the panel.
 */
class RanksPanelGoldenTest {

    private static final int CURRENT_SLOT = 11;
    private static final int NEXT_SLOT = 13;
    private static final int RANKUP_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    private static final RankId CITIZEN = RankId.of("citizen");
    private static final RankId VIP = RankId.of("vip");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRankRepository repository;
    private RankLadder ladder;
    private Rankup rankup;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        guiText = new GuiText(new TemplateMessages());
        scheduler = new SyncScheduler();
        repository = new FakeRankRepository();
        ladder = RankLadder.of(List.of(
                new Rank(CITIZEN, 10, "Citizen", 0, List.of(), List.of()),
                new Rank(VIP, 20, "VIP", 5000, List.of("money 5000"), List.of())));
        CurrentRank currentRank = new CurrentRank(repository, ladder);
        RankRequirementEvaluator requirements = (who, requirement) -> true;
        RankActionRunner actions = (who, lines) -> {};
        rankup = new Rankup(
                currentRank, repository, ladder, requirements, actions, Optional.<RankEconomy>empty(), event -> {});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void panelRendersTheCurrentRankAndTheNextRankCostAndRequirements() {
        openEngine();

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(materialAt(inv, CURRENT_SLOT)).isEqualTo(Material.DIAMOND);
        assertThat(materialAt(inv, NEXT_SLOT)).isEqualTo(Material.EXPERIENCE_BOTTLE);
        assertThat(materialAt(inv, RANKUP_SLOT)).isEqualTo(Material.EMERALD);
        assertThat(materialAt(inv, CLOSE_SLOT)).isEqualTo(Material.BARRIER);
        // A fresh player has no stored pointer, so they stand on the ladder's first rank (Citizen); the next rung is
        // VIP, costing 5000 and requiring "money 5000". Those values reach the lore only through the ranks_* bindings.
        assertThat(loreAt(inv, CURRENT_SLOT)).contains("Citizen");
        assertThat(loreAt(inv, NEXT_SLOT)).contains("VIP").contains("5000").contains("money 5000");
    }

    @Test
    void clickingRankUpRunsTheRankupPipelineAndRedrawsWithTheNewStanding() {
        openEngine();

        fireClick(RANKUP_SLOT);

        // The rank-up button advanced the pointer one rung through the real Rankup use case.
        assertThat(repository.find(player.getUniqueId()))
                .map(PlayerRank::rankId)
                .contains(VIP);
        // The panel reopened with the new standing: the current display now names VIP.
        Inventory reopened = player.getOpenInventory().getTopInventory();
        assertThat(loreAt(reopened, CURRENT_SLOT)).contains("VIP");
    }

    @Test
    void clickingCloseShutsThePanel() {
        openEngine();

        fireClick(CLOSE_SLOT);

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
    }

    @Test
    void theBareRanksOpenIsNotWiredWhenTheGuiIsDisabled() {
        // A disabled GUI hands RanksCommand no panel, so the /ranks root carries no bare-open executor: it stays
        // the admin setrank-only surface, the operator-visible proof that a disabled GUI registers no open.
        RanksCommand command = new RanksCommand(
                new SetRank(repository, ladder, event -> {}), ladder, Optional.empty(), new TemplateMessages());

        assertThat(command.build().getCommand()).isNull();
    }

    @Test
    void theBareRanksOpenIsWiredWhenTheGuiIsEnabled() {
        RanksCommand command = new RanksCommand(
                new SetRank(repository, ladder, event -> {}), ladder, Optional.of(newPanel()), new TemplateMessages());

        assertThat(command.build().getCommand()).isNotNull();
    }

    /** Build the engine, register the ranks panel binding + spec, and open the panel for the player. */
    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        MenuVocabulary.registerConditions(bindings, mock(Permissions.class), mock(Logger.class));
        MenuVocabulary.registerPlaceholders(bindings);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        RanksPanelMenu panel =
                new RanksPanelMenu(menus, rankup, currentRank(), ladder, scheduler, new TemplateMessages());
        panel.register(bindings, specDir(), NOOP);
        panel.open(player, new PlayerRef(player.getUniqueId(), player.getName()));
    }

    private CurrentRank currentRank() {
        return new CurrentRank(repository, ladder);
    }

    /** A ranks panel over a throwaway engine, enough for the command to hold as its {@link Optional} GUI handle. */
    private RanksPanelMenu newPanel() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        return new RanksPanelMenu(menus, rankup, currentRank(), ladder, scheduler, new TemplateMessages());
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static Material materialAt(Inventory inv, int slot) {
        return Objects.requireNonNull(inv.getItem(slot), "no item at slot " + slot)
                .getType();
    }

    private static String loreAt(Inventory inv, int slot) {
        ItemStack item = Objects.requireNonNull(inv.getItem(slot), "no item at slot " + slot);
        List<Component> lore =
                Objects.requireNonNull(item.getItemMeta(), "no meta").lore();
        if (lore == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Component line : lore) {
            out.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n');
        }
        return out.toString();
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped ranks-panel spec. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    /** The in-memory rank pointer store the test's Rankup pipeline advances. */
    private static final class FakeRankRepository implements PlayerRankRepository {
        private final Map<UUID, PlayerRank> pointers = new HashMap<>();

        @Override
        public Optional<PlayerRank> find(UUID playerId) {
            return Optional.ofNullable(pointers.get(playerId));
        }

        @Override
        public void save(UUID playerId, RankId rankId, Prestige prestige) {
            pointers.put(playerId, new PlayerRank(rankId, prestige));
        }
    }

    /**
     * A {@link Messages} double that fills the lore templates' {@code {token}} arguments from the resolved
     * placeholder map, so the ranks_* bindings actually reach the rendered lore; every other key resolves to its
     * own key verbatim, which is enough for the item names and the (unasserted) rank-up feedback lines.
     */
    private static final class TemplateMessages implements Messages {
        private static final Map<String, String> TEMPLATES = Map.of(
                "ranks.gui-current-lore", "Rank {ranks_current} Prestige {ranks_prestige}",
                "ranks.gui-next-lore", "Next {ranks_next} Cost {ranks_next_cost} Requires {ranks_next_requirements}");

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String text = TEMPLATES.getOrDefault(key.key(), key.key());
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return text;
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef entity, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}

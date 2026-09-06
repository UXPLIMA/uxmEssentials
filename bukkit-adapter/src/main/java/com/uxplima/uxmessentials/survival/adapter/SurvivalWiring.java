package com.uxplima.uxmessentials.survival.adapter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.SurvivalPlaceholders;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.AutoPickupCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.AutoSellCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.AutoSmeltCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.AutoToolCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.FarmProtectCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.SurvivalCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.TreeFellerCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.VeinminerCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.gui.SurvivalSettingsView;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.AnvilUnlockListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.AutoDropsListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.AutoDropsPipeline;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.AutoToolListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.FarmAssistListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.FarmProtectListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.FastLeafDecayListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.HeadDropListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.OnePlayerSleepListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.TreeFellerListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.VeinminerListener;
import com.uxplima.uxmessentials.survival.adapter.outbound.AutoSellNotices;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.adapter.outbound.ThreadLocalRandomSource;
import com.uxplima.uxmessentials.survival.adapter.outbound.TogglesSurvivalPlaceholders;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.SaleNotice;
import com.uxplima.uxmessentials.survival.application.SurvivalMessageKey;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import com.uxplima.uxmessentials.survival.domain.AutoToolSelector;
import com.uxplima.uxmessentials.survival.domain.Crops;
import com.uxplima.uxmessentials.survival.domain.DropChance;
import com.uxplima.uxmessentials.survival.domain.SellPrices;
import com.uxplima.uxmessentials.survival.domain.SleepThreshold;
import com.uxplima.uxmessentials.survival.domain.SmeltMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the survival context's adapters over the injected kernel ports and produces the mechanic commands and
 * listeners the plugin registers: the harvesting core (tree-feller, veinminer), fast-leaf-decay, farmland protection
 * ({@code /farmprotect}), farm-assist, anvil-unlocker, one-player-sleep, and head-drop. Each mechanic wires only when
 * its config gate is on: with
 * {@code tree-feller.enabled = false} no {@code /treefeller} command and no tree-feller listener land, and likewise
 * for every other mechanic, so a disabled mechanic contributes nothing, the same "disabled means absent" property
 * the module gate gives at the context level.
 *
 * <p>The context persists nothing. The per-player toggle is a transient PDC stamp held in
 * {@link PdcSurvivalToggles}, so there is no repository, migration, or teardown state; the {@link Wired} record is
 * just the commands and listeners to publish.
 */
@NullMarked
public final class SurvivalWiring {

    /** The node that gates the settings panel, shared by {@code /survival} and the hub entry. */
    private static final String SURVIVAL_GUI_PERMISSION = "uxmessentials.survival.gui";

    private SurvivalWiring() {}

    /**
     * Build the survival commands and listeners from {@code ctx}, ready to register.
     *
     * @param ctx the module context carrying the scoped config and kernel ports
     * @param sales the economy seam auto-sell credits through, empty when no economy provider is wired (auto-sell is
     *     then inert)
     * @param guiLayouts the operator-editable GUI layout loader the {@code /survival} panel reads its geometry from
     * @param guiRegistry the {@code /uxmess gui} hub the settings panel registers its entry on
     * @param menus the menu engine the {@code /survival} panel opens through
     * @param server the server the panel resolves the viewer's live {@code Player} from to read their PDC toggles
     */
    public static Wired wire(
            ModuleContext ctx,
            Optional<SurvivalSales> sales,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            Server server) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(sales, "sales");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(server, "server");
        KernelPorts kernel = ctx.kernel();
        SurvivalConfig config = SurvivalConfig.from(ctx.config());
        PdcSurvivalToggles toggles = new PdcSurvivalToggles();
        // The break-drop pipeline (auto-pickup / smelt / sell) is built once when at least one of its stages is on, so
        // the AutoDropsListener and the tree-feller / veinminer cascades all route through the same transform; it is
        // null when every stage is off, and then the cascades drop naturally on the ground.
        @Nullable AutoDropsPipeline autoDrops =
                autoDropsPipeline(config, sales, saleNotices(config, sales, kernel, server), toggles);

        List<CommandRegistration> commands = new ArrayList<>();
        List<Listener> listeners = new ArrayList<>();
        // The panel /survival opens is the same one the /uxmess gui hub entry opens, so both surfaces show one
        // player the same live toggles.
        SurvivalSettingsView settingsView = new SurvivalSettingsView(
                new GuiText(kernel.messages()),
                kernel.scheduler(),
                guiLayouts,
                kernel.messages(),
                kernel.permissions(),
                menus,
                server,
                toggles,
                config);
        guiRegistry.register(new ManagementGuiEntry(
                "survival",
                SurvivalMessageKey.SURVIVAL_GUI_TITLE,
                Material.IRON_PICKAXE,
                SURVIVAL_GUI_PERMISSION,
                settingsView::open));
        commands.add(new SurvivalCommand(settingsView, kernel.messages()));
        if (config.treeFeller().enabled()) {
            commands.add(new TreeFellerCommand(toggles, kernel.messages()));
            listeners.add(new TreeFellerListener(config.treeFeller(), toggles, kernel.scheduler(), autoDrops));
        }
        if (config.veinminer().enabled()) {
            commands.add(new VeinminerCommand(toggles, kernel.messages()));
            Set<Material> triggers = triggerMaterials(config.veinminer(), kernel.log());
            listeners.add(new VeinminerListener(config.veinminer(), triggers, toggles, autoDrops));
        }
        if (config.leafDecay().enabled()) {
            listeners.add(new FastLeafDecayListener(config.leafDecay(), kernel.scheduler()));
        }
        if (config.farmProtect().enabled()) {
            commands.add(new FarmProtectCommand(toggles, kernel.messages()));
            listeners.add(new FarmProtectListener(toggles));
        }
        if (config.farmAssist().enabled()) {
            Map<Material, Material> cropToSeed = cropSeedMap(config.farmAssist(), kernel.log());
            listeners.add(new FarmAssistListener(cropToSeed));
        }
        if (config.anvilUnlocker().enabled()) {
            listeners.add(new AnvilUnlockListener(config.anvilUnlocker()));
        }
        if (config.onePlayerSleep().enabled()) {
            SleepThreshold threshold = new SleepThreshold(
                    config.onePlayerSleep().requiredCount(),
                    config.onePlayerSleep().requiredPercent());
            listeners.add(new OnePlayerSleepListener(threshold, kernel.scheduler()));
        }
        if (config.headDrop().enabled()) {
            listeners.add(new HeadDropListener(
                    config.headDrop().playerHeadOnPvp(),
                    new DropChance(config.headDrop().mobChance()),
                    mobChances(config.headDrop(), kernel.log()),
                    new ThreadLocalRandomSource()));
        }
        wireAutos(config, toggles, autoDrops, kernel, commands, listeners);
        return new Wired(commands, listeners, new TogglesSurvivalPlaceholders(server, toggles, config));
    }

    /**
     * The composed break-drop pipeline (auto-pickup → smelt → sell), built once when at least one of the three stages
     * is enabled and shared by the {@link AutoDropsListener} and the harvesting cascades so a felled trunk or a mined
     * vein is routed exactly like the block broken by hand. Returns {@code null} when all three are off: the cascades
     * then fall back to a plain ground-dropping break.
     */
    private static @Nullable AutoDropsPipeline autoDropsPipeline(
            SurvivalConfig config,
            Optional<SurvivalSales> sales,
            Optional<AutoSellNotices> notices,
            PdcSurvivalToggles toggles) {
        if (!config.autoPickup().enabled()
                && !config.autoSmelt().enabled()
                && !config.autoSell().enabled()) {
            return null;
        }
        return new AutoDropsPipeline(
                config.autoPickup().enabled(),
                config.autoSmelt().enabled(),
                new SmeltMap(config.autoSmelt().smelt()),
                config.autoSell().enabled(),
                new SellPrices(config.autoSell().prices()),
                sales,
                notices,
                toggles);
    }

    /**
     * The auto-sell receipt, present only when there is something to report: auto-sell enabled, an economy wired to
     * pay for the drops, and a notice surface configured ({@code autosell.notify.mode} other than {@code off}). It is
     * the economy seam itself that renders the money figure, so the receipt reads like every other balance line.
     */
    private static Optional<AutoSellNotices> saleNotices(
            SurvivalConfig config, Optional<SurvivalSales> sales, KernelPorts kernel, Server server) {
        if (!config.autoSell().enabled() || config.autoSell().notice() == SaleNotice.OFF) {
            return Optional.empty();
        }
        return sales.map(seam -> new AutoSellNotices(
                server,
                kernel.scheduler(),
                kernel.messages(),
                seam,
                config.autoSell().notice(),
                config.autoSell().noticeIntervalSeconds()));
    }

    /**
     * Wire the four auto-* mechanics: the {@code /autopickup}, {@code /autosmelt}, {@code /autosell}, {@code /autotool}
     * toggle commands (each present only when its mechanic is enabled) plus the two listeners. The break-drop trio
     * (pickup, smelt, sell) share a single {@link AutoDropsListener} over the already-built {@code autoDrops} pipeline;
     * it registers exactly when that pipeline exists (at least one stage on). Auto-tool is an independent listener on a
     * different event.
     */
    private static void wireAutos(
            SurvivalConfig config,
            PdcSurvivalToggles toggles,
            @Nullable AutoDropsPipeline autoDrops,
            KernelPorts kernel,
            List<CommandRegistration> commands,
            List<Listener> listeners) {
        if (config.autoPickup().enabled()) {
            commands.add(new AutoPickupCommand(toggles, kernel.messages()));
        }
        if (config.autoSmelt().enabled()) {
            commands.add(new AutoSmeltCommand(toggles, kernel.messages()));
        }
        if (config.autoSell().enabled()) {
            commands.add(new AutoSellCommand(toggles, kernel.messages()));
        }
        if (autoDrops != null) {
            listeners.add(new AutoDropsListener(
                    autoDrops, kernel.scheduler(), config.autoPickup().transferXp()));
        }
        if (config.autoTool().enabled()) {
            commands.add(new AutoToolCommand(toggles, kernel.messages()));
            listeners.add(new AutoToolListener(new AutoToolSelector(), toggles));
        }
    }

    /** Resolve each configured per-mob head chance to its {@link EntityType}, warning on and skipping any unknown name. */
    private static Map<EntityType, DropChance> mobChances(SurvivalConfig.HeadDrop config, Logger log) {
        Map<EntityType, DropChance> chances = new EnumMap<>(EntityType.class);
        for (Map.Entry<String, Double> entry : config.mobs().entrySet()) {
            EntityType type = matchEntityType(entry.getKey());
            if (type == null) {
                log.warn("survival headdrop: unknown mob type '{}', skipping", entry.getKey());
            } else {
                chances.put(type, new DropChance(entry.getValue()));
            }
        }
        return chances;
    }

    /** The {@link EntityType} named {@code name} (case-insensitively), or {@code null} when none matches. */
    private static @Nullable EntityType matchEntityType(String name) {
        for (EntityType type : EntityType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    /** Resolve each configured farm-assist crop to its crop→seed material pair, skipping any that will not resolve. */
    private static Map<Material, Material> cropSeedMap(SurvivalConfig.FarmAssist config, Logger log) {
        Map<Material, Material> cropToSeed = new EnumMap<>(Material.class);
        for (String name : config.crops()) {
            Material crop = Material.matchMaterial(name);
            Optional<String> seedName = Crops.seedFor(name);
            if (crop == null) {
                log.warn("survival farmassist: unknown crop material '{}', skipping", name);
            } else if (seedName.isEmpty()) {
                log.warn("survival farmassist: no seed known for crop '{}', skipping", name);
            } else {
                Material seed = Material.matchMaterial(seedName.get());
                if (seed == null) {
                    log.warn(
                            "survival farmassist: unknown seed material '{}' for crop '{}', skipping",
                            seedName.get(),
                            name);
                } else {
                    cropToSeed.put(crop, seed);
                }
            }
        }
        return cropToSeed;
    }

    /** Resolve the configured veinminer trigger names to materials, warning on and skipping any unknown id. */
    private static Set<Material> triggerMaterials(SurvivalConfig.Veinminer config, Logger log) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : config.blocks()) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                log.warn("survival veinminer: unknown block material '{}', skipping", name);
            } else {
                materials.add(material);
            }
        }
        return materials;
    }

    /**
     * Everything the survival module contributes once wired: the mechanic toggle commands and the break listeners,
     * each present only when its mechanic is enabled. The context holds no runtime state, so there is nothing to
     * drain on stop.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param placeholders the read seam the {@code survival_*} placeholders answer from
     */
    public record Wired(
            List<CommandRegistration> commands, List<Listener> listeners, SurvivalPlaceholders placeholders) {
        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(placeholders, "placeholders");
        }
    }
}

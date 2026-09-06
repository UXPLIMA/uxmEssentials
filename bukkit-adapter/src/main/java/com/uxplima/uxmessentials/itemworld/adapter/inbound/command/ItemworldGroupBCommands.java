package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.EntityCountMenu;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PowertoolToggleStore;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.UnlimitedPlacementStore;
import com.uxplima.uxmessentials.itemworld.application.PowertoolPolicy;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds the inbound Brigadier surface for itemworld group B, powertool, mob/entity, time/weather (with the
 * {@code /sun /rain /thunder /day /night} aliases), and admin-fun (docs/10-feature-modules.md §15.10), as
 * {@link CommandRegistration}s over the constructed {@link ItemworldServices} and the group's policies/stores.
 * Collected in one greppable table that mirrors {@link ItemworldGroupACommands}, so the literal/permission
 * pairing matches the permissions reference and the kernel's {@code ItemworldCommandSurface}; the plugin's
 * {@code LifecycleEvents.COMMANDS} handler registers each on the next fire.
 *
 * <p>Every command here gates on its sub-feature group plus its per-command disable through
 * {@link ItemworldCommandSupport#enabled} before any mutation, validates its inputs at the boundary, and (for
 * the abusable mob/entity-purge family and the admin-fun verbs) audit-logs through
 * {@link ItemworldServices#audit()}. The stateful corners. Powertool bindings (item PDC) and the powertool /
 * unlimited per-player toggles. Are transient runtime state the wiring owns and the module's {@code stop()}
 * drops with the wiring; itemworld keeps no persistence.
 */
@NullMarked
public final class ItemworldGroupBCommands {

    private ItemworldGroupBCommands() {}

    /** Every itemworld group-B command, in surface order grouped by sub-feature group. */
    public static List<CommandRegistration> all(
            ItemworldServices services,
            PowertoolPolicy powertoolPolicy,
            PurgePolicy purgePolicy,
            PdcPowertoolStore powertoolStore,
            PowertoolToggleStore powertoolToggles,
            UnlimitedPlacementStore unlimited,
            @Nullable EntityCountMenu entityCountView) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(powertoolPolicy, "powertoolPolicy");
        Objects.requireNonNull(purgePolicy, "purgePolicy");
        Objects.requireNonNull(powertoolStore, "powertoolStore");
        Objects.requireNonNull(powertoolToggles, "powertoolToggles");
        Objects.requireNonNull(unlimited, "unlimited");

        List<CommandRegistration> commands = new ArrayList<>();
        addPowertool(commands, services, powertoolPolicy, powertoolStore, powertoolToggles);
        addMobEntity(commands, services, purgePolicy, unlimited, entityCountView);
        addTimeWeather(commands, services);
        addAdminFun(commands, services);
        return List.copyOf(commands);
    }

    private static void addPowertool(
            List<CommandRegistration> commands,
            ItemworldServices services,
            PowertoolPolicy policy,
            PdcPowertoolStore store,
            PowertoolToggleStore toggles) {
        commands.add(new PowertoolCommand(services, policy, store));
        commands.add(new PowertoolToggleCommand(services, toggles));
        commands.add(new PowertoolListCommand(services, store));
    }

    private static void addMobEntity(
            List<CommandRegistration> commands,
            ItemworldServices services,
            PurgePolicy purgePolicy,
            UnlimitedPlacementStore unlimited,
            @Nullable EntityCountMenu entityCountView) {
        commands.add(new SpawnMobCommand(services));
        commands.add(new SpawnerCommand(services));
        commands.add(new KillCommand(services));
        commands.add(new ButcherCommand(services, purgePolicy));
        commands.add(new KillAllCommand(services, purgePolicy));
        commands.add(new RemoveCommand(services, purgePolicy));
        commands.add(new EntityCountCommand(services, entityCountView));
        commands.add(new UnlimitedCommand(services, unlimited));
    }

    private static void addTimeWeather(List<CommandRegistration> commands, ItemworldServices services) {
        commands.add(new TimeCommand(services));
        commands.add(new WeatherCommand(services));
        commands.add(TimeAliasCommand.day(services));
        commands.add(TimeAliasCommand.night(services));
        commands.add(WeatherAliasCommand.sun(services));
        commands.add(WeatherAliasCommand.rain(services));
        commands.add(WeatherAliasCommand.thunder(services));
    }

    private static void addAdminFun(List<CommandRegistration> commands, ItemworldServices services) {
        commands.add(new LightningCommand(services));
        commands.add(new FireballCommand(services));
        commands.add(new KittycannonCommand(services));
        commands.add(new AntiochCommand(services));
        commands.add(new BeezookaCommand(services));
        commands.add(new BreakCommand(services));
        commands.add(new TreeCommand(services));
        commands.add(new BigTreeCommand(services));
        commands.add(new NukeCommand(services));
    }
}

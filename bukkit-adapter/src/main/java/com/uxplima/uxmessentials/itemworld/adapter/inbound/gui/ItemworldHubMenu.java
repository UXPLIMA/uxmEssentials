package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.Workstation;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitEntityPurger;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitWeatherApplier;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.itemworld.domain.TimeSpec;
import com.uxplima.uxmessentials.itemworld.domain.WeatherSpec;
import com.uxplima.uxmessentials.itemworld.domain.event.EntitiesPurged;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the itemworld utilities hub ({@code /itemworld gui}, and the itemworld entry on the {@code /uxmess gui}
 * hub) with the menu engine and opens it. The hub is a launcher panel: eight buttons open a virtual workstation,
 * two set the world time, two set the weather, and two run a cleanup sweep. Each running the same surface its
 * command does, and each drawn only when the viewer holds that command's permission. The menu carries no new
 * domain logic; it registers the per-button feature actions and the visibility conditions, then loads the
 * {@code itemworld-hub} spec and hands it to {@link Menus}.
 *
 * <p>Visibility is gated by the engine's {@code perm} condition on each button's {@code view}, so a button the
 * viewer may not use is simply not drawn (the glass backdrop shows through), exactly as the old launcher hid it.
 * The click actions replicate the original handlers: opening a station opens its {@link Workstation} view and
 * replies, the time/weather buttons hop to the global region and apply through the same code the {@code /day} /
 * {@code /weather} commands use, and the two sweeps thread under Folia exactly as {@code /killall item} and
 * {@code /butcher} do (a radius purge on the actor's region, a world purge across every region) then report,
 * audit, and publish the same {@link EntitiesPurged} event. The menu opens with no subject (it is viewer-scoped).
 */
@NullMarked
public final class ItemworldHubMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "itemworld-hub";

    private static final String SPEC_RESOURCE = "modules/itemworld/gui/itemworld-hub.conf";

    private static final String DROPS_TYPE = "item";
    private static final int BUTCHER_RADIUS = 64;

    private final Menus menus;
    private final Messages messages;
    private final Scheduler scheduler;
    private final ItemworldServices services;
    private final PurgePolicy purgePolicy;

    public ItemworldHubMenu(
            Menus menus, Messages messages, Scheduler scheduler, ItemworldServices services, PurgePolicy purgePolicy) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.services = Objects.requireNonNull(services, "services");
        this.purgePolicy = Objects.requireNonNull(purgePolicy, "purgePolicy");
    }

    /** Register the per-button label placeholders and the feature actions the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        for (Workstation station : Workstation.values()) {
            bindings.placeholder("iw_station_" + station.literal(), ctx -> stationLabel(ctx, station));
            bindings.action("itemworld:open-" + station.literal(), ctx -> openStation(ctx, station));
        }
        bindings.action("itemworld:time-day", ctx -> setTime(ctx, TimeSpec.day()));
        bindings.action("itemworld:time-night", ctx -> setTime(ctx, TimeSpec.night()));
        bindings.action("itemworld:weather-clear", ctx -> setWeather(ctx, WeatherSpec.of(WeatherSpec.Kind.CLEAR)));
        bindings.action("itemworld:weather-rain", ctx -> setWeather(ctx, WeatherSpec.of(WeatherSpec.Kind.RAIN)));
        bindings.action("itemworld:clear-drops", ctx -> sweep(ctx, purgePolicy.killAll(DROPS_TYPE)));
        bindings.action("itemworld:clear-mobs", ctx -> sweep(ctx, purgePolicy.butcher(BUTCHER_RADIUS)));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /** Open the launcher for {@code viewer} (the live player resolved by the engine). */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, SPEC_ID, null);
    }

    /** The fully-resolved button label for {@code station}: the workstation name filled into the hub catalog key. */
    private String stationLabel(MenuContext ctx, Workstation station) {
        return messages.resolve(
                ctx.viewer(), ItemworldMessageKey.GUI_HUB_WORKSTATION, Map.of("station", station.displayName()));
    }

    /** Open the clicked station for the viewer and reply, mirroring the old launcher's workstation button. */
    private void openStation(MenuActionContext ctx, Workstation station) {
        station.open(ctx.player());
        reply(ctx.viewer(), ItemworldMessageKey.WORKSTATION_OPENED, Map.of("station", station.displayName()));
    }

    /** Set the world time on the global region, then reply, exactly as the old time button did. */
    private void setTime(MenuActionContext ctx, TimeSpec spec) {
        PlayerRef viewer = ctx.viewer();
        World world = ctx.player().getWorld();
        scheduler.onGlobal(() -> {
            long resolved = spec.mode() == TimeSpec.Mode.SET ? spec.ticks() : world.getTime() + spec.ticks();
            world.setTime(resolved);
            reply(
                    viewer,
                    ItemworldMessageKey.TIME_SET,
                    Map.of("world", world.getName(), "ticks", String.valueOf(world.getTime())));
        });
    }

    /** Apply the weather on the global region, then reply, exactly as the old weather button did. */
    private void setWeather(MenuActionContext ctx, WeatherSpec spec) {
        PlayerRef viewer = ctx.viewer();
        World world = ctx.player().getWorld();
        scheduler.onGlobal(() -> {
            BukkitWeatherApplier.apply(world, spec);
            reply(
                    viewer,
                    ItemworldMessageKey.WEATHER_SET,
                    Map.of(
                            "world",
                            world.getName(),
                            "weather",
                            spec.kind().name().toLowerCase(Locale.ROOT)));
        });
    }

    /**
     * Run a cleanup sweep, threading under Folia exactly as the commands do: the clear-mobs radius purge is owned
     * by the actor's own region (a synchronous read-and-remove on the entity thread), while the clear-drops world
     * purge spans every region, snapshotting on the global thread and removing each drop on the region that owns it.
     */
    private void sweep(MenuActionContext ctx, PurgeSelection selection) {
        Player player = ctx.player();
        PlayerRef actor = ctx.viewer();
        WorldRef world = BukkitRefs.toRef(player.getWorld());
        if (selection.scope() == PurgeSelection.Scope.RADIUS) {
            scheduler.onEntity(
                    actor, () -> finish(actor, world, selection, BukkitEntityPurger.purge(player, selection)));
        } else {
            BukkitEntityPurger.purgeWorld(
                    scheduler, player, selection, removed -> finish(actor, world, selection, removed));
        }
    }

    private void finish(PlayerRef actor, WorldRef world, PurgeSelection selection, int removed) {
        MessageKey key = selection.category() == PurgeSelection.Category.MONSTERS
                ? ItemworldMessageKey.BUTCHER_DONE
                : ItemworldMessageKey.KILLALL_DONE;
        reply(
                actor,
                key,
                Map.of(
                        "count", String.valueOf(removed),
                        "type", selection.typeId().orElse("all"),
                        "radius", String.valueOf(selection.radius())));
        if (selection.category() == PurgeSelection.Category.MONSTERS) {
            services.audit().butchered(actor, selection, removed);
        } else {
            services.audit().killedAll(actor, selection, removed);
        }
        services.kernel().events().publish(new EntitiesPurged(actor, selection, world, removed, Instant.now()));
    }

    private void reply(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        services.kernel()
                .messageSink()
                .deliver(viewer, services.kernel().messages().resolve(viewer, key, placeholders));
    }
}

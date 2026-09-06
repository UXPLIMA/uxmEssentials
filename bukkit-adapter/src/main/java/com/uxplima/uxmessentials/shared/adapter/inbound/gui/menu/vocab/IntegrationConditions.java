package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.PermissionQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardReflection;
import com.uxplima.uxmessentials.shared.application.port.ClientProtocol;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The integration slice of the menu condition vocabulary: gates that ask a neighbouring plugin, a permission group,
 * a currency balance, a job, a protected region, or the viewer's own world weather and a persistent per-label
 * cooldown. Registered once at startup into the shared {@link MenuBindings} alongside the generic
 * {@link MenuVocabulary} conditions and the {@link RequirementConditions} / {@link StringConditions} /
 * {@link NumericSpatialConditions} packs, so a disk-loaded spec resolves {@code has-group} / {@code weather} /
 * {@code cooldown} the same way a code-registered feature menu does. It also registers one paired action,
 * {@code set-cooldown}, so a menu can start the very cooldown a {@code cooldown:} view-condition then gates.
 *
 * <p>A valued condition is written {@code has-group:vip} or {@code weather:clear} in config; the parser leaves that
 * whole (it is registry-blind), and the runtime's registry-aware split re-keys it to the registered head
 * ({@code has-group}, {@code weather}) with the remainder carried as {@code value}. So every handler here reads its
 * argument from {@code value}, exactly as an action does.
 *
 * <p>Several lean on existing substrate: {@code has-group} rides the Vault {@link PermissionQuery} seam (which
 * resolves LuckPerms and friends through Vault. Absent → false), and {@code has-points} is a convenience alias over
 * the existing PlayerPoints {@link Currencies} back-end. {@code weather} reads the viewer's own world,
 * {@code cooldown}/{@code set-cooldown} ride the shared {@link Cooldowns} PDC-backed port, and
 * {@code client-version} reads the {@link ClientProtocol} port ViaVersion backs. The rest ({@code job} for
 * JobsReborn, {@code worldguard-region} for WorldGuard, {@code mcmmo-level} and {@code mcmmo-power} for mcMMO) are
 * soft-depends reached purely by reflection behind a plugin-present guard, exactly as the reflective currency and
 * item providers are: they name the SDK only by string class-name, so a server without the plugin loads none of its
 * classes, and any reflective or unchecked failure is warned once then degraded to {@code false}.
 *
 * <p><strong>Every condition fails closed.</strong> A gate that cannot be evaluated must not pass: an offline viewer
 * (where a live world is needed), a blank or unknown argument, an absent integration, or anything a lookup
 * unexpectedly throws all answer {@code false} through the {@link #closed} wrapper, better to hide an item or deny a
 * click than to grant on a value we could not read. The paired {@code set-cooldown} action is fail-soft the same way
 * through {@link #safe}: a stamp that throws is a logged no-op, never an aborted click chain.
 *
 * <p>Threading: these run on the render/entity thread. {@code view} during the renderer's populate, a click gate on
 * the viewer's entity thread. {@code weather} reads only the viewer's <em>own</em> world (Folia-safe, no foreign
 * entity, no roster enumeration), and the reflective gates read only the viewer's own job membership and standing
 * location. Nothing here produces player-facing text (a condition returns a boolean, the action is silent), so no
 * {@code MessageKey} is involved; the only text is a diagnostic {@code log} line when a lookup throws.
 */
public final class IntegrationConditions {

    private IntegrationConditions() {}

    /**
     * Register the integration conditions (and the paired {@code set-cooldown} action) into {@code bindings}.
     * {@code permissions} is the Vault permission seam {@code has-group} tests through; {@code currencies} is the
     * multi-currency façade {@code has-points} resolves PlayerPoints through; {@code cooldowns} is the shared
     * PDC-backed port the cooldown gate reads and {@code set-cooldown} stamps; {@code server} resolves the viewer's
     * live handle and gates the reflective integrations on plugin presence; {@code log} is the operator console
     * logger a fail-closed condition or fail-soft action warns through. Left separate from
     * {@link MenuVocabulary#registerConditions} so that method's existing call-sites stay untouched, the
     * composition root calls both.
     */
    public static void register(
            MenuBindings bindings,
            PermissionQuery permissions,
            Currencies currencies,
            Cooldowns cooldowns,
            ClientProtocol protocol,
            Server server,
            Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(cooldowns, "cooldowns");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        registerGroup(bindings, permissions, log);
        registerPoints(bindings, currencies, log);
        registerWeather(bindings, server, log);
        registerCooldown(bindings, cooldowns, log);
        registerJob(bindings, server, log);
        registerRegion(bindings, server, log);
        registerClientVersion(bindings, protocol, log);
        registerMcMmo(bindings, server, log);
    }

    /** {@code has-group:<group>} (aliases {@code luckperm-group}, {@code group}): the viewer's Vault group membership. */
    private static void registerGroup(MenuBindings bindings, PermissionQuery permissions, Logger log) {
        BiPredicate<MenuContext, Map<String, String>> gate =
                closed("has-group", log, (ctx, args) -> hasGroup(ctx, args, permissions));
        bindings.condition("has-group", gate);
        bindings.condition("luckperm-group", gate);
        bindings.condition("group", gate);
    }

    /** {@code has-points:<amount>}: a convenience alias over the existing PlayerPoints currency back-end. */
    private static void registerPoints(MenuBindings bindings, Currencies currencies, Logger log) {
        bindings.condition("has-points", closed("has-points", log, (ctx, args) -> hasPoints(ctx, args, currencies)));
    }

    /** {@code weather:<clear|rain|thunder>}: the viewer's current world weather. */
    private static void registerWeather(MenuBindings bindings, Server server, Logger log) {
        bindings.condition("weather", closed("weather", log, (ctx, args) -> weather(ctx, args, server)));
    }

    /**
     * {@code cooldown:<label>}. True when the named persistent cooldown is <em>ready</em> (expired), so the gate
     * passes when the viewer is NOT on cooldown: plus the paired {@code set-cooldown:<label> [seconds]} action.
     */
    private static void registerCooldown(MenuBindings bindings, Cooldowns cooldowns, Logger log) {
        bindings.condition("cooldown", closed("cooldown", log, (ctx, args) -> cooldownReady(ctx, args, cooldowns)));
        bindings.action("set-cooldown", safe("set-cooldown", log, ctx -> setCooldown(ctx, cooldowns)));
    }

    /**
     * {@code client-version:<protocol>} (alias {@code protocol}). Whether the viewer's client speaks at least that
     * protocol version, for hiding items an older client renders badly.
     *
     * <p>This is the one condition here that does <em>not</em> fail closed on a missing answer, and the asymmetry is
     * deliberate. Every other gate answers a question about the player (their group, their balance, their job) where
     * an unknown answer means "we could not verify, so deny". This one answers a question about their client, and an
     * unknown answer means there is no translation layer installed at all, in which case every player is on the
     * server's own version and the gate should pass. Denying instead would empty every menu that uses it on the
     * overwhelmingly common server that has no ViaVersion.
     */
    private static void registerClientVersion(MenuBindings bindings, ClientProtocol protocol, Logger log) {
        BiPredicate<MenuContext, Map<String, String>> gate =
                closed("client-version", log, (ctx, args) -> clientVersion(ctx, args, protocol));
        bindings.condition("client-version", gate);
        bindings.condition("protocol", gate);
    }

    /** {@code job:<jobname>}: whether the viewer holds that JobsReborn job (reflection, absent → false). */
    private static void registerJob(MenuBindings bindings, Server server, Logger log) {
        JobsIntegration jobs = new JobsIntegration(server, log);
        bindings.condition("job", closed("job", log, (ctx, args) -> job(ctx, args, jobs, server)));
    }

    /**
     * {@code worldguard-region:<regionId>} (aliases {@code wg-region}, {@code region}). Whether the viewer stands in
     * that WorldGuard region (reflection, absent → false).
     */
    private static void registerRegion(MenuBindings bindings, Server server, Logger log) {
        WorldGuardRegions regions = new WorldGuardRegions(server, log);
        BiPredicate<MenuContext, Map<String, String>> gate =
                closed("worldguard-region", log, (ctx, args) -> region(ctx, args, regions, server));
        bindings.condition("worldguard-region", gate);
        bindings.condition("wg-region", gate);
        bindings.condition("region", gate);
    }

    /**
     * {@code mcmmo-level:<skill>:<level>} and {@code mcmmo-power:<level>}. The viewer's mcMMO standing, for menus
     * that gate a reward or a warp on how far a player has actually got (reflection, absent → false).
     */
    private static void registerMcMmo(MenuBindings bindings, Server server, Logger log) {
        McMmoIntegration mcmmo = new McMmoIntegration(server, log);
        bindings.condition(
                "mcmmo-level", closed("mcmmo-level", log, (ctx, args) -> mcmmoLevel(ctx, args, mcmmo, server)));
        bindings.condition(
                "mcmmo-power", closed("mcmmo-power", log, (ctx, args) -> mcmmoPower(ctx, args, mcmmo, server)));
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a {@code false}
     * result rather than escaping into the render or click path. A condition that cannot be evaluated must fail
     * closed, the same discipline the sibling condition packs apply.
     */
    private static BiPredicate<MenuContext, Map<String, String>> closed(
            String condition, Logger log, BiPredicate<MenuContext, Map<String, String>> body) {
        return (ctx, args) -> {
            try {
                return body.test(ctx, args);
            } catch (RuntimeException failure) {
                log.warn("event=menu_integration_condition_failed condition={} value={}", condition, value(args));
                return false;
            }
        };
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op rather
     * than escaping into the click dispatch: the same fail-soft contract the sibling action packs apply.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_integration_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    // --- substrate-backed gates --------------------------------------------------------------------------------------

    /** {@code has-group:<group>}: whether the viewer belongs to {@code group}; a blank group or absent Vault is false. */
    private static boolean hasGroup(MenuContext ctx, Map<String, String> args, PermissionQuery permissions) {
        String group = value(args).strip();
        return !group.isEmpty() && permissions.inGroup(ctx.viewer().uuid(), group);
    }

    /** {@code has-points:<amount>}: whether the viewer holds at least {@code amount} PlayerPoints; non-numeric is false. */
    private static boolean hasPoints(MenuContext ctx, Map<String, String> args, Currencies currencies) {
        OptionalDouble amount = parseNumber(value(args));
        return amount.isPresent()
                && currencies.resolve("playerpoints").has(ctx.viewer().uuid(), amount.getAsDouble());
    }

    /**
     * {@code client-version:<protocol>}. True when the viewer's protocol version is at least the given number, and
     * true as well when no version is known (see {@link #registerClientVersion}). A blank or unparseable argument is
     * still false: that is an authoring mistake, not a missing answer.
     */
    private static boolean clientVersion(MenuContext ctx, Map<String, String> args, ClientProtocol protocol) {
        OptionalDouble required = parseNumber(value(args));
        if (required.isEmpty()) {
            return false;
        }
        int actual = protocol.protocolVersion(ctx.viewer());
        return actual == ClientProtocol.UNKNOWN || actual >= required.getAsDouble();
    }

    /** {@code weather:<clear|rain|thunder>}: matches the viewer's world weather; an unknown token or offline is false. */
    private static boolean weather(MenuContext ctx, Map<String, String> args, Server server) {
        Player player = viewer(ctx, server);
        if (player == null) {
            return false;
        }
        World world = player.getWorld();
        return switch (value(args).strip().toLowerCase(Locale.ROOT)) {
            case "thunder" -> world.isThundering();
            case "rain" -> world.hasStorm() && !world.isThundering();
            case "clear" -> !world.hasStorm() && !world.isThundering();
            default -> false;
        };
    }

    /**
     * {@code cooldown:<label>}. True when the persistent, PDC-backed cooldown keyed by {@code label} is ready
     * (expired), i.e. the viewer is NOT currently on it; an active cooldown answers an {@link Cooldowns#checkLabel
     * err Result} and so is false, and a blank label is false. The label is the first token, so it agrees with the
     * paired {@code set-cooldown}, whose optional trailing seconds token is not part of the label.
     */
    private static boolean cooldownReady(MenuContext ctx, Map<String, String> args, Cooldowns cooldowns) {
        String label = firstToken(value(args));
        return !label.isEmpty() && cooldowns.checkLabel(ctx.viewer(), label).isOk();
    }

    /**
     * {@code set-cooldown:<label> [seconds]}. Start or refresh the persistent cooldown keyed by {@code label} so a
     * {@code cooldown:} view-condition then gates it. The optional {@code [seconds]} token is accepted for a natural
     * operator grammar but is not applied per call: the {@link Cooldowns} port sizes a label's duration from its
     * resolved quota nodes (the {@code uxmessentials.cooldown.bypass.<label>} min-reducer), not from the action, so
     * we stamp the label and let the port own the length. A blank label is a no-op.
     */
    private static void setCooldown(MenuActionContext ctx, Cooldowns cooldowns) {
        String label = firstToken(ctx.arg());
        if (!label.isEmpty()) {
            cooldowns.stampLabel(ctx.viewer(), label);
        }
    }

    // --- reflective gates ------------------------------------------------------------------------------------------

    /** {@code job:<jobname>}: whether the viewer holds that job; an offline viewer or absent JobsReborn is false. */
    private static boolean job(MenuContext ctx, Map<String, String> args, JobsIntegration jobs, Server server) {
        Player player = viewer(ctx, server);
        return player != null && jobs.inJob(player, value(args).strip());
    }

    /** {@code worldguard-region:<regionId>}: whether the viewer stands in that region; offline or absent WG is false. */
    private static boolean region(MenuContext ctx, Map<String, String> args, WorldGuardRegions regions, Server server) {
        Player player = viewer(ctx, server);
        return player != null && regions.inRegion(player, value(args).strip());
    }

    /**
     * {@code mcmmo-level:<skill>:<level>}: whether the viewer's level in that mcMMO skill is at least {@code level}.
     * The skill and the level arrive as one argument because the runtime's split carries everything after the head, so
     * they are separated here; a missing level, an unknown skill or an absent mcMMO is false.
     */
    private static boolean mcmmoLevel(
            MenuContext ctx, Map<String, String> args, McMmoIntegration mcmmo, Server server) {
        String[] parts = value(args).strip().split("[:\\s]+", 2);
        if (parts.length < 2) {
            return false;
        }
        OptionalDouble required = parseNumber(parts[1]);
        Player player = viewer(ctx, server);
        return player != null
                && required.isPresent()
                && atLeast(mcmmo.skillLevel(player, parts[0]), required.getAsDouble());
    }

    /** {@code mcmmo-power:<level>}: whether the viewer's mcMMO power level is at least {@code level}. */
    private static boolean mcmmoPower(
            MenuContext ctx, Map<String, String> args, McMmoIntegration mcmmo, Server server) {
        OptionalDouble required = parseNumber(value(args));
        Player player = viewer(ctx, server);
        return player != null && required.isPresent() && atLeast(mcmmo.powerLevel(player), required.getAsDouble());
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    /** The live {@link Player} for the open context's viewer, or {@code null} when that player is offline. */
    private static @Nullable Player viewer(MenuContext ctx, Server server) {
        return server.getPlayer(ctx.viewer().uuid());
    }

    /** The single positional argument the runtime's split carried onto the condition/action ref, or empty. */
    private static String value(Map<String, String> args) {
        return args.getOrDefault("value", "");
    }

    /** The first whitespace-delimited token of {@code value} (stripped), or empty when it has none. */
    private static String firstToken(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? "" : stripped.split("\\s+", 2)[0];
    }

    /** Whether an integration answered at all, and answered at least {@code required}; an empty answer denies. */
    private static boolean atLeast(OptionalInt actual, double required) {
        return actual.isPresent() && actual.getAsInt() >= required;
    }

    /** The token as a finite-or-not {@code double}, or empty when it is blank or not numeric. */
    private static OptionalDouble parseNumber(String token) {
        try {
            return OptionalDouble.of(Double.parseDouble(token.strip()));
        } catch (NumberFormatException notNumeric) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Invoke the first public single-argument method named {@code method} on {@code target} whose parameter accepts
     * {@code arg}. The reflective integrations use this to bind to an SDK method whose exact declared parameter type
     * we do not name (a {@code UUID}, an SDK {@code Job}, a WorldEdit {@code Location}), matching by
     * {@link Class#isInstance} sidesteps the exact overload while still hitting the intended method.
     */
    private static @Nullable Object invokeSingleArg(Object target, String method, Object arg)
            throws ReflectiveOperationException {
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(method)
                    && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(arg)) {
                return candidate.invoke(target, arg);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + method);
    }

    /**
     * The JobsReborn (plugin {@code Jobs}) membership check, reached purely by reflection. Named the SDK only by
     * string class-name ({@code com.gamingmesh.jobs.Jobs}), so no field or method signature here carries a
     * {@code com.gamingmesh} type: constructing this on a server without JobsReborn loads none of its classes, and
     * the present-guard short-circuits before any reflection runs. Any {@link ReflectiveOperationException} (the API
     * absent, or its shape shifted under a version bump) or unchecked failure is logged exactly once and degraded to
     * {@code false}, the same discipline {@code ReflectiveItemProvider} uses.
     */
    private static final class JobsIntegration {

        private final Server server;
        private final Logger log;
        private final AtomicBoolean warned = new AtomicBoolean();

        JobsIntegration(Server server, Logger log) {
            this.server = server;
            this.log = log;
        }

        boolean inJob(Player player, String jobName) {
            if (jobName.isBlank() || !server.getPluginManager().isPluginEnabled("Jobs")) {
                return false;
            }
            try {
                return lookup(player, jobName);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }

        private boolean lookup(Player player, String jobName) throws ReflectiveOperationException {
            Class<?> jobsApi = Class.forName("com.gamingmesh.jobs.Jobs");
            Object job = jobsApi.getMethod("getJob", String.class).invoke(null, jobName);
            if (job == null) {
                return false; // no such job configured. The viewer cannot be in it
            }
            Object manager = jobsApi.getMethod("getPlayerManager").invoke(null);
            if (manager == null) {
                return false;
            }
            Object jobsPlayer = invokeSingleArg(manager, "getJobsPlayer", player.getUniqueId());
            Object inJob = jobsPlayer == null ? null : invokeSingleArg(jobsPlayer, "isInJob", job);
            return inJob instanceof Boolean flag && flag;
        }

        private void degrade(Exception failure) {
            if (warned.compareAndSet(false, true)) {
                log.warn(
                        "event=menu_integration_reflection_failed integration={} reason={}",
                        "Jobs",
                        failure.toString());
            }
        }
    }

    /**
     * The WorldGuard (plugin {@code WorldGuard}) standing-region check, reached through the shared
     * {@link WorldGuardReflection} entry point, so no field or method signature here carries a {@code com.sk89q}
     * type: constructing this on a server without WorldGuard loads none of its classes, and the present-guard
     * short-circuits before any reflection runs. The region-query chain is version-fiddly, so it is best-effort: any
     * {@link ReflectiveOperationException} or unchecked failure is logged exactly once and degraded to {@code false}.
     * The absent path plus this fail-closed degrade are the tested contract; the happy path degrades safely if a
     * version bump moves the chain.
     */
    private static final class WorldGuardRegions {

        private final Server server;
        private final Logger log;
        private final AtomicBoolean warned = new AtomicBoolean();

        WorldGuardRegions(Server server, Logger log) {
            this.server = server;
            this.log = log;
        }

        boolean inRegion(Player player, String regionId) {
            if (regionId.isBlank() || !WorldGuardReflection.isEnabled(server)) {
                return false;
            }
            try {
                return contains(player, regionId);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }

        private boolean contains(Player player, String regionId) throws ReflectiveOperationException {
            Location location = player.getLocation();
            if (location == null) {
                return false;
            }
            Object applicable = WorldGuardReflection.applicableRegions(
                    WorldGuardReflection.createQuery(), WorldGuardReflection.adapt(location));
            if (applicable == null) {
                return false;
            }
            Object regions = applicable.getClass().getMethod("getRegions").invoke(applicable);
            if (!(regions instanceof Iterable<?> iterable)) {
                return false;
            }
            for (Object protectedRegion : iterable) {
                Object id = protectedRegion.getClass().getMethod("getId").invoke(protectedRegion);
                if (regionId.equalsIgnoreCase(String.valueOf(id))) {
                    return true;
                }
            }
            return false;
        }

        private void degrade(Exception failure) {
            if (warned.compareAndSet(false, true)) {
                log.warn(
                        "event=menu_integration_reflection_failed integration={} reason={}",
                        "WorldGuard",
                        failure.toString());
            }
        }
    }
}

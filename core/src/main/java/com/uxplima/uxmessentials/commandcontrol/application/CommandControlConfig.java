package com.uxplima.uxmessentials.commandcontrol.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.commandcontrol.domain.ChannelHidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.NamespaceBypassRule;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.commandcontrol.domain.SpamAction;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldHidePolicies;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldRuleSets;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/commandcontrol/config.conf}: the enable gate, the whitelist/blacklist
 * mode, which deny message to show, the {@code default} plus per-group command lists, and the PlHidePro-parity extras -
 * command-spam protection, the plugin-channel hider, the auto-lowercase normalisation, and per-world overrides of the
 * command lists and hide behaviour. It is resolved once from the module's scoped {@link ConfigStore} when the module
 * starts and, per the atomic-reload rule, rebuilt whole on reload - so a command dispatched mid-reload sees one coherent
 * snapshot.
 *
 * <p>The per-group lists live under {@code commands { default = […], <group> = […] }}: the {@code default} key is the
 * fallback list applied to a player whose group has no list, and every other key names a permission group. A {@code
 * worlds { <world> { … } }} block overrides the base command lists and hide behaviour in a named world (unspecified
 * fields inherit the base), so {@code /fly} can be allowed in a creative world and blocked elsewhere; a world with no
 * override falls back to the base. Reading the group and world names off the config tree ({@link ConfigStore#getKeys})
 * keeps both sets open - an operator adds a group or a world by adding a key, with no code change.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param mode whether the lists are a whitelist or a blacklist ({@code mode}, default {@code blacklist})
 * @param useUnknownCommandMessage show the vanilla-style "unknown command" line on deny rather than the
 *     "no permission" line ({@code use-unknown-command-message}, default {@code true})
 * @param defaultCommands the fallback command list ({@code commands.default})
 * @param groupCommands the per-group command lists, keyed by permission group name
 * @param tabCompletionEnabled scrub the disallowed commands from the client command list / tab-completion
 *     ({@code tab-completion.enabled}, default {@code true}. A no-op while the rule set stays inert)
 * @param pluginHideEnabled hide the plugin-listing / help commands from players without the view permission
 *     ({@code plugin-hide.enabled}, default {@code true}; the view permission defaults to op, so only non-ops are hit)
 * @param hiddenCommands the plugin-listing / help commands the hide covers ({@code plugin-hide.hidden-commands})
 * @param denyListCommands also block the hidden commands from executing for those players, so a {@code /plugins} or
 *     {@code /help} cannot leak plugin names ({@code plugin-hide.deny-list-commands}, default {@code true})
 * @param blockNamespaceBypass deny the {@code namespace:command} form (e.g. {@code /minecraft:gamemode}) of any command
 *     the bare form is denied, so a player cannot dodge the filter with a namespace prefix
 *     ({@code block-namespace-bypass}, default {@code true})
 * @param spamEnabled rate-limit the commands a player may send in a sliding window ({@code command-spam.enabled},
 *     default {@code false})
 * @param spamMaxPerWindow the greatest number of commands allowed inside the window ({@code command-spam.max-per-window})
 * @param spamWindowSeconds the sliding-window width in seconds ({@code command-spam.window-seconds})
 * @param spamAction what to do to a player who exceeds the limit ({@code command-spam.action}: KICK, BLOCK, or WARN)
 * @param autoLowercaseBaseCommands lowercase the leading command label before the checks and execution, so
 *     {@code /GAMEMODE} is treated as {@code /gamemode} ({@code auto-lowercase-base-commands}, default {@code true})
 * @param channelHideEnabled strip non-allowed plugin-messaging channels from the client's channel-registration list so
 *     client mods cannot fingerprint installed plugins ({@code plugin-channel-hide.enabled}, default {@code false})
 * @param allowedChannels the plugin-messaging channels that may still be advertised ({@code
 *     plugin-channel-hide.allowed-channels})
 * @param worldOverrides the per-world overrides of the command lists and hide behaviour, keyed by world name
 */
public record CommandControlConfig(
        boolean enabled,
        RuleMode mode,
        boolean useUnknownCommandMessage,
        List<String> defaultCommands,
        Map<String, List<String>> groupCommands,
        boolean tabCompletionEnabled,
        boolean pluginHideEnabled,
        List<String> hiddenCommands,
        boolean denyListCommands,
        boolean blockNamespaceBypass,
        boolean spamEnabled,
        int spamMaxPerWindow,
        int spamWindowSeconds,
        SpamAction spamAction,
        boolean autoLowercaseBaseCommands,
        boolean channelHideEnabled,
        List<String> allowedChannels,
        Map<String, WorldOverride> worldOverrides) {

    /** The config key under {@code commands} that holds the fallback list rather than a named group. */
    private static final String DEFAULT_LIST_KEY = "default";

    /** The plugin-listing / help commands the hide covers out of the box, before an operator edits the list. */
    private static final List<String> DEFAULT_HIDDEN_COMMANDS =
            List.of("plugins", "pl", "?", "help", "ver", "version", "about", "icanhasbukkit");

    /** The channels left advertised out of the box: the brand plus the proxy control channels a network needs. */
    private static final List<String> DEFAULT_ALLOWED_CHANNELS =
            List.of("minecraft:brand", "bungeecord:main", "velocity:main");

    public CommandControlConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(defaultCommands, "defaultCommands");
        Objects.requireNonNull(groupCommands, "groupCommands");
        Objects.requireNonNull(hiddenCommands, "hiddenCommands");
        Objects.requireNonNull(spamAction, "spamAction");
        Objects.requireNonNull(allowedChannels, "allowedChannels");
        Objects.requireNonNull(worldOverrides, "worldOverrides");
        defaultCommands = List.copyOf(defaultCommands);
        hiddenCommands = List.copyOf(hiddenCommands);
        allowedChannels = List.copyOf(allowedChannels);
        groupCommands = copyGroups(groupCommands);
        worldOverrides = Map.copyOf(new LinkedHashMap<>(worldOverrides));
    }

    /** A per-world override of the base command lists and hide behaviour, applied while a player is in that world. */
    public record WorldOverride(
            RuleMode mode,
            List<String> defaultCommands,
            Map<String, List<String>> groupCommands,
            boolean pluginHideEnabled,
            List<String> hiddenCommands) {

        public WorldOverride {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(defaultCommands, "defaultCommands");
            Objects.requireNonNull(groupCommands, "groupCommands");
            Objects.requireNonNull(hiddenCommands, "hiddenCommands");
            defaultCommands = List.copyOf(defaultCommands);
            hiddenCommands = List.copyOf(hiddenCommands);
            groupCommands = copyGroups(groupCommands);
        }
    }

    /** Resolve the command-control config from the module's scoped {@link ConfigStore} ({@code modules.commandcontrol}). */
    public static CommandControlConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        RuleMode mode = RuleMode.fromConfig(config.getString("mode", "blacklist"), RuleMode.BLACKLIST);
        List<String> defaults = config.getStringList("commands." + DEFAULT_LIST_KEY, List.of());
        Map<String, List<String>> groups = readGroups(config, "commands");
        boolean pluginHide = config.getBoolean("plugin-hide.enabled", true);
        List<String> hidden = config.getStringList("plugin-hide.hidden-commands", DEFAULT_HIDDEN_COMMANDS);
        Map<String, WorldOverride> worlds = readWorldOverrides(config, mode, defaults, pluginHide, hidden);
        return new CommandControlConfig(
                config.getBoolean("enabled", true),
                mode,
                config.getBoolean("use-unknown-command-message", true),
                defaults,
                groups,
                config.getBoolean("tab-completion.enabled", true),
                pluginHide,
                hidden,
                config.getBoolean("plugin-hide.deny-list-commands", true),
                config.getBoolean("block-namespace-bypass", true),
                config.getBoolean("command-spam.enabled", false),
                config.getInt("command-spam.max-per-window", 40),
                config.getInt("command-spam.window-seconds", 2),
                SpamAction.fromConfig(config.getString("command-spam.action", "block"), SpamAction.BLOCK),
                config.getBoolean("auto-lowercase-base-commands", true),
                config.getBoolean("plugin-channel-hide.enabled", false),
                config.getStringList("plugin-channel-hide.allowed-channels", DEFAULT_ALLOWED_CHANNELS),
                worlds);
    }

    /** Build the pure {@link RuleSet} the base config describes, gating {@code .bypass} on {@code bypassPermission}. */
    public RuleSet toRuleSet(String bypassPermission) {
        return RuleSet.of(mode, defaultCommands, groupCommands, bypassPermission);
    }

    /** Build the world-scoped rule set: the base rule set plus a per-world override for each configured world. */
    public WorldRuleSets toWorldRuleSets(String bypassPermission) {
        Map<String, RuleSet> byWorld = new LinkedHashMap<>();
        worldOverrides.forEach((world, override) -> byWorld.put(
                world,
                RuleSet.of(override.mode(), override.defaultCommands(), override.groupCommands(), bypassPermission)));
        return WorldRuleSets.of(toRuleSet(bypassPermission), byWorld);
    }

    /** Build the pure {@link HidePolicy} the base config describes, revealing the hidden commands to {@code viewPermission}. */
    public HidePolicy toHidePolicy(String viewPermission) {
        return HidePolicy.of(pluginHideEnabled, hiddenCommands, viewPermission);
    }

    /** Build the world-scoped hide policy: the base policy plus a per-world override for each configured world. */
    public WorldHidePolicies toWorldHidePolicies(String viewPermission) {
        Map<String, HidePolicy> byWorld = new LinkedHashMap<>();
        worldOverrides.forEach((world, override) -> byWorld.put(
                world, HidePolicy.of(override.pluginHideEnabled(), override.hiddenCommands(), viewPermission)));
        return WorldHidePolicies.of(toHidePolicy(viewPermission), byWorld);
    }

    /** Build the namespace-bypass block over {@code rules}, switched on per {@link #blockNamespaceBypass}. */
    public NamespaceBypassRule toNamespaceRule(RuleSet rules) {
        return NamespaceBypassRule.of(rules, blockNamespaceBypass);
    }

    /** Build the command-spam rate limiter, clamping the window and count to sane positive values. */
    public CommandRateLimiter toRateLimiter() {
        return CommandRateLimiter.of(
                spamEnabled, Math.max(1, spamMaxPerWindow), Math.max(1, spamWindowSeconds), spamAction);
    }

    /** Build the channel-hide policy this config describes. */
    public ChannelHidePolicy toChannelHidePolicy() {
        return ChannelHidePolicy.of(channelHideEnabled, allowedChannels);
    }

    /** The deny line to show on a blocked command, per {@link #useUnknownCommandMessage}. */
    public CommandControlMessageKey denyMessage() {
        return useUnknownCommandMessage
                ? CommandControlMessageKey.COMMANDCONTROL_UNKNOWN_COMMAND
                : CommandControlMessageKey.COMMANDCONTROL_NO_PERMISSION;
    }

    /** Read {@code <path>.<group>} into a per-group list map, treating {@code default} as the fallback (excluded here). */
    private static Map<String, List<String>> readGroups(ConfigStore config, String path) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String key : config.getKeys(path)) {
            if (!key.equalsIgnoreCase(DEFAULT_LIST_KEY)) {
                groups.put(key, config.getStringList(path + "." + key, List.of()));
            }
        }
        return groups;
    }

    /**
     * Read every {@code worlds.<world>} override, inheriting the base {@code mode}, {@code default} list, and hide
     * behaviour for any field the world does not set. A world's per-group lists are self-contained (a group the world
     * omits is simply not overridden in that world), so the base groups are not merged in.
     */
    private static Map<String, WorldOverride> readWorldOverrides(
            ConfigStore config,
            RuleMode baseMode,
            List<String> baseDefaults,
            boolean basePluginHide,
            List<String> baseHidden) {
        Map<String, WorldOverride> worlds = new LinkedHashMap<>();
        for (String world : config.getKeys("worlds")) {
            String root = "worlds." + world;
            worlds.put(
                    world,
                    new WorldOverride(
                            RuleMode.fromConfig(config.getString(root + ".mode", ""), baseMode),
                            config.getStringList(root + ".commands." + DEFAULT_LIST_KEY, baseDefaults),
                            readGroups(config, root + ".commands"),
                            config.getBoolean(root + ".plugin-hide.enabled", basePluginHide),
                            config.getStringList(root + ".plugin-hide.hidden-commands", baseHidden)));
        }
        return worlds;
    }

    /** Defensive deep copy of a per-group list map so a caller cannot mutate the stored lists after construction. */
    private static Map<String, List<String>> copyGroups(Map<String, List<String>> groups) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        groups.forEach((group, list) -> copied.put(group, List.copyOf(list)));
        return Map.copyOf(copied);
    }
}

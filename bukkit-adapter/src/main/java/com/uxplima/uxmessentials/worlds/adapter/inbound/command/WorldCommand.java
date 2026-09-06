package com.uxplima.uxmessentials.worlds.adapter.inbound.command;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The {@code /worlds} command: create/import/load/unload/unregister/delete/list/info/backup/restore. */
@NullMarked
public final class WorldCommand extends WorldCommandSupport implements CommandRegistration {

    private static final String USE = "uxmessentials.world.use";
    private static final String CREATE = "uxmessentials.world.create";
    private static final String IMPORT = "uxmessentials.world.import";
    private static final String LOAD = "uxmessentials.world.load";
    private static final String UNLOAD = "uxmessentials.world.unload";
    private static final String UNREGISTER = "uxmessentials.world.unregister";
    private static final String DELETE = "uxmessentials.world.delete";
    private static final String LIST = "uxmessentials.world.list";
    private static final String INFO = "uxmessentials.world.info";
    private static final String SET = "uxmessentials.world.set";
    private static final String GAMERULE = "uxmessentials.world.gamerule";
    private static final String SETSPAWN = "uxmessentials.world.setspawn";
    private static final String SPAWN = "uxmessentials.world.spawn";
    private static final String TP = "uxmessentials.world.tp";
    private static final String TP_OTHERS = "uxmessentials.world.tp.others";
    private static final String GUI = "uxmessentials.world.gui";
    private static final String PREGEN = "uxmessentials.world.pregen";
    private static final String BACKUP = "uxmessentials.world.backup";
    private static final String RESTORE = "uxmessentials.world.restore";

    private static final DateTimeFormatter BACKUP_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    public WorldCommand(WorldsServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("worlds")
                .requires(src -> src.getSender().hasPermission(USE))
                .then(Commands.literal("list").requires(p(LIST)).executes(this::runList))
                .then(Commands.literal("info").requires(p(INFO)).then(nameArg().executes(this::runInfo)))
                .then(Commands.literal("create")
                        .requires(p(CREATE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::runCreate)
                                .then(envArg().executes(this::runCreate)
                                        .then(typeArg()
                                                .executes(this::runCreate)
                                                .then(generatorArg().executes(this::runCreate))))))
                .then(Commands.literal("import")
                        .requires(p(IMPORT))
                        .then(folderArg()
                                .then(envArg().executes(this::runImport)
                                        .then(Commands.argument("generator", StringArgumentType.greedyString())
                                                .executes(this::runImport)))))
                .then(Commands.literal("load").requires(p(LOAD)).then(nameArg().executes(this::runLoad)))
                .then(Commands.literal("unload")
                        .requires(p(UNLOAD))
                        .then(nameArg().executes(this::runUnload)))
                .then(Commands.literal("unregister")
                        .requires(p(UNREGISTER))
                        .then(nameArg().executes(this::runUnregister)))
                .then(Commands.literal("delete")
                        .requires(p(DELETE))
                        .then(nameArg().executes(this::runDelete)))
                .then(Commands.literal("confirm")
                        .requires(p(DELETE))
                        .then(nameArg().executes(this::runConfirm)))
                .then(Commands.literal("set")
                        .requires(p(SET))
                        .then(nameArg()
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests(CommandSuggestions.fromStrings(this::propertyKeys))
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(this::runSet)))))
                .then(Commands.literal("gamerule")
                        .requires(p(GAMERULE))
                        .then(nameArg()
                                .then(Commands.argument("rule", StringArgumentType.word())
                                        .suggests(CommandSuggestions.fromStrings(services::gameRuleNames))
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(this::runGamerule)))))
                .then(setSpawnNode())
                .then(Commands.literal("spawn")
                        .requires(p(SPAWN))
                        .executes(this::runSpawnCurrent)
                        .then(nameArg().executes(this::runSpawnNamed)))
                .then(Commands.literal("tp")
                        .requires(p(TP))
                        .then(nameArg()
                                .executes(this::runTpSelf)
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .suggests(CommandSuggestions.singlePlayerTarget())
                                        .requires(p(TP_OTHERS))
                                        .executes(this::runTpOther))))
                .then(Commands.literal("gui")
                        .requires(p(GUI))
                        .executes(this::runGuiList)
                        .then(nameArg().executes(this::runGuiWorld)))
                .then(Commands.literal("pregen")
                        .requires(p(PREGEN))
                        .then(Commands.literal("cancel").then(nameArg().executes(this::runPregenCancel)))
                        .then(nameArg()
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(this::runPregen))))
                .then(Commands.literal("backup")
                        .requires(p(BACKUP))
                        .then(nameArg().executes(this::runBackup)))
                .then(Commands.literal("backups")
                        .requires(p(BACKUP))
                        .then(nameArg().executes(this::runBackups)))
                .then(Commands.literal("restore")
                        .requires(p(RESTORE))
                        .then(nameArg()
                                // No backup-id suggestions: enumerating them is a backups-directory read, and
                                // suggestion callbacks run on the tick thread and must stay off I/O.
                                .then(Commands.argument("backup", StringArgumentType.word())
                                        .executes(this::runRestore))))
                .then(Commands.literal("restoreconfirm")
                        .requires(p(RESTORE))
                        .then(nameArg().executes(this::runRestoreConfirm)))
                .build();
    }

    @Override
    public String description() {
        return "Manage worlds: create, import, load, unload, unregister, delete, list, info, spawn, tp, gui, pregen,"
                + " backup, backups, restore, restoreconfirm.";
    }

    /**
     * With the catalog {@code gui} flag on, bare {@code /worlds} opens the world list GUI, the same picker
     * {@code /worlds gui} opens: from which a staff member can edit or create a world. With the flag off the root
     * falls through to the usage text instead.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return Optional.of(this::runGuiList);
    }

    private static Predicate<CommandSourceStack> p(String node) {
        return src -> src.getSender().hasPermission(node);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() -> services.repository().all().stream()
                        .map(w -> w.name().value())
                        .toList()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> folderArg() {
        services.refreshImportableFolders(); // fire-and-forget async rescan
        return Commands.argument("folder", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(services::importableFolders));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> envArg() {
        return Commands.argument("environment", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() ->
                        Arrays.stream(WorldEnvironment.values()).map(Enum::name).toList()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> typeArg() {
        return Commands.argument("type", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() ->
                        Arrays.stream(WorldGenType.values()).map(Enum::name).toList()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> generatorArg() {
        return Commands.argument("generator", StringArgumentType.word())
                .suggests(
                        CommandSuggestions.fromStrings(() -> List.of(BuiltInGenerators.VOID, BuiltInGenerators.FLAT)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> setSpawnNode() {
        return Commands.literal("setspawn")
                .requires(p(SETSPAWN))
                .then(nameArg()
                        .executes(this::runSetSpawn)
                        .then(setSpawnCoordinates())
                        .then(Commands.literal("at").then(setSpawnCoordinates())));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Double> setSpawnCoordinates() {
        return Commands.argument("x", DoubleArgumentType.doubleArg())
                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                .executes(ctx -> runSetSpawnAt(ctx, 0f, 0f))
                                .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> runSetSpawnAt(
                                                        ctx, floatArg(ctx, "yaw"), floatArg(ctx, "pitch")))))));
    }

    private int runCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        WorldSpec spec = new WorldSpec(
                arg(ctx, "environment", WorldEnvironment.class, WorldEnvironment.NORMAL),
                arg(ctx, "type", WorldGenType.class, WorldGenType.NORMAL),
                Optional.empty(),
                parseGenerator(ctx),
                true,
                Optional.empty());
        PlayerRef who = actor(ctx);
        feedback.send(sender, WorldsMessageKey.WORLD_CREATING, Map.of("world", name.value()));
        onGlobal(() -> services.createWorld().create(who, name, spec, true));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maps the optional trailing {@code generator} token to a ref: a {@code void}/{@code flat} token
     * (case-insensitive) becomes the namespaced built-in ref; any other non-blank token is an external
     * generator ref passed through unchanged; absent or blank yields empty (vanilla generation).
     */
    private static Optional<GeneratorRef> parseGenerator(CommandContext<CommandSourceStack> ctx) {
        return optionalString(ctx, "generator")
                .filter(token -> !token.isBlank())
                .map(token -> {
                    String lower = token.toLowerCase(Locale.ROOT);
                    boolean builtIn = lower.equals(BuiltInGenerators.VOID) || lower.equals(BuiltInGenerators.FLAT);
                    return builtIn ? BuiltInGenerators.ref(lower) : GeneratorRef.of(token);
                });
    }

    private int runImport(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("folder", String.class));
        if (name == null) {
            return 0;
        }
        WorldEnvironment env = arg(ctx, "environment", WorldEnvironment.class, WorldEnvironment.NORMAL);
        Optional<GeneratorRef> gen = optionalString(ctx, "generator").map(GeneratorRef::of);
        PlayerRef who = actor(ctx);
        feedback.send(sender, WorldsMessageKey.WORLD_IMPORTING, Map.of("world", name.value()));
        onGlobal(() -> services.importWorld().importWorld(who, name, env, gen));
        return Command.SINGLE_SUCCESS;
    }

    private int runLoad(CommandContext<CommandSourceStack> ctx) {
        return mutate(ctx, (who, name) -> services.loadWorld().load(who, name));
    }

    private int runUnload(CommandContext<CommandSourceStack> ctx) {
        return mutate(ctx, (who, name) -> services.unloadWorld().unload(who, name, true));
    }

    private int runUnregister(CommandContext<CommandSourceStack> ctx) {
        return mutate(ctx, (who, name) -> services.unregisterWorld().unregister(who, name));
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        services.deleteWorld().request(actor(ctx), name); // inline: validation + staging, no I/O
        return Command.SINGLE_SUCCESS;
    }

    // The target of the delete-confirmation prompt's click. Kept under the root as `/worlds confirm <name>`
    // rather than a separate top-level command so the whole world surface lives behind one literal.
    private int runConfirm(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.deleteWorld().confirm(who, name)); // unload + off-tick file delete
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var entries = services.listWorlds().all();
        if (entries.isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_LIST_EMPTY, Map.of());
            return Command.SINGLE_SUCCESS;
        }
        feedback.send(sender, WorldsMessageKey.WORLD_LIST_HEADER, Map.of("count", Integer.toString(entries.size())));
        for (ListWorlds.WorldListEntry entry : entries) {
            feedback.send(
                    sender,
                    WorldsMessageKey.WORLD_LIST_ENTRY,
                    Map.of(
                            "world", entry.name().value(),
                            "loaded", Boolean.toString(entry.loaded()),
                            "environment", entry.environment().name()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        Optional<ManagedWorld> found = services.worldInfo().find(name);
        if (found.isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_NOT_FOUND, Map.of("world", name.value()));
            return 0;
        }
        ManagedWorld w = found.orElseThrow();
        feedback.send(
                sender,
                WorldsMessageKey.WORLD_INFO_HEADER,
                Map.of("world", w.name().value()));
        feedback.send(
                sender,
                WorldsMessageKey.WORLD_INFO_ENVIRONMENT,
                Map.of("value", w.spec().environment().name()));
        feedback.send(
                sender,
                WorldsMessageKey.WORLD_INFO_TYPE,
                Map.of("value", w.spec().worldType().name()));
        feedback.send(sender, WorldsMessageKey.WORLD_INFO_AUTOLOAD, Map.of("value", Boolean.toString(w.autoLoad())));
        return Command.SINGLE_SUCCESS;
    }

    private java.util.List<String> propertyKeys() {
        java.util.List<String> keys =
                new java.util.ArrayList<>(com.uxplima.uxmessentials.worlds.domain.WorldProperties.ALL.stream()
                        .map(com.uxplima.uxmessentials.worlds.domain.WorldProperty::key)
                        .toList());
        keys.add("alias");
        return keys;
    }

    private int runSet(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        String property = ctx.getArgument("property", String.class);
        String value = ctx.getArgument("value", String.class);
        PlayerRef who = actor(ctx);
        if (property.equalsIgnoreCase("alias")) {
            onGlobal(() -> services.setWorldAlias().set(who, name, Optional.of(value)));
        } else {
            onGlobal(() -> services.setWorldProperty().set(who, name, property, value));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runGamerule(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        String rule = ctx.getArgument("rule", String.class);
        String value = ctx.getArgument("value", String.class);
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.setGamerule().set(who, name, rule, value));
        return Command.SINGLE_SUCCESS;
    }

    private int runSetSpawn(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        Position spawn = position(sender);
        onGlobal(() -> services.setWorldSpawn().set(who, name, spawn));
        return Command.SINGLE_SUCCESS;
    }

    private int runSetSpawnAt(CommandContext<CommandSourceStack> ctx, float yaw, float pitch) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        Optional<ManagedWorld> managed = services.repository().find(name);
        if (managed.isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_NOT_FOUND, Map.of("world", name.value()));
            return 0;
        }
        Optional<java.util.UUID> uid = managed.orElseThrow().knownUid();
        if (uid.isEmpty()) {
            org.bukkit.World loaded = sender.getServer().getWorld(name.value());
            if (loaded != null) {
                uid = Optional.of(loaded.getUID());
            }
        }
        double x = ctx.getArgument("x", Double.class);
        double y = ctx.getArgument("y", Double.class);
        double z = ctx.getArgument("z", Double.class);
        if (uid.isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_NOT_LOADED, Map.of("world", name.value()));
            return 0;
        }
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            feedback.send(
                    sender,
                    com.uxplima.uxmessentials.shared.application.message.SharedMessageKey.COMMAND_INVALID_POSITION);
            return 0;
        }
        Position spawn = new Position(new WorldRef(uid.orElseThrow(), name.value()), x, y, z, yaw, pitch);
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.setWorldSpawn().set(who, name, spawn));
        return Command.SINGLE_SUCCESS;
    }

    private static float floatArg(CommandContext<CommandSourceStack> ctx, String name) {
        return (float) (double) ctx.getArgument(name, Double.class);
    }

    private int runSpawnCurrent(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        return spawnTo(sender, sender.getWorld().getName());
    }

    private int runSpawnNamed(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        return spawnTo(sender, ctx.getArgument("name", String.class));
    }

    private int spawnTo(Player sender, String raw) {
        WorldName name = parseName(sender, raw);
        if (name == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        onGlobal(() -> services.worldTeleport().spawn(who, name));
        return Command.SINGLE_SUCCESS;
    }

    private int runTpSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        onGlobal(() -> services.worldTeleport().forced(who, who, name));
        return Command.SINGLE_SUCCESS;
    }

    private int runTpOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Optional<Player> target = resolveTarget(ctx);
        if (target.isEmpty()) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef actor = actor(ctx);
        PlayerRef subject = ref(target.get());
        onGlobal(() -> services.worldTeleport().forced(actor, subject, name));
        return Command.SINGLE_SUCCESS;
    }

    private int runGuiList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.openWorldList(sender, ref(sender)); // the menu self-schedules onEntity
        return Command.SINGLE_SUCCESS;
    }

    private int runGuiWorld(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        if (services.repository().find(name).isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_NOT_FOUND, Map.of("world", name.value()));
            return 0;
        }
        services.openWorldMain(sender, ref(sender), name); // the menu self-schedules onEntity
        return Command.SINGLE_SUCCESS;
    }

    private int runPregen(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        int radius = ctx.getArgument("radius", Integer.class);
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.pregen().start(who, name, radius));
        return Command.SINGLE_SUCCESS;
    }

    private int runPregenCancel(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.pregen().cancel(who, name));
        return Command.SINGLE_SUCCESS;
    }

    private int runBackup(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.backupWorld().backup(who, name));
        return Command.SINGLE_SUCCESS;
    }

    private int runBackups(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        // The list is a backups-directory read; keep it off the command thread. It runs on the global region
        // thread (a small directory scan, cheap there) and feedback is rendered in the same hop.
        onGlobal(() -> renderBackups(sender, name));
        return Command.SINGLE_SUCCESS;
    }

    /** Render a world's stored backups (header + one row each, or the empty notice) to the sender. */
    private void renderBackups(CommandSender sender, WorldName name) {
        List<BackupRef> refs = services.listBackups().list(name);
        if (refs.isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_BACKUP_LIST_EMPTY, Map.of("world", name.value()));
            return;
        }
        feedback.send(
                sender,
                WorldsMessageKey.WORLD_BACKUP_LIST_HEADER,
                Map.of("world", name.value(), "count", Integer.toString(refs.size())));
        for (BackupRef ref : refs) {
            feedback.send(
                    sender,
                    WorldsMessageKey.WORLD_BACKUP_LIST_ENTRY,
                    Map.of(
                            "backup", ref.id().value(),
                            "date", BACKUP_DATE.format(ref.createdAt()),
                            "size", formatSize(ref.sizeBytes())));
        }
    }

    /** A compact kilobyte rendering of a backup archive's byte size, rounded up so a non-empty file never reads 0. */
    private static String formatSize(long bytes) {
        long kb = (bytes + 1023L) / 1024L;
        return kb + " KB";
    }

    private int runRestore(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        BackupId id = parseBackupId(sender, ctx.getArgument("backup", String.class));
        if (id == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.restoreWorld().request(who, name, id));
        return Command.SINGLE_SUCCESS;
    }

    /** Parse a raw token into a {@link BackupId}, or {@code null} (after sending the not-found reply). */
    private @Nullable BackupId parseBackupId(CommandSender sender, String raw) {
        try {
            return BackupId.of(raw);
        } catch (IllegalArgumentException invalid) {
            feedback.send(sender, WorldsMessageKey.WORLD_BACKUP_NOT_FOUND, Map.of("backup", raw));
            return null;
        }
    }

    private int runRestoreConfirm(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        onGlobal(() -> services.restoreWorld().confirm(who, name));
        return Command.SINGLE_SUCCESS;
    }

    /** Resolve the {@code player} selector to a single target, or empty (no online match) for the actor. */
    private Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        return resolved.isEmpty() ? Optional.empty() : Optional.of(resolved.get(0));
    }

    private int mutate(CommandContext<CommandSourceStack> ctx, Mutation mutation) {
        CommandSender sender = ctx.getSource().getSender();
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        onGlobal(() -> mutation.run(who, name));
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface Mutation {
        void run(PlayerRef who, WorldName name);
    }

    private static <E extends Enum<E>> E arg(
            CommandContext<CommandSourceStack> ctx, String key, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, ctx.getArgument(key, String.class).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException missingOrInvalid) {
            return fallback;
        }
    }

    private static Optional<String> optionalString(CommandContext<CommandSourceStack> ctx, String key) {
        try {
            return Optional.of(ctx.getArgument(key, String.class));
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }
}

package com.uxplima.uxmessentials.bootstrap.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.migration.adapter.MigrationImportService;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.RepairResult;
import com.uxplima.uxmessentials.shared.application.health.RepairableHealthCheck;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pins the {@code /uxmess doctor} subcommand onto the operator surface. {@code doctor} is a child of the
 * {@code /uxmess} root (not its own catalog literal), so it is not tracked by {@code CommandCatalogDriftTest};
 * this guard asserts the literal is wired under the root and, the point of the command, that its run is
 * resilient: a health check whose probe throws is still rendered (as a {@code FAIL} entry) rather than aborting
 * the run, because every check goes through {@link HealthCheck#safe()}.
 */
class DoctorCommandSurfaceTest {

    @Test
    void doctorIsWiredAsAChildOfTheUxmessRoot() {
        UxmessCommand command = command(List.of(okCheck()));

        CommandNode<CommandSourceStack> doctor = command.build().getChild("doctor");

        assertThat(doctor)
                .as("/uxmess doctor must be a child literal of the root")
                .isNotNull();
        assertThat(doctor.getName()).isEqualTo("doctor");
        assertThat(doctor.getChild("repair")).isNotNull();
        assertThat(doctor.getChild("repair").getChild("confirm")).isNotNull();
    }

    @Test
    void uxmessLiteralStaysAValidCommandId() {
        // doctor lives under the existing /uxmess catalog literal, which must stay a valid id.
        assertThat(new CommandId("uxmess").value()).isEqualTo("uxmess");
    }

    @Test
    void aThrowingCheckDoesNotPropagateThroughTheRun() throws Exception {
        HealthCheck explodes = new HealthCheck() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public HealthResult check() {
                throw new IllegalStateException("probe failed");
            }
        };
        // The InlineScheduler runs the async + onGlobal stages synchronously, so the whole doctor run executes on
        // this thread; a leaked exception would surface here. It must not: every check is safe()-wrapped.
        UxmessCommand command = command(List.of(okCheck(), explodes));
        CommandSourceStack source = sourceStack();

        int result = command.build().getChild("doctor").getCommand().run(contextFor(source));

        assertThat(result).isEqualTo(com.mojang.brigadier.Command.SINGLE_SUCCESS);
    }

    @Test
    void repairPreviewMutatesNothingAndConfirmRunsTheIdempotentRepair() throws Exception {
        AtomicInteger repairs = new AtomicInteger();
        RepairableHealthCheck repairable = new RepairableHealthCheck() {
            @Override
            public String name() {
                return "data-integrity";
            }

            @Override
            public HealthResult check() {
                return HealthResult.warn("orphanRows=1");
            }

            @Override
            public RepairResult repair() {
                repairs.incrementAndGet();
                return RepairResult.repaired("changedRows=1");
            }
        };
        UxmessCommand command = command(List.of(repairable));
        CommandNode<CommandSourceStack> repair =
                command.build().getChild("doctor").getChild("repair");

        repair.getCommand().run(contextFor(sourceStack()));
        assertThat(repairs).hasValue(0);

        repair.getChild("confirm").getCommand().run(contextFor(sourceStack()));
        assertThat(repairs).hasValue(1);
    }

    private static UxmessCommand command(List<HealthCheck> checks) {
        ModuleRegistry registry = new DefaultModuleRegistry();
        ConfigStore config = Mockito.mock(ConfigStore.class);
        Mockito.when(config.scoped(Mockito.anyString())).thenReturn(config);
        MigrationImportService service = Mockito.mock(MigrationImportService.class);
        return new UxmessCommand(
                registry,
                config,
                new MigrationImportNode(service),
                guiNode(),
                permissionsNode(),
                placeholdersNode(),
                new InlineScheduler(),
                checks,
                List.of());
    }

    /** A permissions node pointed at a scratch folder: this guard never runs its export. */
    private static PermissionsSubcommand permissionsNode() {
        return new PermissionsSubcommand(Path.of("build", "tmp", "permissions-guard"), Mockito.mock(Logger.class));
    }

    /** A placeholders node pointed at a scratch folder: this guard never runs its export either. */
    private static PlaceholdersSubcommand placeholdersNode() {
        return new PlaceholdersSubcommand(Path.of("build", "tmp", "placeholders-guard"), Mockito.mock(Logger.class));
    }

    /** A minimal /uxmess gui node: this surface guard only inspects the doctor child, never opens the hub. */
    private static GuiSubcommand guiNode() {
        Scheduler scheduler = new InlineScheduler();
        Permissions permissions = Mockito.mock(Permissions.class);
        Messages messages = Mockito.mock(Messages.class);
        ManagementGuiRegistry guiRegistry = new ManagementGuiRegistry();
        GuiText guiText = new GuiText(messages);
        EntityListLayout layout = EntityListLayout.paginatedDefault(org.bukkit.Material.NETHER_STAR);
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus menus =
                com.uxplima.uxmessentials.shared.menu.TestMenuEngine.create(messages, scheduler)
                        .menus();
        ManagementHubView hub = new ManagementHubView(menus, guiText, scheduler, permissions, guiRegistry, layout);
        return new GuiSubcommand(guiRegistry, hub, permissions, messages);
    }

    private static HealthCheck okCheck() {
        return new HealthCheck() {
            @Override
            public String name() {
                return "ok";
            }

            @Override
            public HealthResult check() {
                return HealthResult.ok("up");
            }
        };
    }

    @SuppressWarnings("unchecked") // Mockito.mock on a generic type needs the unchecked cast.
    private static com.mojang.brigadier.context.CommandContext<CommandSourceStack> contextFor(
            CommandSourceStack source) {
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx =
                Mockito.mock(com.mojang.brigadier.context.CommandContext.class);
        Mockito.when(ctx.getSource()).thenReturn(source);
        return ctx;
    }

    private static CommandSourceStack sourceStack() {
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        org.bukkit.command.CommandSender sender = Mockito.mock(org.bukkit.command.CommandSender.class);
        Mockito.when(source.getSender()).thenReturn(sender);
        return source;
    }

    /** Runs every scheduled stage inline so the off-tick doctor run executes synchronously in the test thread. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
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

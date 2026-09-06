package com.uxplima.uxmessentials.migration;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The migration adapter as a first-class, command-gated {@link FeatureModule} (docs/12-migration §10).
 * Unlike the twelve feature contexts it is <em>not</em> a steady-state feature: it ships <strong>
 * disabled</strong> in {@code modules.conf} and is enabled only for a cutover (§9). It runs nothing at
 * plugin enable (the importer fires only on the {@code /uxmess import} command) so this module wires no
 * listeners and acquires no runtime state in {@link #start}; the importer's bounded executor is created
 * and torn down per command invocation, not held here.
 *
 * <p>It is intentionally absent from the feature-context registry (the eleven contexts there are the
 * steady-state surface). Bootstrap consults this module's {@link #enabled(ConfigStore)} gate directly
 * when deciding whether to publish the {@code /uxmess import} subcommand, so a disabled module surfaces a
 * dormant importer and an enabled one a live command: wiring zero adapters until the command runs.
 */
@NullMarked
public final class MigrationModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("migration");

    @Override
    public ModuleId id() {
        return ID;
    }

    @Override
    public String configRoot() {
        return ID.configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        // The /uxmess import subcommand is published under the operator root in bootstrap, not as a
        // top-level feature command, so this module contributes none through the feature-command seam.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The migration_cursor table ships with the module's own Flyway location only when the module is
        // enabled for a cutover; v1 needs no extra location, so the module owns none here.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // Ships disabled: the importer is a one-shot cutover tool, not a steady-state feature (§1, §9).
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        // Nothing runs at enable: the importer is dispatched only by /uxmess import. No adapters, no
        // listeners, no background work are acquired here (docs/12-migration §10).
    }

    @Override
    public void stop() {
        // No steady-state runtime state to release; each import owns and closes its own executor.
    }
}

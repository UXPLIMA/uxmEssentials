package com.uxplima.uxmessentials.shared.application.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * Insertion-ordered {@link ModuleRegistry} backed by an explicit list.
 *
 * <p>Modules are added in dependency-first wiring order via {@link #register(FeatureModule)};
 * registration order is preserved by {@link #all()} and {@link #enabledModules(ConfigStore)}. A
 * duplicate {@link ModuleId} is rejected so the set stays unambiguous. The registry holds no Bukkit
 * or infrastructure type: it is pure application code.
 */
public final class ListModuleRegistry implements ModuleRegistry {

    private final List<FeatureModule> modules = new ArrayList<>();
    private final Map<ModuleId, FeatureModule> byId = new LinkedHashMap<>();

    @Override
    public ModuleRegistry register(FeatureModule module) {
        Objects.requireNonNull(module, "module");
        ModuleId id = Objects.requireNonNull(module.id(), "module id");
        if (byId.putIfAbsent(id, module) != null) {
            throw new IllegalArgumentException("duplicate module id: " + id);
        }
        modules.add(module);
        return this;
    }

    @Override
    public List<FeatureModule> all() {
        return List.copyOf(modules);
    }

    @Override
    public Optional<FeatureModule> byId(ModuleId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public List<FeatureModule> enabledModules(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        List<FeatureModule> enabled = new ArrayList<>();
        for (FeatureModule module : modules) {
            if (module.enabled(config)) {
                enabled.add(module);
            }
        }
        return List.copyOf(enabled);
    }
}

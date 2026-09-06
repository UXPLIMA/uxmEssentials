package com.uxplima.uxmessentials.loader;

import java.util.List;

/**
 * The runtime-dependency coordinates and Maven repositories shared by every plugin's {@code PluginLoader}
 * (the bukkit-adapter and the discord-adapter each have one). Paper's loader downloads third-party libraries
 * from Maven Central at boot, so each loader builds its own {@code MavenLibraryResolver}; the pieces that are
 * identical across loaders, the two repositories and the libraries shared between them, live here once so a
 * version pin (notably configurate) cannot drift between two hand-edited loader classes.
 *
 * <p>This is deliberately Paper-free: it holds only coordinate strings and exposes them as data, leaving each
 * loader to add the repositories and dependencies to its own resolver with the Paper/Aether types it already
 * imports. That keeps the shared pins in one file without dragging the Paper plugin-loader API into the kernel.
 */
public final class LoaderDependencies {

    /** Configurate (HOCON) version, pinned once for every loader that resolves it at boot. */
    public static final String CONFIGURATE_VERSION = "4.1.2";

    private LoaderDependencies() {}

    /**
     * The Maven repositories every loader resolves against, in order: the Central mirror Paper exposes and
     * Paper's own public repository. Each pair is {@code (id, url)}; the loader builds the Aether
     * {@code RemoteRepository} from it.
     */
    public static List<Repository> repositories(String centralMirrorUrl) {
        return List.of(
                new Repository("paper-central", centralMirrorUrl),
                new Repository("paper-public", "https://repo.papermc.io/repository/maven-public/"));
    }

    /** The configurate-hocon artifact coordinate, shared by both loaders, on the single pinned version. */
    public static String configurateHocon() {
        return "org.spongepowered:configurate-hocon:" + CONFIGURATE_VERSION;
    }

    /** A Maven repository the loader registers on its resolver: a stable {@code id} and the resolve {@code url}. */
    public record Repository(String id, String url) {}
}

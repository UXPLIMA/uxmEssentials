package com.uxplima.uxmessentials.discord;

import java.util.List;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

import com.uxplima.uxmessentials.loader.LoaderDependencies;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Runtime classpath hook required by {@code paper-plugin.yml}'s {@code loader:} directive for discord-adapter.
 * Resolves JDA and configurate-hocon dynamically at boot.
 */
@NullMarked
public final class UxmDiscordLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        for (LoaderDependencies.Repository repo :
                LoaderDependencies.repositories(MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)) {
            resolver.addRepository(new RemoteRepository.Builder(repo.id(), "default", repo.url()).build());
        }

        // Voice/opus is dead weight for a text-only bridge, drop the native codec so its
        // platform-specific binary never gets resolved onto the runtime classpath.
        Exclusion opus = new Exclusion("club.minnced", "opus-java", "*", "*");
        resolver.addDependency(
                new Dependency(new DefaultArtifact("net.dv8tion:JDA:5.6.1"), null, false, List.of(opus)));
        resolver.addDependency(new Dependency(new DefaultArtifact(LoaderDependencies.configurateHocon()), null));

        classpath.addLibrary(resolver);
    }
}

package dev.dasannn.neopets.plugin.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Adds NeoPets' database runtime libraries to its isolated plugin classpath.
 */
public final class NeoPetsLoader implements PluginLoader {

    private static final String LIBRARIES_RESOURCE = "/runtime-libraries.properties";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        var resolver = new MavenLibraryResolver();
        var libraries = runtimeLibraries();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        libraries.stringPropertyNames().stream()
                .sorted()
                .map(libraries::getProperty)
                .map(DefaultArtifact::new)
                .map(artifact -> new Dependency(artifact, null))
                .forEach(resolver::addDependency);
        classpathBuilder.addLibrary(resolver);
    }

    private static Properties runtimeLibraries() {
        var libraries = new Properties();
        try (InputStream input = NeoPetsLoader.class.getResourceAsStream(LIBRARIES_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + LIBRARIES_RESOURCE);
            }
            libraries.load(input);
            return libraries;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + LIBRARIES_RESOURCE, exception);
        }
    }
}

package dev.dasannn.neopets.storage.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ModuleDependencyTest {
    private static final String[] FORBIDDEN_PACKAGES = {
        "org.bukkit..",
        "io.papermc..",
    };

    @Test
    void storageDoesNotReferencePaperPackages() throws URISyntaxException {
        var productionClasses = new ClassFileImporter()
                .importPath(productionClassesPath());

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(FORBIDDEN_PACKAGES)
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    private static Path productionClassesPath() throws URISyntaxException {
        var testClassesPath = Path.of(ModuleDependencyTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        return testClassesPath.resolveSibling("main");
    }
}

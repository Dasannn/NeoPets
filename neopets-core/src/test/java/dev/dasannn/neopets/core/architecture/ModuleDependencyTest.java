package dev.dasannn.neopets.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ModuleDependencyTest {
    private static final String[] FORBIDDEN_PACKAGES = {
        "org.bukkit..",
        "io.papermc..",
        "net.kyori..",
        "com.zaxxer..",
        "java.sql..",
        "net.milkbowl..",
        "net.luckperms..",
        "me.clip..",
    };

    @Test
    void coreDoesNotReferencePlatformOrInfrastructurePackages() throws URISyntaxException {
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

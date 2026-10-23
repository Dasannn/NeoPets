import org.gradle.api.artifacts.ProjectDependency
import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    base
}

val allowedProjectDependencies = setOf(
    ":neopets-core" to ":neopets-api",
    ":neopets-storage" to ":neopets-core",
    ":neopets-platform-paper" to ":neopets-core",
    ":neopets-plugin" to ":neopets-platform-paper",
    ":neopets-plugin" to ":neopets-storage",
)

val verifyProjectDependencies = tasks.register("verifyProjectDependencies") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that project dependencies follow the approved module graph."

    doLast {
        val declaredProjectDependencies = subprojects
            .flatMap { sourceProject ->
                sourceProject.configurations.flatMap { configuration ->
                    configuration.dependencies
                        .withType(ProjectDependency::class.java)
                        .map { dependency -> sourceProject.path to dependency.path }
                }
            }
            .toSet()

        val forbiddenProjectDependencies = declaredProjectDependencies - allowedProjectDependencies
        if (forbiddenProjectDependencies.isNotEmpty()) {
            val details = forbiddenProjectDependencies
                .sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second }))
                .joinToString(separator = System.lineSeparator()) { (source, target) ->
                    "  $source -> $target"
                }
            throw GradleException(
                "Forbidden project dependencies found:${System.lineSeparator()}$details",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyProjectDependencies)
}

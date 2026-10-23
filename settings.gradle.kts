plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "neopets"

includeBuild("build-logic")

include(
    "neopets-api",
    "neopets-core",
    "neopets-storage",
    "neopets-platform-paper",
    "neopets-plugin",
)

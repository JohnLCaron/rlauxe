pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "rlauxe"

include("core")
include("plots")
// include("rla") // slow tests
include("cases")

// these are placed inside the jar/uberjar
project(":core").name = "rlauxe-core"
project(":cases").name = "rlauxe-cases"
project(":plots").name = "rlauxe-plots"


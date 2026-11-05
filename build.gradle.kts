plugins {
    kotlin("jvm") version "2.4.10" apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    `jacoco-report-aggregation`
}

group = "org.cryptobiotic.rlauxe"
version = libs.versions.rlauxe.get()

repositories {
    mavenCentral()
    google()
}

subprojects {
    repositories {
        mavenCentral()
        google()
    }
}


dependencies {
    // Since you are tracking "bothCoreAndCases", explicitly declare your two target modules here
    jacocoAggregation(project(":rlauxe-core"))
    jacocoAggregation(project(":rlauxe-cases"))
}

reporting {
    reports {
        // CHANGED: Use register instead of named because this custom name does not natively exist
        register<JacocoCoverageReport>("bothCoreAndCases") {
            testSuiteName.set("test")
        }
    }
}

tasks.named<JacocoReport>("bothCoreAndCases") {
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("test") })

    classDirectories.setFrom(files(classDirectories.files.map { classDir ->
        fileTree(classDir) {
            // FIXED: Added the missing "include" keyword to apply the package restriction properly
            include("org/cryptobiotic/rlauxe/**")
        }
    }))
}
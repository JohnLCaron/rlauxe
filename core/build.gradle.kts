plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    id ("java-test-fixtures")
}

group = "org.cryptobiotic.rlauxe"
version = libs.versions.rlauxe.get()

dependencies {
    implementation(files("../libs/raire-java-1.0.2.jar"))

    implementation(libs.bull.result)
    implementation(libs.commons.math)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.cli)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.oshai.logging)

    testFixturesImplementation(files("../libs/raire-java-1.0.2.jar"))
    testFixturesImplementation(libs.bundles.jvmtest)
    testFixturesImplementation(libs.kotest.property)
    testFixturesImplementation(libs.kotlinx.cli)
    testFixturesImplementation(libs.oshai.logging)
    testFixturesImplementation(libs.commons.math)

    testImplementation(libs.bundles.jvmtest)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    minHeapSize = "512m"
    maxHeapSize = "8g"
    jvmArgs = listOf("-Xss128m")

    // Make tests run in parallel
    // More info: https://www.jvt.me/posts/2021/03/11/gradle-speed-parallel/
    systemProperties["junit.jupiter.execution.parallel.enabled"] = "true"
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    systemProperties["junit.jupiter.execution.parallel.mode.classes.default"] = "concurrent"

    // kantis.github.io/posts/Faster-Kotest-startup/
    systemProperty("kotest.framework.discovery.jar.scan.disable", "true")
    systemProperty("kotest.framework.classpath.scanning.config.disable", "true")
    systemProperty("kotest.framework.classpath.scanning.autoscan.disable", "true")
}

kotlin {
    jvmToolchain(21)
}

tasks.register<Jar>("uberJar") {
    archiveClassifier = "uber"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.cryptobiotic.rlauxe.cli.verifier.RunVerifier")
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

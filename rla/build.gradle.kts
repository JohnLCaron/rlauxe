plugins {
    kotlin("jvm")
    alias(libs.plugins.serialization)
    id ("java-test-fixtures")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core"))
    implementation(files("../libs/raire-java-1.0.2-jar-with-dependencies.jar"))

    implementation(libs.bull.result)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.apache.commons:commons-csv:1.4")

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.egtest)
    testImplementation(testFixtures(project(":core")))
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
}

kotlin {
    jvmToolchain(21)
}

tasks.register<Jar>("uberJar") {
    archiveClassifier = "uber"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.cryptobiotic.eg.cli.RunShowSystem")
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
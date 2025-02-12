plugins {
    kotlin("jvm")
    alias(libs.plugins.serialization)
    id ("java-test-fixtures")
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")
}

dependencies {
    api(project(":core"))

    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lets.plot)
    implementation(libs.lets.plot.statistics)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.egtest)
    testImplementation(testFixtures(project(":core")))
}

tasks.withType<Test>().configureEach {
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
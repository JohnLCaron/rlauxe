plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    id ("java-test-fixtures")
}

group = "org.cryptobiotic.rlauxe"
version = libs.versions.rlauxe.get()

repositories {
    maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")  // why?? for raire-java ??
}

dependencies {
    api(project(":core"))
    api(project(":cases"))

    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lets.plot)
    implementation(libs.lets.plot.statistics)

    testImplementation(libs.bundles.jvmtest)
    testImplementation(libs.kotest.property)
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
plugins {
    alias(libs.plugins.kotlin.jvm)
    id ("java-test-fixtures")
}

group = "org.cryptobiotic.rlauxe"
version = libs.versions.rlauxe.get()

dependencies {
    implementation(files("../libs/raire-java-1.0.2.jar"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.commons.math)

    testFixturesImplementation(files("../libs/raire-java-1.0.2.jar"))
    testFixturesImplementation(libs.bundles.jvmtest)
    testFixturesImplementation(libs.kotest.property)

    testImplementation(libs.bundles.jvmtest)
    testImplementation(libs.kotest.property)
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
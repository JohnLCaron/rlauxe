plugins {
    kotlin("jvm") version "2.0.20"
    alias(libs.plugins.serialization)
}

group = "org.cryptobiotic.rlauxe"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
//    maven {
//        url ("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    }
}

dependencies {
    implementation(libs.bull.result)
    implementation(libs.bundles.xmlutil)
    implementation(libs.ktor.serialization.kotlinx.json.jvm )
    implementation(libs.kotlinx.cli)
    implementation(libs.bundles.logging)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
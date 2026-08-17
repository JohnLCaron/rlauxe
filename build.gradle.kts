plugins {
    kotlin("jvm") version "2.4.10" apply false
    alias(libs.plugins.kotlinx.serialization) apply false
}

group = "org.cryptobiotic.rlauxe"
version = libs.versions.rlauxe.get()

subprojects {
    repositories {
        mavenCentral()
        google()
    }
}
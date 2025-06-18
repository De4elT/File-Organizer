plugins {
    kotlin("jvm") version "1.9.23"
    application
}

repositories {
    mavenCentral()
}

val osName = System.getProperty("os.name").lowercase()
val platform = when {
    osName.contains("win") -> "win"
    osName.contains("mac") -> "mac"
    osName.contains("linux") -> "linux"
    else -> error("Unsupported OS: $osName")
}

val javafxVersion = "21.0.7"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")

}

application {
    mainClass.set("FileOrganizerAppKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--module-path", "C:/Program Files/Java/javafx-sdk-21/lib",
        "--add-modules", "javafx.controls,javafx.base,javafx.graphics"
    )
}

plugins {
    kotlin("jvm") version "1.9.23"
    application

    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

val osName = System.getProperty("os.name").lowercase()
val platform = when {
    osName.contains("win")   -> "win"
    osName.contains("mac")   -> "mac"
    osName.contains("linux") -> "linux"
    else -> error("Unsupported OS: $osName")
}

val javafxVersion = "21.0.7"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")


    listOf("base", "graphics", "controls").forEach { mod ->
        implementation("org.openjfx:javafx-$mod:$javafxVersion:$platform")
    }
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
    jvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.shadowJar {
    archiveBaseName.set("FileOrganizer")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest { attributes["Main-Class"] = "FileOrganizerAppKt" }
}

tasks.register("fatJar") {
    dependsOn(tasks.shadowJar)
    group = "build"
    description = "Zbuduj pojedyńczy JAR z wbudowanymi zależnościami"
}

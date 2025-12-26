plugins {
    java
    id("io.freefair.lombok") version "8.10"
}

group = "de.felix.lumelobby"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly(project(":LumeCommands"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(
            "version" to project.version.toString(),
            "name" to project.name,
        )
    }
}

val serverDir = providers.gradleProperty("serverDir")
    .orElse(providers.environmentVariable("ISKYWARS_SERVER_DIR"))
    .orElse(rootProject.layout.projectDirectory.dir("ISkyWarsServer").asFile.absolutePath)

val pluginsDir = serverDir.map { file("$it/plugins") }
val jarTask = tasks.named<Jar>("jar")

tasks.register<Copy>("deployToServer") {
    group = "distribution"
    description = "Builds the plugin jar and copies it into the Paper server plugins/ directory."

    dependsOn(jarTask)
    from(jarTask.flatMap { it.archiveFile })
    into(pluginsDir)

    doFirst {
        val dir = pluginsDir.get()
        require(dir.isDirectory) {
            "Plugins directory not found: ${dir.absolutePath} (set -PserverDir=... or ISKYWARS_SERVER_DIR)"
        }
    }
}

plugins {
    kotlin("jvm") version "2.5.0-Beta1"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.viaversion.com")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.viaversion:viaversion-api:5.12.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation(kotlin("test"))
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

kotlin {
    // Tests live in the git-ignored .dev folder and only run on the developer's machine.
    sourceSets.named("test") { kotlin.srcDir(".dev/test/kotlin") }
    // Java 21 bytecode runs on every server from 1.21.4 (Java 21) up to 26.x (Java 25).
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    // The shaded jar (with the Kotlin runtime) is the one to deploy, so give it the plain name.
    shadowJar {
        archiveClassifier.set("")
    }

    jar {
        archiveClassifier.set("plain")
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.4")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.property("version", version.toString())
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

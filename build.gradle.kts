import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("application")
}

val kotlinCoroutinesVersion = "1.8.1"
val tornadoFxVersion = "1.7.20"
val log4jVersion = "2.23.1"
val slf4jVersion = "2.0.13"
val kotlinLoggingVersion = "3.0.5"
val testFxVersion = "4.0.18"
val junitVersion = "5.10.2"
val vlcjVersion = "4.8.3"
val humbleVersion = "0.3.0"
val flywayVersion = "10.14.0"
val controlsFxVersion = "11.2.1"

val defaultAppJvmArgs = listOf(
    // Tornadofx
    "--add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED",
    "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED",
    "--add-opens=javafx.controls/javafx.scene.control=ALL-UNNAMED",
    // necessary for ControlsFX
    "--add-opens=javafx.base/com.sun.javafx.event=ALL-UNNAMED",
    "--add-opens=javafx.base/com.sun.javafx.collections=ALL-UNNAMED",
    "--add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED",
    "--add-opens=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED",
    "--add-opens=javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED",
    "--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
    "--enable-native-access=ALL-UNNAMED",
    "--enable-native-access=javafx.graphics",
    "--enable-preview"
)

version = "1.0.0"

val appVersion: String = version as String

allprojects {
    apply(plugin = "kotlin")

    repositories {
        mavenCentral()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
    }

    dependencies {
        implementation(platform(kotlin("bom")))
        implementation(kotlin("stdlib"))

        implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
        implementation("org.slf4j:slf4j-api:$slf4jVersion")
        implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
        implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
        implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(27))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }
    }

    // NEU: sorgt dafür, dass compileJava dieselbe Toolchain wie compileKotlin nutzt
    plugins.withType<JavaBasePlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(27))
                vendor.set(JvmVendorSpec.ADOPTIUM)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs = defaultAppJvmArgs
}

dependencies {
    implementation(project(":api-client"))

    implementation("no.tornado:tornadofx:$tornadoFxVersion")
    implementation("org.controlsfx:controlsfx:$controlsFxVersion")
    implementation("no.tornado:tornadofx-controlsfx:0.1.1")

    implementation("org.pdfsam.rxjava3:rxjavafx:3.0.3")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("de.jangassen:nsmenufx:3.1.0")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.github.davidmoten:rxjava3-jdbc:0.1.4") {
        exclude("com.google.code.findbugs", "jsr305")
        exclude("com.google.code.findbugs", "annotations")
        exclude("net.jcip", "jcip-annotations")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:$kotlinCoroutinesVersion")

    val os = OperatingSystem.current()
    implementation("io.humble:humble-video-noarch:$humbleVersion")
    if (os.isMacOsX) {
        implementation("io.humble:humble-video-arch-x86_64-apple-darwin18:$humbleVersion")
    } else if (os.isWindows) {
        implementation("io.humble:humble-video-arch-x86_64-w64-mingw32:$humbleVersion")
        implementation("io.humble:humble-video-arch-i686-w64-mingw32:$humbleVersion")
    } else if (os.isLinux) {
        implementation("io.humble:humble-video-arch-i686-pc-linux-gnu6:$humbleVersion")
        implementation("io.humble:humble-video-arch-x86_64-pc-linux-gnu6:$humbleVersion")
    }
    implementation("uk.co.caprica:vlcj:$vlcjVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.testfx:testfx-core:$testFxVersion")
    testImplementation("org.testfx:testfx-junit5:$testFxVersion")
    testImplementation("org.hamcrest:hamcrest:2.2")
}

configurations {
    all {
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "org.openjfx", module = "javafx-web")
        exclude(group = "org.openjfx", module = "javafx-swing")
        exclude(group = "org.openjfx", module = "javafx-fxml")
    }
}

javafx {
    version = "26.0.2"
    modules = mutableListOf("javafx.base", "javafx.graphics", "javafx.controls", "javafx.media")
}

application {
    mainClass.set("online.hudacek.fxradio.FxRadioKt")
    applicationDefaultJvmArgs = defaultAppJvmArgs
}

// --- jpackage-based macOS packaging (replaces javapackager/launch4j) ---

val jpackageInputDir = layout.buildDirectory.dir("jpackage/input")
val jpackageLibDir = layout.buildDirectory.dir("jpackage/input/lib")
val jpackageOutputDir = layout.buildDirectory.dir("jpackage/output")

// Make the jar runnable standalone by pointing its manifest Class-Path at lib/*.jar
tasks.jar {
    manifest {
        attributes["Class-Path"] = configurations.runtimeClasspath.get().files
            .joinToString(" ") { "lib/${it.name}" }
        attributes["Main-Class"] = "online.hudacek.fxradio.FxRadioKt"
    }
}

val copyDependencies by tasks.registering(Copy::class) {
    from(configurations.runtimeClasspath)
    into(jpackageLibDir)
}

val copyMainJar by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(jpackageInputDir)
    rename { "FxRadio.jar" }
}

tasks.register<Exec>("jpackageMac") {
    group = "distribution"
    description = "Packages the app as a macOS .app/.dmg using the JDK's built-in jpackage tool"

    dependsOn(copyDependencies, copyMainJar)

    //val javaHome = System.getProperty("java.home")
    val javaHome = "/Library/Java/JavaVirtualMachines/temurin-27.jdk/Contents/Home"
    val jpackageBin = "$javaHome/bin/jpackage"

    doFirst {
        jpackageOutputDir.get().asFile.mkdirs()
    }

    commandLine(
        listOf(
            jpackageBin,
            "--type", "dmg",
            "--input", jpackageInputDir.get().asFile.absolutePath,
            "--main-jar", "FxRadio.jar",
            "--main-class", "online.hudacek.fxradio.FxRadioKt",
            "--name", "FXRadio",
            "--app-version", appVersion,
            "--description", "Internet Radio Directory",
            "--vendor", "FXRadio",
            "--dest", jpackageOutputDir.get().asFile.absolutePath,
            "--icon", "src/main/deploy/package/mac/FxRadio.icns"
        ) + defaultAppJvmArgs.flatMap { listOf("--java-options", it) }
    )
}
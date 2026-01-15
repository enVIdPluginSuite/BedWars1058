// BedWars1058 - Gradle build (replaces the former Maven build, same modules and artifacts).
//
// Requirements: JDK 21 or newer to run Gradle. Each module compiles for the Java release it used with Maven
// (see `javaRelease` in the module scripts).
//
// NMS modules need the Spigot server artifacts (org.spigotmc:spigot:<version>-R0.1-SNAPSHOT) from the CodeMC "nms"
// repository, or from `mavenLocal()` after running Spigot BuildTools with `--remapped` where needed.

plugins {
    // Kotlin is applied by the modules that contain Kotlin code (bedwars-plugin and the 1.21+/26.x version support).
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    group = "com.andrei1058.bedwars"
    version = providers.gradleProperty("version").get()

    repositories {
        mavenLocal()
        mavenCentral()
        // Repositories that only serve their own groups are filtered, so a slow or down repository can not
        // break the resolution of unrelated dependencies.
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            content { includeGroup("org.spigotmc"); includeGroup("net.md-5"); includeGroup("org.bukkit") }
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            content { includeGroup("net.md-5") }
        }
        maven("https://repo.codemc.io/repository/nms/") {
            content { includeGroup("org.spigotmc") }
        }
        maven("https://repo.codemc.io/repository/maven-public/") {
            content { includeGroup("net.citizensnpcs"); includeGroup("org.bstats") }
        }
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.andrei1058.com/releases/") {
            content { includeGroupByRegex("com\\.andrei1058.*") }
        }
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
            content { includeGroup("me.clip") }
        }
        maven("https://simonsator.de/repo/") {
            content { includeGroup("de.simonsator") }
        }
        maven("https://maven.citizensnpcs.co/repo") {
            content { includeGroup("net.citizensnpcs") }
        }
        maven("https://repo.alessiodp.com/releases/") {
            content { includeGroup("com.alessiodp.parties") }
        }
        maven("https://repo.glaremasters.me/repository/concuncan/") {
            content { includeGroup("com.grinderwolf") }
        }
        maven("https://repo.rapture.pw/repository/maven-snapshots/") {
            content { includeGroup("com.grinderwolf") }
        }
        maven("https://repo.infernalsuite.com/repository/maven-snapshots/") {
            content { includeGroup("com.infernalsuite.aswm") }
        }
        maven("https://repo.fusesource.com/nexus/content/repositories/releases-3rd-party/") {
            content { includeGroup("com.google.common") }
        }
    }
}

subprojects {
    apply(plugin = "java-library")

    // modules override this in their own build script
    extra["javaRelease"] = 11

    dependencies {
        "compileOnly"("net.md-5:bungeecord-chat:1.8-SNAPSHOT")
        "compileOnly"("commons-io:commons-io:2.13.0")
        "compileOnly"("org.jetbrains:annotations:24.0.1")
        "compileOnly"("org.projectlombok:lombok:1.18.36")
        "annotationProcessor"("org.projectlombok:lombok:1.18.36")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(provider { project.extra["javaRelease"] as Int })
    }

    tasks.withType<Javadoc>().configureEach { enabled = false }

    // Modules with Kotlin code compile it for the same Java release as their Java code.
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        afterEvaluate {
            val release = project.extra["javaRelease"] as Int
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.toVersion(release)
                targetCompatibility = JavaVersion.toVersion(release)
            }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(release.toString()))
            }
        }
    }
}

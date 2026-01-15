plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.5"
}

// The core is mostly Java; Kotlin is used for newer code (see src/main/kotlin).

dependencies {
    // ---- bundled into the final jar (relocated where needed, see shadowJar) ----
    implementation(project(":bedwars-api"))
    implementation(project(":versionsupport_common"))
    implementation("io.papermc:paperlib:1.0.8")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("commons-io:commons-io:2.13.0")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("com.andrei1058.vipfeatures:vipfeatures-api:1.+")

    // version support modules are loaded by name at runtime, they are not needed to compile the core
    runtimeOnly(project(":versionsupport_1_8_R3"))
    runtimeOnly(project(":versionsupport_1_12_R1"))
    runtimeOnly(project(":versionsupport_v1_16_R3"))
    runtimeOnly(project(":versionsupport_v1_17_R1"))
    runtimeOnly(project(":versionsupport_v1_18_R2"))
    runtimeOnly(project(":versionsupport_v1_19_R2"))
    runtimeOnly(project(":versionsupport_v1_19_R3"))
    runtimeOnly(project(":versionsupport_v1_20_R1"))
    runtimeOnly(project(":versionsupport_v1_20_R2"))
    runtimeOnly(project(":versionsupport_v1_20_R3"))
    runtimeOnly(project(":versionsupport_v1_20_R4"))
    // 1.21.x and 26.x, every revision extends the previous one
    runtimeOnly(project(":versionsupport_v1_21_R1"))
    runtimeOnly(project(":versionsupport_v1_21_R2"))
    runtimeOnly(project(":versionsupport_v1_21_R3"))
    runtimeOnly(project(":versionsupport_v1_21_R4"))
    runtimeOnly(project(":versionsupport_v1_21_R5"))
    runtimeOnly(project(":versionsupport_v1_21_R6"))
    runtimeOnly(project(":versionsupport_v1_21_R7"))
    runtimeOnly(project(":versionsupport_v26_R1"))
    runtimeOnly(project(":resetadapter_slime"))
    runtimeOnly(project(":resetadapter_slimepaper"))
    runtimeOnly(project(":resetadapter_aswm"))

    // sidebar library, one provider per supported server revision
    implementation("com.andrei1058.spigot.sidebar:sidebar-base:24.2")
    for (v in listOf("v1_8_R3", "v1_12_R1", "v1_16_R3", "v1_17_R1", "v1_18_R2", "v1_19_R2", "v1_19_R3", "v1_20_R1", "v1_20_R2", "v1_20_R3")) {
        runtimeOnly("com.andrei1058.spigot.sidebar:sidebar-$v:24.2")
    }
    runtimeOnly("com.andrei1058.spigot.sidebar:sidebar-v1_20_R4:23.12")

    // ---- provided by the server or by other plugins ----
    compileOnly("org.spigotmc:spigot:1.8.8-R0.1-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-chat:1.18-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("de.simonsator:Party-and-Friends-MySQL-Edition-Spigot-API:1.5.4-RELEASE")
    compileOnly("de.simonsator:Spigot-Party-API-For-RedisBungee:1.0.3-SNAPSHOT")
    compileOnly("de.simonsator:DevelopmentPAFSpigot:1.0.239")
    compileOnly("com.alessiodp.parties:parties-api:3.2.9") {
        isTransitive = false // its transitive libby dependency is only needed at runtime inside Parties
    }
    compileOnly("net.citizensnpcs:citizens-main:2.0.30-SNAPSHOT") {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.bstats", module = "bstats-bukkit")
        exclude(group = "net.byteflux", module = "libby-bukkit") // runtime-only for Citizens itself
    }
    compileOnly("net.milkbowl.vault:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

tasks.processResources {
    include("plugin.yml")
    filesMatching("plugin.yml") {
        filter { it.replace("\${project.version}", project.version.toString()) }
    }
}

tasks.shadowJar {
    archiveBaseName.set("bedwars-plugin")
    archiveClassifier.set("")
    relocate("org.bstats", "com.andrei1058.bedwars.libs.bstats")
    relocate("com.zaxxer.hikari", "com.andrei1058.bedwars.libs.hikari")
    relocate("org.slf4j", "com.andrei1058.bedwars.libs.slf4j")
    relocate("com.andrei1058.spigot.sidebar", "com.andrei1058.bedwars.libs.sidebar")
    relocate("com.andrei1058.spigot.sidebarutils", "com.andrei1058.bedwars.libs.updater")
    // the Kotlin runtime is bundled, relocated so it can not clash with other plugins shipping their own
    relocate("kotlin", "com.andrei1058.bedwars.libs.kotlin")
    exclude("org/jetbrains/annotations/**", "DebugProbesKt.bin", "META-INF/*.kotlin_module", "META-INF/versions/**")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.jar { enabled = false }
tasks.assemble { dependsOn(tasks.shadowJar) }

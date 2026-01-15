plugins {
    kotlin("jvm")
}

// Version support for Minecraft 1.18.2 and newer, API only (no NMS/CraftBukkit). The newer revisions extend this one.
extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
}

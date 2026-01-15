plugins {
    kotlin("jvm")
}

// Version support for Minecraft 1.21.x and 26.x. API only, no NMS/CraftBukkit.
// Extends the 1.20.4 module and only overrides what the 1.21 API does differently.
extra["javaRelease"] = 21

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnlyApi(project(":versionsupport_v1_20_R4"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

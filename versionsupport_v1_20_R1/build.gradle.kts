plugins {
    kotlin("jvm")
}

// Version specific fixes for this server revision; everything else is inherited from v1_19_R3.
extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnlyApi(project(":versionsupport_v1_19_R3"))
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
}

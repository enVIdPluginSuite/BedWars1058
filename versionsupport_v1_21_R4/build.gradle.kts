plugins {
    kotlin("jvm")
}

extra["javaRelease"] = 21

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnlyApi(project(":versionsupport_v1_21_R3"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

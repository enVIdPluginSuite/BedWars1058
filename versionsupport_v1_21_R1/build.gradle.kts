// Version support for Minecraft 1.21.x and 26.x. API only, no NMS/CraftBukkit.
extra["javaRelease"] = 21

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

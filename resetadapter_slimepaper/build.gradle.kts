extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.infernalsuite.aswm:api:1.20-R0.1-SNAPSHOT")
    api("com.flowpowered:flow-nbt:2.0.0")
}

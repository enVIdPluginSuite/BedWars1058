extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnly("org.spigotmc:spigot:1.19.3-R0.1-SNAPSHOT")
}

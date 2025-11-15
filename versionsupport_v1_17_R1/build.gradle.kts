extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnly("org.spigotmc:spigot:1.17.1-R0.1-SNAPSHOT")
}

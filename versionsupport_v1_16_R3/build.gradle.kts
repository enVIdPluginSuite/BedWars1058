extra["javaRelease"] = 11

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnly("org.spigotmc:spigot:1.16.5-R0.1-SNAPSHOT")
}

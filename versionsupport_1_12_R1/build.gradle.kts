extra["javaRelease"] = 11

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnly("org.spigotmc:spigot:1.12.2-R0.1-SNAPSHOT")
}

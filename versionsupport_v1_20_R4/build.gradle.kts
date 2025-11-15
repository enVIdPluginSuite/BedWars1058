extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly(project(":versionsupport_common"))
    compileOnly("org.spigotmc:spigot:1.20.4-R0.1-SNAPSHOT")
    compileOnlyApi(project(":versionsupport_v1_20_R3"))
}

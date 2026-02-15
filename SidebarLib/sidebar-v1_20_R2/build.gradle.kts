// Sidebar provider using the server internals of one Minecraft version.
extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":sidebar-base"))
    compileOnly("org.spigotmc:spigot:1.20.2-R0.1-SNAPSHOT")
}

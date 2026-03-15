// Sidebar provider using the server internals of one Minecraft version.
extra["javaRelease"] = 11

dependencies {
    compileOnly(project(":sidebar-base"))
    compileOnly("org.spigotmc:spigot:1.8.8-R0.1-SNAPSHOT")
}

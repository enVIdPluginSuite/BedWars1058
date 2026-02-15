// Sidebar provider using the server internals of one Minecraft version.
extra["javaRelease"] = 21

dependencies {
    compileOnly(project(":sidebar-base"))
    compileOnly("org.spigotmc:spigot:1.21.3-R0.1-SNAPSHOT")
    api(project(":sidebar-cmn1"))
}

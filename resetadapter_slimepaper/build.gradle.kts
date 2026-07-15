extra["javaRelease"] = 17

dependencies {
    compileOnly(project(":bedwars-api"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.infernalsuite.aswm:api:1.20-R0.1-SNAPSHOT")
    api("com.flowpowered:flow-nbt:2.0.0")
}

// Maven resolved flow-nbt to the version declared directly above ("nearest wins"). Gradle would pick the newer
// 2.0.2 requested by the ASWM API instead, which is not published on any public repository.
configurations.configureEach {
    resolutionStrategy.force("com.flowpowered:flow-nbt:2.0.0")
}

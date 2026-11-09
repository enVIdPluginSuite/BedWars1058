// Compile-time stubs for the VipFeatures plugin API. Never bundled: the real classes come from the VipFeatures plugin,
// which BedWars1058 only talks to when that plugin is installed (softdepend). Keeps the build independent of its repository.
dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

plugins {
    `maven-publish`
}

dependencies {
    // Maven: provided
    compileOnly("com.google.common:google-collect:1.0")
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    // Maven declared this with compile scope; it is only needed to compile, so it is not exposed to consumers.
    compileOnly("org.spigotmc:spigot:1.16.5-R0.1-SNAPSHOT")

    // exposed to dependants because the sidebar types are part of the API
    api(project(":sidebar-base"))
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    enabled = true
    isFailOnError = false
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "bedwars-api"
            from(components["java"])
        }
    }
    repositories {
        maven {
            val snapshot = project.version.toString().endsWith("-SNAPSHOT")
            url = uri(if (snapshot) "https://repo.andrei1058.com/snapshots" else "https://repo.andrei1058.com/releases")
            credentials {
                username = "__token__"
                password = System.getenv("MVN_REPO_TOKEN")
            }
        }
    }
}

plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":conflux-platform-paper"))
}

tasks.jar { archiveClassifier = "thin" }
tasks.shadowJar {
    archiveBaseName = "Conflux"
    archiveClassifier = ""
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifact(tasks.shadowJar)
            artifactId = "conflux"
            pom {
                name = "Conflux"
                description = project.description.toString()
                url = "https://github.com/IanTapply22/Conflux"
            }
        }
    }
}

tasks.runServer {
    minecraftVersion("26.2")
    pluginJars.from(tasks.shadowJar.flatMap { it.archiveFile })
    pluginJars.from(rootProject.file("../Relay/build/libs/Relay-1.0.0.jar"))
    jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}

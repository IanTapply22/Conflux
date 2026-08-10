plugins {
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("confluxApi") {
            from(components["java"])
            artifactId = "conflux-api"
            pom {
                name = "Conflux API"
                description = "Player ghost synchronization wire contracts for Conflux"
                url = "https://github.com/IanTapply22/Conflux"
            }
        }
    }
}

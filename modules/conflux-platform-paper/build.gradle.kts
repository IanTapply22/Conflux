plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}

dependencies {
    implementation(project(":conflux-api"))
    compileOnly("com.iantapply:relay-api:1.0.0")
    paperweight.paperDevBundle("26.2.build.111-stable")
}

tasks.processResources {
    inputs.property("version", project.version)
    expand("version" to project.version)
}

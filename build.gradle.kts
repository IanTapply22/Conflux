import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.signing.SigningExtension

plugins {
    base
    id("com.diffplug.spotless") version "8.9.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("org.cyclonedx.bom") version "3.4.0"
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

allprojects {
    group = providers.gradleProperty("group").get()
    version =
        providers
            .environmentVariable("CONFLUX_VERSION")
            .orElse(providers.gradleProperty("version"))
            .map { it.removePrefix("v") }
            .get()
    description = providers.gradleProperty("description").get()
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.pkg.github.com/IanTapply22/Relay") {
            name = "RelayGitHubPackages"
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
            content { includeGroup("com.iantapply") }
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
        withJavadocJar()
    }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    pluginManager.withPlugin("maven-publish") {
        apply(plugin = "signing")
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    scm {
                        connection = "scm:git:https://github.com/IanTapply22/Conflux.git"
                        developerConnection = "scm:git:ssh://git@github.com/IanTapply22/Conflux.git"
                        url = "https://github.com/IanTapply22/Conflux"
                    }
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    val repository =
                        providers
                            .environmentVariable("GITHUB_REPOSITORY")
                            .orElse("IanTapply22/Conflux")
                            .get()
                            .lowercase()
                    url = uri("https://maven.pkg.github.com/$repository")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                        password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    }
                }
            }
        }
        extensions.configure<SigningExtension> {
            val signingKey = providers.environmentVariable("SIGNING_KEY")
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
            setRequired(signingKey.isPresent)
            if (signingKey.isPresent) {
                useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
            }
            sign(project.extensions.getByType<PublishingExtension>().publications)
        }
    }
}

spotless {
    isEnforceCheck = false
    java {
        target(subprojects.map { module -> module.fileTree("src") { include("**/*.java") } })
        palantirJavaFormat()
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target(files(file("build.gradle.kts"), subprojects.map { it.file("build.gradle.kts") }))
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("projectFiles") {
        target(
            files(
                fileTree(projectDir) {
                    include("*.md", "*.properties", ".gitattributes", ".gitignore")
                },
                subprojects.map { module ->
                    module.fileTree("src/main/resources") { include("**/*.yml", "**/*.json") }
                },
            ),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("lint") { dependsOn(tasks.named("spotlessCheck")) }
tasks.register("lintFix") { dependsOn(tasks.named("spotlessApply")) }
tasks.named("check") { dependsOn(subprojects.map { it.tasks.named("check") }) }
tasks.named("assemble") { dependsOn(":conflux-distribution:assemble") }
tasks.register("test") { dependsOn(subprojects.map { it.tasks.named("test") }) }
tasks.register("jar") { dependsOn(":conflux-distribution:shadowJar") }
tasks.register("runServer") { dependsOn(":conflux-distribution:runServer") }
tasks.register("publish") {
    group = "publishing"
    description = "Publishes the Conflux API and distribution to GitHub Packages."
    dependsOn(":conflux-api:publish", ":conflux-distribution:publish")
}
tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes the Conflux API and distribution to the local Maven repository."
    dependsOn(":conflux-api:publishToMavenLocal", ":conflux-distribution:publishToMavenLocal")
}

val documentedProjects = subprojects.filter { it.name != "conflux-distribution" }

tasks.register<Sync>("javadoc") {
    group = "documentation"
    description = "Aggregates Javadocs from every documented Conflux module."
    dependsOn(documentedProjects.map { it.tasks.named("javadoc") })
    into(layout.buildDirectory.dir("docs/javadoc"))
    from("docs/javadoc-index.html") { rename { "index.html" } }
    documentedProjects.forEach { module ->
        from(module.layout.buildDirectory.dir("docs/javadoc")) { into(module.name) }
    }
}

tasks.register<Exec>("installGitHooks") {
    group = "build setup"
    description = "Configures this Git checkout to use the tracked hooks in .githooks."
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        commandLine("git", "config", "core.hooksPath", ".githooks")
    } else {
        commandLine("sh", "-c", "chmod +x .githooks/pre-commit && git config core.hooksPath .githooks")
    }
}

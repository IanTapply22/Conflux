import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    base
    id("com.diffplug.spotless") version "8.9.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
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
}

spotless {
    isEnforceCheck = false
    java {
        target("modules/**/src/**/*.java")
        palantirJavaFormat()
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "modules/**/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("projectFiles") {
        target("*.md", "*.properties", ".gitattributes", ".gitignore", "modules/**/*.yml", "modules/**/*.json")
        targetExclude("**/build/**")
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

tasks.register<Javadoc>("aggregateJavadoc") {
    group = "documentation"
    description = "Generates combined Javadocs for the Conflux API and Paper platform modules."

    val documentedProjects = subprojects.filter { it.name != "conflux-distribution" }
    val mainSourceSets =
        documentedProjects.map {
            it.extensions
                .getByType<SourceSetContainer>()
                .named("main")
                .get()
        }

    dependsOn(documentedProjects.map { it.tasks.named("classes") })
    source(mainSourceSets.map { it.allJava })
    classpath = files(mainSourceSets.map { it.compileClasspath + it.output })
    javadocTool =
        documentedProjects.first().extensions.getByType<JavaToolchainService>().javadocToolFor {
            languageVersion = JavaLanguageVersion.of(25)
        }
    destinationDir =
        layout.buildDirectory
            .dir("docs/javadoc")
            .get()
            .asFile
}

rootProject.name = "Conflux"

include(
    "conflux-api",
    "conflux-platform-paper",
    "conflux-distribution",
)

listOf(
    "conflux-api",
    "conflux-platform-paper",
    "conflux-distribution",
).forEach { name -> project(":$name").projectDir = file("modules/$name") }

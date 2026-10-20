plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
}

val libraryBomBuild = layout.projectDirectory.file("../../versions/library-bom/build.gradle.kts")

tasks.test {
    useJUnitPlatform()
    inputs.file(libraryBomBuild).withPropertyName("productionLibraryBomBuild")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("sphereon.test.libraryBomBuild", libraryBomBuild.asFile.absolutePath)
}

gradlePlugin {
    plugins {
        create("tomlCatalog") {
            id = "com.sphereon.gradle.toml-catalog"
            implementationClass = "com.sphereon.gradle.buildsupport.TomlCatalogPlugin"
        }
    }
}

publishing {
    repositories {
        val worktreeMavenRepo = System.getenv("WORKTREE_MAVEN_REPO")?.trim()?.takeIf { it.isNotEmpty() }
        if (worktreeMavenRepo != null) {
            maven {
                name = "worktree"
                url = uri(worktreeMavenRepo)
            }
        }
        maven {
            name = "sphereon-opensource"
            val snapshotsUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/"
            val releasesUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-releases/"
            url = uri(if (version.toString().contains("SNAPSHOT")) snapshotsUrl else releasesUrl)
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}

# Gradle Build Support

This project provides a set of Gradle plugins, BOMs (Bill of Materials), and TOML version catalogs to help standardize and simplify Gradle builds for Kotlin projects.
It is used internally in Sphereon to ensure a consistent build environment and versioning for all our software. Although targeting Sphereon, we encourage others to
re-(use) part of our build support.

## Components

### Plugins

- **ConventionsPlugin** (`com.sphereon.gradle.plugin.conventions`): Applies standard conventions for Kotlin Multiplatform projects.
- **IntegrationTestPlugin** (`com.sphereon.gradle.plugin.integration-tests`): Adds support for integration tests.
- **ProjectPublicationPlugin** (`com.sphereon.gradle.plugin.project-publication`): Configures Maven publication with consistent POM metadata and signing.
- **TomlCatalogPlugin** (`com.sphereon.gradle.toml-catalog`): Generates TOML version catalogs from BOMs.

### BOMs (Bill of Materials)

- **common-bom**: Core dependencies for Kotlin projects (Kotlin, KotlinX, testing libraries).
- **library-bom**: Common libraries for Kotlin projects (cryptography, serialization, etc.).
- **gradle-plugin-bom**: Gradle plugins for Kotlin projects (Kotlin, Android, KSP, etc.).

### TOML Version Catalogs

For each BOM, two TOML version catalogs are published:

1. **Versioned Catalog**: Contains both the dependencies and their versions.
2. **BOM Catalog**: Contains only the dependencies, to be used with the BOM.

## Using in Your Project

### Setting Up the Repository

Add the repository to your `settings.gradle.kts` file:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        // Add other repositories as needed
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Add other repositories as needed
    }
}
```

### Using the Plugins

Add the plugin to your `build.gradle.kts` file:

```kotlin
plugins {
    id("com.sphereon.gradle.plugin.conventions") version "0.0.3"
    // Add other plugins as needed
}
```

### Using the BOMs

Add the BOM to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(platform("com.sphereon.gradle:common-bom:0.0.3"))
    implementation(platform("com.sphereon.gradle:library-bom:0.0.3"))
    // Add other dependencies as needed
}
```

### Using the TOML Version Catalogs

Add the TOML version catalog to your `settings.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from("com.sphereon.gradle:common-bom-versioned-catalog:0.0.3")
        }
    }
}
```

Or, if you're using the BOM:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from("com.sphereon.gradle:common-bom-bom-catalog:0.0.3")
        }
    }
}
```

Then, in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(platform("com.sphereon.gradle:common-bom:0.0.3"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    // Add other dependencies as needed
}
```

## Publishing

To publish the artifacts to Maven Central:

1. Set up your Sonatype credentials in `~/.gradle/gradle.properties`:

```properties
ossrhUsername=your-username
ossrhPassword=your-password
signing.gnupg.keyName=your-key-id
```

2. Run the publish task:

```bash
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

For snapshots:

```bash
./gradlew publishToSonatype
```

## License

This project is licensed under the Apache 2.0 License - see the LICENSE file for details.
# Multiplatform Test Conventions Plugin

This Gradle plugin automatically configures **Integration** and **E2E** test support for Kotlin Multiplatform projects across JVM and JS targets.

---

## Usage

### 1. Apply the plugin

In your module's `build.gradle.kts`:

```kotlin
plugins {
    id("com.sphereon.gradle.conventions.multiplatform-test-conventions")
}
```

---

### 2. Define test compilations

Inside your `kotlin {}` block:

```kotlin
kotlin {
    jvm {
        createIntegrationTest()
        createE2eTest()
    }
    js(IR) {
        createIntegrationTest()
    }
}
```

- `createIntegrationTest()` sets up an `IntegrationTest` compilation associated with the normal `test`.
- `createE2eTest()` sets up an `E2eTest` compilation similarly.

---

### 3. Source folder structure

The plugin automatically creates missing source directories based on detected compilations:

| Target | Compilation       | Folder                          |
|:-------|:-------------------|:--------------------------------|
| JVM    | IntegrationTest     | `src/jvmIntegrationTest/kotlin` |
| JVM    | E2eTest             | `src/jvmE2eTest/kotlin`          |
| JS     | IntegrationTest     | `src/jsIntegrationTest/kotlin`  |
| JS     | E2eTest             | `src/jsE2eTest/kotlin`           |

- Only folders for active compilations are created.

---

### 4. Running the tests

Run all integration tests:

```bash
./gradlew integrationTest
```

Run all e2e tests:

```bash
./gradlew e2eTest
```

Run individual platform tests:

```bash
./gradlew jvmIntegrationTest
./gradlew jsIntegrationTest
./gradlew jvmE2eTest
```

- Tasks are only registered if compilations exist.
- Tasks are skipped automatically if no compiled tests exist.

---

## Behavior

- Fully Configuration Cache supported (Gradle 8+).
- No manual task wiring needed.
- Works dynamically per platform and compilation.
- Skips unnecessary source folder creation.
- Clean summary output (or skip message) after project evaluation.

---

# Troubleshooting

### No integration or e2e test tasks showing up?

- Ensure you called `createIntegrationTest()` or `createE2eTest()` inside your `kotlin {}` block.
- Ensure corresponding source folders exist (e.g., `src/jvmIntegrationTest/kotlin`).
- Ensure at least one test file exists inside the folders.
- For JS, ensure you use the IR backend (`js(IR) {}`).

---

### Task registered but no tests run?

- Happens when there are no compiled test classes.
- Ensure you have actual `.kt` test files inside the correct `src/...` folders.

---

### Configuration Cache issues?

- This plugin is fully Configuration Cache safe.
- If issues occur, check if other plugins or custom logic mutate targets outside the configuration phase.

---

# Example full build.gradle.kts

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.sphereon.gradle.conventions.tests")
}

kotlin {
    jvm {
        createIntegrationTest()
        createE2eTest()
    }
    js(IR) {
        browser()
        nodejs()
        createIntegrationTest()
    }
}

dependencies {
    "jvmIntegrationTestImplementation"(kotlin("test-junit"))
    "jvmE2eTestImplementation"(kotlin("test-junit"))
    "jsIntegrationTestImplementation"(kotlin("test-js"))
}
```

---

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kover)
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Kover configuration for code coverage
koverReport {
    filters {
        excludes {
            classes(
                "*Generated*",
                "*_Factory",
                "*Composable*",
                "*.BuildConfig",
                "*.Manifest",
                "*ComposableSingletons*"
            )
            packages("*.generated.*")
        }
    }

    defaults {
        html {
            onCheck = true
            htmlDir = layout.buildDirectory.dir("reports/kover/html")
        }
        xml {
            onCheck = true
            xmlFile = layout.buildDirectory.file("reports/kover/coverage.xml")
        }
    }

    verify {
        rule {
            isEnabled = true
            bound {
                minValue = 70 // Minimum 70% coverage
            }
        }
    }
}

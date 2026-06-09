import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose.compiler)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// Derive a monotonic versionCode from the git commit count so each build supersedes
// the last. This restores Android's downgrade protection: an older (e.g. backup-restored)
// APK has fewer commits -> a lower versionCode -> the OS refuses to silently reinstall it
// over a newer build. Falls back to 1 when git history is unavailable (source archive / CI
// shallow clone).
//
// Uses Gradle's providers.exec API so the git output is tracked as a build input and the
// Configuration Cache is correctly invalidated when the commit count changes.
val computedVersionCode = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.get()
} catch (e: Exception) {
    1
}

android {
    namespace = "com.wellnesswingman"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wellnesswingman"
        minSdk = 26
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Polar OAuth config — read from local.properties
        buildConfigField(
            "String",
            "POLAR_CLIENT_ID",
            "\"${localProps.getProperty("polar.client.id", "")}\""
        )
        buildConfigField(
            "String",
            "POLAR_BROKER_BASE_URL",
            "\"${localProps.getProperty("polar.broker.base.url", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":composeApp"))

    // Android
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // Logging
    implementation(libs.napier)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

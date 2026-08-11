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

// Application version: MAJOR.MINOR.BUILD.
//
// Bump these deliberately when releasing. Nothing derives them from git, which is the point:
// a version must describe the software, not which branch happens to be checked out. A commit
// count only rises along one line of history, so it falls when you switch to an older branch
// or after a squash merge — and Android then refuses the install as a downgrade, on debug
// builds just as much as release.
//
//   0.1.1 -> 10001      bump build
//   0.2.0 -> 20000      bump minor, reset build
//   1.0.0 -> 1000000    bump major
//
// versionCode only ever increases, so a real device keeps proper downgrade protection: an
// older APK cannot silently replace a newer install. And because the number no longer depends
// on git, switching branches never blocks a local install.
val versionMajor = 0
val versionMinor = 1
val versionBuild = 1

val appVersionName = "$versionMajor.$versionMinor.$versionBuild"
val appVersionCode = versionMajor * 1_000_000 + versionMinor * 10_000 + versionBuild

android {
    namespace = "com.wellnesswingman"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wellnesswingman"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

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
        // Deliberately no applicationIdSuffix: debug builds are what actually runs on real
        // devices day to day, and a separate application id would strand that history in the
        // old package. The version scheme above is what keeps installs working across branches.
        debug {
            versionNameSuffix = "-debug"
        }

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

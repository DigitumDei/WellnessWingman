# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Koin
-keep class org.koin.** { *; }

# Keep SQLDelight
-keep class app.cash.sqldelight.** { *; }

# Keep data models
-keep class com.wellnesswingman.data.model.** { *; }

# Keep Ktor
-keep class io.ktor.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wellnesswingman.**$$serializer { *; }
-keepclassmembers class com.wellnesswingman.** {
    *** Companion;
}
-keepclasseswithmembers class com.wellnesswingman.** {
    kotlinx.serialization.KSerializer serializer(...);
}

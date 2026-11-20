# Turnypt R8 rules (v1.5.10: minification enabled after the lag audit —
# the debug build shipped until now carried `application-debuggable`,
# JNI asserts on every call, no ART AOT optimization and 19 dex files;
# THAT was most of the lag the user reported).
#
# Keep rules below are the conservative set: everything the app reflects
# over or crosses a module boundary reflectively is kept; everything
# else is shrunk/optimized. Nothing visible changes — same screens,
# same strings, same drawables.

# ---- Compose (conservative: keep runtime internals) ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin metadata (annotation processing consumers):
-keep class kotlin.Metadata { *; }

# ---- Hilt / Dagger (generated components reference classes by name) ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembernames class * {
    @dagger.* <methods>;
}

# ---- Room entities: SQL column strings reference field names ----
-keep class com.turnypt.data.database.*Entity { *; }

# ---- Enum reflective access (DataStore defaults) ----
-keepclassmembers enum com.turnypt.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Coroutines: silence metadata warnings ----
-dontwarn kotlinx.coroutines.**

# ---- Dev-only logging off hot paths in release (w/e kept) ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# ---- Keep line numbers for crash triage (no runtime cost) ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

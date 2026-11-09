# ==============================================================================
# ProGuard & R8 Hardened Obfuscation Rules for CircleStream
# ==============================================================================
-repackageclasses 'com.circleftp.stream.obf'
-allowaccessmodification
-renamesourcefileattribute "SourceFile"

# Preserve essential metadata for Kotlin, Coroutines, and Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 1. Keep native methods and JNI bindings (FFmpeg C/C++ & System JNI)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 2. Keep Kotlinx Serialization (Cleaned & Deduplicated - Valid Bytecode Syntax)
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.circleftp.stream.** { *; }
-keepclassmembers class com.circleftp.stream.** {
    @kotlinx.serialization.SerialName <fields>;
}

# 3. Keep Room Database Entities (with all fields/getters) & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# 4. Keep Media3 (ExoPlayer) & Jellyfin FFmpeg Audio Renderer (Loaded via Reflection)
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }
-dontwarn androidx.media3.**
-dontwarn org.jellyfin.media3.**

# 5. Keep OkHttp, Ktor & Coroutines safe from aggressive R8 warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# 6. AntiReverseGuard: Keep the code alive, DOES NOT reveal class/method names in JADX!
# (-keepclassmembers,allowobfuscation prevents R8 from deleting checks while renaming them to 'a.b.c()')
-keep,allowobfuscation class com.circleftp.stream.security.AntiReverseGuard { *; }

# 7. Strip verbose/debug/info logging calls completely in Release APK
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# 8. Wortise In-App Ad SDK ProGuard Rules
-keep class com.wortise.ads.** { *; }
-dontwarn com.wortise.ads.**
-dontwarn com.unity3d.**

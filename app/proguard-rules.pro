# Invariant 4 (CLAUDE.md): no sensitive data reaches logcat in release. AsktrixLog delegates to
# android.util.Log only in debug; these rules strip the calls entirely from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# kotlinx.serialization keeps generated serializers via @Serializable; R8 needs the companions.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers,allowshrinking class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces are reflective.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

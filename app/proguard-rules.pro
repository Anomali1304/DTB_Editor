# ProGuard & R8 Optimization Rules for DTB Studio

# Keep all DTB Studio core models, FDT parsers, and ViewModel
-keep class com.dtb.editor.** { *; }

# Preserve line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable

# Strip debugging logs in release build for smaller dex size
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Android Compose & Coroutines standard optimizations
-dontwarn kotlin.reflect.**

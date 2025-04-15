# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Glide generated classes
-keep class com.bumptech.glide.** { *; }

# Keep Apache POI (Excel, Word, PPT)
-keep class org.apache.poi.** { *; }

# Keep main app classes (Modify package name)
-keep class com.example.fileforge.** { *; }

# Remove debugging info to reduce size
-dontwarn org.apache.**
-dontwarn com.bumptech.glide.**

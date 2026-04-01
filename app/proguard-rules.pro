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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep native methods - CRITICAL for JNI to work
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all classes with native methods
-keep class aenu.** { *; }

# Keep emulator classes and their native methods
-keep class aenu.emulator.Emulator { *; }
-keep class aenu.emulator.Emulator$** { *; }
-keep class aenu.ax360e.Emulator { *; }
-keep class aenu.ax360e.Emulator$** { *; }
-keep class aenu.hardware.ProcessorInfo { *; }

# Keep game info classes used by native code
-keep class aenu.ax360e.Emulator$GameInfo { *; }

# Keep reflection-accessed classes
-keepclassmembers class * {
    public <init>(android.content.Context);
}

# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep DocumentFile and related classes
-keep class androidx.documentfile.** { *; }

# Keep JSON classes for serialization
-keep class org.json.** { *; }

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

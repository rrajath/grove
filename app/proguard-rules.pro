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

# Keep crash-report line numbers useful after minification.
-keepattributes SourceFile,LineNumberTable

# WorkManager instantiates workers reflectively by class name (SyncWorker is not
# referenced from the manifest), so keep ListenableWorker subclasses + their ctor.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance runs a widget action by resolving the callback's class name out of the
# tap intent and calling getDeclaredConstructor().newInstance(). Glance's own
# consumer rule ("-keep public class * extends ActionCallback") keeps the class
# but says nothing about its members, so R8 sees the no-arg constructor as
# unreachable and shrinks it away: the class ships, the reflection throws
# NoSuchMethodException, and the tap becomes a silent no-op. Debug builds don't
# run R8, so this reproduces on release builds only — the Agenda widget's
# mark-done circle did nothing at all in production while working on a debug
# install of the same commit.
-keep class * extends androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
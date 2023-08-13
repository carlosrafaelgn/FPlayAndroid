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

#
# http://developer.android.com/tools/help/proguard.html
# https://www.guardsquare.com/en/proguard/manual/usage#obfuscationoptions
# https://www.guardsquare.com/en/proguard/manual/optimizations
# https://www.guardsquare.com/en/proguard/manual/examples
# http://proguard.sourceforge.net/index.html#manual/examples.html
#

-printmapping mapping.txt
-optimizations code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 9
-useuniqueclassmembernames
-allowaccessmodification
-repackageclasses ''

-keep class android.widget.BgEdgeEffect {
    public *;
}

# How to keep an entire package
#-keep interface com.google.gson.** { *; }
#-keep class com.google.gson.** { *; }

# **********************************************************************************************************
# Common area
# **********************************************************************************************************

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * {
    native <methods>;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.FPlay
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.FPlay {
    public *;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin {
    public *;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin$Observer
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin$Observer {
    public *;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.ItemSelectorDialog
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.ItemSelectorDialog {
    public *;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.ItemSelectorDialog$Observer
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.ItemSelectorDialog$Observer {
    public *;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.ItemSelectorDialog$Observer
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.ItemSelectorDialog$Observer {
    public *;
}

-keep class br.com.carlosrafaelgn.fplay.plugin.SlimLock
-keepclassmembers class br.com.carlosrafaelgn.fplay.plugin.SlimLock {
    public *;
}

-keep class br.com.carlosrafaelgn.fplay.plugin.SongInfo
-keepclassmembers class br.com.carlosrafaelgn.fplay.plugin.SongInfo {
    public *;
}

-keep class br.com.carlosrafaelgn.fplay.plugin.SongList
-keepclassmembers class br.com.carlosrafaelgn.fplay.plugin.SongList {
    public *;
}

-keep class * implements br.com.carlosrafaelgn.fplay.plugin.Visualizer
-keepclassmembers class * implements br.com.carlosrafaelgn.fplay.plugin.Visualizer {
	public <init>(...);
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.Visualizer
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.Visualizer {
    public *;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.VisualizerService
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.VisualizerService {
    public *;
}

-keep interface br.com.carlosrafaelgn.fplay.plugin.VisualizerService$Observer
-keepclassmembers interface br.com.carlosrafaelgn.fplay.plugin.VisualizerService$Observer {
    public *;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

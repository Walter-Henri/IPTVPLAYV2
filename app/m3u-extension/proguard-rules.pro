# M3U Extension - ProGuard Rules
# Basic rules to keep necessary classes for Chaquopy and other libraries

-keep class com.m3u.extension.** { *; }
-keep interface com.m3u.extension.** { *; }

# Chaquopy
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# OkHttp/Gson
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

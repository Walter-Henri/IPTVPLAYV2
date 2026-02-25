# M3U Extension - ProGuard Rules

-keep class com.m3u.extension.** { *; }
-keep interface com.m3u.extension.** { *; }

# AIDL Parcelable
-keep class com.m3u.common.** { *; }
-keep interface com.m3u.common.** { *; }

# NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn java.beans.**
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.namespace.QName
-dontwarn com.m3u.extension.youtube.YouTubeExtractorV2
-dontwarn com.m3u.extension.newpipe.NewPipeResolver

# Mozilla Rhino (required by NewPipe Extractor for JavaScript execution)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# Remove Chaquopy (no longer needed)
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

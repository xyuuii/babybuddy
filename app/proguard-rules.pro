# Project-specific R8 rules.

# Gson reflects over the Kotlin data models used for local JSON/WebDAV sync.
-keep class com.yueming.baby.data.** { *; }
-keep class com.yueming.baby.data.cloud.** { *; }
-keep class com.yueming.baby.data.entity.** { *; }

# OkHttp and Media3 ship consumer rules, but these warnings are optional platform integrations.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn androidx.media3.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

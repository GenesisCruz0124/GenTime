# Supabase / Ktor / kotlinx.serialization
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class dev.gentime.app.**$$serializer { *; }
-keepclassmembers class dev.gentime.app.** { *** Companion; }

# ktor's debug detector references JVM-only management APIs that don't exist
# on Android; the code path is dead there.
-dontwarn java.lang.management.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

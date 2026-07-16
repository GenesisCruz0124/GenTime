# Supabase / Ktor / kotlinx.serialization
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class dev.gentime.app.**$$serializer { *; }
-keepclassmembers class dev.gentime.app.** { *** Companion; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

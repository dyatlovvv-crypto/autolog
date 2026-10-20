# Autolog release — R8/ProGuard

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Coil
-dontwarn coil.**

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keep class com.google.gson.** { *; }

# Kotlin / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# Author mark (BuildConfig strings stay readable for ownership)
-keep class ru.autolog.app.BuildConfig { *; }

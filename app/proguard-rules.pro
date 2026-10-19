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

# Google Drive / API client
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**

# Apache HttpClient (transitive; not on Android)
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
-dontwarn org.apache.http.**
-dontwarn com.google.android.gms.**

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

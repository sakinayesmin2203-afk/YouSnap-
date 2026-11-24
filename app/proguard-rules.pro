# Add project specific ProGuard rules here.
# Preserve line numbers and source file names for crash reporting.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-renamesourcefileattribute SourceFile

# Kotlin & Coroutines
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.android.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlinx Serialization (safe rules if used by dependencies or future models)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-dontwarn kotlinx.serialization.**

# @Keep annotations & App Data Models (Firestore toObject, Room, Moshi)
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.example.data.model.** { *; }
-keep class com.example.ui.StoryModel { *; }
-keep class com.example.ui.ViewerProfile { *; }
-keep class com.example.ui.LoginDeviceItem { *; }
-keep class com.example.ui.AccountRestrictionState { *; }
-keep class com.example.ui.screens.FullScreenPhotoData { *; }
-keep class com.example.data.repository.** { *; }

# Firebase SDKs (Firestore, Auth, Messaging, Storage, AI Logic, App Check) & Play Integrity
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.play.core.**

# Google Play Services, Credential Manager, Google Identity, AdMob
-keep class com.google.android.gms.ads.** { *; }
-keepclassmembers class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn androidx.credentials.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }
-keepclassmembers class coil.** { *; }
-dontwarn coil.**

# Retrofit / OkHttp / Moshi
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

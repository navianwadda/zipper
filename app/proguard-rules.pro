-keepclassmembers class kotlin.Metadata { *; }

-keep class dagger.hilt.** { *; }
-keep class dagger.hilt.internal.** { *; }
-keep class javax.inject.** { *; }

-keepclassmembers class com.livetvpro.app.data.models.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.livetvpro.app.data.repository.NativeDataRepository {
    <init>(...);
    native <methods>;
}

-keep class com.livetvpro.app.utils.NativeListenerManager {
    <init>(...);
    native <methods>;
}

-allowaccessmodification
-overloadaggressively

-optimizationpasses 5

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,Exceptions

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application

-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public <init>();
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.DefaultRenderersFactory { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

-keepattributes Signature
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** v(...);
}

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

-dontwarn **
-ignorewarnings

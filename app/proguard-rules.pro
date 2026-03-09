# =============================================================================
# LiveTVPro — corrected ProGuard rules
# =============================================================================
# IMPORTANT: Two groups of models exist and need different treatment:
#
#   GROUP A — fields have NO @SerializedName → Gson uses the KOTLIN FIELD NAME
#             as the JSON key → field names MUST be preserved or parsing breaks.
#
#             Category, Channel, ChannelLink, FavoriteChannel,
#             LiveEvent, LiveEventLink, EventCategory, Playlist
#
#   GROUP B — ALL fields have @SerializedName → Gson uses the annotation value,
#             not the field name → field names CAN be obfuscated safely.
#
#             NewExternalEventRow, ListenerConfig
# =============================================================================

# -----------------------------------------------------------------------------
# 1. OPTIMISATION
# -----------------------------------------------------------------------------
-optimizationpasses 7
-allowaccessmodification
-overloadaggressively
-repackageclasses ''
-flattenpackagehierarchy ''

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

# Strip all logging
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

# -----------------------------------------------------------------------------
# 2. ANDROID FRAMEWORK
# -----------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application
-keep public class * extends androidx.fragment.app.Fragment { public <init>(); }

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class kotlin.Metadata { *; }

# -----------------------------------------------------------------------------
# 3. JNI — exact class + method names required by the native side
# -----------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.livetvpro.app.utils.NativeListenerManager {
    native <methods>;
}
-keepclassmembers class com.livetvpro.app.utils.NativeListenerManager {
    static void <clinit>();
}
-keep class com.livetvpro.app.data.repository.NativeDataRepository {
    native <methods>;
}

# -----------------------------------------------------------------------------
# 4. GROUP A — NO @SerializedName → field names ARE the JSON keys
#    Must keep all field names exactly as written.
# -----------------------------------------------------------------------------

# Category — JSON keys: id, name, slug, iconUrl, m3uUrl, order, createdAt, updatedAt
-keepclassmembers class com.livetvpro.app.data.models.Category { *; }

# Channel — JSON keys: id, name, logoUrl, streamUrl, categoryId, categoryName,
#           groupTitle, links, team1Logo, team2Logo, isLive, startTime, endTime,
#           createdAt, updatedAt
-keepclassmembers class com.livetvpro.app.data.models.Channel { *; }

# ChannelLink — JSON keys: url, cookie, referer, origin, userAgent,
#               drmScheme, drmLicenseUrl  (quality has @SerializedName but keep all)
-keepclassmembers class com.livetvpro.app.data.models.ChannelLink { *; }

# FavoriteChannel — passed via Gson in FavoritesRepository — keep field names
-keepclassmembers class com.livetvpro.app.data.models.FavoriteChannel { *; }

# LiveEvent — JSON keys: id, category, league, leagueLogo, team1Name, team1Logo,
#             team2Name, team2Logo, startTime, endTime, isLive, links, title,
#             description, wrapper, eventCategoryId, eventCategoryName,
#             createdAt, updatedAt
-keepclassmembers class com.livetvpro.app.data.models.LiveEvent { *; }

# LiveEventLink — same as ChannelLink
-keepclassmembers class com.livetvpro.app.data.models.LiveEventLink { *; }

# EventCategory — JSON keys: id, name, slug, logoUrl, order, isDefault,
#                 createdAt, updatedAt
-keepclassmembers class com.livetvpro.app.data.models.EventCategory { *; }

# Playlist — stored in Room and used via Gson in FavoritesRepository
-keepclassmembers class com.livetvpro.app.data.models.Playlist { *; }

# EventStatus enum — referenced by name in UI
-keepnames class com.livetvpro.app.data.models.EventStatus

# -----------------------------------------------------------------------------
# 5. GROUP B — ALL fields have @SerializedName → field names can be obfuscated.
#    Gson reads the annotation value, not the field name, so renaming is safe.
#    We only keep class names so TypeToken/Array<T> reflection still works.
# -----------------------------------------------------------------------------

# NewExternalEventRow — every field has @SerializedName("event_title") etc.
-keepnames class com.livetvpro.app.data.models.NewExternalEventRow

# ListenerConfig — parsed by native layer (elc() in native-lib.cpp) using raw
#   JSON key strings. The Kotlin class is only used to pass data to the UI.
#   Field names don't matter for parsing — class name is enough.
-keepnames class com.livetvpro.app.data.models.ListenerConfig

# Keep @SerializedName annotation values (the actual JSON keys) across all models
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -----------------------------------------------------------------------------
# 6. ROOM — DB schema is fixed; column names must not change
# -----------------------------------------------------------------------------
-keep class com.livetvpro.app.data.local.entity.FavoriteChannelEntity { *; }
-keep class com.livetvpro.app.data.local.entity.PlaylistEntity { *; }
-keep interface com.livetvpro.app.data.local.dao.** { *; }
-keep class com.livetvpro.app.data.local.dao.** { *; }
-keepclassmembers class com.livetvpro.app.data.local.entity.FavoriteChannelConverters {
    @androidx.room.TypeConverter <methods>;
}
-keep class com.livetvpro.app.data.local.AppDatabase { *; }

# -----------------------------------------------------------------------------
# 7. GSON
# -----------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# -----------------------------------------------------------------------------
# 8. HILT / DAGGER
# -----------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class dagger.hilt.internal.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint *;
}
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# -----------------------------------------------------------------------------
# 9. RETROFIT + OKHTTP
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepattributes Signature

# -----------------------------------------------------------------------------
# 10. FIREBASE
# -----------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# -----------------------------------------------------------------------------
# 11. MEDIA3 / EXOPLAYER
# -----------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.DefaultRenderersFactory { *; }

# -----------------------------------------------------------------------------
# 12. GLIDE
# -----------------------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# -----------------------------------------------------------------------------
# 13. SVG
# -----------------------------------------------------------------------------
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# -----------------------------------------------------------------------------
# 14. SUPPRESS WARNINGS
# -----------------------------------------------------------------------------
-dontwarn **
-ignorewarnings

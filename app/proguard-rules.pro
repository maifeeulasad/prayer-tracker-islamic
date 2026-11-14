# R8 / ProGuard rules for Prayer Tracker Islamic.
#
# Compose, kotlinx-coroutines and Play Services ship their own consumer rules,
# so most of what we need is already covered. Keep this file focused on what is
# specific to this app.

# Preserve stack traces for crash reports with the obfuscation mapping file
# that Play Console / sideloaders can symbolicate.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Entity classes serialized by minidb — keep names and fields so the on-disk
# format is stable across R8 renames.
-keep class com.mua.prayertracker.data.entity.** { *; }
-keep class com.mua.prayertracker.domain.model.** { *; }

# BroadcastReceivers referenced from the manifest.
-keep class com.mua.prayertracker.util.PrayerNotificationReceiver { *; }

# Add project specific ProGuard rules here.
# Room, Hilt, and WorkManager ship consumer ProGuard rules, so no extra keep
# rules are required for them in normal usage.

# Keep Hilt-generated Worker factories discoverable by WorkManager.
-keep class * extends androidx.work.ListenableWorker

# Keep Room entities' field names for reflection-based schema validation.
-keepclassmembers class com.fightclub.attendance.data.local.entity.** {
    *;
}

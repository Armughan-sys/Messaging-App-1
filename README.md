# Fight Club Attendance Assistant

A native Android app (Kotlin + Jetpack Compose + Material 3) that automatically tells your fight
club manager, **Abdullah Malik KAK**, whenever you and Saim won't be attending class — on a
schedule, with no manual action required most of the time.

## What it does

- **Every Tuesday and Thursday at 4:00 PM**, the app sends the attendance SMS automatically,
  unconditionally — no prompt, no confirmation needed.
- **On class days (Monday/Wednesday/Friday)**, at **1:00 PM** it shows a full-screen notification:
  *"Are you going to today's fight class?"* with **YES** / **NO** buttons.
  - **YES** → nothing happens.
  - **NO** → the SMS is sent immediately.
  - **No response** → asked again at **3:00 PM**, then **3:45 PM**.
  - **Still no response by 4:00 PM** → the SMS is sent automatically.
- All of this keeps working even if the app is closed or swiped away, and every schedule is
  restored automatically after the phone reboots.
- The Home screen shows the next scheduled action, the next automatic SMS date/time, the next
  attendance prompt, and the last SMS sent (date, time, delivery status).
- Settings let you change the contact, the message text, the three prompt times, the automatic
  send time, which days are "class days" vs. "automatic send days," and the app theme
  (light/dark/system).

## Architecture

- **UI**: Jetpack Compose, Material 3, single-activity (`MainActivity`) with a Navigation Compose
  graph (`Home` ↔ `Settings`).
- **Pattern**: MVVM — each screen has a `@HiltViewModel` exposing a `StateFlow` of UI state;
  Composables are stateless and only render what they're given.
- **DI**: Hilt (`di/DatabaseModule`, `di/RepositoryModule`, `di/UtilModule`), plus
  `androidx.hilt:hilt-work` so `WorkManager` workers get constructor injection too.
- **Persistence**: Room (`data/local`) — a single-row `settings` table, an append-only `sms_log`
  table (for the Home screen's history/status), and an `attendance_status` table keyed by date
  (so the 3:00 PM / 3:45 PM reminders know to stay silent once you've already answered).
- **Scheduling**: `domain/scheduler/AlarmScheduler` owns every `AlarmManager` interaction.
  `AlarmManager` has no "every Tuesday" primitive, so each alarm re-schedules its own next
  occurrence, one week ahead, the moment it fires. Every alarm's `PendingIntent` request code is
  deterministic (`namespace + dayOfWeek.value`), so re-arming an alarm simply replaces the
  previous one instead of stacking a duplicate — this is what "prevent duplicate scheduling"
  means in practice here.
- **Receivers**: `BootReceiver` (restores everything after reboot/app-update),
  `AutoSendAlarmReceiver` (Tue/Thu send + the Mon/Wed/Fri 4:00 PM deadline),
  `AttendancePromptReceiver` (the 1/3/3:45 PM prompts), `NotificationActionReceiver`
  (YES/NO taps from the notification itself, without opening the app).
- **Workers**: `SendSmsWorker` does the actual send (checks attendance status for deadline sends,
  sends via `SmsManager`, logs the result, re-arms next week's alarm) and `RescheduleWorker`
  rebuilds every alarm from the current settings. Both are `CoroutineWorker`s so they survive
  process death and get WorkManager's Doze-aware execution guarantees.
- **Notifications**: `NotificationHelper` builds the attendance-prompt notification with a
  `fullScreenIntent` (so it can appear over the lock screen via `FullScreenAttendanceActivity`)
  and inline YES/NO actions, plus a low-priority SMS delivery-status notification.

## Permissions — and why each one is needed

Requested at runtime, with an in-app explanation screen shown before the system dialog:

| Permission | Why |
|---|---|
| `SEND_SMS` | To text the manager that you and Saim won't be attending. |
| `READ_CONTACTS` | To find "Abdullah Malik KAK" in your phonebook automatically on first launch. |
| `POST_NOTIFICATIONS` | To show the daily attendance question and the SMS delivery-status notification (Android 13+). |

Declared in the manifest but **not** part of the runtime request flow (these are "special"/normal
permissions, not the dangerous-permission dialog):

| Permission | Why |
|---|---|
| `RECEIVE_BOOT_COMPLETED` | So the app can re-create every alarm after the device restarts. Without it, all schedules would be silently lost on every reboot. |
| `SCHEDULE_EXACT_ALARM` | Exact alarms are the only reliable way to fire prompts/SMS at a precise minute, including during Doze. On Android 12–13 this is a toggle the user grants from system Settings; the Home screen shows a warning banner with a shortcut to that screen if it's ever off, and the app falls back to an inexact alarm rather than crashing. |
| `USE_FULL_SCREEN_INTENT` | Lets the attendance prompt appear over the lock screen. |

No other permissions are requested.

## Reliability notes (read this)

- **If the phone is powered off** at a scheduled time, Android cannot fire alarms or send SMS —
  there is no way around this on stock Android; the schedule simply resumes once the phone is
  back on, and the alarm that would have fired reschedules for its next normal occurrence.
- **Doze mode**: alarms use `setExactAndAllowWhileIdle`, which is specifically designed to still
  fire (once, at approximately the exact time) even while the device is idle.
- **Reboots**: `BootReceiver` listens for `BOOT_COMPLETED` (and `MY_PACKAGE_REPLACED`, for app
  updates) and enqueues `RescheduleWorker`, which rebuilds every alarm from the settings saved in
  Room.
- **App closed/swiped away**: alarms are OS-level (`AlarmManager`) and the actual send happens in
  a `WorkManager` worker, both of which run independently of whether any activity is alive.
- **No duplicate jobs**: alarm request codes are deterministic per (purpose, day), so re-running
  `scheduleAll()` (on boot, on every Settings save, after every send) always replaces rather than
  stacks. `WorkManager` enqueues for the SMS send also use `ExistingWorkPolicy.REPLACE`.

## Project structure

```
app/src/main/java/com/fightclub/attendance/
├── FightClubApplication.kt        Hilt entry point, notification channels, WorkManager config
├── data/
│   ├── local/                     Room entities, DAOs, AppDatabase
│   ├── model/                     Domain models (AppSettings, SavedContact, SmsLogEntry)
│   └── repository/                Settings, SmsLog, AttendanceStatus, Contact repositories
├── di/                            Hilt modules
├── domain/scheduler/              AlarmScheduler + AttendanceResponseHandler
├── notification/                  NotificationHelper, FullScreenAttendanceActivity
├── receiver/                      BootReceiver and the three alarm/notification receivers
├── worker/                        SendSmsWorker, RescheduleWorker
├── ui/                            Compose screens (home, settings, permissions, contact picker) + ViewModels
└── util/                          Constants, DateTimeUtils, SmsSender
```

## Building and installing

This project was written entirely by hand in this sandbox, which has **no Android SDK, no
emulator, and no network access to `dl.google.com`** (the source of Android build tools/platforms),
so it could not be compiled or run here — only reviewed by hand for correctness. To build it:

1. Install **Android Studio** (Koala or newer recommended).
2. Open this project's root folder in Android Studio.
3. On first open, Android Studio will offer to generate the Gradle wrapper / download the Gradle
   distribution — accept that (or, if you have Gradle installed, run `gradle wrapper` from the
   project root once to create `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`, which weren't
   generated here since fetching the Gradle distribution requires exactly the network access this
   sandbox didn't have).
4. Let Gradle sync — it will download the Android Gradle Plugin, Compose, Room, Hilt, and
   WorkManager dependencies (`compileSdk 34`, `minSdk 26`, Kotlin 2.0.21).
5. Connect your phone via USB with **USB debugging** enabled (Settings → About phone → tap
   "Build number" 7 times → Developer options → USB debugging), or use an emulator.
6. Press **Run ▶** to install and launch, or use **Build → Build Bundle(s)/APK(s) → Build APK(s)**
   to produce a standalone APK you can transfer/install manually.
7. On first launch, grant the requested permissions, and the app will search your phonebook for
   "Abdullah Malik KAK" automatically — if it isn't found (or more than one match exists), you'll
   be asked to pick the right contact once, and it's saved from then on.

## Testing

Run unit tests with:

```
./gradlew test
```

- `util/DateTimeUtilsTest` — the "next occurrence" date math (same day vs. rolling to next week,
  exact-boundary handling).
- `scheduler/AlarmSchedulerImplTest` — verifies `scheduleAll()` arms the right alarms for the
  right days, cancels alarms for days removed from settings, and never duplicates an alarm on a
  repeat call (Robolectric, inspecting the shadow `AlarmManager`).
- `util/SmsSenderImplTest` — drives `SmsManager`'s sent-broadcast callback (via Robolectric's
  shadow) to confirm `Sent` / `Failed` results are reported correctly.
- `worker/SendSmsWorkerTest` — the unconditional Tue/Thu send, the deadline send being skipped
  once already answered, the deadline firing when nobody responded, and the no-contact-configured
  failure path.
- `receiver/BootReceiverTest` — confirms `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` enqueue exactly
  one `RescheduleWorker` job, unrelated broadcasts are ignored, and repeated boots don't queue up
  duplicates.
- `repository/SettingsRepositoryImplTest`, `repository/AttendanceStatusRepositoryImplTest` —
  Room round-trip tests against an in-memory database.

Instrumented tests (`app/src/androidTest`, run with `./gradlew connectedAndroidTest` on a device
or emulator) include a Room sanity check against the real on-device SQLite implementation.

**These tests were written and reviewed by hand but not executed**, for the same reason the app
itself couldn't be built here — there is no Android SDK/emulator available in this environment.
Please run `./gradlew test` yourself after opening the project; if anything doesn't compile
cleanly, it's most likely a dependency-version mismatch introduced by newer/older tooling than
what's pinned in `gradle/libs.versions.toml`, not a structural issue with the code.

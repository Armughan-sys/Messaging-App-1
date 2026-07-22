# Fight Club Attendance Assistant

A native Android app (Kotlin + Jetpack Compose + Material 3) that automatically tells your fight
club manager, **Abdullah Malik KAK**, on **WhatsApp**, whenever you and Saim won't be attending
class — on a schedule, with no manual action required most of the time.

> **Important — read before relying on this app.** The attendance message is sent through
> WhatsApp by having the app read WhatsApp's own screen and tap its Send button for you
> (an Android Accessibility Service). WhatsApp has no API a third-party app can call to send a
> message on your behalf, so this is the only way to get a message out of WhatsApp without you
> personally tapping Send — see [How the WhatsApp send actually works](#how-the-whatsapp-send-actually-works)
> for exactly what that means, including real failure modes you should expect.

## What it does

- **Every active day** (Monday through Saturday by default — fully customizable), the app follows
  the same flow:
  - At **that day's class time minus a configurable number of lead hours**, it shows a full-screen
    notification: *"Are you going to today's fight class?"* with **YES** / **NO** buttons. Each day
    can have its own class time — e.g. Friday and Saturday can run at a different time than the
    rest of the week.
    - **YES** → nothing happens.
    - **NO** → the WhatsApp message is sent immediately.
    - **No response** → nothing more happens until the deadline.
  - **Still no response by your configured deadline time** → the WhatsApp message is sent
    automatically.
- All of this keeps working even if the app is closed or swiped away, and every schedule is
  restored automatically after the phone reboots.
- The Home screen shows **classes attended this week and this month** (counted from days you
  answered YES), the next scheduled action, the next automatic message date/time, the next
  attendance prompt, and the last message sent (date, time, delivery status) — plus a warning
  banner if WhatsApp Auto-Send is turned off.
- Settings let you customize: the manager contact, the message text, which days are active, each
  active day's class start time, how many hours before class the prompt fires, the deadline
  (auto-send) time, and the app theme (light/dark/system).

## How the WhatsApp send actually works

WhatsApp does not provide any API a regular Android app can call to send a message on your
behalf — the only thing an app can do is open WhatsApp with a chat and message pre-filled, and
from there **a human has to tap Send**. To make the deadline auto-send truly automatic (as
originally specified), this app instead uses an **Accessibility Service**
(`automation/WhatsAppAccessibilityService`) that:

1. Opens WhatsApp directly on Abdullah's chat with the message already typed in, via WhatsApp's
   own documented "click to chat" deep link (`WhatsAppLauncher`).
2. Watches WhatsApp's on-screen content for the message box to contain that exact message, then
   performs a click action on WhatsApp's Send button on your behalf.

**This has to be turned on manually.** Accessibility Services can't be requested like a normal
runtime permission (Android deliberately makes this a deliberate, manual opt-in, since the API is
powerful) — after installing the app, go to **Settings ▸ Accessibility ▸ WhatsApp Auto-Send** and
turn it on. The Home screen shows a warning banner with a direct shortcut there if it's ever off.

**What this means in practice, honestly:**

- **It depends on WhatsApp's internal view IDs** (`com.whatsapp:id/entry`, `com.whatsapp:id/send`),
  which are not a public, stable API. A WhatsApp update *can* break this outright. The code falls
  back to a generic "find the EditText" / "find something labeled Send" search if the known IDs
  are missing, which adds some resilience but is not a guarantee.
- **This app cannot be published to the Play Store** with this feature — Google's policy
  restricts Accessibility Service usage to genuine accessibility purposes, and this doesn't
  qualify. It's meant to be built in Android Studio and installed on your own phone (sideloaded),
  which is exactly how this project is intended to be used.
- **It only ever acts inside WhatsApp**, and only right after this app itself opened a chat it
  prepared — it does not run continuously or watch anything else.
- **A 15-second timeout** applies to every send attempt; if WhatsApp doesn't finish loading the
  chat (slow phone, WhatsApp update mid-flow, etc.) in time, the attempt is logged as failed and
  a notification tells you so — nothing is silently lost, but nothing retries automatically either
  beyond the app's normal daily schedule.
- The very first time you use WhatsApp's click-to-chat link, WhatsApp may show a one-time
  "Continue to chat" confirmation screen; the automation tries to detect and tap through that too,
  but this is the single most likely thing to need a one-off manual tap on a brand-new install.

If you'd rather not deal with any of that fragility, the previous version of this app sent a
plain **SMS** via `SmsManager` instead — fully automatic, no accessibility service, no dependency
on WhatsApp's UI. That code path was removed because WhatsApp delivery was explicitly requested,
but it's a straightforward revert if you change your mind (swap `WhatsAppSender` back for an
`SmsManager`-based implementation of the same interface).

## Architecture

- **UI**: Jetpack Compose, Material 3, single-activity (`MainActivity`) with a Navigation Compose
  graph (`Home` ↔ `Settings`).
- **Pattern**: MVVM — each screen has a `@HiltViewModel` exposing a `StateFlow` of UI state;
  Composables are stateless and only render what they're given.
- **DI**: Hilt (`di/DatabaseModule`, `di/RepositoryModule`, `di/UtilModule`), plus
  `androidx.hilt:hilt-work` so `WorkManager` workers get constructor injection too.
- **Persistence**: Room (`data/local`) — a single-row `settings` table, an append-only
  `message_log` table (for the Home screen's history/status), and an `attendance_status` table
  keyed by date. The latter both stops a stray duplicate alarm firing from double-prompting once
  you've answered, and — via `countByStatusInRange` — powers the "classes attended this
  week/month" counters (a day counts once its status is `ATTENDING`, i.e. you tapped YES).
- **Scheduling**: `domain/scheduler/AlarmScheduler` owns every `AlarmManager` interaction. Every
  active day gets exactly two alarms, both computed from the same settings: a **prompt** alarm at
  `AppSettings.promptTimeFor(day)` (that day's class time, from the per-day `classTimes` map,
  minus the configured lead hours) and a **deadline** alarm at the configured auto-send time.
  `AlarmManager` has no "every Tuesday" primitive, so each alarm
  re-schedules its own next occurrence, one week ahead, the moment it fires. Every alarm's
  `PendingIntent` request code is deterministic (`namespace + dayOfWeek.value`), so re-arming an
  alarm simply replaces the previous one instead of stacking a duplicate — this is what "prevent
  duplicate scheduling" means in practice here.
- **Receivers**: `BootReceiver` (restores everything after reboot/app-update),
  `AutoSendAlarmReceiver` (the daily deadline), `AttendancePromptReceiver` (the daily prompt),
  `NotificationActionReceiver` (YES/NO taps from the notification itself, without opening the app).
- **Automation** (`automation/`): `WhatsAppLauncher` (opens the pre-filled chat),
  `WhatsAppAccessibilityService` (taps Send), `AccessibilityUtils` (checks whether the user has
  turned the service on), `WhatsAppSender` (orchestrates the two, with a timeout).
- **Workers**: `SendWhatsAppMessageWorker` does the actual send (checks attendance status for
  deadline sends, delegates to `WhatsAppSender`, logs the result, re-arms next week's alarm) and
  `RescheduleWorker` rebuilds every alarm from the current settings. Both are `CoroutineWorker`s
  so they survive process death and get WorkManager's Doze-aware execution guarantees.
- **Notifications**: `NotificationHelper` builds the attendance-prompt notification with a
  `fullScreenIntent` (so it can appear over the lock screen via `FullScreenAttendanceActivity`)
  and inline YES/NO actions, plus a low-priority message delivery-status notification and an
  "enable WhatsApp Auto-Send" prompt.

## Permissions — and why each one is needed

Requested at runtime, with an in-app explanation screen shown before the system dialog:

| Permission | Why |
|---|---|
| `READ_CONTACTS` | To find "Abdullah Malik KAK" in your phonebook automatically on first launch. |
| `POST_NOTIFICATIONS` | To show the daily attendance question and the message delivery-status notification (Android 13+). |

Declared in the manifest but **not** part of the runtime request flow (these are "special"/normal
permissions, or a manual system-Settings toggle — not the dangerous-permission dialog):

| Permission | Why |
|---|---|
| `RECEIVE_BOOT_COMPLETED` | So the app can re-create every alarm after the device restarts. Without it, all schedules would be silently lost on every reboot. |
| `SCHEDULE_EXACT_ALARM` | Exact alarms are the only reliable way to fire prompts/messages at a precise minute, including during Doze. On Android 12–13 this is a toggle the user grants from system Settings; the Home screen shows a warning banner with a shortcut to that screen if it's ever off, and the app falls back to an inexact alarm rather than crashing. |
| `USE_FULL_SCREEN_INTENT` | Lets the attendance prompt appear over the lock screen. |
| `BIND_ACCESSIBILITY_SERVICE` | Declared on `WhatsAppAccessibilityService` itself; the user turns the service on manually from Settings ▸ Accessibility — see [How the WhatsApp send actually works](#how-the-whatsapp-send-actually-works). |

No other permissions are requested — notably, **there is no `SEND_SMS` permission**, since the app
no longer sends SMS.

## Reliability notes (read this)

- **If the phone is powered off** at a scheduled time, Android cannot fire alarms or open
  WhatsApp — there is no way around this on stock Android; the schedule simply resumes once the
  phone is back on, and the alarm that would have fired reschedules for its next normal
  occurrence.
- **Doze mode**: alarms use `setExactAndAllowWhileIdle`, which is specifically designed to still
  fire (once, at approximately the exact time) even while the device is idle.
- **Reboots**: `BootReceiver` listens for `BOOT_COMPLETED` (and `MY_PACKAGE_REPLACED`, for app
  updates) and enqueues `RescheduleWorker`, which rebuilds every alarm from the settings saved in
  Room.
- **App closed/swiped away**: alarms are OS-level (`AlarmManager`) and the actual send happens in
  a `WorkManager` worker, both of which run independently of whether any activity is alive. The
  Accessibility Service, once turned on, is likewise kept running by the OS independently of this
  app's own process.
- **No duplicate jobs**: alarm request codes are deterministic per (purpose, day), so re-running
  `scheduleAll()` (on boot, on every Settings save, after every send) always replaces rather than
  stacks. `WorkManager` enqueues for the message send also use `ExistingWorkPolicy.REPLACE`.
- **WhatsApp-specific fragility**: see [How the WhatsApp send actually works](#how-the-whatsapp-send-actually-works)
  above — this is the one part of the app whose reliability depends on something outside this
  app's control (WhatsApp's own UI).

## Project structure

```
app/src/main/java/com/fightclub/attendance/
├── FightClubApplication.kt        Hilt entry point, notification channels, WorkManager config
├── automation/                    WhatsAppLauncher, WhatsAppAccessibilityService, WhatsAppSender, AccessibilityUtils
├── data/
│   ├── local/                     Room entities, DAOs, AppDatabase
│   ├── model/                     Domain models (AppSettings, SavedContact, MessageLogEntry)
│   └── repository/                Settings, MessageLog, AttendanceStatus, Contact repositories
├── di/                            Hilt modules
├── domain/scheduler/              AlarmScheduler + AttendanceResponseHandler
├── notification/                  NotificationHelper, FullScreenAttendanceActivity
├── receiver/                      BootReceiver and the three alarm/notification receivers
├── worker/                        SendWhatsAppMessageWorker, RescheduleWorker
├── ui/                            Compose screens (home, settings, permissions, contact picker) + ViewModels
└── util/                          Constants, DateTimeUtils
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
   "Build number" 7 times → Developer options → USB debugging), or use an emulator (note: an
   emulator won't have a real WhatsApp account signed in, so the automation itself is best tested
   on your actual phone).
6. Press **Run ▶** to install and launch, or use **Build → Build Bundle(s)/APK(s) → Build APK(s)**
   to produce a standalone APK you can transfer/install manually.
7. On first launch, grant the requested permissions, and the app will search your phonebook for
   "Abdullah Malik KAK" automatically — if it isn't found (or more than one match exists), you'll
   be asked to pick the right contact once, and it's saved from then on.
8. **Go to Settings ▸ Accessibility ▸ WhatsApp Auto-Send and turn it on.** The app cannot do this
   for you; automatic sends will fail (with a clear notification explaining why) until you do.

## Testing

Run unit tests with:

```
./gradlew test
```

- `util/DateTimeUtilsTest` — the "next occurrence" date math (same day vs. rolling to next week,
  exact-boundary handling).
- `scheduler/AlarmSchedulerImplTest` — verifies `scheduleAll()` arms one prompt alarm and one
  deadline alarm for every active day, arms nothing for inactive days, cancels alarms for days
  removed from settings, and never duplicates an alarm on a repeat call (Robolectric, inspecting
  the shadow `AlarmManager`).
- `automation/WhatsAppSenderTest` — the realistically off-device-testable parts of the WhatsApp
  send flow: WhatsApp not installed, the accessibility service never enabled, and the service
  enabled-in-settings-but-not-actually-running case. Actually tapping WhatsApp's Send button
  requires a real WhatsApp window and isn't something Robolectric can simulate — see
  [How the WhatsApp send actually works](#how-the-whatsapp-send-actually-works).
- `worker/SendWhatsAppMessageWorkerTest` — the deadline send firing and rescheduling when nobody
  responded, the deadline send being skipped once already answered, a manual NO response sending
  immediately without touching the alarm schedule, the no-contact-configured failure path, and the
  accessibility-service-disabled path.
- `receiver/BootReceiverTest` — confirms `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` enqueue exactly
  one `RescheduleWorker` job, unrelated broadcasts are ignored, and repeated boots don't queue up
  duplicates.
- `repository/SettingsRepositoryImplTest` — Room round-trip tests against an in-memory database,
  including the derived `promptTimeFor(day)` calculation and per-day class time persistence
  (e.g. saving a different Friday/Saturday class time without disturbing other days).
- `repository/AttendanceStatusRepositoryImplTest` — Room round-trip tests plus the weekly/monthly
  attended-class counters, verifying days outside the current week/month are correctly excluded.

Instrumented tests (`app/src/androidTest`, run with `./gradlew connectedAndroidTest` on a device
or emulator) include a Room sanity check against the real on-device SQLite implementation.

**These tests were written and reviewed by hand but not executed**, for the same reason the app
itself couldn't be built here — there is no Android SDK/emulator available in this environment.
Please run `./gradlew test` yourself after opening the project; if anything doesn't compile
cleanly, it's most likely a dependency-version mismatch introduced by newer/older tooling than
what's pinned in `gradle/libs.versions.toml`, not a structural issue with the code.

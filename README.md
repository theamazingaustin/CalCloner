<div align="center">
  <h1>🗓️ CalCloner</h1>
  <p><strong>One-way Android calendar sync — reliable, private, battery-friendly.</strong></p>

  <p>
    <img alt="Version" src="https://img.shields.io/badge/version-2.1-10B981?style=flat-square" />
    <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" />
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
    <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-2024-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
    <img alt="License" src="https://img.shields.io/badge/license-Private-lightgrey?style=flat-square" />
  </p>
</div>

---

## What is CalCloner?

CalCloner clones calendar events **one-way** between any two local Android calendars. It is designed for people who need to share or mirror a calendar (e.g., a work calendar into a shared family calendar, or a personal calendar into a secondary account) without giving any third-party service access to their data.

Everything runs **on-device**. No cloud sync. No accounts. No privacy trade-offs.

---

## Features

### 🔁 Sync Engine
- **One-way sync** between any two calendars on the device
- Configurable **sync window** — choose how many days past and future to sync
- Supports **recurring events** (RRULE), all-day events, exceptions, and reminders
- Events are **tagged** with a unique CalCloner ID so they can be tracked, updated, or removed without touching original events
- **Self-write detection** prevents infinite sync loops when a content observer fires on a write CalCloner itself made

### 📋 Sync Pairs
- Create multiple **sync pair configurations**, each mapping one source calendar to one destination
- Per-pair controls: **enable/disable**, sync now, edit, delete
- Configure what fields to carry across: title, description, location, reminders, availability, status
- Optional **custom title override** per pair
- Sync history displayed per pair: last sync time, events inserted/updated/deleted, duration

### ⚙️ Background Sync
- **WorkManager** periodic background sync (15 min to 24 hr intervals, or manual-only)
- **JobScheduler reactive sync** triggers on calendar content changes
- **Boot receiver** re-schedules sync after device restart
- Respects **battery saver / low battery** mode — configurable

### 🗑️ Delete Screen (Calendar Maintenance)
- Dedicated screen for safe calendar event deletion
- **Two modes:**
  - *Purge Cloned Events Only* — removes only CalCloner-tagged events, leaves originals untouched
  - *Wipe All Events* — removes every event from a selected calendar (with explicit warning)
- **Safety confirmation lock**: the action button is disabled until the user types the exact phrase `delete <calendar name>`, preventing any accidental deletion

### 🎨 UI & UX
- Material 3 design system with full **dark / light / auto theme** support
- Custom **TitaniumMint** color palette (mint greens + rose reds for danger zones)
- Bottom navigation bar switching between **Sync** and **Delete** tabs
- Smooth animations, progress indicators, and per-operation status messages
- Export and import sync pair configurations as **JSON** for backup and transfer

---

## Screenshots

> _Add screenshots here once the app is built. Place images in `releases/screenshots/`._

---

## Architecture

CalCloner follows standard Android **MVVM + Unidirectional Data Flow** with clean separation of concerns:

```
┌─────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                │
│  MainActivity · SyncScreen · DeleteScreen           │
│  Components · Dialogs · Sheets                      │
└──────────────────────────┬──────────────────────────┘
                           │ observes StateFlow
┌──────────────────────────▼──────────────────────────┐
│               ViewModel (MainViewModel)             │
│  MainUiState · Business logic · Coroutine scope     │
└──────────────────────────┬──────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  CalendarSync│  │ SettingsRepository│  │  Background Sync  │
│  Engine      │  │ (DataStore)       │  │  WorkManager      │
│  (facade)    │  └──────────────────┘  │  JobScheduler     │
└──────┬───────┘                        │  BootReceiver     │
       │ delegates                      └──────────────────┘
┌──────▼───────────────────────────────┐
│            Engine Layer              │
│  CalendarProviderReader              │
│  CalendarEventWriter                 │
│  CalendarMaintenance                 │
└──────────────────────────────────────┘
```

### Package Structure

```
com.stripedlens.calcloner/
├── MainActivity.kt                   # Entry point, Compose host
├── CalendarModel.kt                  # Data classes (CalendarInfo, SyncPair, SyncEvent…)
├── CalendarSyncEngine.kt             # High-level sync façade (mutex, self-write guard)
├── CalendarSyncScheduler.kt          # WorkManager + JobScheduler wiring
├── CalendarSyncWorker.kt             # WorkManager background worker
├── CalendarSyncJobService.kt         # Reactive JobScheduler service
├── BootReceiver.kt                   # Re-schedule on boot
├── SettingsRepository.kt             # DataStore persistence for pairs + settings
│
├── engine/
│   ├── CalendarProviderReader.kt     # Android CalendarProvider read queries
│   ├── CalendarEventWriter.kt        # Insert/update/delete via ContentResolver
│   └── CalendarMaintenance.kt        # Purge + wipe operations
│
├── ui/
│   ├── screens/
│   │   ├── SyncScreen.kt             # Main sync pairs screen
│   │   └── DeleteScreen.kt           # Calendar maintenance / deletion screen
│   ├── components/
│   │   ├── AppBottomNavigationBar.kt # Sync ↔ Delete tab bar
│   │   ├── MasterSyncCard.kt         # Sync All controls card
│   │   ├── SyncPairCard.kt           # Individual sync pair card
│   │   ├── SyncEngineSettingsCard.kt # Interval + battery settings card
│   │   ├── CalendarDropdown.kt       # Reusable calendar picker
│   │   ├── SamsungBatteryBanner.kt   # Deep sleep guidance banner
│   │   └── sheets/
│   │       ├── AddEditSyncPairSheet.kt
│   │       └── DangerZoneSection.kt
│   ├── dialogs/
│   │   ├── DeletePairDialog.kt       # Confirm pair deletion with keep/remove choice
│   │   ├── DiscardChangesDialog.kt   # Discard unsaved edits prompt
│   │   ├── DisclaimerConsentDialog.kt
│   │   └── CalendarWarningDialogs.kt
│   └── theme/
│       ├── Theme.kt
│       ├── TitaniumMint.kt           # Custom color palette
│       └── Type.kt
│
├── viewmodel/
│   ├── MainViewModel.kt              # All business logic and state mutations
│   └── MainUiState.kt                # Immutable UI state + enums (AppTab, DeleteOperationType)
│
└── util/
    └── DateTimeUtils.kt
```

---

## Requirements

| Requirement | Minimum |
|---|---|
| Android OS | 8.0 (API 26) |
| Target SDK | 34 (Android 14) |
| Permissions | `READ_CALENDAR`, `WRITE_CALENDAR` |
| Optional | `POST_NOTIFICATIONS` (Android 13+) |

---

## Getting Started

### Build from Source

1. **Clone the repository**
   ```bash
   git clone https://github.com/theamazingaustin/CalCloner.git
   cd CalCloner
   ```

2. **Open in Android Studio** (Hedgehog 2023.1.1 or later recommended)

3. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or use **Run ▶** in Android Studio.

4. **Grant permissions** when prompted on first launch — CalCloner needs calendar read and write access to function.

### Install a Release APK

Download the latest `.apk` from the [Releases](https://github.com/theamazingaustin/CalCloner/releases) page and sideload it:

- Enable **Install Unknown Apps** for your file manager (`Settings → Apps → Special App Access`)
- Open the downloaded `.apk` and install

---

## Releases

Releases follow the naming scheme **`MAJOR.MINOR-MM.DD-BUILD`**:

| Segment | Meaning | Example |
|---|---|---|
| `MAJOR.MINOR` | Feature version | `2.1` |
| `MM.DD` | Build date (month.day) | `9.15` |
| `BUILD` | Daily build counter | `1` |

**Example tag:** `v2.1-9.15-1` → version 2.1, built on September 15, first build of the day.

A GitHub Actions workflow (`.github/workflows/release.yml`) automatically builds and attaches an APK to any release tagged in this format.

See [`releases/README.md`](releases/README.md) for the full release process.

---

## How Sync Works

1. **CalCloner reads** all events from the **source** calendar within the configured window (past N days → future N days).
2. Each event is **compared** against any existing cloned copy in the **destination** calendar using a unique tag embedded in the event description: `[CalCloner-ID: <pairId>:<eventId>]`.
3. **New events** are inserted. **Changed events** are updated. **Removed events** (no longer in the source window) are deleted from the destination.
4. Recurring event **exceptions and cancellations** are propagated correctly.
5. The source calendar is **never modified**.

### Sync Modes

| Mode | Trigger |
|---|---|
| **Manual** | Tap "Sync Now" on any pair or "Sync All" |
| **Periodic** | WorkManager fires every N minutes (15 min minimum, enforced by Android) |
| **Reactive** | JobScheduler detects a calendar content change and syncs within seconds |
| **On Boot** | BootReceiver re-schedules background jobs after device restart |

---

## Privacy

- **100% on-device.** No network requests are ever made. No data leaves the phone.
- CalCloner reads from and writes to the Android `CalendarProvider` only.
- No analytics, no telemetry, no accounts.

---

## Contributing

This is a private project. If you have access and want to contribute:

1. Branch from `v2.1`
2. Keep commits small and focused — one change per commit
3. Follow the existing code style (Kotlin, Material 3 Compose, MVVM)
4. Run `graft build` after significant changes to keep the code graph fresh

---

## License

Private — all rights reserved. Not for redistribution.


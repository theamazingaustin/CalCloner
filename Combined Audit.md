# Combined Codebase Audit & Improvement Plan — CalCloner

> **Generated:** 2026-09-13 (Updated)  
> **Source Documents Consolidated:**  
> 1. `Codebase Audit` (Primary Audit)  
> 2. `Secondary Codebase Audit` (Clean-Slate Architectural & Lifecycle Audit)  
> **Scope:** Full Android codebase (Kotlin source, AndroidManifest, Gradle configs, WorkManager, JobScheduler, ContentProvider, Compose UI layer).

---

## 📑 Table of Contents
1. [Overview & Source Cross-Reference Matrix](#-source-cross-reference-matrix)
2. [Section A: Active Critical & High-Severity Bugs](#-section-a-active-critical--high-severity-bugs)
3. [Section B: Active Concurrency, Persistence & Lifecycle Issues](#-section-b-active-concurrency-persistence--lifecycle-issues)
4. [Section C: Active Calendar Engine & Model Quirks](#-section-c-active-calendar-engine--model-quirks)
5. [Section D: Active High-Value Features & UX Polish](#-section-d-active-high-value-features--ux-polish)
6. [Section E: Future Features & Deferred Improvements](#-section-e-future-features--deferred-improvements)
7. [Implementation Recommendations](#-implementation-recommendations)

---

## 📊 Source Cross-Reference Matrix

| # | Item / Finding | Found In | Category | Active vs Future Status |
| :--- | :--- | :--- | :--- | :--- |
| **1** | `BootReceiver` fails to reschedule periodic sync when interval > 0 | `Secondary Audit (1.1)` | Background Lifecycle | 🔴 **Active Critical** |
| **2** | Reactive `JobService` runs sync but never updates master sync timestamp | `Secondary Audit (1.2)` & `Codebase Audit (#1)` | Background Telemetry | 🔴 **Active Critical** |
| **3** | In-memory `lastKnownFingerprint` lost on process death | `Secondary Audit (1.3)` | Background Performance | 🔴 **Active High** |
| **6** | Absence of Mutex on `SettingsRepository` read-modify-write cycles (DataStore overwrite race) | `Both` (Primary #13, Secondary 2.1) | Concurrency / Persistence | 🟠 **Active Medium** |
| **7** | `ContentProviderResult.count` ignored in batch deletes (inflated delete counts) | `Both` (Primary #4, Secondary 2.2) | Engine Accuracy | 🟠 **Active Medium** |
| **8/9**| Lock application in portrait mode in `AndroidManifest.xml` (immediate rotation fix) | `Secondary Audit (2.3/2.4)` | Activity Lifecycle | 🟠 **Active Immediate** |
| **10** | Overly aggressive sync flags on every routine automated calendar write pass | `Secondary Audit (2.5)` | System Health / Google Sync | 🟠 **Active Medium** |
| **11** | Dead legacy single-pair flow properties in `SettingsRepository` | `Both` (Primary #2 & #16, Secondary 3.2) | Code Cleanliness | 🟡 **Active Low** |
| **12** | Inconsistent "Default 30 days" storage (`null` vs. explicit `30`) across save/load cycles | `Codebase Audit (#3)` | Data Model Drift | 🟡 **Active Low** |
| **13** | UUID generated at default-value time in `SyncPair` data class constructor | `Codebase Audit (#17)` | Architecture Footgun | 🟡 **Active Low** |
| **16** | No error state / visual failure badge on card when sync fails | `Both` (Primary #6, Secondary 4) | UX / Error Visibility | 🟠 **Active Medium** |
| **17** | No configuration backup or restore (JSON Export / Import) | `Both` (Primary #8, Secondary 4) | Feature Request | 🟠 **Active High Value** |
| **19** | Samsung One UI "Deep Sleep" silent background throttling guidance | `Secondary Audit (4)` | Android OS Integration | 🟡 **Active Medium** |
| **21d**| Sticky header scroll shadow & elevation divider | `Codebase Audit (#22)` | UX Polish | 🟡 **Active Polish** |
| **4** | Broken fallback on target calendar signature matching when `CUSTOM_APP_URI` is stripped | `Secondary Audit (1.4)` | Engine / Cloud Sync | 📋 **Future Features** |
| **5** | Self-trigger suppression window expires before large sync finishes writing (redundant 2nd sync) | `Codebase Audit (#5)` | Engine / JobScheduler | 📋 **Future Features** |
| **8f** | Full ViewModel architecture for UI state persistence across fold/unfold & split-screen | `Secondary Audit (2.3)` | Architecture | 📋 **Future Features** |
| **9f** | WorkManager expedited background delegation for manual UI syncs | `Secondary Audit (2.4)` | Coroutines / WorkManager | 📋 **Future Features** |
| **14** | Monolithic files exceeding 1,500 lines (`AddEditSyncPairSheet`, `CalendarSyncEngine`, `MainActivity`) | `Both` (Primary #14, Secondary 3.1) | Architecture Maintainability | 📋 **Future Features** |
| **15** | Zero automated unit and regression test coverage | `Both` (Primary #15, Secondary 3.3) | Quality Assurance | 📋 **Future Features** |
| **18** | No safety net or confirmation preview before permanent event deletion | `Both` (Primary #12, Secondary 4) | Safety / UX | 📋 **Future Features** |
| **20** | Custom keyword exclusion filter rules (e.g. skip "Private", "Hold") | `Secondary Audit (4)` | Feature Request | 📋 **Future Features** |
| **21a**| Master Sync visual feedback (smooth progress bar interpolation) | `Codebase Audit (#19)` | UX Polish | 📋 **Future Features** |
| **21b**| Calendar-lock context in edit sheet (route preservation explanation) | `Codebase Audit (#20)` | UX Polish | 📋 **Future Features** |
| **21c**| "Never Synced" badge on newly created pairs ("Ready to sync") | `Codebase Audit (#21)` | UX Polish | 📋 **Future Features** |
| **22** | Refactor `MainActivity` into a `ViewModel` | `Both` (Deferred #1) | Architecture | 📋 **Future (Top Priority)** |
| **23** | First-run onboarding / walkthrough tutorial | `Codebase Audit (#10)` | Onboarding | 📋 **Future (#2 Priority)** |
| **24** | Conflict resolution for target-modified events | `Codebase Audit (#7)` | Sync Conflict | ❌ **Deferred (Not Needed)** |
| **25** | Success notifications for background sync | `Codebase Audit (#11)` | Notifications | ❌ **Deferred (Silent by Design)** |
| **26** | Historical sync logbook per pair | `Codebase Audit (#9)` | Logging | ❌ **Deferred (Low Value)** |

---

## 🔴 Section A: Active Critical & High-Severity Bugs

---

### Issue 1: `BootReceiver` Fails to Reschedule Periodic Sync When Interval > 0
- **Source:** Found in `Secondary Codebase Audit` (Section 1.1).
- **Files Affected:** [`BootReceiver.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/BootReceiver.kt#L22-L24)

#### Detailed Technical Explanation
In `BootReceiver.onReceive()`:
```kotlin
val interval = repo.syncIntervalFlow.first()
val pairs = repo.syncPairsFlow.first().filter { it.isEnabled }
val syncOnLow = repo.syncOnLowBatteryFlow.first()
if (pairs.isNotEmpty() && interval == 0) {
    CalendarSyncScheduler.scheduleReactiveSync(context, syncOnLow)
}
```
When the device boots (`ACTION_BOOT_COMPLETED`) or the package is updated (`ACTION_MY_PACKAGE_REPLACED`), the receiver inspects `interval`. If the user configured periodic WorkManager sync (e.g., 15 minutes, 30 minutes, 1 hour), `interval > 0`. The conditional `if (pairs.isNotEmpty() && interval == 0)` evaluates to `false` and exits without doing anything.

#### In Simple Terms
When your phone restarts, CalCloner only restarts automatic syncing if your interval was set to "Instant (Event-driven)". If you set it to sync every 15 minutes, 30 minutes, or 1 hour, background sync completely dies on reboot and never runs again until you open the CalCloner app manually.

#### Proposed Solution
Implement complete branching logic in `BootReceiver`:
```kotlin
if (pairs.isNotEmpty()) {
    if (interval == 0) {
        CalendarSyncScheduler.scheduleReactiveSync(context, syncOnLow)
    } else {
        CalendarSyncScheduler.scheduleSync(context, interval, syncOnLow)
    }
} else {
    CalendarSyncScheduler.cancelReactiveSync(context)
    CalendarSyncScheduler.cancelSync(context)
}
```

---

### Issue 2: Reactive `JobService` Executes Sync but Never Updates Master Sync Timestamp
- **Source:** Found in `Secondary Codebase Audit` (Section 1.2) & related to `Codebase Audit` (#1 & #18).
- **Files Affected:** [`CalendarSyncJobService.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncJobService.kt#L157-L177)

#### Detailed Technical Explanation
`CalendarSyncJobService` responds to `CalendarContract.Events.CONTENT_URI` triggers. When an active pair's source calendar changes, it calls `CalendarSyncEngine.syncAllPairs(applicationContext, affectedPairs)`. While `syncAllPairs` internally updates the individual pair's `lastSyncTime`, `CalendarSyncJobService` never calls `repo.saveLastSync(timestamp, summary)`. Consequently, the main screen's top Master Sync card timestamp (`lastSyncTimeFlow`) remains completely frozen.

#### In Simple Terms
When background sync runs automatically, it copies the events correctly, but it forgets to update the master "Last Synced: Today at 4:44 PM" header on the main screen. The screen makes it look like the app hasn't synced in hours or days, even though it just synced in the background seconds ago.

#### Proposed Solution
In `CalendarSyncJobService.kt`:
```kotlin
val results = CalendarSyncEngine.syncAllPairs(applicationContext, affectedPairs)
val totalInserted = results.values.sumOf { it.insertedCount }
val totalUpdated = results.values.sumOf { it.updatedCount }
val totalDeleted = results.values.sumOf { it.deletedCount }
val summary = "Auto-sync: $totalInserted added, $totalUpdated updated, $totalDeleted removed."
repo.saveLastSync(System.currentTimeMillis(), summary)
```

---

### Issue 3: In-Memory `lastKnownFingerprint` Lost on Process Death
- **Source:** Found in `Secondary Codebase Audit` (Section 1.3).
- **Files Affected:** [`CalendarSyncJobService.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncJobService.kt#L30-L31)

#### Detailed Technical Explanation
In `CalendarSyncJobService`:
```kotlin
companion object {
    @Volatile
    private var lastKnownFingerprint: String = ""
}
```
Android's low memory killer regularly kills background service processes once a job completes. When the OS wakes `CalendarSyncJobService` for a general content provider change (`android.intent.action.PROVIDER_CHANGED`), `lastKnownFingerprint` reinitializes to `""`. Safeguard 4 (`currentFingerprint == lastKnownFingerprint`) fails, forcing an expensive full table scan and diffing loop even when zero source calendar events changed.

#### In Simple Terms
The app creates a summary "fingerprint" of your calendar so it can quickly check if anything actually changed before doing heavy work. But because this fingerprint is kept in volatile RAM, Android erases it every time the app goes to sleep. On the next wakeup, the app has amnesia, thinks the calendar might have changed, and wastes battery doing a full calendar scan.

#### Proposed Solution
Store the fingerprint in Android's standard `SharedPreferences` (which provides fast, synchronous read/write operations without suspending coroutines). On process wakeup, load the persisted string, compare it against the freshly calculated fingerprint, and save back the new value only when modified.

---

## 🟠 Section B: Active Concurrency, Persistence & Lifecycle Issues

---

### Issue 6: Absence of Mutex on `SettingsRepository` Read-Modify-Write Cycles (DataStore Overwrite Race)
- **Source:** Found in `Both` (`Codebase Audit` #13, `Secondary Codebase Audit` Section 2.1).
- **Files Affected:** [`SettingsRepository.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/SettingsRepository.kt#L169-L225)

#### Detailed Technical Explanation
All sync pairs are stored as a single serialized JSON array string under the DataStore key `SYNC_PAIRS_JSON`. Operations such as `upsertSyncPair`, `togglePairEnabled`, `deleteSyncPair`, and `updatePairSyncStatus` execute non-atomic read-modify-write sequences:
```kotlin
context.dataStore.edit { preferences ->
    val currentPairs = SyncPair.listFromJsonString(preferences[SYNC_PAIRS_JSON]).toMutableList()
    // modify currentPairs list
    preferences[SYNC_PAIRS_JSON] = SyncPair.listToJsonString(currentPairs)
}
```
If a background worker finishes syncing and updates pair timestamps (`updatePairSyncStatus`) at the same moment the user toggles a pair's enable switch in the UI (`togglePairEnabled`), the two coroutines race. Whichever transaction commits last will overwrite the other's modifications.

#### In Simple Terms
All of your sync pairs are kept in a single document. If the background sync updates a pair's last-synced time at the exact split-second you flip a switch on the screen, one of the two updates will overwrite and erase the other.

#### Proposed Solution
Add a singleton Kotlin `Mutex` inside `SettingsRepository` companion object to serialize all mutations on `SYNC_PAIRS_JSON`. This completely eliminates the race condition immediately with zero risk of database migration errors.

---

### Issue 7: `ContentProviderResult.count` Ignored in Batch Deletes (Inflated Delete Counts)
- **Source:** Found in `Both` (`Codebase Audit` #4, `Secondary Codebase Audit` Section 2.2).
- **Files Affected:** [`CalendarSyncEngine.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncEngine.kt#L929-L930)

#### Detailed Technical Explanation
When stale clones are purged via ContentProvider batch deletion:
```kotlin
val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
deletedCount += results.size
```
`results.size` reflects the number of `ContentProviderOperation` requests submitted in the batch, not the number of rows actually deleted. If an event was already deleted upstream by cloud sync, `results[i].count` is 0.

#### In Simple Terms
When CalCloner deletes old cloned events, it counts how many delete requests it sent rather than how many events actually got deleted. If an event was already gone, CalCloner still counts it as deleted, reporting inaccurate numbers like "10 removed" when only 6 were removed.

#### Proposed Solution
Replace `deletedCount += results.size` with:
```kotlin
deletedCount += results.sumOf { (it.count ?: 0).toInt() }
```

---

### Issues 8 & 9 (Immediate Fix): Lock Application Orientation to Portrait
- **Source:** Found in `Secondary Codebase Audit` (Sections 2.3 & 2.4).
- **Files Affected:** [`AndroidManifest.xml`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/AndroidManifest.xml)

#### Detailed Technical Explanation
Screen rotations destroy and recreate `MainActivity`, wiping uncommitted edit sheet state in Compose `remember` and cancelling coroutines launched in `rememberCoroutineScope()`.

#### In Simple Terms
Locking the app in portrait mode immediately stops phones from rotating sideways, preventing unexpected screen reloads from closing the edit sheet or interrupting syncs.

#### Proposed Solution
In `AndroidManifest.xml`, configure:
```xml
android:screenOrientation="portrait"
```
*(Note: Full ViewModel state persistence and WorkManager delegation for foldable screen transitions are moved to Future Features).*

---

### Issue 10: Overly Aggressive Sync Flags on Routine Automated Calendar Writes
- **Source:** Found in `Secondary Codebase Audit` (Section 2.5).
- **Files Affected:** [`CalendarSyncEngine.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncEngine.kt#L961-L972)

#### Detailed Technical Explanation
After every sync pass, `requestSync()` is called with:
```kotlin
putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
putBoolean(ContentResolver.SYNC_EXTRAS_OVERRIDE_TOO_MANY_DELETIONS, true)
putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
```
Bypassing exponential backoff and overriding deletion safeguards on automated background syncs can cause Google Calendar sync adapters to trigger cloud throttling or excessive battery drain if frequent calendar edits occur.

#### In Simple Terms
Every time CalCloner syncs, it forces Google's cloud sync engine to bypass all safety checks and rate limits. If something changes frequently, Google can temporarily throttle or pause your calendar sync.

#### Proposed Solution
Add an `isManualWipe: Boolean = false` parameter to the sync trigger. Only attach `IGNORE_BACKOFF` and `OVERRIDE_TOO_MANY_DELETIONS` when `isManualWipe` is true. For normal background syncs, use standard expedited sync.

---

## 🟡 Section C: Active Calendar Engine & Model Quirks

---

### Issue 11: Dead Legacy Single-Pair Flow Properties in `SettingsRepository`
- **Source:** Found in `Both` (`Codebase Audit` #2 & #16, `Secondary Codebase Audit` Section 3.2).
- **Files Affected:** [`SettingsRepository.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/SettingsRepository.kt#L76-L90)

#### Detailed Technical Explanation
Lines 76–90 of `SettingsRepository.kt` define `fromCalendarIdFlow`, `toCalendarIdFlow`, `fromCalendarNameFlow`, `toCalendarNameFlow`, `syncDaysPastFlow`, and `syncDaysFutureFlow`. These properties are artifacts from CalCloner 1.0 (before multi-pair support). No UI component or engine function reads them.

#### In Simple Terms
Old leftover code from when CalCloner could only sync one calendar pair is still sitting in the settings file doing nothing.

#### Proposed Solution
Delete the dead flow properties in `SettingsRepository.kt` while retaining `migrateSinglePairToMultiPairIfNeeded()`.

---

### Issue 12: Inconsistent "Default 30 Days" Storage Across Save/Load Cycles
- **Source:** Found in `Codebase Audit` (#3).
- **Files Affected:** [`CalendarModel.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarModel.kt#L54-L55)

#### Detailed Technical Explanation
In `SyncPair`:
```kotlin
val daysPast: Int? = null,
val daysFuture: Int? = null,
```
New pairs store `null`, which the engine evaluates as `30`. However, when a pair is opened in `AddEditSyncPairSheet.kt`, the sliders initialize to `30` and upon saving write explicit `30` back into the model. Thus, unedited pairs store `null` while edited pairs store `30`. If default logic ever changes in the future, unedited pairs would drift while edited pairs remain fixed.

#### In Simple Terms
A brand-new pair has "no number" saved for days (which means default 30). But if you open and save it without changing the slider, it saves an explicit "30". So some pairs store "default" and others store "30" even though they are set to the exact same thing.

#### Proposed Solution
Standardize `daysPast` and `daysFuture` as non-null integers with default `30` (`val daysPast: Int = 30`, `val daysFuture: Int = 30`). This eliminates nullability handling across the UI, JSON serialization, and sync engine.

---

### Issue 13: UUID Generated as Default Parameter in `SyncPair` Data Class Constructor
- **Source:** Found in `Codebase Audit` (#17).
- **Files Affected:** [`CalendarModel.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarModel.kt#L48)

#### Detailed Technical Explanation
`data class SyncPair(val id: String = java.util.UUID.randomUUID().toString(), ...)`. Because `id` has a default value, calling constructor methods or cloning instances without explicitly specifying `id` can silently generate a new UUID, creating orphaned database clones or ghost pairs.

#### In Simple Terms
The pair's unique ID is automatically generated as a default value. If code accidentally copies or creates a pair without passing the existing ID, a brand-new ID is silently created with no compiler error.

#### Proposed Solution
Make `id: String` a required parameter with no default in `SyncPair`, and introduce a companion factory method `SyncPair.createNew(...)` that explicitly creates a new UUID when adding a new pair.

---

## 🟠 Section D: Active High-Value Features & UX Polish

---

### Issue 16: No Error State / Visual Warning on Card When Sync Fails
- **Source:** Found in `Both` (`Codebase Audit` #6, `Secondary Codebase Audit` Section 4).
- **Files Affected:** [`SyncPairCard.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/ui/components/SyncPairCard.kt)

#### Detailed Technical Explanation
If a sync pair fails (e.g. target calendar was deleted or permissions revoked), `pair.lastSyncStatus` contains the error string (e.g. "Calendar Not Found" or "Permission denied"). However, `SyncPairCard.kt` only renders the timestamp and does not inspect `lastSyncStatus` for error prefixes.

#### In Simple Terms
If one of your calendar pairs fails to sync, its card looks completely normal. You would have no idea it failed unless you were actively watching the screen when it happened.

#### Proposed Solution
In `SyncPairCard.kt`, if `pair.lastSyncStatus?.startsWith("Failed") == true || pair.lastSyncStatus?.contains("Not Found") == true`, render a compact warning chip/row in `TitaniumMint.Amber500` or soft red showing the error message and an exclamation badge.

---

### Issue 17: No Configuration Backup or Restore (JSON Export / Import)
- **Source:** Found in `Both` (`Codebase Audit` #8, `Secondary Codebase Audit` Section 4).
- **Files Affected:** Navigation Drawer / `MainActivity.kt`

#### Detailed Technical Explanation
All sync pair configurations reside exclusively in local app DataStore. If the user changes devices, factory resets, or reinstalls CalCloner, all sync routes, custom titles, and filters must be reconfigured manually.

#### In Simple Terms
There is no "Export" or "Import" button. If you get a new phone, you have to recreate all your sync pairs by hand.

#### Proposed Solution
Add "Export Configuration" and "Import Configuration" in the navigation drawer:
- **Export:** Serializes `syncPairsFlow.first()` to a timestamped JSON file (`calcloner_backup_YYYYMMDD.json`) and triggers `Intent.ACTION_SEND` via Android's share sheet.
- **Import:** Uses Compose `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` to select a JSON file, validates the structure, and merges or replaces pairs in `SettingsRepository`.

---

### Issue 19: Samsung One UI "Deep Sleep" Silent Background Throttling Guidance
- **Source:** Found in `Secondary Codebase Audit` (Section 4).
- **Files Affected:** Main Drawer / Settings

#### Detailed Technical Explanation
Samsung One UI aggressively places apps into "Deep Sleeping Apps" if not opened every few days. When deep-slept, Samsung blocks `JobScheduler` alarms and `WorkManager` background triggers, completely halting background sync without user awareness.

#### In Simple Terms
Samsung phones automatically put background apps to sleep after a few days, which silently kills CalCloner's ability to sync in the background.

#### Proposed Solution
Check `Build.MANUFACTURER.contains("samsung", ignoreCase = true)` and `PowerManager.isIgnoringBatteryOptimizations()`. If optimizations are active, show a subtle banner in settings/drawer linking directly to `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

### Issue 21d: Sticky Header Scroll Shadow
- **Source:** Found in `Codebase Audit` (#22).
- **Files Affected:** [`MainActivity.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/MainActivity.kt)
- **Explanation:** When scrolling down the list of sync pair cards, the sticky top header should cast a subtle elevation shadow or render a sleek divider line (`BorderStroke(1.dp, TitaniumMint.ContainerBorder)`) so content cleanly passes underneath.

---

## 📋 Section E: Future Features & Deferred Improvements

> **Note:** The following items have been designated by the user as **Future Features** or deliberate deferred improvements to be implemented in future phases.

---

### Future Feature 1: [#14] Refactor `MainActivity` into a `ViewModel`
- **Original Source:** `Codebase Audit` (#14) & `Secondary Codebase Audit` (2.3, 2.4, 3.1).
- **User Priority:** **Top Priority of Future Features**.
- **Explanation:** Moving operational state, sync coroutines, and pair flows into an Android Jetpack `MainViewModel` provides architectural stability and prevents any UI reset across foldable screen transitions or split-screen resizing.

---

### Future Feature 2: [#10] First-Run Onboarding / Walkthrough Tutorial
- **Original Source:** `Codebase Audit` (#10).
- **User Priority:** **#2 Priority of Future Features**.
- **Explanation:** A clean 3-step introduction explaining Source vs. Target calendars, why Route Locking is required, and how background sync works.

---

### Future Feature 3: Issue 5 — Self-Trigger Suppression Window (Redundant 2nd Sync)
- **Original Source:** `Codebase Audit` (#5).
- **Files Affected:** [`CalendarSyncJobService.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncJobService.kt)
- **Explanation:** Extending the 10-second self-write window to 45 seconds to avoid a second redundant (no-op) sync pass if Android's notification arrives late.

---

### Future Feature 4: Issues 8f & 9f — Full Foldable State Persistence & WorkManager Delegation
- **Original Source:** `Secondary Codebase Audit` (Sections 2.3 & 2.4).
- **Files Affected:** `MainActivity.kt`, `CalendarSyncWorker.kt`
- **Explanation:** Moving full sheet state into `SavedStateHandle` / ViewModel and offloading manual UI sync execution to WorkManager expedited tasks so foldable open/close operations never affect state.

---

### Future Feature 5: Issue 4 — Broken Fallback on Target Signature Matching When `CUSTOM_APP_URI` is Stripped
- **Original Source:** `Secondary Codebase Audit` (Section 1.4).
- **Files Affected:** [`CalendarSyncEngine.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncEngine.kt)
- **Explanation:** Fallback matching target events with identical `title`, `dtStart`, `dtEnd`, and `allDay` attributes if cloud servers strip custom tags.

---

### Future Feature 6: Issue 14 — Monolithic Files Exceeding 1,500 Lines
- **Original Source:** `Both` (`Codebase Audit` #14, `Secondary Codebase Audit` Section 3.1).
- **Files Affected:** `AddEditSyncPairSheet.kt`, `CalendarSyncEngine.kt`, `MainActivity.kt`.
- **Explanation:** Decomposing massive files into modular packages (`sync/engine/`, `ui/sheets/`).

---

### Future Feature 7: Issue 15 — Zero Automated Unit & Regression Test Coverage
- **Original Source:** `Both` (`Codebase Audit` #15, `Secondary Codebase Audit` Section 3.3).
- **Files Affected:** `app/src/test/`
- **Explanation:** Unit tests for `SyncPair` JSON serialization, route cycle detection, UTC all-day timestamp alignment, and batch delete math.

---

### Future Feature 8: Issue 18 — Safety Net & Confirmation Preview Before Permanent Event Deletion
- **Original Source:** `Both` (`Codebase Audit` #12, `Secondary Codebase Audit` Section 4).
- **Files Affected:** [`AddEditSyncPairSheet.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/ui/components/AddEditSyncPairSheet.kt)
- **Explanation:** Preview dialog displaying event count and first 3 titles, followed by a 5-second cancel snackbar before batch deletion executes.

---

### Future Feature 9: Issue 20 — Custom Keyword Exclusion Filter Rules
- **Original Source:** `Secondary Codebase Audit` (Section 4).
- **Files Affected:** `CalendarModel.kt`, `CalendarSyncEngine.kt`
- **Explanation:** Keyword filters (e.g. "Private", "Hold") to skip specific events during sync.

---

### Future Feature 10: Issue 21a — Master Sync Visual Feedback (Smooth Progress Bar)
- **Original Source:** `Codebase Audit` (#19).
- **Files Affected:** `MainActivity.kt`
- **Explanation:** Smooth interpolation of progress fraction when syncing multiple pairs.

---

### Future Feature 11: Issue 21b — Calendar-Lock Context in Edit Sheet
- **Original Source:** `Codebase Audit` (#20).
- **Files Affected:** `AddEditSyncPairSheet.kt`
- **Explanation:** Explanatory subtitle on why source and target calendars are locked on existing pairs.

---

### Future Feature 12: Issue 21c — "Never Synced" Badge
- **Original Source:** `Codebase Audit` (#21).
- **Files Affected:** `SyncPairCard.kt`
- **Explanation:** Neutral status badge reading *"Ready to sync"* on newly created pairs.

---

### Deferred Items (Deliberately Excluded per User Directive)
- **[#7] Sync Conflict Resolution for Target-Modified Events:** Not needed. CalCloner is strictly a one-way mirror; cloned events are not user-edited.
- **[#11] Success Notifications for Background Sync:** Not needed. CalCloner must run silently in the background; notifications only fire on error.
- **[#9] Historical Sync Logbook Per Pair:** Not needed. Minimal utility for a lightweight background sync app.

---

## 🎯 Implementation Recommendations

Execution should proceed in the following prioritized sequence:

1. **Phase 1: Critical Background Plumbing (Zero UI Risk)**
   - Fix `BootReceiver.kt` interval check (Issue 1).
   - Add master sync telemetry persistence to `CalendarSyncJobService.kt` (Issue 2).
   - Fix `deletedCount` calculation in `CalendarSyncEngine.kt` (Issue 7).
   - Add `Mutex` serialization in `SettingsRepository.kt` (Issue 6).
   - Clean up dead legacy flows in `SettingsRepository.kt` (Issue 11).

2. **Phase 2: Engine Safeguards & Model Integrity**
   - Normalize `daysPast` / `daysFuture` defaults in `CalendarModel.kt` (Issue 12 & 13).
   - Restrict aggressive sync flags to manual wipe operations (Issue 10).
   - Lock activity orientation to portrait in `AndroidManifest.xml` (Issues 8 & 9).

3. **Phase 3: High-Value Features & UX Enhancements**
   - Add failed sync warning badge on cards (Issue 16).
   - Implement JSON Configuration Export & Import (Issue 17).
   - Add Samsung One UI Deep Sleep guidance banner in settings/drawer (Issue 19).
   - Add sticky header scroll elevation / border (Issue 21d).

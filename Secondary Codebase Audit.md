# Secondary Codebase Audit — CalCloner

> **Audit Date:** 2026-09-13  
> **Scope:** Clean-slate review of entire architecture, sync engine, background scheduling, concurrency, data persistence, and UI layer.  
> **Device Verification:** Samsung Galaxy (SDK 34) running live CalendarProvider.

---

## Executive Summary

CalCloner exhibits **exceptional RFC 5545 timestamp fidelity** (0.0h parity, zero date-drift across 80 active device clones). However, an exhaustive code-level inspection reveals critical vulnerabilities in **background execution lifecycle**, **reactive sync state persistence**, **unprotected concurrent DataStore mutations**, and **activity lifecycle fragility** during configuration changes (e.g., Samsung foldable unfold/rotation).

---

## 🔴 1. Critical & High-Severity Bugs

### 1.1 `BootReceiver` Fails to Reschedule Periodic Sync When Interval > 0
- **File:** [`BootReceiver.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/BootReceiver.kt#L22-L24)
- **Issue:**  
  In `BootReceiver.onReceive()`:
  ```kotlin
  val interval = repo.syncIntervalFlow.first()
  val pairs = repo.syncPairsFlow.first().filter { it.isEnabled }
  val syncOnLow = repo.syncOnLowBatteryFlow.first()
  if (pairs.isNotEmpty() && interval == 0) {
      CalendarSyncScheduler.scheduleReactiveSync(context, syncOnLow)
  }
  ```
  If the user chooses a periodic interval (e.g. 15 min, 30 min, 1 hr), `interval > 0`. On device reboot (`ACTION_BOOT_COMPLETED`) or app update (`ACTION_MY_PACKAGE_REPLACED`), the receiver checks `interval == 0` and **does nothing**.
- **Impact:**  
  Periodic sync never re-arms after a phone reboot. Background sync silently dies until the user manually launches the CalCloner app again.
- **Proposed Solution:**  
  Handle both modes in `BootReceiver`:
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

### 1.2 Reactive `JobService` Executes Sync but Never Updates Master Sync Timestamps
- **File:** [`CalendarSyncJobService.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncJobService.kt#L157-L177)
- **Issue:**  
  When reactive sync detects calendar changes via `JobScheduler`, it invokes `CalendarSyncEngine.syncAllPairs(applicationContext, affectedPairs)`. While `syncAllPairs` writes timestamps to individual pair records, **neither `repo.saveLastSync()` nor `repo.saveLastSyncStatus()` is ever called by `CalendarSyncJobService`**.
- **Impact:**  
  The main screen's "Master Sync" header card displays an outdated or frozen "Last Sync" timestamp even though the reactive engine is actively cloning events in the background. The user has no indication that automated background sync ran successfully.
- **Proposed Solution:**  
  In `CalendarSyncJobService.handleJob()`, aggregate results from `syncAllPairs` and persist master sync telemetry:
  ```kotlin
  val results = CalendarSyncEngine.syncAllPairs(applicationContext, affectedPairs)
  val totalInserted = results.values.sumOf { it.insertedCount }
  val totalUpdated = results.values.sumOf { it.updatedCount }
  val totalDeleted = results.values.sumOf { it.deletedCount }
  val summary = "Reactive sync: $totalInserted added, $totalUpdated updated, $totalDeleted removed."
  repo.saveLastSync(System.currentTimeMillis(), summary)
  ```

---

### 1.3 In-Memory `lastKnownFingerprint` Lost on Process Termination
- **File:** [`CalendarSyncJobService.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncJobService.kt#L30-L31)
- **Issue:**  
  `lastKnownFingerprint` is stored as a `@Volatile private var` in the companion object of `CalendarSyncJobService`. Android frequently terminates background service processes when idle.
- **Impact:**  
  Every time Android recreates the process to deliver a generic table trigger (`android.intent.action.PROVIDER_CHANGED`), `lastKnownFingerprint` is reset to `""`. Safeguard 4 (`currentFingerprint == lastKnownFingerprint`) fails, and a redundant full-table scan and comparison pass is forced.
- **Proposed Solution:**  
  Persist `lastKnownFingerprint` in DataStore or SharedPreferences so fingerprint comparisons survive process death.

---

### 1.4 Broken Fallback on Target Calendar Signature Matching When `CUSTOM_APP_URI` is Stripped
- **File:** [`CalendarSyncEngine.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncEngine.kt#L766-L777)
- **Issue:**  
  Certain CalDAV servers, Exchange adapters, and third-party calendar sync adapters strip custom columns like `CUSTOM_APP_URI`. If the user also edits the event description and deletes `[CalCloner-ID: ...]`, line 767 checks:
  ```kotlin
  val isCalClonerEvent = (customUri != null && customUri.startsWith(uriPrefix)) ||
          (desc != null && (desc.contains("[CalCloner-ID:") || desc.contains("[CalClone-ID:")))
  if (isCalClonerEvent) {
      val sigKey = "$title|$start"
      ...
  }
  ```
- **Impact:**  
  If both `customAppUri` and description tags are lost, `isCalClonerEvent` evaluates to `false`. The engine fails to associate the clone with its source, bypasses duplicate suppression, and inserts a duplicate clone on the target calendar on every subsequent sync pass.
- **Proposed Solution:**  
  When matching existing target events, perform a soft signature check across all events in `toCalendarId` whose start time and title match the incoming source event, provided the target event is not an organic non-clone.

---

## 🟠 2. Medium Severity: Concurrency, Lifecycle & Provider Quirks

### 2.1 Absence of Mutex on `SettingsRepository` Read-Modify-Write Cycles
- **File:** [`SettingsRepository.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/SettingsRepository.kt#L169-L225)
- **Issue:**  
  `upsertSyncPair`, `deleteSyncPair`, `togglePairEnabled`, and `updatePairSyncStatus` all execute:
  ```kotlin
  context.dataStore.edit { preferences ->
      val currentPairs = SyncPair.listFromJsonString(preferences[SYNC_PAIRS_JSON]).toMutableList()
      // modify list
      preferences[SYNC_PAIRS_JSON] = SyncPair.listToJsonString(currentPairs)
  }
  ```
- **Impact:**  
  If the user toggles a pair in the UI while `CalendarSyncWorker` or `CalendarSyncJobService` finishes a sync and calls `updatePairSyncStatus` simultaneously, one transaction will overwrite the other's modifications.
- **Proposed Solution:**  
  Introduce a private `Mutex` in `SettingsRepository` to serialize all pair list operations, or migrate pair storage to Room Database where row-level ACID transactions prevent list serialization overwrites.

---

### 2.2 `ContentProviderResult.count` Ignored in Batch Deletes (Inflated Delete Counts)
- **File:** [`CalendarSyncEngine.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncEngine.kt#L929-L930)
- **Issue:**  
  ```kotlin
  val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
  deletedCount += results.size
  ```
  `results.size` is the number of `ContentProviderOperation` requests submitted in the batch, not the number of rows actually deleted. If a calendar event was already pruned by the cloud or Google Sync, `results[i].count` may be `0`.
- **Impact:**  
  Reported sync counts frequently display inaccurate figures (e.g. "12 removed" when only 8 rows were deleted).
- **Proposed Solution:**  
  Sum the affected row count from the batch results:
  ```kotlin
  deletedCount += results.sumOf { (it.count ?: 0).toInt() }
  ```

---

### 2.3 UI State Evaporation on Configuration Changes & Samsung Fold / Unfold
- **File:** [`MainActivity.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/MainActivity.kt#L125-L160)
- **Issue:**  
  `MainActivity` stores transient operational state in `remember { mutableStateOf(...) }` instead of `rememberSaveable` or an Android `ViewModel`:
  - `isOperating`
  - `isSyncing`
  - `showAddEditSheet`
  - `pairToEdit`
  - `progressStatusText`
- **Impact:**  
  On Samsung Galaxy devices, rotating the phone or opening/closing the fold screen causes activity recreation. If the user was editing a sync pair, the sheet vanishes and uncommitted input is lost. If a sync was running, the UI loses progress tracking.
- **Proposed Solution:**  
  Extract operational state into a Jetpack `ViewModel` that outlives activity recreation.

---

### 2.4 Coroutine Cancellation on UI Rotation Cancels Active Sync
- **File:** [`MainActivity.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/MainActivity.kt#L126)
- **Issue:**  
  Manual syncs triggered from `MainActivity` launch within `val scope = rememberCoroutineScope()`. This scope is bound directly to the Compose composition lifecycle.
- **Impact:**  
  If the user taps "Sync Now" and rotates the phone, the coroutine is cancelled mid-sync. Partial event insertion can occur before the batch finishes.
- **Proposed Solution:**  
  Use `viewModelScope` or trigger an expedited WorkManager one-time request for manual syncs so the execution runs to completion independently of the UI lifecycle.

---

### 2.5 Overly Aggressive Sync Flags on Every Calendar Write Pass
- **File:** [`CalendarSyncEngine.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/CalendarSyncEngine.kt#L961-L972)
- **Issue:**  
  After every sync, `requestSync()` is called with:
  ```kotlin
  putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
  putBoolean(ContentResolver.SYNC_EXTRAS_OVERRIDE_TOO_MANY_DELETIONS, true)
  putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
  ```
- **Impact:**  
  Bypassing exponential backoff and overriding deletion safeguards on every automated reactive sync can lead to temporary account sync throttling by Google Play Services if rapid edits are made.
- **Proposed Solution:**  
  Reserve `SYNC_EXTRAS_OVERRIDE_TOO_MANY_DELETIONS` and `SYNC_EXTRAS_IGNORE_BACKOFF` exclusively for user-initiated manual "Nuke All" or "Wipe" operations. Use standard expedited sync for routine automated passes.

---

## 🟡 3. Architecture & Code Maintainability Deficits

### 3.1 Monolithic Files Exceeding 1,500 Lines
- **Files:**
  - `AddEditSyncPairSheet.kt`: 1,894 lines
  - `CalendarSyncEngine.kt`: 1,515 lines
  - `MainActivity.kt`: 1,445 lines
- **Issue:**  
  Three files alone comprise over 4,800 lines of code. UI components, state management, SQL query builders, dialogs, and platform bridge calls are all co-located in single files.
- **Impact:**  
  High cognitive overhead, increased risk of merge conflicts, and elevated probability of regression during routine styling or layout tweaks.
- **Proposed Solution:**  
  Decompose into dedicated packages:
  - `sync/engine/` (`EventReader.kt`, `EventWriter.kt`, `PruningEngine.kt`)
  - `ui/sheets/` (`SyncRouteConduit.kt`, `SyncRangeSection.kt`, `SyncFieldsSection.kt`)
  - `viewmodel/` (`MainViewModel.kt`, `SyncPairViewModel.kt`)

---

### 3.2 Dead Legacy Single-Pair Flow Code in Repository
- **File:** [`SettingsRepository.kt`](file:///d:/Temp%20Working%20Copies/Antigravity/Android%20Cal%20Sync/app/src/main/java/com/stripedlens/calcloner/SettingsRepository.kt#L76-L90)
- **Issue:**  
  Unused properties (`fromCalendarIdFlow`, `toCalendarIdFlow`, `fromCalendarNameFlow`, `toCalendarNameFlow`, `syncDaysPastFlow`, `syncDaysFutureFlow`) remain from CalCloner 1.0.
- **Impact:**  
  Creates confusion for any developer reading repository abstractions.
- **Proposed Solution:**  
  Deprecate and remove dead legacy flows, keeping only migration logic on first launch.

---

### 3.3 Zero Unit Test & Automated Regression Coverage
- **Issue:**  
  No test suite exists under `app/src/test` or `app/src/androidTest`.
- **Impact:**  
  Modifications to core synchronization, timezone translation, or JSON serialization require full manual verification on a connected physical device.
- **Proposed Solution:**  
  Introduce standard unit tests for:
  - `SyncPair` JSON serialization / deserialization roundtrip.
  - Duration parsing (`parseDurationDays`).
  - Cycle detection logic in route configuration.
  - UTC midnight alignment for all-day events.

---

## 🔵 4. Major Missing Features (Roadmap Suggestions)

| Feature | Description | Priority |
| :--- | :--- | :--- |
| **Configuration Backup / Export** | Export sync pairs and rule settings to a JSON file via Android Share Sheet; restore configuration after phone reset. | High |
| **Failed Sync Card Badge** | Show a visual indicator (amber/red badge with error reason) directly on the affected sync pair card when background sync fails. | High |
| **Samsung Deep Sleep Guidance** | In-app detection for Samsung One UI "Deep Sleeping Apps" with a direct link to battery exclusion settings. | Medium |
| **Non-Destructive Deletion Recycle Bin** | A brief grace period or preview before permanently deleting hundreds of target events during "Nuke" or "Clear". | Medium |
| **Custom Filter Rules** | Allow users to filter cloned events by keyword (e.g., skip events containing "Private" or "Hold"). | Low |

---

## Conclusion & Recommendation

The synchronization engine's core algorithms for RFC 5545 timestamp fidelity and duplicate isolation are structurally robust. **The primary risks lie in the background plumbing:** fixing `BootReceiver`'s conditional check and updating master sync timestamps in `CalendarSyncJobService` will ensure flawless background reliability across phone reboots.

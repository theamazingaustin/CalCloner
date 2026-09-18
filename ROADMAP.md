# CalCloner Master Roadmap: Prioritized by Urgency & Value

> **Single Source of Truth** for all audit findings, user requests, architectural cleanups, and future feature proposals for CalCloner.  
> **Prioritization Criteria:**  
> 1. **Data Safety & Core Reliability** (Preventing silent failures, accidental overwrites, or data loss).  
> 2. **Immediate UX Clarity & Visual Polish** (Helping users see what calendars and operations they are managing).  
> 3. **High-Impact Workflow Enhancements** (Condition filters, automation, attendee shields).  
> 4. **Niche & Power-User Tools** (One-time copies, staging diffs, background tombstone pruners).

---

## 🚨 Tier 1: Immediate & Urgent (Active Plan)
*Essential core usability, safety feedback, and clean build hygiene.*

| Priority | Feature / Improvement | Category | Why It Matters Most | Status |
|:---:|---|:---:|---|:---:|
| **1** | **3-Option Delete & Maintenance Wizard** | Safety & Data Control | Gives users complete control between: (1) Purging only cloned events [Default], (2) Direct local calendar clear, or (3) Full 3-step cloud nuke. | ✅ **[COMPLETED - `5b8fc19`]** |
| **2** | **Notification on Sync Failure** | Reliability & Safety | Silent background sync failures (permission revoked, calendar missing) leave calendars out-of-sync without the user realizing it. | ✅ **[COMPLETED - `84f4c3a`]** |
| **3** | **Dynamic Calendar Color Dots / Badges** | Visual UX Clarity | Identifies calendars at a glance using their native Google/Outlook colors across cards and selectors instead of plain text names. | ✅ **[COMPLETED - `62aff60`]** |
| **4** | **Compiler Warning Cleanups** | Code Hygiene | Eliminates unused parameter warnings across `SyncRangeCard` and battery cards for a 100% clean, warning-free build. | ✅ **[COMPLETED - `94cefc0`]** |

---

## ⚡ Tier 2: High-Value Features & Reliability (Next Up)
*Features that drastically improve day-to-day usability and give CalCloner an immediate competitive advantage.*

| Priority | Feature / Improvement | Category | Why It's High Value | Status |
|:---:|---|:---:|---|:---:|
| **5** | **Condition-Based Event Sync (Rule Engine)** | Core Feature | Filter by Free/Busy status, RSVP responses (skip declined meetings), day/time windows, and keyword inclusion/exclusion. | 🟡 **[UI PREVIEW COMPLETED - `c4ef4c7`]** |
| **6** | **Invitee / Attendee Replication & Invite Shield** | Feature & Safety | Replicates meeting guests while providing safety modes (strip emails/sanitize) to prevent Google/Exchange from accidentally emailing duplicate invites. | 🟡 **[UI PREVIEW COMPLETED]** |
| **7** | **Zero-Battery Event-Driven Sync** | Performance & Battery | Uses Android `ContentObserver` to trigger sync within 2 seconds of any local calendar edit, achieving real-time sync with 0% idle battery draw. | ✅ **[COMPLETED - `3f25f4b`]** |
| **8** | **UI Hierarchy, Typography & Toggle Standardization** | Visual Polish & Design System | Audit and harmonize element scaling, switches (`scale(0.85f)`, `height(24.dp)`), and typographic hierarchy across all cards (titles > subtitles > controls). | 📋 **[PLANNED]** |
| **9** | **Rolling Sync History / Diagnostic Log** | Diagnostics & Health | Persistent log of the last 10–20 sync runs (+added, ~updated, -removed, duration, error messages) for easy troubleshooting. | 📋 **[PLANNED]** |
| **10** | **Smart Low-Battery Frequency Backoff** | Battery Optimization | Automatically throttles sync frequency (e.g. 15 min → 1 hr) when battery drops below 15%, rather than completely pausing sync. | 📋 **[PLANNED]** |
| **11** | **Granular Field Customization & Validation** | Data Control & Privacy | Per-field accordions for Title, Notes, Location, Availability with prefixes/suffixes, required fallbacks, and real-time validation alerts. | ✅ **[COMPLETED - `f0e1a26`, `67cf562`]** |

---

## 🛠️ Tier 3: Automation & Power-User Tools
*Extending CalCloner for advanced workflows, tasker triggers, and ad-hoc operations.*

| Priority | Feature / Improvement | Category | Description | Status |
|:---:|---|:---:|---|:---:|
| **12** | **One-Time Operations Engine (Dedicated Tab)** | Navigation & Tools | Dedicated screen for ad-hoc event operations: one-time copy/move across calendars, filtered bulk deletion (keyword/regex/date), ICS import/export, deduplication, and calendar migration. | 🟡 **[UI PREVIEW ADDED]** |
| **13** | **Tasker / MacroDroid / Automation APIs** | Integrations | Broadcast Receiver intents (`ACTION_SYNC_ALL`, `ACTION_SYNC_PAIR`) and native Tasker plugin to trigger syncs programmatically. | 📋 **[PLANNED]** |
| **14** | **Staged Event Previews Before Copying** | Verification UX | Interactive checklist of candidate events (+added, ~modified, -removed) with checkboxes and a side-by-side diff inspector. | 📋 **[PLANNED]** |
| **15** | **Event Color Synchronization** | Visual Customization | Replicate individual `EVENT_COLOR` or assign a uniform color tint to all events originating from a specific sync pair. | 📋 **[PLANNED]** |
| **16** | **Foreground-to-Background WorkManager Handoff** | Reliability | Auto-handoff long manual syncs (>1 year) to an expedited `OneTimeWorkRequest` if user backgrounds the app. | 📋 **[PLANNED]** |
| **17** | **Automated Calendar Backup (.ics / JSON)** | Data Protection | Export/backup calendar snapshots to standard `.ics` (iCalendar) or encrypted `.json` files. | 📋 **[PLANNED]** |

---

## 🧱 Tier 4: Code Architecture & Maintenance (Completed)
*Refactoring, unit testing, and design token maintenance to preserve long-term codebase health.*

| Priority | Feature / Improvement | Category | Description | Status |
|:---:|---|:---:|---|:---:|
| **17** | **Core Unit Test Suites** | QA & Regression | Comprehensive unit tests for diffing, UTC date offsets, and deletion safety checks. | ✅ **Done** (`11eb65e`) |
| **18** | **Target Tombstone Purge Utility** | Database Health | Maintenance utility to clean up local `DELETED=1` soft-delete tombstones older than 30–60 days. | ✅ **Done** (`752bdfd`) |
| **19** | **Import/Export Validation & Preview** | Safety Polish | Schema validation and calendar ID auto-remapping preview dialog before applying configs. | ✅ **Done** (`442b397`) |
| **20** | **DataStore Mutation Consolidation** | Code Simplification | Transactional `mutatePairs { ... }` helper in `SettingsRepository` to eliminate Mutex boilerplate. | ✅ **Done** (`a1fe1c2`) |
| **21** | **Monolith Deconstruction (`AddEditSyncPairSheet.kt`)** | Architecture | Break down the 960-line sheet into modular, reusable subcomponents in `ui/components/sheets/`. | ✅ **Done** (`e8a31b6`) |
| **22** | **Decompose `MainActivity.kt`** | Architecture | Extract header branding, dialog coordination, and scaffold sub-sections out of `MainActivity.kt`. | ✅ **Done** (`eb57bc1`) |
| **23** | **Deduplicate Sync Status String Formatting** | Code Simplification | Extract shared `DateTimeUtils.formatSyncSummary(...)` for consistent status reporting. | ✅ **Done** (`4110063`) |
| **24** | **SupervisorJob in BootReceiver** | Android Lifecycle | Add `SupervisorJob()` and `goAsync()` in `BootReceiver` for safe, reliable background restore. | ✅ **Done** (`4c0d875`) |
| **25** | **App-Wide Semantic Color Token System** | Visual Design Architecture | Consolidate duplicate hex colors into 4-family tokens (Mint, Zinc, Amber, Rose) with semantic dark/light pairing. | ✅ **Done** (`1334d5a`) |

---

## ✅ Tier 5: Completed & Released
*Features, architecture refactors, and design enhancements already verified and pushed to production.*

| # | Feature / Improvement | Description | Released In |
|:---:|---|---|:---:|
| 5.1 | **Multi-Pair Calendar Synchronization** | Safe, isolated one-way replication for multiple source/target pairs with pair-tagged tracking. | `v2.1` |
| 5.2 | **Topological Loop / Cycle Detection (DAG)** | Real-time cycle detector that blocks circular paths (A → B → C → A) to prevent infinite duplication loops. | `v2.1` |
| 5.3 | **Safe Route Conduit & Light Pulse Animation** | Interactive visual conduits showing route flow, read-only/full access badges, and animated sync pulses. | `v2.1` |
| 5.4 | **Universal Background Battery Protection Banner** | Universal guidance banner explaining Android background battery restrictions with direct system intent launcher. | `v2.1-9.17-2` |
| 5.5 | **Frosted Glass & Background Window Blur** | Native Android 12+ hardware window blur (`FLAG_BLUR_BEHIND`) and translucent surface styling on all dialogs. | `v2.1-9.16-5` |
| 5.6 | **Centralized `AppConstants.kt` Clustering** | Consolidated single-file source of truth for UI dimensions, dialog widths (92%), corner radii, sync defaults, and regex patterns. | `v2.1-9.16-5` |
| 5.7 | **Automated Signed Release CI Pipeline** | GitHub Actions workflow that decodes a permanent dedicated keystore to ensure consistent update signatures across all builds. | `v2.1-9.17-1` |
| 5.8 | **Duplicate Dialog Elimination** | Merged duplicate `DeletePairDialog` implementations into a single shared dialog in `ui/dialogs/`. | `v2.1` |
| 5.9 | **Precompiled Static Regex Patterns** | Precompiled `CALCLONER_TAG_REGEX` and replaced repetitive in-loop regex compilations. | `v2.1` |
| 5.10 | **Fixed Inverted Dependencies** | Moved `checkHasCycle` from UI component package into `domain.routing.CycleDetector`. | `v2.1` |
| 5.11 | **Coalesced ViewModel Init Flows** | Used `combine()` on `syncPairsFlow`, `syncIntervalFlow`, and `syncOnLowBatteryFlow` to prevent redundant triple-sync rescheduling on startup. | `v2.1` |
| 5.12 | **Human-Readable Modern Naming** | Renamed all dramatic/ambiguous identifiers (`nukeTargetCalendarEvents` → `deleteAllCalendarEvents`, `DangerZoneSection` → `PairDeleteSection`, `NUKE_ALL` → `WIPE_ALL`). | `v2.1` |
| 5.13 | **Theme Token Centralization** | Replaced hardcoded color literals with `TitaniumMint` design tokens and centralized `UiDimensions`. | `v2.1-9.16-4` |
| 5.14 | **SyncPairTest Suite** | Unit tests covering JSON roundtrips and DAG cycle detection. | `v2.1` |

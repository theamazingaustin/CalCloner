# CalCloner Active Roadmap & Feature Backlog

> **Active Source of Truth** for open features, planned enhancements, and backlog proposals for CalCloner.  
> *(All completed milestones have been archived and cleared from this document).*

---

## ⚡ Tier 1: High-Value Features & Reliability (Next Up)
*Features that directly improve day-to-day usability, sync intelligence, and configuration control.*

| Priority | Feature / Improvement | Category | Description | Status |
|:---:|---|:---:|---|:---:|
| **1** | **Condition-Based Event Sync (Rule Engine)** | Core Feature | Filter events by Free/Busy status, RSVP response (skip declined meetings), active day/time windows, and keyword inclusion/exclusion patterns. | 🟡 **UI Preview Implemented** (Engine Logic Next) |
| **2** | **Invitee / Attendee Replication & Invite Shield** | Feature & Safety | Replicate meeting guest lists with safety protection modes (strip attendee emails / sanitize) so upstream calendar providers (Google/Exchange) do not trigger duplicate meeting invites. | 🟡 **UI Preview Implemented** (Engine Logic Next) |
| **3** | **UI Hierarchy, Typography & Toggle Standardization** | Visual Polish & Design System | Audit and standardize toggle dimensions (`scale(0.85f)`, `height(24.dp)`), typographic hierarchy across all cards (titles > subtitles > controls), and container elevation consistency. | 📋 **Planned** |
| **4** | **Rolling Sync History / Diagnostic Log** | Diagnostics & Health | Persistent in-app diagnostic log of the last 10–20 sync runs (+added, ~updated, -removed, duration, error messages) for easy troubleshooting. | 📋 **Planned** |
| **5** | **Smart Low-Battery Frequency Backoff** | Battery Optimization | Automatically throttles background sync frequency (e.g. 15 min → 1 hr) when battery drops below 15%, rather than completely halting background sync. | 📋 **Planned** |

---

## 🛠️ Tier 2: Automation & Power-User Tools
*Extending CalCloner for advanced workflows, automation triggers, and ad-hoc operations.*

| Priority | Feature / Improvement | Category | Description | Status |
|:---:|---|:---:|---|:---:|
| **6** | **One-Time Operations Engine (Dedicated Tab)** | Navigation & Tools | Full execution engine for the One-Time tab: ad-hoc event copying/moving across calendars, filtered bulk deletion (keyword/date/regex), .ics import/export, deduplication, and calendar migration. | 🟡 **UI Tab & Placeholders Implemented** (Engine Logic Next) |
| **7** | **Tasker / MacroDroid / Automation APIs** | Integrations | Broadcast Receiver intents (`ACTION_SYNC_ALL`, `ACTION_SYNC_PAIR`) and native Tasker plugin integration to trigger syncs programmatically. | 📋 **Planned** |
| **8** | **Staged Event Previews Before Copying** | Verification UX | Interactive checklist of candidate events (+added, ~modified, -removed) with checkboxes and a side-by-side diff inspector prior to write commit. | 📋 **Planned** |
| **9** | **Event Color Synchronization** | Visual Customization | Replicate individual `EVENT_COLOR` or assign a uniform color tint to all events originating from a specific sync pair. | 📋 **Planned** |
| **10** | **Foreground-to-Background WorkManager Handoff** | Reliability | Auto-handoff long manual syncs (>1 year range) to an expedited `OneTimeWorkRequest` if the user backgrounds or leaves the app during a sync. | 📋 **Planned** |
| **11** | **Automated Calendar Backup (.ics / JSON)** | Data Protection | Scheduled local backups of calendar snapshots to standard `.ics` (iCalendar) or encrypted `.json` files. | 📋 **Planned** |

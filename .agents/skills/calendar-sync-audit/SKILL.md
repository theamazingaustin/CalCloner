---
name: calendar-sync-audit
description: Audits CalClone Android Calendar Sync Engine and live device database for sync drift, RFC 5545 timestamp alignment, orphaned events, recurrence exception linking, tombstone integrity, and WorkManager background sync status. Use when checking for calendar sync bugs, date shifts, duplicate events, or background worker failures.
---

# Calendar Sync Audit Specialist

This skill performs automated, deterministic diagnostics on CalClone's calendar synchronization engine and live device state via ADB.

## When to Use
- User reports events appearing on the wrong day (e.g. 1-day shifts, timezone issues).
- User reports duplicate events, lingering ghost events, or missing events.
- Validating a new build before pushing or after a sync cycle.
- Checking if Google Sync Adapter has processed tombstones or if background sync is healthy.

## Automated Audit Execution

Run the built-in automated audit script:

```powershell
python ".agents/skills/calendar-sync-audit/scripts/audit_sync.py"
```

### What It Audits
1. **Timestamp & Date Parity**:
   - Queries source and target calendars directly via ADB.
   - Computes start and end differences (`t_start - s_start`).
   - Flags any non-zero difference ($\ne 0.0$ hours).
2. **RFC 5545 & CalendarContract Compliance**:
   - Ensures `allDay = 1` events have `dtstart % 86400000 == 0` (exact UTC midnight) and `eventTimezone == 'UTC'`.
   - Ensures recurring all-day events omit `DTEND` and use `DURATION = "P<N>D"`.
   - Ensures recurrence exceptions have valid `ORIGINAL_ID` and `ORIGINAL_INSTANCE_TIME`.
3. **Orphan / Ghost Event Detection**:
   - Checks if any cloned event in the target calendar has lost its source event.
4. **Tombstone Status**:
   - Checks if deleted rows maintain `deleted=1, dirty=1` for Google Sync.

## Diagnostic Commands Reference

### 1. Query Cloned Target Events
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell 'content query --uri content://com.android.calendar/events --projection _id:calendar_id:title:dtstart:dtend:allDay:eventTimezone:duration:customAppUri --where "deleted=0 AND (customAppUri IS NOT NULL OR description LIKE \"%CalClone-ID%\")"'
```

### 2. Check Background WorkManager Job Status
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell dumpsys jobscheduler | Select-String "com.stripedlens.calclone"
```

### 3. Inspect Calendar Provider & Sync Logcat
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d -s CalendarProvider2:D GoogleCalendarSyncAdapter:D CalClone:D
```

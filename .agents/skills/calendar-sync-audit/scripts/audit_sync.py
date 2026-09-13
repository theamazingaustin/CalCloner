#!/usr/bin/env python3
"""
CalClone Calendar Sync Audit Script
Audits live Android CalendarProvider on connected device for sync parity,
RFC 5545 timestamp alignment, orphaned events, and recurrence integrity.
"""

import sys
import subprocess
import re
from datetime import datetime, timezone

ADB_PATH = r"C:\Users\theam\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def get_device():
    res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
    lines = [l.split('\t')[0] for l in res.stdout.splitlines() if '\tdevice' in l]
    return lines[0] if lines else None

def run_query(device, where_clause, projection="_id:calendar_id:title:dtstart:dtend:allDay:eventTimezone:duration:rrule:original_id:originalInstanceTime:customAppUri:deleted"):
    cmd = [
        ADB_PATH, "-s", device, "shell",
        f'content query --uri content://com.android.calendar/events --projection {projection} --where "{where_clause}"'
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    rows = []
    for line in res.stdout.splitlines():
        if "dtstart=" in line or "_id=" in line:
            parts = dict(re.findall(r'(\w+)=([^,\n\r]+)', line))
            rows.append(parts)
    return rows

def query_calendars(device):
    cmd = [
        ADB_PATH, "-s", device, "shell",
        'content query --uri content://com.android.calendar/calendars --projection _id:calendar_displayName:account_name:isPrimary'
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    cals = {}
    for line in res.stdout.splitlines():
        if "_id=" in line:
            parts = dict(re.findall(r'(\w+)=([^,\n\r]+)', line))
            cid = parts.get('_id')
            if cid:
                cals[cid] = parts.get('calendar_displayName', f"Cal {cid}")
    return cals

def main():
    device = get_device()
    if not device:
        print("[-] ERROR: No ADB device connected.")
        sys.exit(1)

    print(f"[*] CalCloner Audit on Device: {device}")
    calendars = query_calendars(device)

    # Find CalCloner target events (those with customAppUri or [CalCloner-ID] / [CalClone-ID])
    all_target_events = run_query(device, "deleted=0 AND (customAppUri IS NOT NULL OR description LIKE '%CalCloner-ID%' OR description LIKE '%CalClone-ID%')")
    
    if not all_target_events:
        print("[!] No CalCloner cloned events found on device. Perform a sync first.")
        sys.exit(0)

    target_cals = set(t.get('calendar_id') for t in all_target_events if t.get('calendar_id'))
    target_strs = [f"{c} ({calendars.get(c, 'Unknown')})" for c in target_cals]
    print(f"[*] Target Calendars Identified: {', '.join(target_strs)}")

    total_clones = len(all_target_events)
    print(f"[*] Total Cloned Events: {total_clones}")

    # Map target events by source ID
    clone_by_source_id = {}
    source_ids = []
    for t in all_target_events:
        uri = t.get('customAppUri', '')
        sid = None
        if uri and 'event/' in uri:
            sid = uri.split('event/')[-1]
        elif t.get('description') and ('CalCloner-ID:' in t.get('description') or 'CalClone-ID:' in t.get('description')):
            m = re.search(r'CalClone(?:r)?-ID:\s*(\d+)', t['description'])
            if m:
                sid = m.group(1)
        if sid:
            clone_by_source_id[sid] = t
            source_ids.append(sid)

    # Batch query source events
    source_events = {}
    if source_ids:
        chunk_size = 100
        for i in range(0, len(source_ids), chunk_size):
            chunk = source_ids[i:i+chunk_size]
            where = " OR ".join(f"_id={x}" for x in chunk)
            for s in run_query(device, f"({where})"):
                source_events[s['_id']] = s

    # 1. Check Start/End Time Parity
    drift_errors = []
    allday_drift_errors = []
    orphaned_events = []
    valid_parity_count = 0

    for sid, t in clone_by_source_id.items():
        s = source_events.get(sid)
        if not s:
            orphaned_events.append(t)
            continue

        if s.get('deleted') == '1':
            orphaned_events.append(t)
            continue

        s_start = int(s.get('dtstart', 0))
        t_start = int(t.get('dtstart', 0))
        s_allday = s.get('allDay') == '1'
        t_allday = t.get('allDay') == '1'

        start_diff_hours = (t_start - s_start) / 3600000.0

        if start_diff_hours != 0.0:
            err_str = f"'{t.get('title')}' start diff: {start_diff_hours:+.1f}h (Src: {s_start} -> Clone: {t_start})"
            if s_allday:
                allday_drift_errors.append(err_str)
            else:
                drift_errors.append(err_str)
        else:
            # Check dtend parity if present
            s_end = int(s['dtend']) if s.get('dtend') and s['dtend'] != 'NULL' else None
            t_end = int(t['dtend']) if t.get('dtend') and t['dtend'] != 'NULL' else None
            if s_end and t_end and (s_end != t_end):
                end_diff_hours = (t_end - s_end) / 3600000.0
                drift_errors.append(f"'{t.get('title')}' end diff: {end_diff_hours:+.1f}h")
            else:
                valid_parity_count += 1

    # 2. Check RFC 5545 All-Day Conformance
    allday_conformance_errors = []
    for t in all_target_events:
        if t.get('allDay') == '1':
            s_ms = int(t.get('dtstart', 0))
            if s_ms % 86400000 != 0:
                allday_conformance_errors.append(f"'{t.get('title')}' allDay=1 but dtstart not UTC midnight ({s_ms})")
            if t.get('eventTimezone') != 'UTC':
                allday_conformance_errors.append(f"'{t.get('title')}' allDay=1 but timezone is '{t.get('eventTimezone')}' (must be 'UTC')")
            rrule = t.get('rrule')
            has_rrule = bool(rrule and rrule != 'NULL')
            dtend = t.get('dtend')
            has_dtend = bool(dtend and dtend != 'NULL')
            if has_rrule and has_dtend:
                allday_conformance_errors.append(f"'{t.get('title')}' recurring all-day event has both RRULE and DTEND (violates RFC 5545)")

    # Print Summary Table
    print("\n" + "=" * 60)
    print("           CALCLONE SYNC HEALTH REPORT")
    print("=" * 60)
    print(f"Total Clones Checked        : {total_clones}")
    print(f"Exact Parity Matches (0.0h) : {valid_parity_count}")
    print(f"All-Day Drift Errors        : {len(allday_drift_errors)}")
    print(f"Timed Event Drift Errors    : {len(drift_errors)}")
    print(f"Orphaned Clones (Ghost rows): {len(orphaned_events)}")
    print(f"RFC 5545 Spec Violations   : {len(allday_conformance_errors)}")
    print("-" * 60)

    all_passed = True
    if allday_drift_errors:
        all_passed = False
        print("\n[!] ALL-DAY TIMEZONE / DATE SHIFT ERRORS:")
        for e in allday_drift_errors[:10]:
            print(f"    - {e}")

    if drift_errors:
        all_passed = False
        print("\n[!] TIMED EVENT DRIFT ERRORS:")
        for e in drift_errors[:10]:
            print(f"    - {e}")

    if orphaned_events:
        all_passed = False
        print(f"\n[!] ORPHANED CLONES ({len(orphaned_events)} events):")
        for o in orphaned_events[:5]:
            print(f"    - ID {o.get('_id')} '{o.get('title')}' (source missing/deleted)")

    if allday_conformance_errors:
        all_passed = False
        print("\n[!] RFC 5545 SPEC COMPLIANCE ISSUES:")
        for c in allday_conformance_errors[:5]:
            print(f"    - {c}")

    if all_passed:
        print("\n[+] OVERALL STATUS: ALL CHECKS PASSED (100% SYNC INTEGRITY)")
    else:
        print("\n[-] OVERALL STATUS: ISSUES DETECTED")

if __name__ == '__main__':
    main()

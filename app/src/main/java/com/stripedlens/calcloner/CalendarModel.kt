package com.stripedlens.calcloner

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean,
    val accessLevel: Int = 700
) {
    val canWrite: Boolean get() = accessLevel >= 500 // CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
}

data class SyncReminder(
    val minutes: Int,
    val method: Int
)

data class SyncEvent(
    val id: Long,
    val title: String?,
    val description: String?,
    val location: String?,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val allDay: Int,
    val timeZone: String?,
    val rrule: String?,
    val originalId: Long? = null,
    val originalSyncId: String? = null,
    val originalInstanceTime: Long? = null,
    val status: Int? = null,
    val reminders: List<SyncReminder> = emptyList(),
    val syncId: String? = null,
    val availability: Int? = null,
    val exdate: String? = null
)

data class SyncResult(
    val insertedCount: Int,
    val updatedCount: Int,
    val totalSourceEvents: Int,
    val deletedCount: Int = 0
)

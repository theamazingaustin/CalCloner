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
    val deletedCount: Int = 0,
    val durationMs: Long = 0L
)

data class SyncPair(
    val id: String,
    val nickname: String? = null,
    val fromCalendarId: Long,
    val fromCalendarName: String,
    val toCalendarId: Long,
    val toCalendarName: String,
    val daysPast: Int = AppConstants.Sync.DEFAULT_DAYS_PAST,
    val daysFuture: Int = AppConstants.Sync.DEFAULT_DAYS_FUTURE,
    val isEnabled: Boolean = true,
    val syncTitle: Boolean = true,
    val customTitle: String? = null,
    val syncDescription: Boolean = true,
    val syncLocation: Boolean = true,
    val syncReminders: Boolean = true,
    val syncAvailability: Boolean = true,
    val syncStatus: Boolean = true,
    val lastSyncTime: Long? = null,
    val lastSyncStatus: String? = null,
    val lastInsertedCount: Int = 0,
    val lastUpdatedCount: Int = 0,
    val lastDeletedCount: Int = 0,
    val lastDurationMs: Long? = null
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: "$fromCalendarName → $toCalendarName"

    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        if (!nickname.isNullOrEmpty()) put("nickname", nickname)
        put("fromCalendarId", fromCalendarId)
        put("fromCalendarName", fromCalendarName)
        put("toCalendarId", toCalendarId)
        put("toCalendarName", toCalendarName)
        put("daysPast", daysPast)
        put("daysFuture", daysFuture)
        put("isEnabled", isEnabled)
        put("syncTitle", syncTitle)
        if (!customTitle.isNullOrEmpty()) put("customTitle", customTitle)
        put("syncDescription", syncDescription)
        put("syncLocation", syncLocation)
        put("syncReminders", syncReminders)
        put("syncAvailability", syncAvailability)
        put("syncStatus", syncStatus)
        if (lastSyncTime != null) put("lastSyncTime", lastSyncTime)
        if (lastSyncStatus != null) put("lastSyncStatus", lastSyncStatus)
        put("lastInsertedCount", lastInsertedCount)
        put("lastUpdatedCount", lastUpdatedCount)
        put("lastDeletedCount", lastDeletedCount)
        if (lastDurationMs != null) put("lastDurationMs", lastDurationMs)
    }

    companion object {
        fun createNew(
            fromCalendarId: Long,
            fromCalendarName: String,
            toCalendarId: Long,
            toCalendarName: String,
            nickname: String? = null,
            daysPast: Int = 30,
            daysFuture: Int = 30,
            isEnabled: Boolean = true,
            syncTitle: Boolean = true,
            customTitle: String? = null,
            syncDescription: Boolean = true,
            syncLocation: Boolean = true,
            syncReminders: Boolean = true,
            syncAvailability: Boolean = true,
            syncStatus: Boolean = true
        ): SyncPair = SyncPair(
            id = java.util.UUID.randomUUID().toString(),
            nickname = nickname,
            fromCalendarId = fromCalendarId,
            fromCalendarName = fromCalendarName,
            toCalendarId = toCalendarId,
            toCalendarName = toCalendarName,
            daysPast = daysPast,
            daysFuture = daysFuture,
            isEnabled = isEnabled,
            syncTitle = syncTitle,
            customTitle = customTitle,
            syncDescription = syncDescription,
            syncLocation = syncLocation,
            syncReminders = syncReminders,
            syncAvailability = syncAvailability,
            syncStatus = syncStatus
        )

        fun fromJson(json: org.json.JSONObject): SyncPair = SyncPair(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            nickname = if (json.has("nickname") && !json.isNull("nickname")) json.getString("nickname") else null,
            fromCalendarId = json.getLong("fromCalendarId"),
            fromCalendarName = json.optString("fromCalendarName", "Source Calendar"),
            toCalendarId = json.getLong("toCalendarId"),
            toCalendarName = json.optString("toCalendarName", "Clone Calendar"),
            daysPast = json.optInt("daysPast", 30),
            daysFuture = json.optInt("daysFuture", 30),
            isEnabled = json.optBoolean("isEnabled", true),
            syncTitle = json.optBoolean("syncTitle", true),
            customTitle = if (json.has("customTitle") && !json.isNull("customTitle")) json.getString("customTitle") else null,
            syncDescription = json.optBoolean("syncDescription", true),
            syncLocation = json.optBoolean("syncLocation", true),
            syncReminders = json.optBoolean("syncReminders", true),
            syncAvailability = json.optBoolean("syncAvailability", true),
            syncStatus = json.optBoolean("syncStatus", true),
            lastSyncTime = if (json.has("lastSyncTime") && !json.isNull("lastSyncTime")) json.getLong("lastSyncTime") else null,
            lastSyncStatus = if (json.has("lastSyncStatus") && !json.isNull("lastSyncStatus")) json.getString("lastSyncStatus") else null,
            lastInsertedCount = json.optInt("lastInsertedCount", 0),
            lastUpdatedCount = json.optInt("lastUpdatedCount", 0),
            lastDeletedCount = json.optInt("lastDeletedCount", 0),
            lastDurationMs = if (json.has("lastDurationMs") && !json.isNull("lastDurationMs")) json.getLong("lastDurationMs") else null
        )

        fun listToJsonString(pairs: List<SyncPair>): String {
            val array = org.json.JSONArray()
            pairs.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJsonString(jsonString: String?): List<SyncPair> {
            if (jsonString.isNullOrEmpty()) return emptyList()
            return try {
                val array = org.json.JSONArray(jsonString)
                val list = mutableListOf<SyncPair>()
                for (i in 0 until array.length()) {
                    list.add(fromJson(array.getJSONObject(i)))
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}


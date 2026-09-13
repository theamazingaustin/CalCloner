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

data class SyncPair(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fromCalendarId: Long,
    val fromCalendarName: String,
    val toCalendarId: Long,
    val toCalendarName: String,
    val daysPast: Int? = null,
    val daysFuture: Int? = null,
    val isEnabled: Boolean = true,
    val lastSyncTime: Long? = null,
    val lastSyncStatus: String? = null
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("fromCalendarId", fromCalendarId)
        put("fromCalendarName", fromCalendarName)
        put("toCalendarId", toCalendarId)
        put("toCalendarName", toCalendarName)
        if (daysPast != null) put("daysPast", daysPast)
        if (daysFuture != null) put("daysFuture", daysFuture)
        put("isEnabled", isEnabled)
        if (lastSyncTime != null) put("lastSyncTime", lastSyncTime)
        if (lastSyncStatus != null) put("lastSyncStatus", lastSyncStatus)
    }

    companion object {
        fun fromJson(json: org.json.JSONObject): SyncPair = SyncPair(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            fromCalendarId = json.getLong("fromCalendarId"),
            fromCalendarName = json.optString("fromCalendarName", "Source Calendar"),
            toCalendarId = json.getLong("toCalendarId"),
            toCalendarName = json.optString("toCalendarName", "Clone Calendar"),
            daysPast = if (json.has("daysPast") && !json.isNull("daysPast")) json.getInt("daysPast") else null,
            daysFuture = if (json.has("daysFuture") && !json.isNull("daysFuture")) json.getInt("daysFuture") else null,
            isEnabled = json.optBoolean("isEnabled", true),
            lastSyncTime = if (json.has("lastSyncTime") && !json.isNull("lastSyncTime")) json.getLong("lastSyncTime") else null,
            lastSyncStatus = if (json.has("lastSyncStatus") && !json.isNull("lastSyncStatus")) json.getString("lastSyncStatus") else null
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


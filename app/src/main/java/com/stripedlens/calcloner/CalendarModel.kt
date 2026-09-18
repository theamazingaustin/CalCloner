package com.stripedlens.calcloner

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean,
    val accessLevel: Int = 700,
    val color: Int? = null
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
    val titlePrefix: String? = null,
    val titleSuffix: String? = null,
    val syncDescription: Boolean = true,
    val customDescription: String? = null,
    val descriptionPrefix: String? = null,
    val descriptionSuffix: String? = null,
    val syncLocation: Boolean = true,
    val customLocation: String? = null,
    val syncReminders: Boolean = true,
    val syncAvailability: Boolean = true,
    val customAvailability: Int? = null,
    val syncStatus: Boolean = true,
    val customStatus: Int? = null,
    val lastSyncTime: Long? = null,
    val lastSyncStatus: String? = null,
    val lastInsertedCount: Int = 0,
    val lastUpdatedCount: Int = 0,
    val lastDeletedCount: Int = 0,
    val lastDurationMs: Long? = null
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: "$fromCalendarName → $toCalendarName"

    val fieldValidationError: String? get() {
        if (!syncTitle && customTitle.isNullOrBlank()) {
            return "A title is required when event title mirroring is turned off."
        }
        return null
    }

    val isConfigValidForSync: Boolean get() = fieldValidationError == null

    val hasSyncError: Boolean get() {
        if (fieldValidationError != null) return true
        val status = lastSyncStatus ?: return false
        return status.contains("fail", ignoreCase = true) ||
               status.contains("error", ignoreCase = true) ||
               status.contains("not found", ignoreCase = true) ||
               status.contains("denied", ignoreCase = true)
    }

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
        if (!titlePrefix.isNullOrEmpty()) put("titlePrefix", titlePrefix)
        if (!titleSuffix.isNullOrEmpty()) put("titleSuffix", titleSuffix)
        put("syncDescription", syncDescription)
        if (!customDescription.isNullOrEmpty()) put("customDescription", customDescription)
        if (!descriptionPrefix.isNullOrEmpty()) put("descriptionPrefix", descriptionPrefix)
        if (!descriptionSuffix.isNullOrEmpty()) put("descriptionSuffix", descriptionSuffix)
        put("syncLocation", syncLocation)
        if (!customLocation.isNullOrEmpty()) put("customLocation", customLocation)
        put("syncReminders", syncReminders)
        put("syncAvailability", syncAvailability)
        if (customAvailability != null) put("customAvailability", customAvailability)
        put("syncStatus", syncStatus)
        if (customStatus != null) put("customStatus", customStatus)
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
            titlePrefix: String? = null,
            titleSuffix: String? = null,
            syncDescription: Boolean = true,
            customDescription: String? = null,
            descriptionPrefix: String? = null,
            descriptionSuffix: String? = null,
            syncLocation: Boolean = true,
            customLocation: String? = null,
            syncReminders: Boolean = true,
            syncAvailability: Boolean = true,
            customAvailability: Int? = null,
            syncStatus: Boolean = true,
            customStatus: Int? = null
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
            titlePrefix = titlePrefix,
            titleSuffix = titleSuffix,
            syncDescription = syncDescription,
            customDescription = customDescription,
            descriptionPrefix = descriptionPrefix,
            descriptionSuffix = descriptionSuffix,
            syncLocation = syncLocation,
            customLocation = customLocation,
            syncReminders = syncReminders,
            syncAvailability = syncAvailability,
            customAvailability = customAvailability,
            syncStatus = syncStatus,
            customStatus = customStatus
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
            titlePrefix = if (json.has("titlePrefix") && !json.isNull("titlePrefix")) json.getString("titlePrefix") else null,
            titleSuffix = if (json.has("titleSuffix") && !json.isNull("titleSuffix")) json.getString("titleSuffix") else null,
            syncDescription = json.optBoolean("syncDescription", true),
            customDescription = if (json.has("customDescription") && !json.isNull("customDescription")) json.getString("customDescription") else null,
            descriptionPrefix = if (json.has("descriptionPrefix") && !json.isNull("descriptionPrefix")) json.getString("descriptionPrefix") else null,
            descriptionSuffix = if (json.has("descriptionSuffix") && !json.isNull("descriptionSuffix")) json.getString("descriptionSuffix") else null,
            syncLocation = json.optBoolean("syncLocation", true),
            customLocation = if (json.has("customLocation") && !json.isNull("customLocation")) json.getString("customLocation") else null,
            syncReminders = json.optBoolean("syncReminders", true),
            syncAvailability = json.optBoolean("syncAvailability", true),
            customAvailability = if (json.has("customAvailability") && !json.isNull("customAvailability")) json.getInt("customAvailability") else null,
            syncStatus = json.optBoolean("syncStatus", true),
            customStatus = if (json.has("customStatus") && !json.isNull("customStatus")) json.getInt("customStatus") else null,
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


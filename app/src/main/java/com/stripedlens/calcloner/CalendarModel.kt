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

data class SyncAttendee(
    val name: String?,
    val email: String? = null,
    val status: Int = 0
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
    val accessLevel: Int? = null,
    val exdate: String? = null,
    val attendees: List<SyncAttendee> = emptyList(),
    val selfAttendeeStatus: Int? = null
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
    val syncAccessLevel: Boolean = true,
    val customAccessLevel: Int? = null,
    val syncAttendees: Boolean = false,
    val attendeesPlacement: String = "END",
    // Condition Filters
    val filterEnabled: Boolean = false,
    val filterAllowBusy: Boolean = true,
    val filterAllowFree: Boolean = true,
    val filterAllowTentative: Boolean = false,
    val filterAllowEmpty: Boolean = true,
    val filterRsvpAccepted: Boolean = true,
    val filterRsvpTentative: Boolean = false,
    val filterRsvpDeclined: Boolean = false,
    val filterIncludeAllDay: Boolean = true,
    val filterEnableTimeFilter: Boolean = false,
    val filterFromHour: Int = 9,
    val filterFromMinute: Int = 0,
    val filterToHour: Int = 17,
    val filterToMinute: Int = 0,
    val filterActiveDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val filterTitleContains: String = "",
    val filterTitleContainsMatchAll: Boolean = false,
    val filterTitleDoesNotContain: String = "",
    val filterTitleDoesNotContainMatchAll: Boolean = false,
    val filterDescriptionContains: String = "",
    val filterDescriptionContainsMatchAll: Boolean = false,
    val filterDescriptionDoesNotContain: String = "",
    val filterDescriptionDoesNotContainMatchAll: Boolean = false,
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
        put("syncAccessLevel", syncAccessLevel)
        if (customAccessLevel != null) put("customAccessLevel", customAccessLevel)
        put("syncAttendees", syncAttendees)
        put("attendeesPlacement", attendeesPlacement)
        put("filterEnabled", filterEnabled)
        put("filterAllowBusy", filterAllowBusy)
        put("filterAllowFree", filterAllowFree)
        put("filterAllowTentative", filterAllowTentative)
        put("filterAllowEmpty", filterAllowEmpty)
        put("filterRsvpAccepted", filterRsvpAccepted)
        put("filterRsvpTentative", filterRsvpTentative)
        put("filterRsvpDeclined", filterRsvpDeclined)
        put("filterIncludeAllDay", filterIncludeAllDay)
        put("filterEnableTimeFilter", filterEnableTimeFilter)
        put("filterFromHour", filterFromHour)
        put("filterFromMinute", filterFromMinute)
        put("filterToHour", filterToHour)
        put("filterToMinute", filterToMinute)
        put("filterActiveDays", filterActiveDays.joinToString(","))
        if (filterTitleContains.isNotEmpty()) put("filterTitleContains", filterTitleContains)
        put("filterTitleContainsMatchAll", filterTitleContainsMatchAll)
        if (filterTitleDoesNotContain.isNotEmpty()) put("filterTitleDoesNotContain", filterTitleDoesNotContain)
        put("filterTitleDoesNotContainMatchAll", filterTitleDoesNotContainMatchAll)
        if (filterDescriptionContains.isNotEmpty()) put("filterDescriptionContains", filterDescriptionContains)
        put("filterDescriptionContainsMatchAll", filterDescriptionContainsMatchAll)
        if (filterDescriptionDoesNotContain.isNotEmpty()) put("filterDescriptionDoesNotContain", filterDescriptionDoesNotContain)
        put("filterDescriptionDoesNotContainMatchAll", filterDescriptionDoesNotContainMatchAll)
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
            customStatus: Int? = null,
            syncAccessLevel: Boolean = true,
            customAccessLevel: Int? = null,
            syncAttendees: Boolean = false,
            attendeesPlacement: String = "END",
            filterEnabled: Boolean = false,
            filterAllowBusy: Boolean = true,
            filterAllowFree: Boolean = true,
            filterAllowTentative: Boolean = false,
            filterAllowEmpty: Boolean = true,
            filterRsvpAccepted: Boolean = true,
            filterRsvpTentative: Boolean = false,
            filterRsvpDeclined: Boolean = false,
            filterIncludeAllDay: Boolean = true,
            filterEnableTimeFilter: Boolean = false,
            filterFromHour: Int = 9,
            filterFromMinute: Int = 0,
            filterToHour: Int = 17,
            filterToMinute: Int = 0,
            filterActiveDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
            filterTitleContains: String = "",
            filterTitleContainsMatchAll: Boolean = false,
            filterTitleDoesNotContain: String = "",
            filterTitleDoesNotContainMatchAll: Boolean = false,
            filterDescriptionContains: String = "",
            filterDescriptionContainsMatchAll: Boolean = false,
            filterDescriptionDoesNotContain: String = "",
            filterDescriptionDoesNotContainMatchAll: Boolean = false
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
            customStatus = customStatus,
            syncAccessLevel = syncAccessLevel,
            customAccessLevel = customAccessLevel,
            syncAttendees = syncAttendees,
            attendeesPlacement = attendeesPlacement,
            filterEnabled = filterEnabled,
            filterAllowBusy = filterAllowBusy,
            filterAllowFree = filterAllowFree,
            filterAllowTentative = filterAllowTentative,
            filterAllowEmpty = filterAllowEmpty,
            filterRsvpAccepted = filterRsvpAccepted,
            filterRsvpTentative = filterRsvpTentative,
            filterRsvpDeclined = filterRsvpDeclined,
            filterIncludeAllDay = filterIncludeAllDay,
            filterEnableTimeFilter = filterEnableTimeFilter,
            filterFromHour = filterFromHour,
            filterFromMinute = filterFromMinute,
            filterToHour = filterToHour,
            filterToMinute = filterToMinute,
            filterActiveDays = filterActiveDays,
            filterTitleContains = filterTitleContains,
            filterTitleContainsMatchAll = filterTitleContainsMatchAll,
            filterTitleDoesNotContain = filterTitleDoesNotContain,
            filterTitleDoesNotContainMatchAll = filterTitleDoesNotContainMatchAll,
            filterDescriptionContains = filterDescriptionContains,
            filterDescriptionContainsMatchAll = filterDescriptionContainsMatchAll,
            filterDescriptionDoesNotContain = filterDescriptionDoesNotContain,
            filterDescriptionDoesNotContainMatchAll = filterDescriptionDoesNotContainMatchAll
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
            syncAccessLevel = json.optBoolean("syncAccessLevel", true),
            customAccessLevel = if (json.has("customAccessLevel") && !json.isNull("customAccessLevel")) json.getInt("customAccessLevel") else null,
            syncAttendees = json.optBoolean("syncAttendees", false),
            attendeesPlacement = json.optString("attendeesPlacement", "END"),
            filterEnabled = json.optBoolean("filterEnabled", false),
            filterAllowBusy = json.optBoolean("filterAllowBusy", true),
            filterAllowFree = json.optBoolean("filterAllowFree", true),
            filterAllowTentative = json.optBoolean("filterAllowTentative", false),
            filterAllowEmpty = json.optBoolean("filterAllowEmpty", true),
            filterRsvpAccepted = json.optBoolean("filterRsvpAccepted", true),
            filterRsvpTentative = json.optBoolean("filterRsvpTentative", false),
            filterRsvpDeclined = json.optBoolean("filterRsvpDeclined", false),
            filterIncludeAllDay = json.optBoolean("filterIncludeAllDay", true),
            filterEnableTimeFilter = json.optBoolean("filterEnableTimeFilter", false),
            filterFromHour = json.optInt("filterFromHour", 9),
            filterFromMinute = json.optInt("filterFromMinute", 0),
            filterToHour = json.optInt("filterToHour", 17),
            filterToMinute = json.optInt("filterToMinute", 0),
            filterActiveDays = json.optString("filterActiveDays", "1,2,3,4,5,6,7")
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
                .ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) },
            filterTitleContains = json.optString("filterTitleContains", ""),
            filterTitleContainsMatchAll = json.optBoolean("filterTitleContainsMatchAll", false),
            filterTitleDoesNotContain = json.optString("filterTitleDoesNotContain", ""),
            filterTitleDoesNotContainMatchAll = json.optBoolean("filterTitleDoesNotContainMatchAll", false),
            filterDescriptionContains = json.optString("filterDescriptionContains", ""),
            filterDescriptionContainsMatchAll = json.optBoolean("filterDescriptionContainsMatchAll", false),
            filterDescriptionDoesNotContain = json.optString("filterDescriptionDoesNotContain", ""),
            filterDescriptionDoesNotContainMatchAll = json.optBoolean("filterDescriptionDoesNotContainMatchAll", false),
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


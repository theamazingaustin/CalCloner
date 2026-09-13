package com.stripedlens.calcloner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val code: Int) {
    AUTO(0),
    DARK(1),
    LIGHT(2);

    companion object {
        fun fromCode(code: Int): ThemeMode = values().firstOrNull { it.code == code } ?: AUTO
    }
}

class SettingsRepository(private val context: Context) {

    companion object {
        val FROM_CALENDAR_ID = longPreferencesKey("from_calendar_id")
        val FROM_CALENDAR_NAME = stringPreferencesKey("from_calendar_name")
        val TO_CALENDAR_ID = longPreferencesKey("to_calendar_id")
        val TO_CALENDAR_NAME = stringPreferencesKey("to_calendar_name")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        val SYNC_DAYS_PAST = intPreferencesKey("sync_days_past")
        val SYNC_DAYS_FUTURE = intPreferencesKey("sync_days_future")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DISCLAIMER_VERSION_ACCEPTED = intPreferencesKey("disclaimer_version_accepted")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val LAST_SYNC_STATUS = stringPreferencesKey("last_sync_status")

        const val CURRENT_DISCLAIMER_VERSION = 1
    }

    val fromCalendarIdFlow: Flow<Long?> = context.dataStore.data.map { it[FROM_CALENDAR_ID] }
    val fromCalendarNameFlow: Flow<String?> = context.dataStore.data.map { it[FROM_CALENDAR_NAME] }
    val toCalendarIdFlow: Flow<Long?> = context.dataStore.data.map { it[TO_CALENDAR_ID] }
    val toCalendarNameFlow: Flow<String?> = context.dataStore.data.map { it[TO_CALENDAR_NAME] }
    val syncIntervalFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_INTERVAL] ?: 60 } // Default 60 mins
    val customDaysPastFlow: Flow<Int?> = context.dataStore.data.map { it[SYNC_DAYS_PAST] }
    val customDaysFutureFlow: Flow<Int?> = context.dataStore.data.map { it[SYNC_DAYS_FUTURE] }
    val syncDaysPastFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_DAYS_PAST] ?: 30 } // Default 30 days back
    val syncDaysFutureFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_DAYS_FUTURE] ?: 30 } // Default 30 days forward
    val lastSyncTimeFlow: Flow<Long?> = context.dataStore.data.map { it[LAST_SYNC_TIME] }
    val lastSyncStatusFlow: Flow<String?> = context.dataStore.data.map { it[LAST_SYNC_STATUS] }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.fromCode(it[THEME_MODE] ?: ThemeMode.AUTO.code)
    }

    val disclaimerAcceptedFlow: Flow<Boolean> = context.dataStore.data.map {
        (it[DISCLAIMER_VERSION_ACCEPTED] ?: 0) >= CURRENT_DISCLAIMER_VERSION
    }

    suspend fun saveFromCalendar(id: Long?, name: String?) {
        context.dataStore.edit { preferences ->
            if (id != null) preferences[FROM_CALENDAR_ID] = id else preferences.remove(FROM_CALENDAR_ID)
            if (name != null) preferences[FROM_CALENDAR_NAME] = name else preferences.remove(FROM_CALENDAR_NAME)
        }
    }

    suspend fun saveToCalendar(id: Long?, name: String?) {
        context.dataStore.edit { preferences ->
            if (id != null) preferences[TO_CALENDAR_ID] = id else preferences.remove(TO_CALENDAR_ID)
            if (name != null) preferences[TO_CALENDAR_NAME] = name else preferences.remove(TO_CALENDAR_NAME)
        }
    }

    suspend fun saveSyncInterval(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_INTERVAL] = interval
        }
    }

    suspend fun saveSyncDaysPast(days: Int?) {
        context.dataStore.edit { preferences ->
            if (days != null) {
                preferences[SYNC_DAYS_PAST] = days
            } else {
                preferences.remove(SYNC_DAYS_PAST)
            }
        }
    }

    suspend fun saveSyncDaysFuture(days: Int?) {
        context.dataStore.edit { preferences ->
            if (days != null) {
                preferences[SYNC_DAYS_FUTURE] = days
            } else {
                preferences.remove(SYNC_DAYS_FUTURE)
            }
        }
    }

    suspend fun saveLastSync(timestamp: Long, status: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME] = timestamp
            if (status != null) {
                preferences[LAST_SYNC_STATUS] = status
            } else {
                preferences.remove(LAST_SYNC_STATUS)
            }
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.code
        }
    }

    suspend fun acceptDisclaimer() {
        context.dataStore.edit { preferences ->
            preferences[DISCLAIMER_VERSION_ACCEPTED] = CURRENT_DISCLAIMER_VERSION
        }
    }
}

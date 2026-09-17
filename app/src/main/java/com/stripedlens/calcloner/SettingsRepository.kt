package com.stripedlens.calcloner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        private val pairsMutex = Mutex()
        private const val PREFS_NAME = "calcloner_shared_prefs"
        private const val KEY_LAST_FINGERPRINT = "last_known_fingerprint"

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
        val SYNC_PAIRS_JSON = stringPreferencesKey("sync_pairs_json")
        val SYNC_ON_LOW_BATTERY = booleanPreferencesKey("sync_on_low_battery")

        const val CURRENT_DISCLAIMER_VERSION = 1
    }

    val syncPairsFlow: Flow<List<SyncPair>> = context.dataStore.data.map { prefs ->
        val json = prefs[SYNC_PAIRS_JSON]
        if (json != null) {
            SyncPair.listFromJsonString(json)
        } else {
            // Check for legacy single-pair configuration and migrate
            val legacyFromId = prefs[FROM_CALENDAR_ID]
            val legacyFromName = prefs[FROM_CALENDAR_NAME] ?: "Source Calendar"
            val legacyToId = prefs[TO_CALENDAR_ID]
            val legacyToName = prefs[TO_CALENDAR_NAME] ?: "Clone Calendar"
            if (legacyFromId != null && legacyToId != null) {
                val legacyPast = prefs[SYNC_DAYS_PAST] ?: AppConstants.Sync.DEFAULT_DAYS_PAST
                val legacyFuture = prefs[SYNC_DAYS_FUTURE] ?: AppConstants.Sync.DEFAULT_DAYS_FUTURE
                val legacyTime = prefs[LAST_SYNC_TIME]
                val legacyStatus = prefs[LAST_SYNC_STATUS]
                listOf(
                    SyncPair.createNew(
                        fromCalendarId = legacyFromId,
                        fromCalendarName = legacyFromName,
                        toCalendarId = legacyToId,
                        toCalendarName = legacyToName,
                        daysPast = legacyPast,
                        daysFuture = legacyFuture,
                        isEnabled = true
                    ).copy(
                        lastSyncTime = legacyTime,
                        lastSyncStatus = legacyStatus
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    val syncIntervalFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_INTERVAL] ?: 0 } // Default Instant (0)
    val lastSyncTimeFlow: Flow<Long?> = context.dataStore.data.map { it[LAST_SYNC_TIME] }
    val lastSyncStatusFlow: Flow<String?> = context.dataStore.data.map { it[LAST_SYNC_STATUS] }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.fromCode(it[THEME_MODE] ?: ThemeMode.AUTO.code)
    }

    val disclaimerAcceptedFlow: Flow<Boolean> = context.dataStore.data.map {
        (it[DISCLAIMER_VERSION_ACCEPTED] ?: 0) >= CURRENT_DISCLAIMER_VERSION
    }

    val syncOnLowBatteryFlow: Flow<Boolean> = context.dataStore.data.map {
        it[SYNC_ON_LOW_BATTERY] ?: false
    }

    fun getSavedFingerprint(): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_LAST_FINGERPRINT, "") ?: ""
    }

    fun saveFingerprint(fingerprint: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_LAST_FINGERPRINT, fingerprint).apply()
    }

    suspend fun saveSyncInterval(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_INTERVAL] = interval
        }
    }

    suspend fun saveSyncOnLowBattery(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_ON_LOW_BATTERY] = enabled
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

    private suspend fun mutatePairs(transform: (MutableList<SyncPair>) -> Unit) {
        pairsMutex.withLock {
            context.dataStore.edit { preferences ->
                val currentPairs = SyncPair.listFromJsonString(preferences[SYNC_PAIRS_JSON]).toMutableList()
                transform(currentPairs)
                preferences[SYNC_PAIRS_JSON] = SyncPair.listToJsonString(currentPairs)
            }
        }
    }

    suspend fun saveSyncPairs(pairs: List<SyncPair>) {
        mutatePairs { list ->
            list.clear()
            list.addAll(pairs)
        }
    }

    suspend fun upsertSyncPair(pair: SyncPair) {
        mutatePairs { list ->
            val index = list.indexOfFirst { it.id == pair.id }
            if (index >= 0) {
                list[index] = pair
            } else {
                list.add(pair)
            }
        }
    }

    suspend fun deleteSyncPair(pairId: String) {
        mutatePairs { list ->
            list.removeAll { it.id == pairId }
        }
    }

    suspend fun togglePairEnabled(pairId: String, isEnabled: Boolean) {
        mutatePairs { list ->
            val index = list.indexOfFirst { it.id == pairId }
            if (index >= 0) {
                list[index] = list[index].copy(isEnabled = isEnabled)
            }
        }
    }

    suspend fun updatePairSyncStatus(
        pairId: String,
        timestamp: Long,
        status: String,
        insertedCount: Int = 0,
        updatedCount: Int = 0,
        deletedCount: Int = 0,
        durationMs: Long? = null
    ) {
        mutatePairs { list ->
            val index = list.indexOfFirst { it.id == pairId }
            if (index >= 0) {
                list[index] = list[index].copy(
                    lastSyncTime = timestamp,
                    lastSyncStatus = status,
                    lastInsertedCount = insertedCount,
                    lastUpdatedCount = updatedCount,
                    lastDeletedCount = deletedCount,
                    lastDurationMs = durationMs
                )
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

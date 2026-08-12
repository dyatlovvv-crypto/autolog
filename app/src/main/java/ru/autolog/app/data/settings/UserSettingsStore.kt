package ru.autolog.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "autolog_settings",
)

enum class AppPalette(val label: String) {
    SYSTEM("Системная"),
    WARM("Тёплая"),
    COOL("Холодная"),
    AMOLED("AMOLED чёрная"),
    DYNAMIC("Material You"),
}

enum class AppBackground(val label: String) {
    SOLID("Однотонный"),
    MESH("Градиент / mesh"),
    CUSTOM("Своя подложка"),
}

enum class ConsumptionUnit(val label: String) {
    L_PER_100("л / 100 км"),
    KM_PER_L("км / л"),
    MPG("миль / галлон"),
}

enum class VolumeUnit(val label: String) {
    LITERS("Литры"),
    GALLONS("Галлоны"),
}

enum class SyncPeriod(val label: String) {
    OFF("Выкл"),
    DAILY("Каждый день"),
    EVERY_3_DAYS("Раз в 3 дня"),
    WEEKLY("Раз в неделю"),
}

data class UserSettings(
    val palette: AppPalette = AppPalette.SYSTEM,
    val background: AppBackground = AppBackground.MESH,
    val customBackgroundUri: String? = null,
    val consumptionUnit: ConsumptionUnit = ConsumptionUnit.L_PER_100,
    val volumeUnit: VolumeUnit = VolumeUnit.LITERS,
    val googleLinked: Boolean = false,
    val googleEmail: String? = null,
    val googleDisplayName: String? = null,
    val googlePhotoUrl: String? = null,
    val syncPeriod: SyncPeriod = SyncPeriod.OFF,
    val lastCloudSyncEpochMs: Long? = null,
)

class UserSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            palette = prefs[KEY_PALETTE]?.let { runCatching { AppPalette.valueOf(it) }.getOrNull() }
                ?: AppPalette.SYSTEM,
            background = prefs[KEY_BACKGROUND]?.let { runCatching { AppBackground.valueOf(it) }.getOrNull() }
                ?: AppBackground.MESH,
            customBackgroundUri = prefs[KEY_CUSTOM_BG],
            consumptionUnit = prefs[KEY_CONSUMPTION]?.let {
                runCatching { ConsumptionUnit.valueOf(it) }.getOrNull()
            } ?: ConsumptionUnit.L_PER_100,
            volumeUnit = prefs[KEY_VOLUME]?.let {
                runCatching { VolumeUnit.valueOf(it) }.getOrNull()
            } ?: VolumeUnit.LITERS,
            googleLinked = prefs[KEY_GOOGLE_EMAIL]?.isNotBlank() == true,
            googleEmail = prefs[KEY_GOOGLE_EMAIL],
            googleDisplayName = prefs[KEY_GOOGLE_NAME],
            googlePhotoUrl = prefs[KEY_GOOGLE_PHOTO],
            syncPeriod = prefs[KEY_SYNC_PERIOD]?.let {
                runCatching { SyncPeriod.valueOf(it) }.getOrNull()
            } ?: SyncPeriod.OFF,
            lastCloudSyncEpochMs = prefs[KEY_LAST_SYNC],
        )
    }

    suspend fun setPalette(value: AppPalette) {
        dataStore.edit { it[KEY_PALETTE] = value.name }
    }

    suspend fun setBackground(value: AppBackground) {
        dataStore.edit { it[KEY_BACKGROUND] = value.name }
    }

    suspend fun setCustomBackgroundUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(KEY_CUSTOM_BG) else prefs[KEY_CUSTOM_BG] = uri
        }
    }

    suspend fun setConsumptionUnit(value: ConsumptionUnit) {
        dataStore.edit { it[KEY_CONSUMPTION] = value.name }
    }

    suspend fun setVolumeUnit(value: VolumeUnit) {
        dataStore.edit { it[KEY_VOLUME] = value.name }
    }

    suspend fun setGoogleAccount(
        email: String,
        displayName: String?,
        photoUrl: String?,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_GOOGLE_EMAIL] = email
            if (displayName.isNullOrBlank()) prefs.remove(KEY_GOOGLE_NAME)
            else prefs[KEY_GOOGLE_NAME] = displayName
            if (photoUrl.isNullOrBlank()) prefs.remove(KEY_GOOGLE_PHOTO)
            else prefs[KEY_GOOGLE_PHOTO] = photoUrl
        }
    }

    suspend fun clearGoogleAccount() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs.remove(KEY_GOOGLE_NAME)
            prefs.remove(KEY_GOOGLE_PHOTO)
            prefs[KEY_SYNC_PERIOD] = SyncPeriod.OFF.name
        }
    }

    suspend fun setSyncPeriod(value: SyncPeriod) {
        dataStore.edit { it[KEY_SYNC_PERIOD] = value.name }
    }

    suspend fun setLastCloudSyncEpochMs(value: Long?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_LAST_SYNC) else prefs[KEY_LAST_SYNC] = value
        }
    }

    private companion object {
        val KEY_PALETTE = stringPreferencesKey("palette")
        val KEY_BACKGROUND = stringPreferencesKey("background")
        val KEY_CUSTOM_BG = stringPreferencesKey("custom_bg_uri")
        val KEY_CONSUMPTION = stringPreferencesKey("consumption_unit")
        val KEY_VOLUME = stringPreferencesKey("volume_unit")
        val KEY_GOOGLE_EMAIL = stringPreferencesKey("google_email")
        val KEY_GOOGLE_NAME = stringPreferencesKey("google_display_name")
        val KEY_GOOGLE_PHOTO = stringPreferencesKey("google_photo_url")
        val KEY_SYNC_PERIOD = stringPreferencesKey("sync_period")
        val KEY_LAST_SYNC = longPreferencesKey("last_cloud_sync_epoch_ms")
    }
}

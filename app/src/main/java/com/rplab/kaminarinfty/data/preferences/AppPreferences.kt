package com.rplab.kaminarinfty.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rplab.kaminarinfty.ThemeSetting
import com.rplab.kaminarinfty.model.DeliveryMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppPreferencesData(
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val isCompact: Boolean = false,
    val sampleDataEnabled: Boolean = false,
    val activeProfileId: String? = null,
    val deliveryMode: DeliveryMode = DeliveryMode.DEFAULT,
)

/** Non-secret user preferences only. Secrets live in [SecretStore]. */
interface AppPreferences {
    val data: Flow<AppPreferencesData>

    suspend fun activeProfileId(): String?
    suspend fun setTheme(theme: ThemeSetting)
    suspend fun setCompact(compact: Boolean)
    suspend fun setSampleDataEnabled(enabled: Boolean)
    suspend fun setActiveProfileId(id: String)
    suspend fun clearActiveProfileId()
    suspend fun setDeliveryMode(mode: DeliveryMode)
}

private val Context.settingsDataStore by preferencesDataStore(name = "kaminari_settings")

class DataStoreAppPreferences(private val context: Context) : AppPreferences {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val COMPACT = booleanPreferencesKey("compact_density")
        val SAMPLE_DATA = booleanPreferencesKey("sample_data_enabled")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile_id")
        val DELIVERY_MODE = stringPreferencesKey("delivery_mode")
    }

    override val data: Flow<AppPreferencesData> = context.settingsDataStore.data.map { prefs ->
        AppPreferencesData(
            theme = runCatching {
                ThemeSetting.valueOf(prefs[Keys.THEME] ?: ThemeSetting.SYSTEM.name)
            }.getOrDefault(ThemeSetting.SYSTEM),
            isCompact = prefs[Keys.COMPACT] ?: false,
            sampleDataEnabled = prefs[Keys.SAMPLE_DATA] ?: false,
            activeProfileId = prefs[Keys.ACTIVE_PROFILE],
            // Legacy `SAVED` values are migrated to BATTERY_SAVER by fromPersisted.
            deliveryMode = DeliveryMode.fromPersisted(prefs[Keys.DELIVERY_MODE]),
        )
    }

    override suspend fun activeProfileId(): String? =
        context.settingsDataStore.data.first()[Keys.ACTIVE_PROFILE]

    override suspend fun setTheme(theme: ThemeSetting) {
        context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setCompact(compact: Boolean) {
        context.settingsDataStore.edit { it[Keys.COMPACT] = compact }
    }

    override suspend fun setSampleDataEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SAMPLE_DATA] = enabled }
    }

    override suspend fun setActiveProfileId(id: String) {
        context.settingsDataStore.edit { it[Keys.ACTIVE_PROFILE] = id }
    }

    override suspend fun clearActiveProfileId() {
        context.settingsDataStore.edit { it.remove(Keys.ACTIVE_PROFILE) }
    }

    override suspend fun setDeliveryMode(mode: DeliveryMode) {
        context.settingsDataStore.edit { it[Keys.DELIVERY_MODE] = mode.name }
    }
}

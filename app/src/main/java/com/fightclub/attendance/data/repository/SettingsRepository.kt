package com.fightclub.attendance.data.repository

import com.fightclub.attendance.data.local.dao.SettingsDao
import com.fightclub.attendance.data.model.AppSettings
import com.fightclub.attendance.data.model.SavedContact
import com.fightclub.attendance.data.model.toDomain
import com.fightclub.attendance.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    /** Emits the current settings, inserting sensible defaults on first use. */
    fun observeSettings(): Flow<AppSettings>

    suspend fun getSettings(): AppSettings

    suspend fun updateSettings(settings: AppSettings)

    suspend fun updateContact(contact: SavedContact)

    suspend fun markFirstLaunchSetupComplete()
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: SettingsDao
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        settingsDao.observe().map { it?.toDomain() ?: AppSettings.DEFAULT }

    override suspend fun getSettings(): AppSettings {
        settingsDao.insertDefaultIfMissing()
        return settingsDao.get()?.toDomain() ?: AppSettings.DEFAULT
    }

    override suspend fun updateSettings(settings: AppSettings) {
        settingsDao.upsert(settings.toEntity())
    }

    override suspend fun updateContact(contact: SavedContact) {
        val current = observeSettings().first()
        updateSettings(current.copy(contact = contact))
    }

    override suspend fun markFirstLaunchSetupComplete() {
        val current = observeSettings().first()
        updateSettings(current.copy(hasCompletedFirstLaunchSetup = true))
    }
}

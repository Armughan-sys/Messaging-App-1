package com.fightclub.attendance.di

import com.fightclub.attendance.data.repository.AttendanceStatusRepository
import com.fightclub.attendance.data.repository.AttendanceStatusRepositoryImpl
import com.fightclub.attendance.data.repository.ContactRepository
import com.fightclub.attendance.data.repository.ContactRepositoryImpl
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.data.repository.SettingsRepositoryImpl
import com.fightclub.attendance.data.repository.SmsLogRepository
import com.fightclub.attendance.data.repository.SmsLogRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSmsLogRepository(impl: SmsLogRepositoryImpl): SmsLogRepository

    @Binds
    @Singleton
    abstract fun bindAttendanceStatusRepository(
        impl: AttendanceStatusRepositoryImpl
    ): AttendanceStatusRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository
}

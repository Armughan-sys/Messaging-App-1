package com.fightclub.attendance.di

import android.content.Context
import androidx.room.Room
import com.fightclub.attendance.data.local.AppDatabase
import com.fightclub.attendance.data.local.dao.AttendanceStatusDao
import com.fightclub.attendance.data.local.dao.MessageLogDao
import com.fightclub.attendance.data.local.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSettingsDao(database: AppDatabase): SettingsDao = database.settingsDao()

    @Provides
    fun provideMessageLogDao(database: AppDatabase): MessageLogDao = database.messageLogDao()

    @Provides
    fun provideAttendanceStatusDao(database: AppDatabase): AttendanceStatusDao =
        database.attendanceStatusDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

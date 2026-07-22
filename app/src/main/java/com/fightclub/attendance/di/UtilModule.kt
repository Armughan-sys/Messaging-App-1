package com.fightclub.attendance.di

import com.fightclub.attendance.automation.WhatsAppSender
import com.fightclub.attendance.automation.WhatsAppSenderImpl
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import com.fightclub.attendance.domain.scheduler.AlarmSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UtilModule {

    @Binds
    @Singleton
    abstract fun bindAlarmScheduler(impl: AlarmSchedulerImpl): AlarmScheduler

    @Binds
    @Singleton
    abstract fun bindWhatsAppSender(impl: WhatsAppSenderImpl): WhatsAppSender
}

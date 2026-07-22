package com.fightclub.attendance

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fightclub.attendance.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Bootstraps Hilt, creates notification channels up front (required
 * before any notification can be posted), and supplies a [HiltWorkerFactory] so that
 * [androidx.work.ListenableWorker] implementations can receive constructor injection, matching
 * the same DI graph used everywhere else in the app.
 */
@HiltAndroidApp
class FightClubApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

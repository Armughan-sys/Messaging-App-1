package com.fightclub.attendance.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.fightclub.attendance.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

/**
 * Confirms [BootReceiver] restores every alarm after a reboot by enqueueing exactly one
 * [com.fightclub.attendance.worker.RescheduleWorker] under a stable unique work name — using
 * ExistingWorkPolicy.REPLACE so a device that reboots twice in a row still ends up with a single
 * pending reschedule job rather than a growing queue.
 */
@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: BootReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(Executors.newSingleThreadExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        receiver = BootReceiver()
    }

    @Test
    fun `BOOT_COMPLETED enqueues the reschedule worker`() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(Constants.WORK_RESCHEDULE_ALARMS)
            .get()

        assertEquals(1, workInfos.size)
        assertTrue(workInfos.first().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `MY_PACKAGE_REPLACED also enqueues the reschedule worker`() {
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(Constants.WORK_RESCHEDULE_ALARMS)
            .get()

        assertEquals(1, workInfos.size)
    }

    @Test
    fun `unrelated intents are ignored`() {
        receiver.onReceive(context, Intent("some.other.action"))

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(Constants.WORK_RESCHEDULE_ALARMS)
            .get()

        assertTrue(workInfos.isEmpty())
    }

    @Test
    fun `repeated boots do not queue up duplicate reschedule jobs`() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(Constants.WORK_RESCHEDULE_ALARMS)
            .get()

        // ExistingWorkPolicy.REPLACE guarantees only the latest enqueue survives.
        assertEquals(1, workInfos.count { it.state == WorkInfo.State.ENQUEUED })
    }
}

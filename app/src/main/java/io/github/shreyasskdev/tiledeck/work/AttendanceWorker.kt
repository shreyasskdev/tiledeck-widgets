package io.github.shreyasskdev.tiledeck.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.shreyasskdev.tiledeck.data.AttendancePrefs
import io.github.shreyasskdev.tiledeck.data.EtlabRepository
import io.github.shreyasskdev.tiledeck.ui.refreshAttendanceWidgets
import java.util.concurrent.TimeUnit

class AttendanceWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AttendancePrefs(applicationContext)
        val username = prefs.getUsername() ?: return Result.failure()
        val password = prefs.getPassword() ?: return Result.failure()

        return try {
            val repo = EtlabRepository()
            val fetchResult = repo.fetchAttendance(username, password)
            prefs.saveLastResult(fetchResult.attendance)
            fetchResult.timetable?.let { prefs.saveLastTimetable(it) }
            refreshAttendanceWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "attendance_periodic_refresh"

        fun schedulePeriodic(context: Context, intervalMinutes: Long? = null) {
            val prefs = AttendancePrefs(context.applicationContext)
            val minutes = intervalMinutes ?: prefs.getRefreshIntervalMinutes()
            val clampedInterval = minutes.coerceAtLeast(15L) // WorkManager minimum is 15 minutes

            val request = PeriodicWorkRequestBuilder<AttendanceWorker>(clampedInterval, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
package com.ivy.wallet.logic.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.wallet.R
import com.ivy.wallet.base.atEndOfDay
import com.ivy.wallet.base.dateNowUTC
import com.ivy.wallet.persistence.SharedPrefs
import com.ivy.wallet.persistence.dao.TransactionDao
import com.ivy.wallet.system.notification.IvyNotificationChannel
import com.ivy.wallet.system.notification.NotificationService
import com.ivy.wallet.ui.IvyActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class TransactionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context, @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val notificationService: NotificationService,
    private val sharedPrefs: SharedPrefs,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val MINIMUM_TRANSACTIONS_PER_DAY = 1
    }

    override suspend fun doWork() = withContext(Dispatchers.IO) {

        val transactionsToday = transactionDao.findAllBetween(
            startDate = dateNowUTC().atStartOfDay(),
            endDate = dateNowUTC().atEndOfDay()
        )

        val showNotifications = fetchShowNotifications()

        //Double check is needed because the user can switch off notifications in settings after it has been scheduled to show notifications for the next day
        if (transactionsToday.size < MINIMUM_TRANSACTIONS_PER_DAY && showNotifications) {
            //Have less than 1 two transactions today, remind them

            val notification = notificationService
                .defaultIvyNotification(
                    channel = IvyNotificationChannel.TRANSACTION_REMINDER,
                    priority = NotificationCompat.PRIORITY_HIGH
                )
                .setContentTitle("Bucket Money")
//                .setContentTitle(applicationContext.getString(R.string.app_name))
                .setContentText(randomText())
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        1,
                        IvyActivity.getIntent(applicationContext),
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT
                                or PendingIntent.FLAG_IMMUTABLE
                    )
                )

            notificationService.showNotification(notification, 1)
        }

        return@withContext Result.success()
    }

    private fun randomText(): String =
        listOf(
            "Have you made any transactions today? \uD83C\uDFC1",
            "Did you track your expenses today? \uD83D\uDCB8",
            "Have you recorded your transactions today? \uD83C\uDFC1",
        ).shuffled().first()

    private fun fetchShowNotifications(): Boolean =
        sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)
}
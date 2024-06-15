package com.ivy.wallet.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ivy.wallet.R
import com.ivy.wallet.persistence.SharedPrefs
import com.ivy.wallet.system.notification.IvyNotificationChannel
import com.ivy.wallet.system.notification.NotificationService
import com.ivy.wallet.ui.IvyActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var sharedPrefs: SharedPrefs

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.tag("SMSReceiver").d("SMS Received")

        if (intent?.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle["pdus"] as Array<*>
                val messages = pdus.map { pdu -> SmsMessage.createFromPdu(pdu as ByteArray) }
                messages.forEach { message ->
                    val msgFrom = message.originatingAddress
                    val msgBody = message.messageBody
                    // Process the message
                    processMessage(context, msgFrom, msgBody)
                }
            }
        }
    }

    // function to check if message contains any of the keywords given in the list
    private fun String.containsAny(keywords: List<String>): Boolean {
        for (keyword in keywords) {
            if (this.contains(keyword, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun processMessage(context: Context?, msgFrom: String?, msgBody: String?) {
        val keywords = listOf(
            "debit",
            "credit",
            "withdraw",
            "deposit",
            "transfer",
            "payment",
            "purchase",
            "transaction",
            "balance",
            "statement",
            "account",
            "loan",
            "interest",
            "money",
        )
        // Check if the message is a financial transaction
        if ((msgBody != null && msgFrom != null) && (msgBody.containsAny(keywords) || msgFrom.containsAny(
                keywords
            ))
        ) {
            // Send a notification to the user
            sendNotification(context, msgBody)
        }
    }

    private fun sendNotification(context: Context?, message: String) {
        val showIncomingNotifications = sharedPrefs.getBoolean(SharedPrefs.SHOW_INCOMING_NOTIFICATIONS, true)
        if (showIncomingNotifications) {
            val notification = notificationService
                .defaultIvyNotification(
                    channel = IvyNotificationChannel.TRANSACTION_REMINDER,
                    priority = NotificationCompat.PRIORITY_LOW
                )
                .setContentTitle("Bucket Money")
//                .setContentTitle(applicationContext.getString(R.string.app_name))
                .setContentText("It seems you have received a financial message. Would you like to record this transaction?")
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        1,
                        context?.let { IvyActivity.getIntent(it) },
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT
                                or PendingIntent.FLAG_IMMUTABLE
                    )
                )

            notificationService.showNotification(notification, 1)
        }
    }
}

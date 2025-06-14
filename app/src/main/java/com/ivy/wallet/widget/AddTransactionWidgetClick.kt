package com.ivy.wallet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.IdRes
import com.ivy.wallet.model.TransactionType
import com.ivy.wallet.ui.IvyActivity
import com.ivy.wallet.widget.AddTransactionWidget

class AddTransactionWidgetClick {
    companion object {
        const val ACTION_ADD_INCOME = "com.ivy.wallet.ACTION_ADD_INCOME"
        const val ACTION_ADD_EXPENSE = "com.ivy.wallet.ACTION_ADD_EXPENSE"
        const val ACTION_ADD_TRANSFER = "com.ivy.wallet.ACTION_ADD_TRANSFER"
    }

    //============================= <HANDLE> =======================================================
    fun handleClick(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ADD_INCOME -> {
                context.startActivity(
                    IvyActivity.addTransactionStart(
                        context = context,
                        type = TransactionType.INCOME
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        setPackage(context.packageName)
                    }
                )
            }
            ACTION_ADD_EXPENSE -> {
                context.startActivity(
                    IvyActivity.addTransactionStart(
                        context = context,
                        type = TransactionType.EXPENSE
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        setPackage(context.packageName)
                    }
                )
            }
            ACTION_ADD_TRANSFER -> {
                context.startActivity(
                    IvyActivity.addTransactionStart(
                        context = context,
                        type = TransactionType.TRANSFER
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        setPackage(context.packageName)
                    }
                )
            }
            else -> return
        }
    }

    //============================= <HANDLE> =======================================================
    //------------------------------ <SETUP> -------------------------------------------------------
    class Setup(
        private val context: Context,
        private val rv: RemoteViews,
        private val appWidgetId: Int
    ) {
        fun clickListener(@IdRes viewId: Int, action: String) {
            val actionIntent = newActionIntent(context, appWidgetId, action)
            rv.setOnClickPendingIntent(viewId, actionIntent)
        }

        private fun newActionIntent(
            context: Context,
            appWidgetId: Int,
            action: String
        ): PendingIntent {
            val intent = Intent(context, AddTransactionWidget::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.action = action
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    } //----------------------------- </SETUP> -------------------------------------------------------
}
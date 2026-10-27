package com.returnables.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.returnables.app.MainActivity
import com.returnables.app.data.PurchaseStore
import com.returnables.app.domain.DeadlineRules
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DeadlineWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!enabled(applicationContext)) return Result.success()
        channel(applicationContext)
        return try {
            PurchaseStore(applicationContext).use { store ->
                val today = LocalDate.now()
                for (purchase in store.all()) for (deadline in DeadlineRules.forPurchase(purchase, today)) {
                    if (deadline.daysRemaining !in 0L..3L) continue
                    val key="${purchase.id}:${deadline.kind}:${deadline.date}:$today"
                    if (store.reminderWasSent(key)) continue
                    val pending=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext, MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    val timing=if(deadline.daysRemaining==0L) "today" else "in ${deadline.daysRemaining} days"
                    val notification=NotificationCompat.Builder(applicationContext,CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("${deadline.kind.name.lowercase().replaceFirstChar { it.uppercase() }} deadline $timing")
                        .setContentText("${purchase.title} · ${purchase.merchant}").setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
                    try { NotificationManagerCompat.from(applicationContext).notify(key.hashCode(),notification) }
                    catch (_: SecurityException) { return Result.success() }
                    store.markReminderSent(key)
                }
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        private const val CHANNEL="purchase-deadlines"
        fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
        fun channel(context: Context) { context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Return and warranty deadlines",NotificationManager.IMPORTANCE_DEFAULT).apply { description="User-confirmed deadlines in the next three days" }) }
        fun schedule(context: Context) {
            channel(context)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("deadline-checks",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<DeadlineWorker>(12,TimeUnit.HOURS).build())
        }
        fun checkNow(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("deadline-check-now",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<DeadlineWorker>().build()) }
    }
}

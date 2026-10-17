package io.github.nicholas_sb.restart.downloader

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app running while the DownloadQueue has work, and shows its progress in a
 * notification ("Downloading 3 of 12"). When everything is done it leaves a summary
 * notification listing the songs that couldn't be downloaded, and stops itself.
 *
 * A *foreground* service is Android's way of saying "the user is waiting for this": the app
 * isn't killed in the background, and in exchange it must show a notification.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue by lazy { DownloadQueue.get(this) }
    private var lastStartId = 0

    override fun onBind(intent: Intent?) = null  // nothing binds to it; it's only started

    override fun onCreate() {
        super.onCreate()
        // The "Compat" version also works before Android 8, which had no channels.
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW).setName("Downloads").build()
        )

        scope.launch {
            queue.progress.collect { progress ->
                if (progress.active) {
                    notify(PROGRESS_ID, progressNotification(progress))
                } else {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    if (progress.total > 0 || progress.failedTitles.isNotEmpty()) notify(DONE_ID, doneNotification(progress))
                    // Only stops if no newer start came in meanwhile (a new batch keeps it going).
                    stopSelf(lastStartId)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        NotificationManagerCompat.from(this).cancel(DONE_ID)  // a new batch: the old summary is outdated
        // Every startForegroundService() must be answered with startForeground(), quickly.
        val notification = progressNotification(queue.progress.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(PROGRESS_ID, notification)
        }
        return START_NOT_STICKY  // if Android kills it, don't restart it: the queue is gone too
    }

    /** Android 15+ limits how long this kind of service may run; stop when told to. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(progress: BatchProgress) =
        baseNotification()
            .setContentTitle(if (progress.total > 0) "Downloading ${minOf(progress.finished + 1, progress.total)} of ${progress.total}" else "Downloading")
            .setContentText(progress.currentStep)
            .setProgress(
                PROGRESS_MAX,
                // The finished songs, plus how far along the current one is.
                if (progress.total > 0) ((progress.finished + progress.currentPercent / 100f) / progress.total * PROGRESS_MAX).toInt() else 0,
                progress.total == 0,  // no songs known yet: an animated bar
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun doneNotification(progress: BatchProgress) =
        baseNotification().apply {
            val failed = progress.failedTitles
            val songsDone = progress.finished - failed.count { !it.startsWith("Playlist: ") }
            setContentTitle(if (failed.isEmpty()) "Downloads done" else "Downloads done · ${failed.size} failed")
            if (failed.isEmpty()) {
                setContentText("$songsDone of ${progress.total} songs ready")
            } else {
                setContentText("Couldn't download: ${failed.joinToString(", ")}")
                // Expanded, it lists them one per line.
                setStyle(NotificationCompat.InboxStyle().also { style ->
                    style.setBigContentTitle("Couldn't download ${failed.size}:")
                    failed.forEach { style.addLine(it) }
                    style.setSummaryText("$songsDone of ${progress.total} songs ready")
                })
            }
            setAutoCancel(true)
        }.build()

    private fun baseNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)

    /** Tapping the notification opens the app (whatever its main screen is). */
    private fun openAppIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun notify(id: Int, notification: Notification) {
        // Without the notification permission this does nothing (downloads still run).
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            @Suppress("MissingPermission")  // checked just above
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val PROGRESS_ID = 2001
        private const val DONE_ID = 2002
        private const val PROGRESS_MAX = 1000

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}

package moe.nikableh.sift

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import moe.nikableh.sift.ui.grouped

/**
 * Tells Android that [MoveRunner] is at work, so the process isn't frozen or dropped once the app
 * leaves the screen, and shows the progress. The run doesn't depend on it: where Android refuses
 * the service, the files still move for as long as the process lives
 */
class MoveService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var lock: PowerManager.WakeLock? = null

    private val update = object : Runnable {
        override fun run() {
            val run = MoveRunner.run ?: return
            getSystemService(NotificationManager::class.java).notify(ID, notification(run))
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) MoveRunner.stop()
        val run = MoveRunner.run
        if (run == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (lock != null) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Moving files", NotificationManager.IMPORTANCE_LOW))
        try {
            startForeground(ID, notification(run), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            // Android 12 and later refuse it once the app has left the screen
            stopSelf()
            return START_NOT_STICKY
        }
        // A foreground service alone lets the CPU sleep when the screen goes off
        lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sift:move").apply {
            setReferenceCounted(false)
            acquire(LIMIT)
        }
        main.postDelayed(update, 1000)
        return START_NOT_STICKY
    }

    /** Android 15 and later allow this kind of service six hours a day and crash an app that ignores the end of them. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(update)
        lock?.release()
        lock = null
    }

    private fun notification(run: MoveRunner.Run): Notification {
        val done = MoveRunner.done
        // The launcher's own intent brings the open task forward. Single top covers a task that was
        // opened some other way, where the same intent would stack a second activity
        val open = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_move)
            .setContentTitle(run.title)
            .setContentText("${done.grouped()} of ${run.total.grouped()}")
            .setProgress(run.total, done, false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
        // Without it Android 12 and later hold the notification back for ten seconds
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (run.cancellable) {
            val stop = PendingIntent.getService(this, 0, Intent(this, MoveService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "Stop", stop).build())
        }
        return builder.build()
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "move"
        private const val ACTION_STOP = "moe.nikableh.sift.STOP_MOVE"
        private const val LIMIT = 6 * 60 * 60 * 1000L

        fun start(ctx: Context) {
            try {
                // Not startForegroundService, which kills the process when a short run ends before
                // the service has shown its notification
                ctx.startService(Intent(ctx, MoveService::class.java))
            } catch (e: Exception) {
                // The app left the screen while Android was asked for write access
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MoveService::class.java))
        }
    }
}

package fr.videoconverter.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import fr.videoconverter.MainActivity
import fr.videoconverter.R
import fr.videoconverter.VideoApp
import kotlinx.coroutines.*

/** Service de premier plan qui exécute la file d'attente (un seul encodage à la fois). */
class ConversionService : Service() {
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val q = (application as VideoApp).queue
        when (intent?.action) {
            ACTION_PAUSE -> { q.pauseAfterCurrent(); return START_NOT_STICKY }
            ACTION_CANCEL -> { q.cancelAll(); return START_NOT_STICKY }
        }
        startForeground(NOTIF_ID, build("Préparation…", -1f), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        if (job?.isActive == true) return START_NOT_STICKY
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "videoconverter:encode").apply { acquire(12 * 60 * 60 * 1000L) }
        job = scope.launch {
            try {
                q.runLoop { text, p -> notify(text, p) }
            } finally {
                val (ok, ko) = q.summary()
                withContext(NonCancellable) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (q.paused.value) notifyEnd("File en pause", "Touchez pour reprendre.")
                    else if (ok + ko > 0) notifyEnd("Conversions terminées", "$ok réussie(s), $ko en erreur.")
                }
                runCatching { wake?.release() }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15+ : limite de durée des services « mediaProcessing » ; la file reste sauvegardée et reprend à la demande. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as VideoApp).queue.pauseAfterCurrent()
        (application as VideoApp).store.log("WARN", "Limite de durée du service Android atteinte : file mise en pause.")
    }

    override fun onDestroy() {
        job?.cancel()
        runCatching { wake?.release() }
        super.onDestroy()
    }

    private fun open(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    private fun action(label: String, act: String): NotificationCompat.Action =
        NotificationCompat.Action(0, label, PendingIntent.getService(this, act.hashCode(), Intent(this, ConversionService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE))

    private fun build(text: String, progress: Float): Notification =
        NotificationCompat.Builder(this, VideoApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("VideoConverter")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open())
            .setProgress(100, (progress * 100).toInt().coerceAtLeast(0), progress < 0)
            .addAction(action("Pause", ACTION_PAUSE))
            .addAction(action("Annuler", ACTION_CANCEL))
            .build()

    private fun notify(text: String, progress: Float) {
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, build(text, progress))
    }

    private fun notifyEnd(title: String, text: String) {
        val n = NotificationCompat.Builder(this, VideoApp.CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(open()).setAutoCancel(true).build()
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID + 1, n)
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_PAUSE = "fr.videoconverter.PAUSE"
        const val ACTION_CANCEL = "fr.videoconverter.CANCEL"
    }
}

package fr.videoconverter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import fr.videoconverter.data.Store
import fr.videoconverter.engine.Ffmpeg
import fr.videoconverter.service.QueueManager

class VideoApp : Application() {
    lateinit var store: Store
        private set
    lateinit var queue: QueueManager
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        Ffmpeg.init()
        queue = QueueManager(this, store)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Conversions", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progression des conversions vidéo"
        })
    }

    companion object { const val CHANNEL = "conversions" }
}

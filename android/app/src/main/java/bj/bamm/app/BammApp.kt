package bj.bamm.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import bj.bamm.app.data.CommunityRepository
import java.util.concurrent.Executors

class BammApp : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(CH_ALERT, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH))
            createNotificationChannel(NotificationChannel(CH_INFO, getString(R.string.channel_info), NotificationManager.IMPORTANCE_DEFAULT))
        }
        io.execute { CommunityRepository(this).sync() }
    }

    companion object {
        const val CH_ALERT = "alerts"
        const val CH_INFO = "info"
        /** Pool unique pour le réseau et l'inférence hors du thread principal. */
        val io = Executors.newFixedThreadPool(2)
    }
}

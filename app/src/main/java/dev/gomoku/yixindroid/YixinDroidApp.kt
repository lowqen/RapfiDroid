package dev.gomoku.yixindroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.gomoku.yixindroid.data.engine.EngineService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YixinDroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createEngineChannel()
    }

    private fun createEngineChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            EngineService.CHANNEL_ID,
            getString(R.string.engine_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.engine_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
